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

package code.api.dynamic.entity.query

import code.DynamicData.{DynamicDataAccessProvider, DynamicDataProvider}
import code.api.dynamic.entity.helper.{DynamicEntityHelper, DynamicEntityInfo}
import code.api.dynamic.entity.projection.{IndexingCapabilities, PostgresProjectionBackend, ProjectionNaming, ProjectionProvisioner, ProjectionReadiness, ProjectionStore}
import code.api.util.ErrorMessages.{DynamicEntityJoinRequiresProjection, DynamicQueryEntityNotReadable, DynamicQueryInvalid}
import com.openbankproject.commons.util.JsonAliases
import net.liftweb.util.StringHelpers
import org.json4s.JsonAST._

import scala.util.Try

/**
 * A Dynamic Query is a Dynamic Resource Doc whose body is a declaration rather than code
 * (`programming_lang` `Query`). It reads the records of one Dynamic Entity, adds the records joined
 * to them, and returns them in a named list. See ideas/DYNAMIC_QUERIES.md.
 *
 * The body is JSON:
 *
 * {{{
 * {
 *   "from": "activity",
 *   "select": ["activity_id", "name", "city"],
 *   "where": { "city": "eq:Berlin" },
 *   "join": [
 *     { "entity": "operator", "on": "operator_id", "fields": { "operator_legal_name": "legal_name" } },
 *     { "entity": "certificate", "on": "activity_id", "cardinality": "at_most_one",
 *       "pick": "latest_by:issue_date", "fields": { "certificate_number": "number" } }
 *   ],
 *   "envelope": { "rows": "activities", "count": "count" }
 * }
 * }}}
 *
 *  - `from` (required): the entity whose records are returned.
 *  - `select`: the fields of those records to return, in this order; all of them when absent.
 *  - `where`: filters on them, in the list endpoint's grammar (`"field": "op:value"`, or a list of
 *    such strings for several filters on one field); the fields must be declared `"indexed": true`.
 *  - `join`: see [[JoinRequest]]; each entry's keys are `entity`, `on`, `direction`, `cardinality`,
 *    `where`, `fields` (an object, result name -> field), `as`, `pick`, `order`, `true_value`,
 *    `false_value`.
 *  - `envelope`: `rows` names the list (by default the entity's list name, as its own endpoints use)
 *    and `count`, when given, names a field holding how many records match in all, not only on this page.
 *
 * Unknown keys are rejected, so a misspelt key is reported instead of silently ignored.
 */
case class DynamicQueryEnvelope(rows: String, count: Option[String])

case class DynamicQueryDeclaration(
  from: String,
  select: Option[List[String]],
  where: List[Filter],
  joins: List[JoinRequest],
  envelope: DynamicQueryEnvelope
)

object DynamicQueryDeclaration {

  private val topKeys = List("from", "select", "where", "join", "envelope")
  private val joinKeys = List("entity", "on", "direction", "cardinality", "where", "fields", "as", "pick", "order", "true_value", "false_value")

  /** Parse a body. The message of a Left says what is wrong and where, without the error code. */
  def parse(body: String): Either[QueryError, DynamicQueryDeclaration] =
    for {
      json <- Try(JsonAliases.parse(body)).toOption.collect { case o: JObject => o }
                .toRight(QueryError("The body of a Dynamic Query must be a JSON object."))
      _ <- unknownKeys(json, topKeys, "The Dynamic Query")
      from <- requiredString(json, "from", "The Dynamic Query")
      select <- optionalStringList(json, "select", "The Dynamic Query")
      where <- filtersOf(json \ "where", "The Dynamic Query's 'where'")
      joins <- json \ "join" match {
                 case JNothing | JNull => Right(Nil)
                 case JArray(items) => traverse(items.zipWithIndex)(item => joinOf(item._1, item._2))
                 case _ => Left(QueryError("The Dynamic Query's 'join' must be a list."))
               }
      envelope <- envelopeOf(json \ "envelope", from)
    } yield DynamicQueryDeclaration(from, select, where, joins, envelope)

