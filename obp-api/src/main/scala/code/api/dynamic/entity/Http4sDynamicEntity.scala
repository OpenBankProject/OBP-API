/**
Open Bank Project - API
Copyright (C) 2011-2025, TESOBE GmbH

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
TESOBE GmbH
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.dynamic.entity

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.DynamicData.{DynamicData, DynamicDataProvider, DynamicDataAccessProvider, DynamicDataAccessPermission}
import code.api.Constant.PARAM_LOCALE
import code.api.dynamic.entity.helper.{CommunityEntityName, DynamicEntityHelper, DynamicEntityInfo, EntityAccessName, EntityName, PublicEntityName}
import code.api.dynamic.entity.query.{FieldSpec, InMemoryQueryExecutor, JoinTargetInfo, QueryParamParser, QueryPlan, QueryPlanner}
import code.api.dynamic.entity.projection.{IndexingCapabilities, PostgresProjectionBackend, ProjectionProvisioner}
import cats.effect.unsafe.implicits.{global => ioRuntime} // aliased: avoids clashing with the EC `global` imported below
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.{Http4sCallContextBuilder, Http4sRequestAttributes, RequestScopeConnection}
import code.api.util.{CallContext, CustomJsonFormats, NewStyle}
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityOperation
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.util.{ApiShortVersions, ApiStandards, JsonUtils}
import net.liftweb.common._
import org.json4s.JsonAST.{JArray, JBool, JObject, JValue}
import org.json4s.JsonDSL._
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils
import org.http4s.{HttpRoutes, Method, Request, Response}

import scala.concurrent.Future

/**
 * Native http4s service for DynamicEntity runtime CRUD (under /obp/dynamic-entity/).
 *
 * Replaces the Lift OBPAPIDynamicEntity dispatch (genericEndpoint / publicEndpoint /
 * communityEndpoint).  The business logic is a faithful port of
 * [[code.api.dynamic.entity.APIMethodsDynamicEntity]] — same `authenticatedAccess` /
 * `anonymousAccess` / `getBank` / `hasEntitlement` / `invokeDynamicConnector` calls,
 * same before/after authenticate interceptors, same response shapes and status codes.
 *
 * Notes on the port:
 *   - The dynamic-entity set is runtime-mutable (`DynamicEntityHelper.definitionsMap` is
 *     re-queried per request), so this service does NOT use `ResourceDocMiddleware`
 *     (whose ResourceDoc index is built once at startup).  Auth / role / bank checks are
 *     performed inline, exactly as the Lift handlers did.
 *   - The before/after authenticate interceptors carry auth-type / query-param / header-key
 *     validation (before) and Force-Error / JSON-schema validation (after) — see
 *     APIUtil.beforeAuthenticateInterceptors / afterAuthenticateInterceptors.  They are
 *     invoked here exactly as the Lift handlers invoked them; the resulting Box[JsonResponse]
 *     is reduced to (message, code) via JsonResponseExtractor and re-raised through
 *     booleanToFuture (no Lift JsonResponse rendering).
 *   - `CallContext` is built via `Http4sCallContextBuilder.fromRequest` and attached to the
 *     request so the `EndpointHelpers` (error conversion + metric) can be reused.
 *   - Mutating verbs (POST/PUT/DELETE) run inside
 *     `RequestScopeConnection.withBusinessDBTransaction`; GET runs on auto-commit.
 */
