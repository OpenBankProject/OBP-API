/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.v7_0_0

import java.util.Date

import cats.effect.IO
import code.api.Constant.ApiPathZero
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc, generateUUID}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CallContext, CustomJsonFormats}
import code.dynamicEntity.DynamicEntityProvider
import code.retention.Retention._
import code.retention._
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.mapper.QueryParam
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Try

/**
 * This object holds the v7.0.0 data retention endpoints: retention policies, their previews and runs,
 * the approval of runs by a second user, the disposal register, legal holds and the retention
 * archive. See RETENTION_PLAN.md for the model and code.retention for the work itself.
 *
 * BANK_ID is the space: a bank's id, or SYS for the system space. The ResourceDocs declare
 * allowSystemSpace(), so the middleware lets SYS through and checks each Role at the BANK_ID in the
 * URL. Everything is looked up within that space: an id of another space is not found.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700Retention {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val MaxPageSize = 500
  private val DefaultPageSize = 50
  private val MaxNameLength = 255
  private val MaxReasonLength = 2000

  private val tags = apiTagDataRetention :: apiTagApi :: Nil

  // ---- lookups, always within the space of the URL

  private def policyFound(bankId: String, policyId: String, cc: CallContext): Future[RetentionPolicy] =
    Future(RetentionPolicy.find(By(RetentionPolicy.BankId, bankId), By(RetentionPolicy.RetentionPolicyId, policyId)))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), s"$RetentionPolicyNotFound Current RETENTION_POLICY_ID is $policyId.", 404))

  private def runFound(bankId: String, runId: String, cc: CallContext): Future[RetentionRun] =
    Future(RetentionRun.find(By(RetentionRun.BankId, bankId), By(RetentionRun.RetentionRunId, runId)))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), s"$RetentionRunNotFound Current RETENTION_RUN_ID is $runId.", 404))

  private def holdFound(bankId: String, holdId: String, cc: CallContext): Future[RetentionHold] =
    Future(RetentionHold.find(By(RetentionHold.BankId, bankId), By(RetentionHold.RetentionHoldId, holdId)))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), s"$RetentionHoldNotFound Current RETENTION_HOLD_ID is $holdId.", 404))

  private def archiveEntryFound(bankId: String, archiveId: String, cc: CallContext): Future[RetentionArchive] =
    Future(RetentionArchive.find(By(RetentionArchive.BankId, bankId), By(RetentionArchive.RetentionArchiveId, archiveId)))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), s"$RetentionArchiveEntryNotFound Current RETENTION_ARCHIVE_ID is $archiveId.", 404))

  private def check(problem: Option[String], prefix: String, cc: CallContext): Future[Box[Unit]] =
    Helper.booleanToFuture(s"$prefix${problem.getOrElse("")}", 400, Some(cc))(problem.isEmpty)

  // ---- query parameters

  private case class Page(limit: Int, offset: Int)

  private def params(req: Request[IO]): Map[String, String] =
    req.uri.query.params.map { case (k, v) => k -> v.trim }.filter(_._2.nonEmpty)

  private def page(req: Request[IO], cc: CallContext): Future[Page] = {
    val p = params(req)
    val limit = p.get("limit").map(v => Try(v.toInt).getOrElse(-1)).getOrElse(DefaultPageSize)
    val offset = p.get("offset").map(v => Try(v.toInt).getOrElse(-1)).getOrElse(0)
    for {
      _ <- Helper.booleanToFuture(s"${InvalidRetentionQueryParameter}limit must be a whole number from 1 to $MaxPageSize.", 400, Some(cc)) {
        limit >= 1 && limit <= MaxPageSize
      }
      _ <- Helper.booleanToFuture(s"${InvalidRetentionQueryParameter}offset must be a whole number, 0 or more.", 400, Some(cc)) {
        offset >= 0
      }
    } yield Page(limit, offset)
  }

  // ---- policy validation

  private case class PolicySettings(resourceType: String, entityName: Option[String], dateBasis: String,
                                    condition: Option[RetentionCondition], retainDays: Int, archiveDays: Option[Int],
                                    action: String, name: String, description: String)

  /** What is wrong with a policy's settings, if anything. */
  private def policyProblem(bankId: String, s: PolicySettings): Option[String] = {
    def entityExists(name: String): Boolean =
      DynamicEntityProvider.connectorMethodProvider.vend
        .getByEntityName(DynamicEntitySpace.bankIdOrNoneForSystem(bankId), name).isDefined
    if (!resourceTypes.contains(s.resourceType)) Some(s"resource_type must be one of ${resourceTypes.mkString(", ")}.")
    else if (s.resourceType == ResourceDynamicEntityRecord && s.entityName.isEmpty)
      Some("entity_name is required for dynamic_entity_record.")
    else if (s.resourceType != ResourceDynamicEntityRecord && s.entityName.isDefined)
      Some("entity_name is only allowed for dynamic_entity_record.")
    else if (s.resourceType == ResourceDynamicEntityRecord && !entityExists(s.entityName.getOrElse("")))
      Some(s"There is no dynamic entity named ${s.entityName.getOrElse("")} in the space $bankId.")
    else if (s.resourceType == ResourceCustomer && bankId == SystemSpace)
      Some("A Customer belongs to a bank, so a customer policy names a bank, not SYS.")
    else if (!isValidDateBasis(s.resourceType, s.dateBasis))
      Some(s"date_basis must be one of ${dateBases(s.resourceType).mkString(", ")}" +
        (if (s.resourceType == ResourceDynamicEntityRecord) s", or $BasisFieldPrefix<field name>." else "."))
    else if (!actionsFor(s.resourceType).contains(s.action))
      Some(s"action must be one of ${actionsFor(s.resourceType).mkString(", ")} for ${s.resourceType}.")
    else if (s.retainDays < RetentionProps.minimumRetainDays || s.retainDays > MaxDays)
      Some(s"retain_days must be a whole number from ${RetentionProps.minimumRetainDays} (retention.minimum_retain_days) to $MaxDays.")
    else if (s.action == ActionArchive && s.archiveDays.isEmpty)
      Some("archive_days is required for the archive action: how long an archived resource is kept before it is deleted.")
    else if (s.action != ActionArchive && s.archiveDays.isDefined)
      Some("archive_days is only allowed for the archive action.")
    else if (s.archiveDays.exists(d => d < 0 || d > MaxDays)) Some(s"archive_days must be a whole number from 0 to $MaxDays.")
    else if (s.condition.exists(_.value.length > 2000)) Some("condition.value must be at most 2000 characters.")
    else if (s.name.length > MaxNameLength) Some(s"name must be at most $MaxNameLength characters.")
    else None
  }

  private def conditionOf(json: Option[RetentionConditionJsonV700]): Option[RetentionCondition] =
    json.map(c => RetentionCondition(Option(c.field).map(_.trim).getOrElse(""), Option(c.value).getOrElse("")))
      .filter(_.field.nonEmpty)

  private def save(policy: RetentionPolicy, s: PolicySettings, cascade: Boolean, requiresApproval: Boolean, enabled: Boolean): RetentionPolicy =
    policy
      .Name(s.name)
      .Description(s.description)
      .ResourceType(s.resourceType)
      .EntityName(s.entityName.getOrElse(""))
      .DateBasis(s.dateBasis)
      .ConditionField(s.condition.map(_.field).getOrElse(""))
      .ConditionValue(s.condition.map(_.value).getOrElse(""))
      .RetainDays(s.retainDays)
      .ArchiveDays(s.archiveDays.getOrElse(-1))
      .Action(s.action)
      .Cascade(cascade)
      .RequiresApproval(requiresApproval)
      .Enabled(enabled)
      .saveMe()

  private val policyDescription =
    s"""A retention policy says how long one kind of resource of a space stays live, and what happens to it then.
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.
       |Periods are configuration, set here per space, never in code.
       |
       |* `resource_type`: one of ${resourceTypes.mkString(", ")}.
       |* `entity_name`: the dynamic entity, for `dynamic_entity_record` only. It must exist in the space.
       |* `date_basis`: what the period is counted from. `created` or `updated` for dynamic entity records,
       |  chat messages and customers; or, for dynamic entity records, `field:<name>`, the ISO 8601 date in that
       |  field of the record. `created` for files. `created`, `updated` or `last_activity` (the last message,
       |  or creation when there was none) for chat rooms. `created`, `updated` or `expires` (the expiry of the
       |  Consent's token; the default) for consents. Defaults to `created` otherwise.
       |* `condition`: optional, `{"field": "status", "value": "CLOSED"}`. Only resources whose field has this
       |  value (compared as text) are governed. For a dynamic entity record the field is one of the record's own;
       |  for the other types it is a field of the resource's JSON copy, such as `media_type`, `message_type`,
       |  `customer_type` or `status`.
       |* `retain_days`: how long a resource stays live after its trigger date. At least `retention.minimum_retain_days`
       |  (1825 days, five years, by default).
       |* `action`: `archive` (move it out of the live tables into the retention archive, readable only with
       |  CanGetRetentionArchive, then delete it after `archive_days`), `delete` (final), or, for customers
       |  only, `anonymise` (clear the personal fields and delete the attributes, addresses, dependants, tax
       |  residences and links to Users, keeping the customer id so that references stay valid).
       |  Archiving is not an undo: nothing is ever restored from the archive.
       |* `archive_days`: required for `archive`, not allowed otherwise.
       |* `cascade`: files attached to a disposed resource go with it (a dynamic entity record's files are attached
       |  with the entity name as record type; a customer's with `customer`). A file still attached to another
       |  record stays. Default false: the attachments go, the files stay.
       |* `requires_approval`: an execution waits as pending until a second, different user approves it.
       |* `enabled`: whether the retention scheduler runs the policy. Default true. A run can always be
       |  started by hand.
       |
       |Every disposal is written to the disposal register. Resources on legal hold are skipped by every run.
       |A run that would archive is refused unless `retention.archive.enabled` is true, and one that would delete
       |or anonymise unless `retention.delete.enabled` is true. See RETENTION_PLAN.md.""".stripMargin

  private val errorsOnPolicyWrite = List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
    InvalidRetentionPolicy, UnknownError)

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/retention-policies (201)
  lazy val createRetentionPolicy: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankId / "retention-policies" =>
      EndpointHelpers.withUserAndBodyCreated[PostRetentionPolicyJsonV700, RetentionPolicyJsonV700](req) { (user, body, cc) =>
        val resourceType = Option(body.resource_type).map(_.trim).getOrElse("")
        val settings = PolicySettings(
          resourceType = resourceType,
          entityName = body.entity_name.map(_.trim).filter(_.nonEmpty),
          dateBasis = body.date_basis.map(_.trim).filter(_.nonEmpty).getOrElse(defaultDateBasis(resourceType)),
          condition = conditionOf(body.condition),
          retainDays = body.retain_days,
          archiveDays = body.archive_days,
          action = Option(body.action).map(_.trim).getOrElse(""),
          name = body.name.map(_.trim).getOrElse(""),
          description = body.description.getOrElse(""))
        for {
          problem <- Future(policyProblem(bankId, settings))
          _ <- check(problem, InvalidRetentionPolicy, cc)
          created <- Future(save(RetentionPolicy.create.RetentionPolicyId(generateUUID()).BankId(bankId).CreatedByUserId(user.userId),
            settings, body.cascade.getOrElse(false), body.requires_approval.getOrElse(false), body.enabled.getOrElse(true)))
        } yield JSONFactory700Retention.createPolicyJson(created)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createRetentionPolicy),
    "POST",
    "/management/banks/BANK_ID/retention-policies",
    "Create Retention Policy",
    s"""Create a retention policy for a space.
       |
       |Only `resource_type`, `retain_days` and `action` are required (and `entity_name` and `archive_days` where
       |the type and action call for them).
       |
       |$policyDescription""".stripMargin,
    JSONFactory700Retention.postRetentionPolicyJsonV700Example,
    JSONFactory700Retention.retentionPolicyJsonV700Example,
    errorsOnPolicyWrite,
    tags,
    Some(canManageRetentionPolicy :: Nil),
    http4sPartialFunction = Some(createRetentionPolicy)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-policies
  lazy val getRetentionPolicies: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-policies" =>
      EndpointHelpers.withUser(req) { (_, _) =>
        Future(RetentionPolicy.findAll(By(RetentionPolicy.BankId, bankId), OrderBy(RetentionPolicy.id, Ascending)))
          .map(policies => RetentionPoliciesJsonV700(policies.map(JSONFactory700Retention.createPolicyJson)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionPolicies),
    "GET",
    "/management/banks/BANK_ID/retention-policies",
    "Get Retention Policies",
    s"""The retention policies of a space, oldest first.
       |
       |$policyDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionPoliciesJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    tags,
    Some(canGetRetentionPolicies :: canManageRetentionPolicy :: Nil),
    http4sPartialFunction = Some(getRetentionPolicies)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID
  lazy val getRetentionPolicy: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-policies" / policyId if policyId.nonEmpty =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        policyFound(bankId, policyId, cc).map(JSONFactory700Retention.createPolicyJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionPolicy),
    "GET",
    "/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID",
    "Get Retention Policy",
    s"""One retention policy of a space.
       |
       |$policyDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionPolicyJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, RetentionPolicyNotFound, UnknownError),
    tags,
    Some(canGetRetentionPolicies :: canManageRetentionPolicy :: Nil),
    http4sPartialFunction = Some(getRetentionPolicy)
  ).allowSystemSpace()

  // Route: PUT /obp/v7.0.0/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID
  lazy val updateRetentionPolicy: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "management" / "banks" / bankId / "retention-policies" / policyId if policyId.nonEmpty =>
      EndpointHelpers.withUserAndBody[PutRetentionPolicyJsonV700, RetentionPolicyJsonV700](req) { (_, body, cc) =>
        for {
          policy <- policyFound(bankId, policyId, cc)
          action = body.action.map(_.trim).getOrElse(policy.action)
          settings = PolicySettings(
            resourceType = policy.resourceType,
            entityName = policy.entityName,
            dateBasis = body.date_basis.map(_.trim).filter(_.nonEmpty).getOrElse(policy.dateBasis),
            condition = if (body.condition.isDefined) conditionOf(body.condition) else policy.condition,
            retainDays = body.retain_days.getOrElse(policy.retainDays),
            archiveDays = if (action != ActionArchive) None else body.archive_days.orElse(policy.archiveDays),
            action = action,
            name = body.name.map(_.trim).getOrElse(policy.name),
            description = body.description.getOrElse(policy.description))
          problem <- Future(policyProblem(bankId, settings))
          _ <- check(problem, InvalidRetentionPolicy, cc)
          updated <- Future(save(policy, settings, body.cascade.getOrElse(policy.cascade),
            body.requires_approval.getOrElse(policy.requiresApproval), body.enabled.getOrElse(policy.enabled)))
        } yield JSONFactory700Retention.createPolicyJson(updated)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateRetentionPolicy),
    "PUT",
    "/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID",
    "Update Retention Policy",
    s"""Change a retention policy. Every field is optional, and a field left out keeps its value. The resource type
       |and the entity name cannot change: create another policy instead. A `condition` whose `field` is empty
       |removes the condition. `archive_days` is cleared when the action becomes anything but `archive`.
       |
       |A change applies to runs started after it; a pending run executes with the policy as it is when approved.
       |
       |$policyDescription""".stripMargin,
    JSONFactory700Retention.putRetentionPolicyJsonV700Example,
    JSONFactory700Retention.retentionPolicyJsonV700Example,
    RetentionPolicyNotFound :: errorsOnPolicyWrite,
    tags,
    Some(canManageRetentionPolicy :: Nil),
    http4sPartialFunction = Some(updateRetentionPolicy)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID (204)
  lazy val deleteRetentionPolicy: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "management" / "banks" / bankId / "retention-policies" / policyId if policyId.nonEmpty =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        for {
          policy <- policyFound(bankId, policyId, cc)
          _ <- Future {
            RetentionRun.findAll(By(RetentionRun.RetentionPolicyId, policyId), By(RetentionRun.Status, StatusPending)).foreach(
              _.Status(StatusRefused).EndedAt(new Date()).Message("The policy of this run has been deleted.").saveMe())
            policy.delete_!
          }
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteRetentionPolicy),
    "DELETE",
    "/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID",
    "Delete Retention Policy",
    s"""Delete a retention policy. Its pending runs are refused. Its runs, the disposal register and what it
       |archived stay; archived resources are still deleted from the archive when their archive period ends (by the
       |retention scheduler, with `retention.delete.enabled`).
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, RetentionPolicyNotFound, UnknownError),
    tags,
    Some(canManageRetentionPolicy :: Nil),
    http4sPartialFunction = Some(deleteRetentionPolicy)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID/preview
  lazy val getRetentionPolicyPreview: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-policies" / policyId / "preview" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        for {
          policy <- policyFound(bankId, policyId, cc)
          result <- Future(RetentionEngine.preview(policy))
        } yield JSONFactory700Retention.createPreviewJson(policy, result)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionPolicyPreview),
    "GET",
    "/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID/preview",
    "Get Retention Policy Preview",
    s"""A dry run: what a run of the policy would dispose of now. Nothing is changed or recorded, and a preview works
       |whatever the props say.
       |
       |* `cutoff_date`: a resource whose trigger date is on or before it is due. It counts `retain_days`, or
       |  `minimum_retain_days` (`retention.minimum_retain_days`) if that is longer.
       |* `matched_count`: the resources due; `held_count` of them are on legal hold and would be skipped;
       |  `due_count` is the rest. One run disposes of at most `max_items_per_run`.
       |* `oldest_trigger_date` and up to ${RetentionEngine.SampleSize} `sample_resource_ids` not on hold.
       |* `archive_entries_due_count`: resources this policy archived whose archive period has ended.
       |* `count_is_capped`: counting stopped at ${RetentionEngine.PreviewScanLimit}.
       |* `can_execute`: whether `retention.archive.enabled` / `retention.delete.enabled` allow an execution now.
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionPolicyPreviewJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, RetentionPolicyNotFound, UnknownError),
    tags,
    Some(canGetRetentionPolicies :: canRunRetentionPolicy :: canManageRetentionPolicy :: Nil),
    http4sPartialFunction = Some(getRetentionPolicyPreview)
  ).allowSystemSpace()

  /** A refused run is recorded, then answered with the reason and the run's id. */
  private def answerRun(run: RetentionRun, cc: CallContext): Future[RetentionRunJsonV700] =
    Helper.booleanToFuture(s"${run.message} The refused run is recorded as RETENTION_RUN_ID ${run.retentionRunId}.", 400, Some(cc)) {
      run.status != StatusRefused
    }.map(_ => JSONFactory700Retention.createRunJson(run))

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID/runs (201)
  lazy val createRetentionRun: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankId / "retention-policies" / policyId / "runs" =>
      EndpointHelpers.withUserAndStatus(req) { (user, cc) =>
        val rawBody = cc.httpBody.getOrElse("").trim
        val body: Option[PostRetentionRunJsonV700] =
          if (rawBody.isEmpty) Some(PostRetentionRunJsonV700())
          else Try(com.openbankproject.commons.util.JsonAliases.parse(rawBody).extract[PostRetentionRunJsonV700]).toOption
        val mode = body.flatMap(_.mode).map(_.trim).filter(_.nonEmpty).getOrElse(ModeExecute)
        for {
          _ <- Helper.booleanToFuture(s"$InvalidJsonFormat The Json body should be the PostRetentionRunJsonV700 ", 400, Some(cc))(body.isDefined)
          _ <- Helper.booleanToFuture(InvalidRetentionRun, 400, Some(cc))(modes.contains(mode))
          policy <- policyFound(bankId, policyId, cc)
          _ <- Helper.booleanToFuture(RetentionRunAlreadyInProgress, 409, Some(cc)) {
            mode == ModePreview || !RetentionEngine.isRunning(policy.retentionPolicyId)
          }
          run <- Future(RetentionEngine.requestRun(policy, mode, user.userId))
          json <- answerRun(run, cc)
        } yield (json, 201)
      }
  }

  private val runDescription =
    s"""A run has a `mode`, `preview` (counts only, nothing changes) or `execute`, and a `status`: `pending` (waiting for
       |approval by a second user), `running`, `done`, `refused` (the props switch its action off, the approver rejected
       |it, or its policy was deleted) or `failed` (some resources could not be disposed of and were left in place; see
       |`message`). The counts include what went with a resource: cascaded files, and a chat room's messages.
       |`requested_by_user_id` is left out when the retention scheduler requested the run.
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.""".stripMargin

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createRetentionRun),
    "POST",
    "/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID/runs",
    "Create Retention Run",
    s"""Run a retention policy now. The body is optional: `{"mode": "execute"}` (the default) or `{"mode": "preview"}`.
       |
       |An execution of a policy that requires approval is created as `pending` and runs when a different user approves
       |it with Update Retention Run Approval. Otherwise it runs now and the response shows its outcome. One run disposes
       |of at most `retention.run.max_items` resources; the next run continues.
       |
       |An execution is refused, with 400 and a recorded `refused` run, when `retention.archive.enabled` (for `archive`)
       |or `retention.delete.enabled` (for `delete` and `anonymise`) is not true. A preview is never refused.
       |
       |$runDescription""".stripMargin,
    JSONFactory700Retention.postRetentionRunJsonV700Example,
    JSONFactory700Retention.retentionRunJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidRetentionRun,
      RetentionPolicyNotFound, RetentionRunAlreadyInProgress, RetentionArchiveIsDisabled, RetentionDeleteIsDisabled, UnknownError),
    tags,
    Some(canRunRetentionPolicy :: Nil),
    http4sPartialFunction = Some(createRetentionRun)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-runs
  lazy val getRetentionRuns: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-runs" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val p = params(req)
        for {
          pg <- page(req, cc)
          _ <- Helper.booleanToFuture(s"${InvalidRetentionQueryParameter}status must be one of ${statuses.mkString(", ")}.", 400, Some(cc)) {
            p.get("status").forall(statuses.contains)
          }
          runs <- Future(RetentionRun.findAll(
            (List[QueryParam[RetentionRun]](By(RetentionRun.BankId, bankId)) :::
              p.get("retention_policy_id").map(id => By(RetentionRun.RetentionPolicyId, id)).toList :::
              p.get("status").map(s => By(RetentionRun.Status, s)).toList :::
              List(OrderBy(RetentionRun.id, Descending), StartAt[RetentionRun](pg.offset), MaxRows[RetentionRun](pg.limit))): _*))
        } yield RetentionRunsJsonV700(runs.map(JSONFactory700Retention.createRunJson))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionRuns),
    "GET",
    "/management/banks/BANK_ID/retention-runs",
    "Get Retention Runs",
    s"""The retention runs of a space, newest first. Optional query parameters: `retention_policy_id`, `status`,
       |`limit` (1 to $MaxPageSize, default $DefaultPageSize) and `offset` (default 0).
       |
       |$runDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionRunsJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidRetentionQueryParameter, UnknownError),
    tags,
    Some(canGetRetentionRuns :: Nil),
    http4sPartialFunction = Some(getRetentionRuns)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID
  lazy val getRetentionRun: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-runs" / runId if runId.nonEmpty =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        runFound(bankId, runId, cc).map(JSONFactory700Retention.createRunJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionRun),
    "GET",
    "/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID",
    "Get Retention Run",
    s"""One retention run of a space.
       |
       |$runDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionRunJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, RetentionRunNotFound, UnknownError),
    tags,
    Some(canGetRetentionRuns :: Nil),
    http4sPartialFunction = Some(getRetentionRun)
  ).allowSystemSpace()

  // Route: PUT /obp/v7.0.0/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID/approval
  lazy val updateRetentionRunApproval: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "management" / "banks" / bankId / "retention-runs" / runId / "approval" =>
      EndpointHelpers.withUserAndBody[PutRetentionRunApprovalJsonV700, RetentionRunJsonV700](req) { (user, body, cc) =>
        val decision = Option(body.decision).map(_.trim.toLowerCase).getOrElse("")
        for {
          _ <- Helper.booleanToFuture(InvalidRetentionRunApproval, 400, Some(cc)) {
            decision == DecisionApprove || decision == DecisionReject
          }
          run <- runFound(bankId, runId, cc)
          _ <- Helper.booleanToFuture(s"$RetentionRunNotPending Current status is ${run.status}.", 400, Some(cc))(run.status == StatusPending)
          _ <- Helper.booleanToFuture(RetentionRunApproverIsRequester, 403, Some(cc))(run.requestedByUserId != user.userId)
          policy = run.retentionPolicyId.flatMap(id =>
            RetentionPolicy.find(By(RetentionPolicy.BankId, bankId), By(RetentionPolicy.RetentionPolicyId, id)).toOption)
          _ <- Helper.booleanToFuture(RetentionRunAlreadyInProgress, 409, Some(cc)) {
            decision == DecisionReject || policy.forall(p => !RetentionEngine.isRunning(p.retentionPolicyId))
          }
          decided <- Future(RetentionEngine.decide(run, policy, user.userId, decision == DecisionApprove,
            body.comment.map(_.trim).filter(_.nonEmpty)))
          json <- if (decision == DecisionReject) Future.successful(JSONFactory700Retention.createRunJson(decided))
                  else answerRun(decided, cc)
        } yield json
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateRetentionRunApproval),
    "PUT",
    "/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID/approval",
    "Update Retention Run Approval",
    s"""Approve or reject a pending run: `{"decision": "approve"}` or `{"decision": "reject"}`, with an optional
       |`comment`. The approver must be a different user from the one who requested the run (403 otherwise). A run the
       |retention scheduler requested may be approved by any user holding the Role.
       |
       |An approved run executes now and the response shows its outcome. It is refused, with 400, if the props have
       |switched its action off since it was requested, or if its policy has been deleted. A rejected run ends as
       |`refused` with `approval_decision` `rejected`.
       |
       |$runDescription""".stripMargin,
    JSONFactory700Retention.putRetentionRunApprovalJsonV700Example,
    JSONFactory700Retention.retentionRunJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidRetentionRunApproval,
      RetentionRunNotFound, RetentionRunNotPending, RetentionRunApproverIsRequester, RetentionRunAlreadyInProgress,
      RetentionArchiveIsDisabled, RetentionDeleteIsDisabled, UnknownError),
    tags,
    Some(canApproveRetentionRun :: Nil),
    http4sPartialFunction = Some(updateRetentionRunApproval)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID/items
  lazy val getRetentionRunItems: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-runs" / runId / "items" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        for {
          pg <- page(req, cc)
          run <- runFound(bankId, runId, cc)
          items <- Future(RetentionRunItem.findAll(By(RetentionRunItem.RetentionRunId, run.retentionRunId),
            OrderBy(RetentionRunItem.id, Ascending), StartAt[RetentionRunItem](pg.offset), MaxRows[RetentionRunItem](pg.limit)))
        } yield RetentionRunItemsJsonV700(items.map(JSONFactory700Retention.createRunItemJson))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionRunItems),
    "GET",
    "/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID/items",
    "Get Retention Run Items",
    s"""The disposal register of one run: one item per resource it archived, anonymised or deleted, and per archived
       |resource it deleted from the archive (`delete_from_archive`), in the order they were disposed of. Identifiers
       |only, never content, so the register outlives the resources. A cascaded file, or a message of a disposed chat
       |room, names its parent in `parent_resource_type` and `parent_resource_id`. Optional query parameters: `limit`
       |(1 to $MaxPageSize, default $DefaultPageSize) and `offset` (default 0).
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionRunItemsJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidRetentionQueryParameter, RetentionRunNotFound, UnknownError),
    tags,
    Some(canGetRetentionRuns :: Nil),
    http4sPartialFunction = Some(getRetentionRunItems)
  ).allowSystemSpace()

  private val holdDescription =
    s"""A legal hold keeps resources out of every retention run until it is released: they are not archived,
       |anonymised or deleted, and an archived resource on hold is not deleted from the archive. A hold names one
       |resource by `resource_type` and `resource_id` (for a dynamic entity record, `entity_name` may be given too), or,
       |for `dynamic_entity_record`, every record of one dynamic entity by `entity_name` alone. A consent is named by its
       |`consent_reference_id`. A chat room with a held message is kept whole.
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.""".stripMargin

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/retention-holds (201)
  lazy val createRetentionHold: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankId / "retention-holds" =>
      EndpointHelpers.withUserAndBodyCreated[PostRetentionHoldJsonV700, RetentionHoldJsonV700](req) { (user, body, cc) =>
        val resourceType = Option(body.resource_type).map(_.trim).getOrElse("")
        val resourceId = body.resource_id.map(_.trim).filter(_.nonEmpty)
        val entityName = body.entity_name.map(_.trim).filter(_.nonEmpty)
        val reason = Option(body.reason).map(_.trim).getOrElse("")
        val problem =
          if (!resourceTypes.contains(resourceType)) Some(s"resource_type must be one of ${resourceTypes.mkString(", ")}.")
          else if (entityName.isDefined && resourceType != ResourceDynamicEntityRecord) Some("entity_name is only allowed for dynamic_entity_record.")
          else if (resourceId.isEmpty && entityName.isEmpty) Some("resource_id is required, or, for dynamic_entity_record, entity_name.")
          else if (reason.isEmpty) Some("reason is required.")
          else if (reason.length > MaxReasonLength) Some(s"reason must be at most $MaxReasonLength characters.")
          else None
        for {
          _ <- check(problem, InvalidRetentionHold, cc)
          hold <- Future(RetentionHold.create
            .RetentionHoldId(generateUUID())
            .BankId(bankId)
            .ResourceType(resourceType)
            .ResourceId(resourceId.getOrElse(""))
            .EntityName(entityName.getOrElse(""))
            .Reason(reason)
            .PlacedByUserId(user.userId)
            .PlacedAt(new Date())
            .saveMe())
        } yield JSONFactory700Retention.createHoldJson(hold)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createRetentionHold),
    "POST",
    "/management/banks/BANK_ID/retention-holds",
    "Create Retention Hold",
    s"""Place a legal hold. `reason` is required.
       |
       |$holdDescription""".stripMargin,
    JSONFactory700Retention.postRetentionHoldJsonV700Example,
    JSONFactory700Retention.retentionHoldJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidRetentionHold, UnknownError),
    tags,
    Some(canManageRetentionHold :: Nil),
    http4sPartialFunction = Some(createRetentionHold)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-holds
  lazy val getRetentionHolds: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-holds" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val p = params(req)
        for {
          pg <- page(req, cc)
          _ <- Helper.booleanToFuture(s"${InvalidRetentionQueryParameter}is_active must be true or false.", 400, Some(cc)) {
            p.get("is_active").forall(v => v == "true" || v == "false")
          }
          holds <- Future(RetentionHold.findAll(
            (List[QueryParam[RetentionHold]](By(RetentionHold.BankId, bankId)) :::
              p.get("resource_type").map(t => By(RetentionHold.ResourceType, t)).toList :::
              p.get("resource_id").map(id => By(RetentionHold.ResourceId, id)).toList :::
              p.get("is_active").map(v => if (v == "true") NullRef(RetentionHold.ReleasedAt) else NotNullRef(RetentionHold.ReleasedAt)).toList :::
              List(OrderBy(RetentionHold.id, Descending), StartAt[RetentionHold](pg.offset), MaxRows[RetentionHold](pg.limit))): _*))
        } yield RetentionHoldsJsonV700(holds.map(JSONFactory700Retention.createHoldJson))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionHolds),
    "GET",
    "/management/banks/BANK_ID/retention-holds",
    "Get Retention Holds",
    s"""The legal holds of a space, newest first, released ones included. Optional query parameters: `is_active`
       |(true or false), `resource_type`, `resource_id`, `limit` (1 to $MaxPageSize, default $DefaultPageSize) and
       |`offset` (default 0).
       |
       |$holdDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionHoldsJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidRetentionQueryParameter, UnknownError),
    tags,
    Some(canManageRetentionHold :: canGetRetentionPolicies :: Nil),
    http4sPartialFunction = Some(getRetentionHolds)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/management/banks/BANK_ID/retention-holds/RETENTION_HOLD_ID (204)
  lazy val deleteRetentionHold: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "management" / "banks" / bankId / "retention-holds" / holdId if holdId.nonEmpty =>
      EndpointHelpers.withUserDelete(req) { (user, cc) =>
        for {
          hold <- holdFound(bankId, holdId, cc)
          _ <- Helper.booleanToFuture(RetentionHoldAlreadyReleased, 400, Some(cc))(hold.isActive)
          _ <- Future(hold.ReleasedByUserId(user.userId).ReleasedAt(new Date()).saveMe())
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteRetentionHold),
    "DELETE",
    "/management/banks/BANK_ID/retention-holds/RETENTION_HOLD_ID",
    "Delete Retention Hold",
    s"""Release a legal hold. The hold is kept, with who released it and when, and is listed by Get Retention Holds
       |with `is_active` false; the next run no longer skips what it named.
       |
       |$holdDescription""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, RetentionHoldNotFound, RetentionHoldAlreadyReleased, UnknownError),
    tags,
    Some(canManageRetentionHold :: Nil),
    http4sPartialFunction = Some(deleteRetentionHold)
  ).allowSystemSpace()

  private val archiveDescription =
    s"""The retention archive holds what `archive` policies moved out of the live tables: a read-only JSON copy of each
       |resource (and a file's bytes), until `delete_after`, when it is deleted. Nothing in it is reachable through any
       |other endpoint, and nothing is ever restored from it. A cascaded file names the record it went with in
       |`parent_resource_type` and `parent_resource_id`; a chat room's messages are inside the room's copy.
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.""".stripMargin

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-archive
  lazy val getRetentionArchiveEntries: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-archive" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val p = params(req)
        for {
          pg <- page(req, cc)
          entries <- Future(RetentionArchive.findAll(
            (List[QueryParam[RetentionArchive]](By(RetentionArchive.BankId, bankId)) :::
              p.get("resource_type").map(v => By(RetentionArchive.ResourceType, v)).toList :::
              p.get("resource_id").map(v => By(RetentionArchive.ResourceId, v)).toList :::
              p.get("entity_name").map(v => By(RetentionArchive.EntityName, v)).toList :::
              p.get("owner_user_id").map(v => By(RetentionArchive.OwnerUserId, v)).toList :::
              p.get("retention_policy_id").map(v => By(RetentionArchive.RetentionPolicyId, v)).toList :::
              p.get("retention_run_id").map(v => By(RetentionArchive.RetentionRunId, v)).toList :::
              List(OrderBy(RetentionArchive.id, Descending), StartAt[RetentionArchive](pg.offset), MaxRows[RetentionArchive](pg.limit))): _*))
        } yield RetentionArchiveEntriesJsonV700(entries.map(JSONFactory700Retention.createArchiveEntryJson(_, withData = false, None)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionArchiveEntries),
    "GET",
    "/management/banks/BANK_ID/retention-archive",
    "Get Retention Archive Entries",
    s"""The entries of a space's retention archive, newest first, without their content. Optional query parameters,
       |each matching exactly: `resource_type`, `resource_id`, `entity_name`, `owner_user_id`, `retention_policy_id`,
       |`retention_run_id`; and `limit` (1 to $MaxPageSize, default $DefaultPageSize) and `offset` (default 0).
       |
       |$archiveDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionArchiveEntriesJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidRetentionQueryParameter, UnknownError),
    tags,
    Some(canGetRetentionArchive :: Nil),
    http4sPartialFunction = Some(getRetentionArchiveEntries)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/retention-archive/RETENTION_ARCHIVE_ID
  lazy val getRetentionArchiveEntry: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankId / "retention-archive" / archiveId if archiveId.nonEmpty =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val includeContent = params(req).get("include_content")
        for {
          _ <- Helper.booleanToFuture(s"${InvalidRetentionQueryParameter}include_content must be true or false.", 400, Some(cc)) {
            includeContent.forall(v => v == "true" || v == "false")
          }
          entry <- archiveEntryFound(bankId, archiveId, cc)
          content <- Future(
            if (includeContent.contains("true") && entry.hasContent)
              RetentionArchiveContent.find(By(RetentionArchiveContent.RetentionArchiveId, entry.retentionArchiveId))
                .toOption.flatMap(_.content)
            else None)
        } yield JSONFactory700Retention.createArchiveEntryJson(entry, withData = true, content)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getRetentionArchiveEntry),
    "GET",
    "/management/banks/BANK_ID/retention-archive/RETENTION_ARCHIVE_ID",
    "Get Retention Archive Entry",
    s"""One entry of a space's retention archive, with its JSON copy in `data`. With `include_content=true`, a file's
       |bytes are given too, Base64 encoded, in `content_base64`.
       |
       |$archiveDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Retention.retentionArchiveEntryJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidRetentionQueryParameter,
      RetentionArchiveEntryNotFound, UnknownError),
    tags,
    Some(canGetRetentionArchive :: Nil),
    http4sPartialFunction = Some(getRetentionArchiveEntry)
  ).allowSystemSpace()
}