  private def joinOf(value: JValue, index: Int): Either[QueryError, JoinRequest] = {
    val where = s"Join ${index + 1}"
    value match {
      case join: JObject =>
        for {
          _ <- unknownKeys(join, joinKeys, where)
          entity <- requiredString(join, "entity", where)
          on <- requiredString(join, "on", where)
          direction <- optionalString(join, "direction", where)
          cardinality <- optionalString(join, "cardinality", where)
          filters <- filtersOf(join \ "where", s"$where's 'where'")
          fields <- join \ "fields" match {
                      case JNothing | JNull => Right(Nil)
                      case JObject(pairs) => traverse(pairs) {
                        case (name, JString(field)) => Right(name -> field)
                        case (name, _) => Left(QueryError(s"$where's 'fields' must map each result name to a field name; '$name' does not."))
                      }
                      case _ => Left(QueryError(s"$where's 'fields' must be an object mapping result names to field names."))
                    }
          as <- optionalString(join, "as", where)
          pick <- optionalString(join, "pick", where)
          order <- optionalString(join, "order", where)
        } yield JoinRequest(entity, on, direction, cardinality, filters, fields, as, pick, order,
          Some(join \ "true_value").filter(_ != JNothing), Some(join \ "false_value").filter(_ != JNothing))
      case _ => Left(QueryError(s"$where must be an object."))
    }
  }

  private def filtersOf(value: JValue, what: String): Either[QueryError, List[Filter]] = value match {
    case JNothing | JNull => Right(Nil)
    case JObject(pairs) =>
      traverse(pairs) {
        case (field, JString(raw)) => QueryParamParser.parseOneFilter(field, raw).map(List(_))
        case (field, JArray(items)) => traverse(items) {
          case JString(raw) => QueryParamParser.parseOneFilter(field, raw)
          case _ => Left(QueryError(s"$what: each filter on '$field' must be a string such as \"eq:value\"."))
        }
        case (field, _) => Left(QueryError(s"$what: the filter on '$field' must be a string such as \"eq:value\", or a list of them."))
      }.map(_.flatten).left.map(e => if (e.message.startsWith(what)) e else QueryError(s"$what: ${e.message}"))
    case _ => Left(QueryError(s"$what must be an object mapping field names to filters such as \"eq:value\"."))
  }

  private def envelopeOf(value: JValue, from: String): Either[QueryError, DynamicQueryEnvelope] = {
    val defaultRows = StringHelpers.snakify(from).replaceFirst("[-_]*$", "_list")
    value match {
      case JNothing | JNull => Right(DynamicQueryEnvelope(defaultRows, None))
      case envelope: JObject =>
        for {
          _ <- unknownKeys(envelope, List("rows", "count"), "The 'envelope'")
          rows <- optionalString(envelope, "rows", "The 'envelope'")
          count <- optionalString(envelope, "count", "The 'envelope'")
          _ <- if (count.isDefined && count == rows.orElse(Some(defaultRows))) Left(QueryError("The 'envelope' cannot use one name for both 'rows' and 'count'."))
               else Right(())
        } yield DynamicQueryEnvelope(rows.getOrElse(defaultRows), count)
      case _ => Left(QueryError("The 'envelope' must be an object with 'rows' and optionally 'count'."))
    }
  }

  private def unknownKeys(json: JObject, allowed: List[String], what: String): Either[QueryError, Unit] =
    json.obj.map(_._1).find(k => !allowed.contains(k))
      .map(k => QueryError(s"$what has an unknown key '$k'. Allowed keys: ${allowed.mkString(", ")}.")).toLeft(())

  private def requiredString(json: JObject, key: String, what: String): Either[QueryError, String] =
    optionalString(json, key, what).flatMap(_.toRight(QueryError(s"$what needs '$key'.")))