object Http4sDynamicEntity extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  private val apiStandard = ApiStandards.obp.toString
  private val apiVersionString = ApiShortVersions.`dynamic-entity`.toString // "dynamic-entity"

  // ----- helpers ported from APIMethodsDynamicEntity -----

  private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
    if (box.isInstanceOf[Failure]) {
      val failure = box.asInstanceOf[Failure]
      // change the internal db column name 'dynamicdataid' to entity's id name
      val msg = failure.msg.replace(DynamicData.idColumnName, StringUtils.uncapitalize(entityName) + "Id")
      val changedMsgFailure = failure.copy(msg = s"$InternalServerError $msg")
      fullBoxOrException[T](changedMsgFailure)
    }
    box.openOrThrowException("impossible error")
  }

  // ----- DE_indexing: declarative filter / sort / paginate for GET-all list reads -----
  // Replaces the old bare-param equality filter. The query contract is `obp_filter[FIELD]=OP:VALUE`
  // (+ obp_sort_by / obp_sort_direction / obp_offset / obp_limit), validated against the entity's
  // declared `indexed` fields. Phase 1 backend = in-memory portable floor (projection backend later).

  private def deIndexedFields(bankId: Option[String], entityName: String): Map[String, FieldSpec] =
    DynamicEntityHelper.definitionsMap.get((bankId, entityName)).map(_.indexedFields).getOrElse(Map.empty)

  private def deReferenceFields(bankId: Option[String], entityName: String): Map[String, String] =
    DynamicEntityHelper.definitionsMap.get((bankId, entityName)).map(_.referenceFields).getOrElse(Map.empty)

  /** Resolve a join-target (child) entity's indexed + reference fields for the planner (same bank scope). */
  private def childJoinInfo(bankId: Option[String])(child: String): Option[JoinTargetInfo] =
    DynamicEntityHelper.definitionsMap.get((bankId, child)).map(i => JoinTargetInfo(i.indexedFields, i.referenceFields))

  /** Parse + validate list-read query params into a QueryPlan; fail 400 (clear message) on any error. */
  private def buildQueryPlan(req: Request[IO], bankId: Option[String], entityName: String, cc: Option[CallContext]): Future[QueryPlan] = {
    val planned = QueryParamParser.parse(queryParams(req)).flatMap { case (filters, joins, sort, page) =>
      QueryPlanner.plan(filters, joins, sort, page, entityName,
        deIndexedFields(bankId, entityName), deReferenceFields(bankId, entityName), childJoinInfo(bankId))
    }
    planned match {
      case Right(plan) => Future.successful(plan)
      case Left(err)   => Helper.booleanToFuture(err.message, 400, cc = cc) { false }.map(_ => QueryPlan.empty)
    }
  }

  /** Apply a validated plan to the fetched records (Phase 1: in-memory; the SQL backend comes later). */
  private def applyQueryPlan(resultList: JArray, plan: QueryPlan, indexed: Map[String, FieldSpec]): JArray = {
    val records = resultList.arr.collect { case o: JObject => o }
    val fieldTypes = indexed.map { case (name, field) => name -> field.fieldType }
    JArray(InMemoryQueryExecutor.execute(records, plan, fieldTypes))
  }

  /**
   * Legacy GET-all filter (documented, unversioned contract — preserved byte-for-byte): bare query
   * params filter by field value, AND across distinct fields, OR across a field's repeated values,
   * e.g. `?name=A&number=1&number=2` => name==A && (number==1 || number==2). Equality only; runs
   * in-memory on any field. `locale` and the new `obp_*` params (which are handled by the QueryPlan
   * path) are excluded. This composes with the new path: legacy filter first, then operators/sort/page.
   */
  private def filterDynamicObjects(resultList: JArray, params: Map[String, List[String]]): JArray = {
    val legacyParams = params.filter { case (k, _) => k != PARAM_LOCALE && !k.startsWith("obp_") }
    if (legacyParams.isEmpty) resultList
    else JArray(resultList.arr.filter { jValue =>
      legacyParams.forall { case (path, values) => values.exists(JsonUtils.isFieldEquals(jValue, path, _)) }
    })
  }

  private def queryParams(req: Request[IO]): Map[String, List[String]] =
    req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }

  private def listName(entityName: String): String = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
  private def singleName(entityName: String): String = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")

  private def wrapBankId(bankId: Option[String], result: JObject): JObject =
    if (bankId.isDefined) (("bank_id" -> bankId.getOrElse("")): JObject) merge result else result

  private def notFoundMsg(entityName: String, id: String, bankId: Option[String]): String =
    s"$EntityNotFoundByEntityId Entity: '$entityName', entityId: '$id'" + bankId.map(b => s", bank_id: '$b'").getOrElse("")

  /** Resolve bankId to a Bank (404 if missing) for bank-level entities; no-op otherwise. */
  private def bankCheck(bankId: Option[String], cc: Option[CallContext]): Future[(Any, Option[CallContext])] =
    if (bankId.isDefined) NewStyle.function.getBank(BankId(bankId.get), cc).map { case (b, c) => (b, c) }
    else Future.successful(("", cc))

  /**
   * Enrich the CallContext with the dynamic-entity operationId + ResourceDoc, mirroring the
   * Lift handlers.  Used for rate limiting (authenticatedAccess injects operationId), metrics,
   * and the interceptors (Force-Error validation reads resourceDocument.errorResponseBodies).
   * The name key follows the Lift convention: generic system/bank -> `Entity` / `Entity(bankId)`,
   * personal -> `MyEntity...`, public -> `PublicEntity...`, community -> `CommunityEntity...`.
   */
  private def enrichCallContext(cc: CallContext, operation: DynamicEntityOperation, entityName: String, bankId: Option[String], scope: String): CallContext = {
    val splitNameWithBankId = if (bankId.isDefined) s"$entityName(${bankId.getOrElse("")})" else entityName
    val key = scope match {
      case "my"        => s"My$splitNameWithBankId"
      case "public"    => s"Public$splitNameWithBankId"
      case "community" => s"Community$splitNameWithBankId"
      case _           => splitNameWithBankId
    }
    val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> key)
    cc.copy(operationId = Some(resourceDoc.map(_.operationId).orNull), resourceDocument = resourceDoc)
  }

  // Before-authenticate interceptors: auth-type / query-param / request-header-key validation.
  // After-authenticate interceptors: Force-Error / JSON-schema validation.
  // Both reduce a Box[JsonResponse] to a Box[ErrorMessage(code, message)] via JsonResponseExtractor.
  private def beforeIntercept(cc: CallContext, operationId: String): Box[ErrorMessage] =
    beforeAuthenticateInterceptResult(Option(cc), operationId).collect { case JsonResponseExtractor(message, code) => ErrorMessage(code, message) }

  private def afterIntercept(cc: Option[CallContext], operationId: String): Box[ErrorMessage] =
    afterAuthenticateInterceptResult(cc, operationId).collect { case JsonResponseExtractor(message, code) => ErrorMessage(code, message) }

  private def failIf(error: Box[ErrorMessage], cc: Option[CallContext]): Future[Box[Unit]] =
    Helper.booleanToFuture(failMsg = error.map(_.message).orNull, failCode = error.map(_.code).openOr(400), cc = cc) { error.isEmpty }

  // ----- Field-level write permissions: POST/PUT never write write-restricted fields -----
  private def writeRestrictedFieldsOf(bankId: Option[String], entityName: String): List[String] =
    DynamicEntityHelper.definitionsMap.get((bankId, entityName)).map(_.writeRestrictedFields).getOrElse(Nil)

  private def stripFields(obj: JObject, fields: List[String]): JObject =
    if (fields.isEmpty) obj else JObject(obj.obj.filterNot(f => fields.contains(f.name)))

  // For PUT: drop any write-restricted fields the caller sent, then re-inject their current values from the
  // existing record, so an ordinary update never blanks or clobbers a restricted field (no stale-echo issue).
  private def preserveRestrictedOnPut(incoming: JObject, existing: Box[JValue], restricted: List[String]): JObject = {
    if (restricted.isEmpty) incoming
    else {
      val stripped = stripFields(incoming, restricted).obj
      val preserved = existing match {
        case Full(o: JObject) => o.obj.filter(f => restricted.contains(f.name))
        case _ => Nil
      }
      JObject(stripped ++ preserved)
    }
  }

  // ----- Field-level write path (PATCH): per-field authorisation -----
  // Authorisation is per field present in the body: a write-restricted field is governed by its field write
  // role; an unrestricted field is governed by the entity update role. The caller may change a field iff they
  // hold the role that governs it — there is no blanket entity-update precondition, so holding only a field's
  // write role is sufficient to PATCH that field alone. Returns the distinct role names the caller is missing.
  // For a personal entity that doesn't require a role, the entity update role on unrestricted fields is skipped,
  // but write-restricted fields are still gated by their field write role.
  private def missingPatchRoleNames(
    bodyFieldNames: List[String], bankId: Option[String], entityName: String, userId: String, requireEntityRole: Boolean
  ): List[String] = {
    val info = DynamicEntityHelper.definitionsMap.get((bankId, entityName))
    // Only declared schema fields are meaningful (id/audit/unknown fields are ignored by the merge).
    val schemaFields = info.map(_.propertyNames).getOrElse(bodyFieldNames)
    val touched = bodyFieldNames.intersect(schemaFields)
    val writeRestricted = info.map(_.writeRestrictedFields).getOrElse(Nil).toSet
    def has(role: code.api.util.ApiRole): Boolean = code.api.util.APIUtil.hasEntitlement(bankId.getOrElse(""), userId, role)
    touched.flatMap { f =>
      if (writeRestricted.contains(f)) {
        val role = DynamicEntityInfo.fieldWriteRole(entityName, f, bankId, info.flatMap(_.explicitWriteRole(f)))
        if (has(role)) None else Some(role.toString())
      } else if (requireEntityRole) {
        val role = DynamicEntityInfo.canUpdateRole(entityName, bankId)
        if (has(role)) None else Some(role.toString())
      } else None
    }.distinct
  }

  // PATCH partial-update merge: incoming values override existing; absent fields preserved.
  // Bounded to declared schema fields so id/audit fields aren't written into the stored data.
  private def mergePatch(info: Option[DynamicEntityInfo], existing: Box[JValue], incoming: JObject): JObject = {
    val existingMap: Map[String, JValue] = (existing match { case Full(o: JObject) => o.obj; case _ => Nil }).map(f => f.name -> f.value).toMap
    val incomingMap: Map[String, JValue] = incoming.obj.map(f => f.name -> f.value).toMap
    val names: List[String] = info.map(_.propertyNames).getOrElse((incomingMap.keys ++ existingMap.keys).toList).distinct
    JObject(names.flatMap(n => incomingMap.get(n).orElse(existingMap.get(n)).map(v => JField(n, v))))
  }

  // ----- Field-level read permissions: omit read-restricted fields the caller cannot read -----
  private def omitFields(value: JValue, fields: Set[String]): JValue = value match {
    case JObject(obj) => JObject(obj.filterNot(f => fields.contains(f.name)))
    case JArray(arr)  => JArray(arr.map(omitFields(_, fields)))
    case other        => other
  }

  // Remove any read-restricted field the caller lacks the read role for (anonymous => userIdOpt None => omit all).
  private def applyReadRestrictions(value: JValue, bankId: Option[String], entityName: String, userIdOpt: Option[String]): JValue = {
    val info = DynamicEntityHelper.definitionsMap.get((bankId, entityName))
    val readRestricted = info.map(_.readRestrictedFields).getOrElse(Nil)
    val omit: Set[String] = readRestricted.filterNot { f =>
      userIdOpt.exists { uid =>
        val role = DynamicEntityInfo.fieldReadRole(entityName, f, bankId, info.flatMap(_.explicitReadRole(f)))
        code.api.util.APIUtil.hasEntitlement(bankId.getOrElse(""), uid, role)
      }
    }.toSet
    if (omit.isEmpty) value else omitFields(value, omit)
  }

  // ----- DE_indexing: read-path backend selection (projection vs in-memory) -----
  // Phase 3: applied to the authenticated genericGet only. Public/community stay in-memory for now
  // (different scoping). Projection is used only for pure obp_filter/sort queries (no legacy bare
  // params) whose every field is indexed AND ready; an indexed-but-not-ready field returns 409.
  private sealed trait ProjDecision
  private case object UseProjection extends ProjDecision
  private case object PendingProjection extends ProjDecision
  private case object UseInMemory extends ProjDecision
  private case object JoinsNeedProjection extends ProjDecision // joins present but projection unavailable -> 400

  private def legacyParamsPresent(req: Request[IO]): Boolean =
    queryParams(req).keys.exists(k => k != PARAM_LOCALE && !k.startsWith("obp_"))

  /** True if the request carries any obp_exists / obp_not_exists join clause (used to reject joins on
   *  the non-projection get-all paths: public, community, row-level). */
  private def joinParamsPresent(req: Request[IO]): Boolean =
    queryParams(req).keys.exists(k => k.startsWith("obp_exists[") || k.startsWith("obp_not_exists["))

  private def planFields(plan: QueryPlan): List[String] =
    (plan.filters.map(_.field) ++ plan.sort.map(_.field)).distinct

  private def decideProjection(req: Request[IO], bankId: Option[String], entityName: String, plan: QueryPlan): ProjDecision =
    if (plan.joins.nonEmpty) {
      // Joins are projection-only. Legacy bare params can't combine with joins (they force in-memory).
      if (!IndexingCapabilities.projectionEnabled || legacyParamsPresent(req)) JoinsNeedProjection
      else {
        val parentReady = ProjectionProvisioner.readyFields(bankId, entityName)
        val parentFieldsReady = planFields(plan).forall(parentReady.contains)
        val joinsReady = plan.joins.forall { j =>
          val childReady = ProjectionProvisioner.readyFields(bankId, j.childEntity)
          val linkReady  = if (j.onChild) childReady.contains(j.linkField) else parentReady.contains(j.linkField)
          linkReady && j.predicate.map(_.field).forall(childReady.contains)
        }
        if (parentFieldsReady && joinsReady) UseProjection else PendingProjection
      }
    } else if (!IndexingCapabilities.projectionEnabled || legacyParamsPresent(req) || planFields(plan).isEmpty) UseInMemory
    else {
      val ready = ProjectionProvisioner.readyFields(bankId, entityName)
      if (planFields(plan).forall(ready.contains)) UseProjection else PendingProjection
    }

  private def projectionList(entityName: String, bankId: Option[String], userId: Option[String], isPersonalEntity: Boolean, plan: QueryPlan): Future[JArray] =
    PostgresProjectionBackend.query(entityName, bankId, userId, isPersonalEntity, plan).map(JArray(_)).unsafeToFuture()(ioRuntime)

  // ----- row-level access (use_row_level_access) -----
  // When an entity opts into row-level access the per-entity GET/UPDATE/DELETE roles do NOT
  // gate access; the DynamicDataAccess ACL decides per row. Rows are fetched UNSCOPED (community
  // path) so ownership doesn't pre-filter, then the ACL gates. Row-level entities are local by
  // definition (§8.5 guard), so calling the local provider directly violates no abstraction.
  // See ideas/DYNAMIC_ENTITY_ROW_LEVEL_ACCESS.md §5.

  private def aclVend = DynamicDataAccessProvider.provider.vend
  private def dataVend = DynamicDataProvider.connectorMethodProvider.vend
  private def isRowLevel(bankId: Option[String], entityName: String): Boolean =
    DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.useRowLevelAccess)

  private def rowLevelGet(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String): Future[JValue] = {
    val isGetAll = StringUtils.isBlank(id)
    val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
    val callContext0 = enrichCallContext(cc, operation, entityName, bankId, "")
    val operationId = callContext0.operationId.orNull
    for {
      _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
      (Full(u), callContext) <- authenticatedAccess(callContext0)
      (_, callContext) <- bankCheck(bankId, callContext)
      // Row-level: type-level get role is NOT required — the ACL decides per row.
      _ <- failIf(afterIntercept(callContext, operationId), callContext)
      // Row-level get-all is served in-memory (ACL-gated); joins require the projection backend.
      _ <- if (isGetAll && joinParamsPresent(req)) Helper.booleanToFuture(DynamicEntityJoinRequiresProjection, 400, cc = callContext) { false }
           else Future.successful(true)
      result <- if (isGetAll) Future {
                  // In-memory floor: fetch all rows (unscoped) and keep those the ACL marks readable.
                  // (The projection EXISTS backend for row-level get-all is a documented follow-up; the
                  // in-memory path is always correct, just not index-accelerated.)
                  val readable = aclVend.getReadableDynamicDataIds(u.userId, entityName, bankId).toSet
                  val readableRows = dataVend.getAllCommunity(bankId, entityName).filter(_.dynamicDataId.exists(readable.contains))
                  val readableJson: JArray = JArray(readableRows.map(r => parse(r.dataJson)))
                  val filtered = filterDynamicObjects(readableJson, queryParams(req))
                  wrapBankId(bankId, (listName(entityName) -> applyReadRestrictions(filtered, bankId, entityName, Some(u.userId))))
                } else {
                  val box: Box[JValue] = dataVend.getCommunity(bankId, entityName, id).map(it => parse(it.dataJson))
                  for {
                    // Hide existence: a row you cannot read is indistinguishable from a missing row (404).
                    _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) {
                           box.isDefined && aclVend.allows(id, u.userId, DynamicDataAccessPermission.Read)
                         }
                  } yield {
                    val singleObject: JValue = unboxResult(box, entityName)
                    wrapBankId(bankId, (singleName(entityName) -> applyReadRestrictions(singleObject, bankId, entityName, Some(u.userId))))
                  }
                }
    } yield result
  }

  private def rowLevelPut(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String): Future[JValue] = {
    val callContext0 = enrichCallContext(cc, UPDATE, entityName, bankId, "")
    val operationId = callContext0.operationId.orNull
    for {
      _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
      (Full(u), callContext) <- authenticatedAccess(callContext0)
      (_, callContext) <- bankCheck(bankId, callContext)
      _ <- failIf(afterIntercept(callContext, operationId), callContext)
      json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { parse(cc.httpBody.getOrElse("")) }
      existing: Box[JValue] = dataVend.getCommunity(bankId, entityName, id).map(it => parse(it.dataJson))
      _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
      _ <- Helper.booleanToFuture(s"$UserHasMissingRoles update access on this row", 403, cc = callContext) {
             aclVend.allows(id, u.userId, DynamicDataAccessPermission.Update) }
      // Field-level write roles still apply on top of the row ACL.
      updateJson = preserveRestrictedOnPut(json.asInstanceOf[JObject], existing, writeRestrictedFieldsOf(bankId, entityName))
      box: Box[JValue] = dataVend.updateCommunity(bankId, entityName, updateJson, id).map(it => parse(it.dataJson))
      singleObject: JValue = unboxResult(box, entityName)
    } yield wrapBankId(bankId, (singleName(entityName) -> singleObject))
  }

  private def rowLevelPatch(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String): Future[JValue] = {
    val callContext0 = enrichCallContext(cc, UPDATE, entityName, bankId, "")
    val operationId = callContext0.operationId.orNull
    for {
      _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
      (Full(u), callContext) <- authenticatedAccess(callContext0)
      (_, callContext) <- bankCheck(bankId, callContext)
      _ <- failIf(afterIntercept(callContext, operationId), callContext)
      json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { parse(cc.httpBody.getOrElse("")) }
      bodyObj = json.asInstanceOf[JObject]
      // Row ACL replaces the entity-update role; per-field write roles still apply (requireEntityRole = false).
      _ <- Helper.booleanToFuture(s"$UserHasMissingRoles update access on this row", 403, cc = callContext) {
             aclVend.allows(id, u.userId, DynamicDataAccessPermission.Update) }
      missingRoles = missingPatchRoleNames(bodyObj.obj.map(_.name), bankId, entityName, u.userId, requireEntityRole = false)
      _ <- Helper.booleanToFuture(s"$UserHasMissingRoles ${missingRoles.mkString(", ")}", 403, cc = callContext) { missingRoles.isEmpty }
      existing: Box[JValue] = dataVend.getCommunity(bankId, entityName, id).map(it => parse(it.dataJson))
      _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
      mergedJson = mergePatch(DynamicEntityHelper.definitionsMap.get((bankId, entityName)), existing, bodyObj)
      box: Box[JValue] = dataVend.updateCommunity(bankId, entityName, mergedJson, id).map(it => parse(it.dataJson))
      singleObject: JValue = unboxResult(box, entityName)
    } yield wrapBankId(bankId, (singleName(entityName) -> singleObject))
  }

  private def rowLevelDelete(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String): Future[JValue] = {
    val callContext0 = enrichCallContext(cc, DELETE, entityName, bankId, "")
    val operationId = callContext0.operationId.orNull
    for {
      _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
      (Full(u), callContext) <- authenticatedAccess(callContext0)
      (_, callContext) <- bankCheck(bankId, callContext)
      _ <- failIf(afterIntercept(callContext, operationId), callContext)
      existing: Box[JValue] = dataVend.getCommunity(bankId, entityName, id).map(it => parse(it.dataJson))
      _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
      _ <- Helper.booleanToFuture(s"$UserHasMissingRoles delete access on this row", 403, cc = callContext) {
             aclVend.allows(id, u.userId, DynamicDataAccessPermission.Delete) }
      _ = dataVend.deleteCommunity(bankId, entityName, id)
      _ = aclVend.deleteAllForRow(id) // cascade the ACL rows for the deleted data row
    } yield JObject(Nil)
  }

  // ----- row-level access management: share / list / revoke (.../<Entity>/<id>/access) -----
  // §6. Authorisation: ACL CanGrant on the row OR the per-entity admin role. The owner holds
  // CanGrant via their bootstrap row, so they share their own records with no role (§8.1).

  private def boolField(o: JObject, name: String, default: Boolean): Boolean =
    (o \ name) match { case JBool(b) => b; case _ => default }
  private def strField(o: JObject, name: String): Option[String] =
    (o \ name) match { case JString(s) if s.nonEmpty => Some(s); case _ => None }

  private def rowAccessRowJson(a: code.DynamicData.DynamicDataAccessT): JObject =
    ("user_id" -> a.userId) ~
    ("can_read" -> a.canRead) ~
    ("can_update" -> a.canUpdate) ~
    ("can_delete" -> a.canDelete) ~
    ("can_grant" -> a.canGrant) ~
    ("granted_by" -> a.grantedBy)

  private def rowAccessListJson(dataId: String): JObject =
    ("access" -> JArray(aclVend.getAccessForRow(dataId).map(rowAccessRowJson)))

  // Shared preamble: before-intercept, auth, bank, flag-off (400), grant authorisation (403).
  private def rowAccessAuthorise(cc: CallContext, bankId: Option[String], entityName: String, id: String,
                                 operation: DynamicEntityOperation): Future[(User, Option[CallContext])] = {
    val callContext0 = enrichCallContext(cc, operation, entityName, bankId, "")
    val operationId = callContext0.operationId.orNull
    for {
      _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
      (Full(u), callContext) <- authenticatedAccess(callContext0)
      (_, callContext2) <- bankCheck(bankId, callContext)
      _ <- Helper.booleanToFuture(RowLevelAccessNotEnabled, 400, cc = callContext2) { isRowLevel(bankId, entityName) }
      _ <- Helper.booleanToFuture(s"$UserHasMissingRoles grant access on this row", 403, cc = callContext2) {
             aclVend.allows(id, u.userId, DynamicDataAccessPermission.Grant) ||
               hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGrantRowAccessRole(entityName, bankId))
           }
    } yield (u, callContext2)
  }

  private def listRowAccess(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      for {
        _ <- rowAccessAuthorise(cc, bankId, entityName, id, GET_ALL)
      } yield rowAccessListJson(id)
    }

  private def upsertRowAccess(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      for {
        (granter, callContext) <- rowAccessAuthorise(cc, bankId, entityName, id, UPDATE)
        json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { parse(cc.httpBody.getOrElse("")) }
        entries = json match {
          case JArray(arr) => arr.collect { case o: JObject => o }
          case o: JObject  => List(o)
          case _           => Nil
        }
        _ <- Helper.booleanToFuture(s"$InvalidJsonFormat each access entry needs a non-empty user_id", 400, cc = callContext) {
               entries.nonEmpty && entries.forall(o => strField(o, "user_id").isDefined)
             }
        _ = entries.foreach { o =>
              aclVend.grant(id, strField(o, "user_id").get,
                canRead = boolField(o, "can_read", default = false),
                canUpdate = boolField(o, "can_update", default = false),
                canDelete = boolField(o, "can_delete", default = false),
                canGrant = boolField(o, "can_grant", default = true), // §8.1: re-share by default
                entityName, bankId, grantedBy = granter.userId)
            }
      } yield rowAccessListJson(id)
    }

  private def revokeRowAccess(req: Request[IO], bankId: Option[String], entityName: String, id: String, grantUserId: String): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      for {
        _ <- rowAccessAuthorise(cc, bankId, entityName, id, DELETE)
        removed = aclVend.revoke(id, grantUserId).getOrElse(0) // cascades to downstream grants (§7)
      } yield (("revoked_count" -> removed): JObject)
    }

  // ----- generic endpoint (authenticated, system / bank / personal) -----

  private def genericGet(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      if (isRowLevel(bankId, entityName)) rowLevelGet(req, cc, bankId, entityName, id)
      else _genericGet(req, cc, bankId, entityName, id, isPersonalEntity)
    }

  private def _genericGet(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): Future[JValue] = {
      val isGetAll = StringUtils.isBlank(id)
      val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
      val callContext0 = enrichCallContext(cc, operation, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        queryPlan <- if (isGetAll) buildQueryPlan(req, bankId, entityName, callContext) else Future.successful(QueryPlan.empty)
        decision = if (isGetAll) decideProjection(req, bankId, entityName, queryPlan) else UseInMemory
        _ <- if (decision == JoinsNeedProjection) Helper.booleanToFuture(DynamicEntityJoinRequiresProjection, 400, cc = callContext) { false }
             else Future.successful(true)
        _ <- if (decision == PendingProjection) Helper.booleanToFuture(DynamicEntityFieldNotYetQueryable, 409, cc = callContext) { false }
             else Future.successful(true)
        // Projection path: serve the list from SQL, skipping the fetch-all connector call.
        projList <- if (decision == UseProjection) projectionList(entityName, bankId, Some(u.userId), isPersonalEntity, queryPlan).map(Option(_))
                    else Future.successful(Option.empty[JArray])
        (box, _) <- if (decision == UseProjection) Future.successful((net.liftweb.common.Empty: Box[JValue], callContext))
                    else NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- if (decision == UseProjection) Future.successful(true)
             else Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { box.isDefined }
      } yield {
        if (isGetAll) {
          val filtered: JArray = projList.getOrElse {
            val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
            val legacyFiltered = filterDynamicObjects(resultList, queryParams(req))
            applyQueryPlan(legacyFiltered, queryPlan, deIndexedFields(bankId, entityName))
          }
          wrapBankId(bankId, (listName(entityName) -> applyReadRestrictions(filtered, bankId, entityName, Some(u.userId))))
        } else {
          val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
          wrapBankId(bankId, (singleName(entityName) -> applyReadRestrictions(singleObject, bankId, entityName, Some(u.userId))))
        }
      }
    }

  private def genericPost(req: Request[IO], bankId: Option[String], entityName: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeFutureCreated(req) {
      val cc = req.callContext
      val callContext0 = enrichCallContext(cc, CREATE, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canCreateRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")) }
        // Write-restricted fields are never set via POST; strip them before persisting.
        createJson = stripFields(json.asInstanceOf[JObject], writeRestrictedFieldsOf(bankId, entityName))
        (box, _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(createJson), None, bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
        // Row-level access: bootstrap the owner ACL row (R/U/D + Grant) so the creator can read,
        // edit, and share their own record with no role and no meta-admin hop (§4 / §8.1).
        _ = if (isRowLevel(bankId, entityName)) (singleObject \ DynamicEntityHelper.createEntityId(entityName)) match {
              case JString(rid) =>
                aclVend.grant(rid, u.userId, canRead = true, canUpdate = true, canDelete = true, canGrant = true, entityName, bankId, grantedBy = u.userId)
              case _ =>
            }
      } yield wrapBankId(bankId, (singleName(entityName) -> singleObject))
    }

  private def genericPut(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      if (isRowLevel(bankId, entityName)) rowLevelPut(req, cc, bankId, entityName, id)
      else _genericPut(req, cc, bankId, entityName, id, isPersonalEntity)
    }

  private def _genericPut(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): Future[JValue] = {
      val callContext0 = enrichCallContext(cc, UPDATE, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canUpdateRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")) }
        (existing, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
        // Write-restricted fields are not updated via PUT; preserve their existing values.
        updateJson = preserveRestrictedOnPut(json.asInstanceOf[JObject], existing.asInstanceOf[Box[JValue]], writeRestrictedFieldsOf(bankId, entityName))
        (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(UPDATE, entityName, Some(updateJson), Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        singleObject: JValue = unboxResult(box, entityName)
      } yield wrapBankId(bankId, (singleName(entityName) -> singleObject))
    }

  private def genericPatch(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      if (isRowLevel(bankId, entityName)) rowLevelPatch(req, cc, bankId, entityName, id)
      else _genericPatch(req, cc, bankId, entityName, id, isPersonalEntity)
    }

  private def _genericPatch(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): Future[JValue] = {
      val callContext0 = enrichCallContext(cc, UPDATE, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")) }
        bodyObj = json.asInstanceOf[JObject]
        // Per-field authorisation: each field present in the body needs the role that governs it — its field
        // write role if write-restricted, otherwise the entity update role. No blanket entity-update precondition.
        // For a personal entity without a required role, the entity role on unrestricted fields is skipped.
        requireEntityRole = !(isPersonalEntity && !personalRequiresRole)
        missingRoles = missingPatchRoleNames(bodyObj.obj.map(_.name), bankId, entityName, u.userId, requireEntityRole)
        _ <- Helper.booleanToFuture(s"$UserHasMissingRoles ${missingRoles.mkString(", ")}", 403, cc = callContext) { missingRoles.isEmpty }
        (existing, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
        // PATCH = partial update: merge incoming fields over the existing record.
        mergedJson = mergePatch(DynamicEntityHelper.definitionsMap.get((bankId, entityName)), existing.asInstanceOf[Box[JValue]], bodyObj)
        (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(UPDATE, entityName, Some(mergedJson), Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        singleObject: JValue = unboxResult(box, entityName)
      } yield wrapBankId(bankId, (singleName(entityName) -> singleObject))
    }

  private def genericDelete(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      if (isRowLevel(bankId, entityName)) rowLevelDelete(req, cc, bankId, entityName, id)
      else _genericDelete(req, cc, bankId, entityName, id, isPersonalEntity)
    }

  private def _genericDelete(req: Request[IO], cc: CallContext, bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): Future[JValue] = {
      val callContext0 = enrichCallContext(cc, DELETE, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canDeleteRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        (existing, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
        (box, _) <- NewStyle.function.invokeDynamicConnector(DELETE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _: JBool = unboxResult(box.asInstanceOf[Box[JBool]], entityName)
      } yield JObject(Nil)
    }

  // ----- public endpoint (anonymous, read-only; before-interceptors only, no role) -----

  private def publicGet(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      val isGetAll = StringUtils.isBlank(id)
      val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
      val callContext0 = enrichCallContext(cc, operation, entityName, bankId, "public")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (_, callContext) <- anonymousAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        queryPlan <- if (isGetAll) buildQueryPlan(req, bankId, entityName, callContext) else Future.successful(QueryPlan.empty)
        // Public reads are in-memory only; joins require the projection backend.
        _ <- if (queryPlan.joins.nonEmpty) Helper.booleanToFuture(DynamicEntityJoinRequiresProjection, 400, cc = callContext) { false }
             else Future.successful(true)
        (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None, None, false, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { box.isDefined }
      } yield {
        if (isGetAll) {
          val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
          val legacyFiltered = filterDynamicObjects(resultList, queryParams(req))
          val filtered = applyQueryPlan(legacyFiltered, queryPlan, deIndexedFields(bankId, entityName))
          wrapBankId(bankId, (listName(entityName) -> applyReadRestrictions(filtered, bankId, entityName, None)))
        } else {
          val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
          wrapBankId(bankId, (singleName(entityName) -> applyReadRestrictions(singleObject, bankId, entityName, None)))
        }
      }
    }

  // ----- community endpoint (authenticated + CanGet role, read-only, ALL records) -----

  private def communityGet(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      val isGetAll = StringUtils.isBlank(id)
      val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
      val callContext0 = enrichCallContext(cc, operation, entityName, bankId, "community")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        queryPlan <- if (isGetAll) buildQueryPlan(req, bankId, entityName, callContext) else Future.successful(QueryPlan.empty)
        // Community reads are in-memory only; joins require the projection backend.
        _ <- if (queryPlan.joins.nonEmpty) Helper.booleanToFuture(DynamicEntityJoinRequiresProjection, 400, cc = callContext) { false }
             else Future.successful(true)
      } yield {
        if (isGetAll) {
          val resultList: List[JObject] = DynamicDataProvider.connectorMethodProvider.vend.getAllDataJsonCommunity(bankId, entityName)
          val resultArray = JArray(resultList)
          val legacyFiltered = filterDynamicObjects(resultArray, queryParams(req))
          val filtered = applyQueryPlan(legacyFiltered, queryPlan, deIndexedFields(bankId, entityName))
          wrapBankId(bankId, (listName(entityName) -> applyReadRestrictions(filtered, bankId, entityName, Some(u.userId))))
        } else {
          val singleResult = DynamicDataProvider.connectorMethodProvider.vend.getCommunity(bankId, entityName, id)
          val singleObject: JValue = singleResult match {
            case Full(data) => com.openbankproject.commons.util.JsonAliases.parse(data.dataJson)
            case _ => throw new RuntimeException(notFoundMsg(entityName, id, bankId))
          }
          wrapBankId(bankId, (singleName(entityName) -> applyReadRestrictions(singleObject, bankId, entityName, Some(u.userId))))
        }
      }
    }

  // ----- dispatch -----

  /**
   * Match the remaining path segments (after `/obp/dynamic-entity`) against the same
   * extractors the Lift dispatcher used.  Order public -> community -> generic mirrors
   * OBPAPIDynamicEntity.routes.  No match -> OptionT.none (request falls through the chain).
   */
  private def dispatch(req: Request[IO], rest: List[String]): OptionT[IO, Response[IO]] = {
    val handlerOpt: Option[Request[IO] => IO[Response[IO]]] = (req.method, rest) match {
      case (Method.GET, PublicEntityName(bankId, entityName, id)) =>
        Some(r => publicGet(r, bankId, entityName, id))
      case (Method.GET, CommunityEntityName(bankId, entityName, id)) =>
        Some(r => communityGet(r, bankId, entityName, id))
      // Row-level access management — must precede the generic EntityName match.
      case (Method.GET, EntityAccessName(bankId, entityName, id, None)) =>
        Some(r => listRowAccess(r, bankId, entityName, id))
      case (Method.PUT, EntityAccessName(bankId, entityName, id, None)) =>
        Some(r => upsertRowAccess(r, bankId, entityName, id))
      case (Method.DELETE, EntityAccessName(bankId, entityName, id, Some(grantUserId))) =>
        Some(r => revokeRowAccess(r, bankId, entityName, id, grantUserId))
      case (method, EntityName(bankId, entityName, id, isPersonalEntity)) =>
        method match {
          case Method.GET    => Some(r => genericGet(r, bankId, entityName, id, isPersonalEntity))
          case Method.POST   => Some(r => genericPost(r, bankId, entityName, isPersonalEntity))
          case Method.PUT    => Some(r => genericPut(r, bankId, entityName, id, isPersonalEntity))
          case Method.PATCH  => Some(r => genericPatch(r, bankId, entityName, id, isPersonalEntity))
          case Method.DELETE => Some(r => genericDelete(r, bankId, entityName, id, isPersonalEntity))
          case _             => None
        }
      case _ => None
    }

    handlerOpt match {
      case None => OptionT.none[IO, Response[IO]]
      case Some(handler) =>
        OptionT.liftF {
          Http4sCallContextBuilder.fromRequest(req, apiVersionString).flatMap { cc =>
            val reqWithCc = req.withAttribute(Http4sRequestAttributes.callContextKey, cc)
            val io = handler(reqWithCc)
            if (req.method == Method.GET || req.method == Method.HEAD) io
            else RequestScopeConnection.withBusinessDBTransaction(io)
          }
        }
    }
  }

  /** Entry point wired into Http4sApp.baseServices (before the Lift bridge). */
  lazy val wrappedRoutesDynamicEntity: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { (req: Request[IO]) =>
      req.uri.path.segments.map(_.encoded).toList match {
        case standard :: version :: rest if standard == apiStandard && version == apiVersionString =>
          dispatch(req, rest)
        case _ =>
          OptionT.none[IO, Response[IO]]
      }
    }
}
