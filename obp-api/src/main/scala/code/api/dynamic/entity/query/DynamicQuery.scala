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
import code.api.dynamic.entity.projection.{IndexingCapabilities, PostgresProjectionBackend, ProjectionReadiness}
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
    def mayReadField(entityName: String, field: String): Boolean = DynamicEntityInfo.mayReadField(bankId, entityName, field, callerUserId)
    for {
      parentDefinition <- DynamicEntityHelper.definitionOf(bankId, from).toRight(invalid(s"There is no Dynamic Entity '$from' in this space."))
      plan <- planPage(bankId, declaration, parentDefinition, callerParams)
      readEntities = (from :: declaration.joins.map(_.entity) ++ plan.joins.map(_.childEntity)).distinct
      _ <- readEntities.find(e => !mayReadEntity(e))
             .map(e => DynamicQueryFailure(403, s"${DynamicQueryEntityNotReadable}It reads '$e', and you hold neither its read Role nor other access to it.")).toLeft(())
      unreadable = (ProjectionReadiness.planFields(plan).filterNot(mayReadField(from, _)).map(from -> _) ++
                    plan.joins.flatMap(j => j.predicate.map(_.field).filterNot(mayReadField(j.childEntity, _)).map(j.childEntity -> _))).headOption
      _ <- unreadable.map { case (entity, field) => invalid(s"You may not read '$field' on '$entity', so it cannot be filtered or sorted on.") }.toLeft(())
      joinPlan <- JoinPlanner.planFor(bankId, from, declaration.joins, callerUserId, mayReadEntity).left.map(e => invalid(e.message))
      pageAndTotal <- readPage(bankId, from, parentDefinition, plan, callerUserId, declaration.envelope.count.isDefined)
    } yield {
      val (page, total) = pageAndTotal
      val joined = joinPlan(page, bankId, callerUserId)
      val joinedNames = joinPlan.joins.flatMap(_.resultNames)
      val rows = joined.map(record => project(record, declaration.select, joinedNames, parentDefinition, mayReadField(from, _)))
      JObject(JField(declaration.envelope.rows, JArray(rows)) :: declaration.envelope.count.map(name => JField(name, JInt(total))).toList)
    }
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
    val planTouchesFields = ProjectionReadiness.planFields(plan).nonEmpty || plan.joins.nonEmpty
    val projectionReady = IndexingCapabilities.projectionEnabled && planTouchesFields && ProjectionReadiness.ready(bankId, from, plan)

    if (parent.useRowLevelAccess) {
      if (plan.joins.nonEmpty) Left(DynamicQueryFailure(400, DynamicEntityJoinRequiresProjection))
      else {
        val readable = callerUserId.map(DynamicDataAccessProvider.provider.vend.getReadableDynamicDataIds(bankId, from, _).toSet).getOrElse(Set.empty)
        Right(inMemory(provider.getAllCommunity(bankId, from).filter(_.dynamicDataId.exists(readable.contains)).map(r => parse(r.dataJson))))
      }
    } else if (projectionReady) {
      import cats.effect.unsafe.implicits.global
      val page = PostgresProjectionBackend.query(from, bankId, None, isPersonalEntity = false, plan).unsafeRunSync()
      val total = if (wantTotal) PostgresProjectionBackend.count(from, bankId, None, isPersonalEntity = false, plan).unsafeRunSync() else 0L
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
        val restricted = parent.readRestrictedFields.toSet.filterNot(mayReadField)
        JObject(record.obj.filterNot { case (name, _) => restricted.contains(name) })
    }
  }
}