  private def optionalString(json: JObject, key: String, what: String): Either[QueryError, Option[String]] =
    json \ key match {
      case JNothing | JNull => Right(None)
      case JString(s) if s.trim.nonEmpty => Right(Some(s.trim))
      case _ => Left(QueryError(s"$what: '$key' must be a non-empty string."))
    }

  private def optionalStringList(json: JObject, key: String, what: String): Either[QueryError, Option[List[String]]] =
    json \ key match {
      case JNothing | JNull => Right(None)
      case JArray(items) if items.nonEmpty && items.forall { case JString(s) => s.trim.nonEmpty; case _ => false } =>
        Right(Some(items.collect { case JString(s) => s.trim }))
      case _ => Left(QueryError(s"$what: '$key' must be a non-empty list of field names."))
    }

  private def traverse[A, B](xs: List[A])(f: A => Either[QueryError, B]): Either[QueryError, List[B]] =
    xs.foldRight(Right(Nil): Either[QueryError, List[B]]) { (a, acc) => for { b <- f(a); rest <- acc } yield b :: rest }
}

/** One entity a Dynamic Query reads, the access it needs, and whether the explained caller has it. */
case class ExplainedEntity(entity: String, readRole: String, bankId: String, publicAccess: Boolean, rowLevelAccess: Boolean, callerMayRead: Boolean)

/**
 * One restricted field a Dynamic Query touches: the rule restricting it (`read_role_required`, or
 * `hide_field_from_public_access`), the Role that lifts it, and whether the explained caller may read it.
 */
case class ExplainedField(entity: String, field: String, restriction: String, readRole: String, callerMayRead: Boolean)

/** One read a Dynamic Query makes: what, how (`projection`, `record provider`, or `shared` with an earlier step), and its SQL when OBP builds it. */
case class ExplainedStep(purpose: String, backend: String, sql: Option[String], notes: List[String])

/** How a Dynamic Query would be answered for one caller: see [[DynamicQuery.explain]]. */
case class DynamicQueryExplanation(space: String, anonymousCaller: Boolean, callerMayRun: Boolean, refusal: Option[String],
                                   entities: List[ExplainedEntity], restrictedFields: List[ExplainedField], rules: List[String],
                                   steps: List[ExplainedStep])

/** Why a Dynamic Query could not answer: the HTTP status and the full message, error code included. */
case class DynamicQueryFailure(status: Int, message: String)

/**
 * This object checks and runs Dynamic Queries.
 *
 * [[validate]] runs when a doc is created, updated, approved or dry-run compiled. It knows no caller,
 * so it checks only what the declaration says against the entity definitions of its space.
 *
 * [[run]] answers one request. In order:
 *  1. the caller must be able to read every entity the query reads (`from`, each join, and each
 *     entity a caller's `obp_exists` names), by the rule a GET applies, or the answer is 403; this is
 *     on top of any Roles the doc itself requires;
 *  2. the caller may narrow the result with the list endpoint's own parameters (`obp_filter`,
 *     `obp_sort_by`, `obp_sort_direction`, `obp_limit`, `obp_offset`, `obp_exists`,
 *     `obp_not_exists`); they are added to the declaration's `where`, never replace it; a filter or
 *     sort on a field the caller may not read is refused;
 *  3. the page is read: from the query projection when it is enabled and every field the plan needs
 *     is ready, otherwise in memory (`obp_exists` joins need the projection); always shared records
 *     only, and for an entity with row-level access only the records the caller's access list allows;
 *  4. the joins are applied to the page (see [[RecordJoiner]]);
 *  5. the fields are chosen (`select`, or all), with a read-restricted field null (when selected) or
 *     left out (when not) unless the caller holds its read Role;
 *  6. the result is wrapped in the envelope, with the total count when the envelope names one.
 */
object DynamicQuery {

  private def invalid(message: String) = DynamicQueryFailure(400, s"$DynamicQueryInvalid$message")

  def validate(bankId: Option[String], declaration: DynamicQueryDeclaration): Either[DynamicQueryFailure, Unit] = {
    def infoOf(entityName: String): Option[JoinEntityInfo] = DynamicEntityHelper.definitionOf(bankId, entityName).map(JoinEntityInfo.of)
    for {
      parentDefinition <- DynamicEntityHelper.definitionOf(bankId, declaration.from)
                            .toRight(invalid(s"There is no Dynamic Entity '${declaration.from}' in this space."))
      parent = JoinEntityInfo.of(parentDefinition)
      _ <- selectError(declaration, parent).toLeft(())
      _ <- planPage(bankId, declaration, parentDefinition, Map.empty).map(_ => ())
      _ <- JoinPlanner.plan(declaration.from, parent, declaration.joins, infoOf, _ => true, (_, _) => true).left.map(e => invalid(e.message))
    } yield ()
  }

  def run(bankId: Option[String], declaration: DynamicQueryDeclaration, callerParams: Map[String, List[String]],
          callerUserId: Option[String], consumerId: String): Either[DynamicQueryFailure, JObject] = {
    val from = declaration.from
    def mayReadEntity(entityName: String): Boolean = DynamicEntityInfo.mayReadRecords(bankId, entityName, callerUserId, consumerId)
    val mayReadField: (String, String) => Boolean = DynamicEntityInfo.fieldReader(bankId, callerUserId, consumerId)
    for {
      parentDefinition <- DynamicEntityHelper.definitionOf(bankId, from).toRight(invalid(s"There is no Dynamic Entity '$from' in this space."))
      plan <- planPage(bankId, declaration, parentDefinition, callerParams)
      _ <- accessProblem(bankId, declaration, plan, mayReadEntity, mayReadField).toLeft(())
      joinPlan <- JoinPlanner.planFor(bankId, from, declaration.joins, callerUserId, consumerId, mayReadEntity).left.map(e => invalid(e.message))
      pageAndTotal <- readPage(bankId, from, parentDefinition, plan, callerUserId, declaration.envelope.count.isDefined)
    } yield {
      val (page, total) = pageAndTotal
      val joined = joinPlan(page, bankId, callerUserId, consumerId)
      val joinedNames = joinPlan.joins.flatMap(_.resultNames)
      val rows = joined.map(record => project(record, declaration.select, joinedNames, parentDefinition, mayReadField(from, _)))
      JObject(JField(declaration.envelope.rows, JArray(rows)) :: declaration.envelope.count.map(name => JField(name, JInt(total))).toList)
    }
  }

  /**
   * This explains how a Dynamic Query would be answered for one caller, without reading any record:
   * the statements it would run, in order (the SQL, with `?` for every value, when the step is SQL
   * built by OBP; words when it goes through the record provider), and the access it needs, with
   * whether this caller has it. It is for the author of a query, to check that the SQL is sane and
   * that the access rules are what they expect.
   *
   * The SQL comes from the same builders as the statements a call runs, and the verdict from the same
   * access check ([[accessProblem]]), so the explanation cannot differ from what a call does.
   */
  def explain(bankId: Option[String], declaration: DynamicQueryDeclaration, callerParams: Map[String, List[String]],
              callerUserId: Option[String], consumerId: String): Either[DynamicQueryFailure, DynamicQueryExplanation] = {
    val from = declaration.from
    val space = code.api.dynamic.entity.helper.DynamicEntitySpace.bankIdOrSystem(bankId)
    def definition(entityName: String): Option[DynamicEntityInfo] = DynamicEntityHelper.definitionOf(bankId, entityName)
    def infoOf(entityName: String): Option[JoinEntityInfo] = definition(entityName).map(JoinEntityInfo.of)
    def mayReadEntity(entityName: String): Boolean = DynamicEntityInfo.mayReadRecords(bankId, entityName, callerUserId, consumerId)
    val mayReadField: (String, String) => Boolean = DynamicEntityInfo.fieldReader(bankId, callerUserId, consumerId)
    for {
      // The same checks as creating or Check: an invalid declaration is refused, not explained.
      _ <- validate(bankId, declaration)
      parentDefinition <- definition(from).toRight(invalid(s"There is no Dynamic Entity '$from' in this space."))
      plan <- planPage(bankId, declaration, parentDefinition, callerParams)
      // Planned without the caller's access, so the joins can be described even for a caller who may not run them.
      joins <- JoinPlanner.plan(from, JoinEntityInfo.of(parentDefinition), declaration.joins, infoOf, _ => true, (_, _) => true)
                 .left.map(e => invalid(e.message))
    } yield {
      val entities = entitiesRead(declaration, plan).map { entity =>
        val info = definition(entity)
        ExplainedEntity(entity, DynamicEntityInfo.canGetRole(entity, bankId).toString, space,
          info.exists(_.hasPublicAccess), info.exists(_.useRowLevelAccess), mayReadEntity(entity))
      }
      // Read-restricted fields the query touches: those it returns or copies, and those it filters, sorts or picks by.
      val touched: List[(String, String)] =
        (declaration.select.getOrElse(parentDefinition.propertyNames).map(from -> _) ++ ProjectionReadiness.planFields(plan).map(from -> _) ++
          joins.flatMap(j => (j.fields.map(_._2) ++ j.where.map(_.field) ++ j.order.map(_.field).toList).map(j.entity -> _)) ++
          plan.joins.flatMap(j => j.predicate.map(_.field).map(j.childEntity -> _))).distinct
      // A field can be restricted twice: by its own read Role, and (for a caller reaching the entity through
      // public access) by hide_field_from_public_access, which the entity's read Role lifts.
      val restricted = touched.flatMap { case (entity, field) =>
        val info = definition(entity)
        val byRole = info.filter(_.readRestrictedFields.contains(field)).map(i =>
          ExplainedField(entity, field, "read_role_required",
            DynamicEntityInfo.fieldReadRole(entity, field, bankId, i.explicitReadRole(field)).toString, mayReadField(entity, field)))
        val fromPublic = info.filter(_.publicHiddenFields.contains(field)).map(_ =>
          ExplainedField(entity, field, "hide_field_from_public_access",
            DynamicEntityInfo.canGetRole(entity, bankId).toString, mayReadField(entity, field)))
        byRole.toList ++ fromPublic.toList
      }
      val refusal = accessProblem(bankId, declaration, plan, mayReadEntity, mayReadField)
        .orElse(if (parentDefinition.useRowLevelAccess && plan.joins.nonEmpty || !pageReadable(bankId, from, plan) && plan.joins.nonEmpty)
          Some(DynamicQueryFailure(400, DynamicEntityJoinRequiresProjection)) else None)
      DynamicQueryExplanation(space, callerUserId.isEmpty, refusal.isEmpty, refusal.map(_.message), entities, restricted,
        rules(space), pageSteps(bankId, declaration, parentDefinition, plan, callerUserId) ++ joinSteps(bankId, from, joins))
    }
  }

  /** True when the page would be read from the projection (see [[readPage]], which makes the same choice). */
  private def pageReadable(bankId: Option[String], from: String, plan: QueryPlan): Boolean =
    IndexingCapabilities.projectionEnabled && (ProjectionReadiness.planFields(plan).nonEmpty || plan.joins.nonEmpty) &&
      ProjectionReadiness.ready(bankId, from, plan)

  private def rules(space: String): List[String] = List(
    s"Only Dynamic Entities of space $space are read, and only through their definitions: no other OBP data can be named.",
    "Only shared records are used, never a User's personal records, whoever owns them.",
    "For an entity with row-level access, only the records the caller's access list allows are used.",
    "A field that requires a read Role is null (or left out, when not selected) unless the caller holds that Role, and the caller cannot filter or sort on it.",
    "A joined value is null when there is no matching record, when the caller may not read it, or when it lacks the field, so a join never reveals a hidden record.",
    "Every value is bound as a parameter (shown as ?); table and column names come from the definitions.")

  private def pageSteps(bankId: Option[String], declaration: DynamicQueryDeclaration, parent: DynamicEntityInfo,
                        plan: QueryPlan, callerUserId: Option[String]): List[ExplainedStep] = {
    val from = declaration.from
    val wantsCount = declaration.envelope.count.isDefined
    if (parent.useRowLevelAccess)
      List(ExplainedStep(s"Read the page of '$from'", "record provider", None, List(
        s"'$from' uses row-level access: its shared records are read through the record provider, only those the caller's access list allows are kept, and they are filtered, sorted and paged in memory.")))
    else if (pageReadable(bankId, from, plan))
      ExplainedStep(s"Read the page of '$from'", "projection",
        PostgresProjectionBackend.sqlFor(from, bankId, callerUserId, isPersonalEntity = false, plan),
        List("Filters and the sort run on the projection's indexed columns; only the records of the page are read.")) ::
        (if (wantsCount) List(ExplainedStep(s"Count every match of '$from', for the envelope's '${declaration.envelope.count.getOrElse("")}'", "projection",
          PostgresProjectionBackend.sqlFor(from, bankId, callerUserId, isPersonalEntity = false, plan, counting = true), Nil)) else Nil)
    else {
      val reason =
        if (!IndexingCapabilities.projectionEnabled) "This instance does not use the query projection"
        else if (ProjectionReadiness.planFields(plan).isEmpty && plan.joins.isEmpty) "The page neither filters nor sorts on an indexed field"
        else "A field the page filters or sorts on is indexed but its projection column is not ready yet"
      List(ExplainedStep(s"Read the page of '$from'", "record provider", None, List(
        s"$reason, so every shared record of '$from' is read through the record provider and filtered, sorted and paged in memory." +
          (if (wantsCount) " The count is the number of matches before paging." else ""))))
    }
  }

  private def joinSteps(bankId: Option[String], from: String, joins: List[Join]): List[ExplainedStep] = {
    val firstUse = scala.collection.mutable.Map[RecordJoiner.Link, Int]()
    joins.zipWithIndex.map { case (join, index) =>
      val number = index + 1
      val link = RecordJoiner.linkOf(join)
      val described = join.direction match {
        case JoinDirection.Forward => s"Join $number follows '$from.${join.on}' to '${join.entity}' (forward)"
        case JoinDirection.Reverse => s"Join $number: '${join.entity}' records whose '${join.on}' names the '$from' (reverse)"
      }
      val afterRead = List(
        Some(s"Cardinality ${join.cardinality.name}" + join.order.map(o => s", ordered ${if (o.descending) "latest" else "earliest"} first by '${o.field}'").getOrElse("") + "."),
        if (join.where.nonEmpty) Some(s"Its where filter is applied in memory to the records read: ${join.where.map(f => s"${f.field} ${f.op.name} ${f.values.mkString(",")}").mkString(", ")}.") else None,
        if (definitionRowLevel(bankId, join.entity)) Some(s"'${join.entity}' uses row-level access: only records the caller's access list allows are used.") else None
      ).flatten
      firstUse.get(link) match {
        case Some(earlier) =>
          ExplainedStep(described, "shared", None, s"Uses the records already read for Join $earlier; nothing more is read." :: afterRead)
        case None =>
          firstUse(link) = number
          join.direction match {
            case JoinDirection.Forward =>
              ExplainedStep(described, "record provider", None,
                s"One read for the whole page: the '${join.entity}' records whose id is one of the page's '${join.on}' values; personal records are dropped." :: afterRead)
            case JoinDirection.Reverse if IndexingCapabilities.projectionEnabled && ProjectionProvisioner.readyFields(bankId, join.entity).contains(join.on) =>
              ExplainedStep(described, "projection",
                Some(ProjectionStore.readByReferenceSql(ProjectionNaming.tableName(bankId, join.entity), ProjectionNaming.columnName(join.on),
                  bankId, join.entity, List("?"))),
                s"One read for the whole page, through the index on '${join.on}': IN (...) holds one ? per record on the page, in batches of 1000." :: afterRead)
            case JoinDirection.Reverse =>
              ExplainedStep(described, "record provider", None,
                s"The projection column for '${join.on}' is not in use here, so every shared record of '${join.entity}' is read and those whose '${join.on}' names a record of the page are kept." :: afterRead)
          }
      }
    }
  }

  private def definitionRowLevel(bankId: Option[String], entityName: String): Boolean =
    DynamicEntityHelper.definitionOf(bankId, entityName).exists(_.useRowLevelAccess)

  /** Every entity a query reads for this plan: `from`, each join's entity, and each entity a caller's obp_exists names. */
  private def entitiesRead(declaration: DynamicQueryDeclaration, plan: QueryPlan): List[String] =
    (declaration.from :: declaration.joins.map(_.entity) ++ plan.joins.map(_.childEntity)).distinct

  /**
   * Why this caller may not run this query, or None. Shared by [[run]] and [[explain]], so the
   * verdict an explanation shows is the one a call gets: first every entity the caller may not read
   * (403, all of them named), then a filter or sort on a field the caller may not read (400).
   */
  private def accessProblem(bankId: Option[String], declaration: DynamicQueryDeclaration, plan: QueryPlan,
                            mayReadEntity: String => Boolean, mayReadField: (String, String) => Boolean): Option[DynamicQueryFailure] =
    notReadable(bankId, entitiesRead(declaration, plan).filterNot(mayReadEntity)).orElse {
      val from = declaration.from
      (ProjectionReadiness.planFields(plan).filterNot(mayReadField(from, _)).map(from -> _) ++
        plan.joins.flatMap(j => j.predicate.map(_.field).filterNot(mayReadField(j.childEntity, _)).map(j.childEntity -> _))).headOption
        .map { case (entity, field) => invalid(s"You may not read '$field' on '$entity', so it cannot be filtered or sorted on.") }
    }

  /**
   * The 403 for the entities the caller may not read, all of them at once, each with the Role that
   * would let the caller read it and the bank it is needed at, so a caller missing several learns of
   * them in one answer. None when the list is empty.
   */
  private def notReadable(bankId: Option[String], entities: List[String]): Option[DynamicQueryFailure] =
    if (entities.isEmpty) None
    else {
      val bank = code.api.dynamic.entity.helper.DynamicEntitySpace.bankIdOrSystem(bankId)
      val missing = entities.map(entity => s"$entity (needs ${DynamicEntityInfo.canGetRole(entity, bankId)} at bank $bank)")
      Some(DynamicQueryFailure(403, s"$DynamicQueryEntityNotReadable${missing.mkString(", ")}."))
    }

  private def selectError(declaration: DynamicQueryDeclaration, parent: JoinEntityInfo): Option[DynamicQueryFailure] =
    declaration.select.flatMap { fields =>
      val declared = parent.propertyNames + parent.idFieldName
      fields.find(f => !declared.contains(f)).map(f => invalid(s"'select' names '$f', which '${declaration.from}' does not have."))
        .orElse(if (fields.distinct.size != fields.size) Some(invalid("'select' names a field twice.")) else None)
    }

  /** The page's plan: the declaration's `where` plus the caller's parameters, checked by the list endpoint's planner. */
  private def planPage(bankId: Option[String], declaration: DynamicQueryDeclaration, parent: DynamicEntityInfo,
                       callerParams: Map[String, List[String]]): Either[DynamicQueryFailure, QueryPlan] =
    (for {
      parsed <- QueryParamParser.parse(callerParams)
      (callerFilters, callerJoins, sort, page) = parsed
      plan <- QueryPlanner.plan(declaration.where ++ callerFilters, callerJoins, sort, page, declaration.from,
                parent.indexedFields, parent.referenceFields,
                child => DynamicEntityHelper.definitionOf(bankId, child).map(i => JoinTargetInfo(i.indexedFields, i.referenceFields, i.unindexedReferenceFields)),
                parent.unindexedReferenceFields)
    } yield plan).left.map(e => invalid(e.message))

  /** One page of shared parent records, and how many match in all (0 unless `wantTotal`). */
  private def readPage(bankId: Option[String], from: String, parent: DynamicEntityInfo, plan: QueryPlan,
                       callerUserId: Option[String], wantTotal: Boolean): Either[DynamicQueryFailure, (List[JObject], Long)] = {
    val fieldTypes = parent.indexedFields.map { case (name, spec) => name -> spec.fieldType }
    def inMemory(records: List[JObject]): (List[JObject], Long) = {
      val matching = InMemoryQueryExecutor.execute(records, plan.copy(page = Page.empty), fieldTypes)
      val afterOffset = plan.page.offset.filter(_ > 0).fold(matching)(matching.drop)
      (plan.page.limit.filter(_ >= 0).fold(afterOffset)(afterOffset.take), matching.size.toLong)
    }
    def parse(json: String): JObject = JsonAliases.parse(json).asInstanceOf[JObject]
    val provider = DynamicDataProvider.connectorMethodProvider.vend

    // The projection is used only when the plan filters, sorts or joins: an entity's projection table
    // exists only once a field is indexed, and a plan touching no field gains nothing from it.
    val projectionReady = pageReadable(bankId, from, plan)

    if (parent.useRowLevelAccess) {
      if (plan.joins.nonEmpty) Left(DynamicQueryFailure(400, DynamicEntityJoinRequiresProjection))
      else {
        val readable = callerUserId.map(DynamicDataAccessProvider.provider.vend.getReadableDynamicDataIds(bankId, from, _).toSet).getOrElse(Set.empty)
        Right(inMemory(provider.getAllCommunity(bankId, from).filter(_.dynamicDataId.exists(readable.contains)).map(r => parse(r.dataJson))))
      }
    } else if (projectionReady) {
      import cats.effect.unsafe.implicits.global
      // The caller is passed so an obp_exists join onto a row-level entity counts only the rows the caller
      // may read. (With isPersonalEntity = false it does not change which parent records are in scope.)
      val page = PostgresProjectionBackend.query(from, bankId, callerUserId, isPersonalEntity = false, plan).unsafeRunSync()
      val total = if (wantTotal) PostgresProjectionBackend.count(from, bankId, callerUserId, isPersonalEntity = false, plan).unsafeRunSync() else 0L
      Right((page, total))
    } else if (plan.joins.nonEmpty) {
      // obp_exists / obp_not_exists are evaluated only by the projection.
      Left(DynamicQueryFailure(400, DynamicEntityJoinRequiresProjection))
    } else {
      // Not enabled, or a field the plan needs is not ready yet: in memory gives the same answer, only slower.
      Right(inMemory(provider.getAllDataJson(bankId, from, None, isPersonalEntity = false)))
    }
  }

  /**
   * The fields of one returned record: the selected ones in order (null when missing or not readable),
   * or every field the caller may read; then the joined results, in the order of the joins.
   */
  private def project(record: JObject, select: Option[List[String]], joinedNames: List[String], parent: DynamicEntityInfo,
                      mayReadField: String => Boolean): JObject = {
    val joinedFields = joinedNames.map(name => JField(name, record \ name))
    select match {
      case Some(fields) =>
        JObject(fields.map(f => JField(f, if (mayReadField(f)) record \ f match { case JNothing => JNull; case v => v } else JNull)) ++ joinedFields)
      case None =>
        val restricted = (parent.readRestrictedFields ++ parent.publicHiddenFields).toSet.filterNot(mayReadField)
        JObject(record.obj.filterNot { case (name, _) => restricted.contains(name) })
    }
  }
}
