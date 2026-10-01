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

import code.api.dynamic.entity.helper.{DynamicEntityHelper, DynamicEntityInfo}
import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import org.json4s.JsonAST.{JBool, JObject, JValue}

/**
 * A join adds to each record of a page something about the records of another entity that are linked
 * to it by a `reference:` field. It is the join machinery Dynamic Views are built on (see
 * ideas/DYNAMIC_ENTITY_VIEWS.md); no endpoint accepts a join from a caller.
 *
 * A reference field links two entities, and it can be read from either end:
 *  - forward: the record's own field names the other record. An `activity` whose `operator_id` is
 *    `reference:operator` is joined to its operator. At most one record can match.
 *  - reverse: the other entity's field names this record. `certificate` records whose `activity_id` is
 *    `reference:activity` are joined to their activity. Any number can match.
 *
 * The author does not say which: the planner sees which entity holds the `on` field and infers it. Only
 * when both readings are possible, as with a self-reference (`employee.manager_id` is
 * `reference:employee`: my manager, or my direct reports?), must the author give `direction`.
 *
 * `cardinality` says what to do with the matching records: copy the fields of one (`at_most_one`), list
 * them all (`many`), or say whether any exists (`exists`). A forward join is `at_most_one` unless it
 * says `exists`, and can never be `many`. A reverse join must state its cardinality, and an
 * `at_most_one` reverse join must say how to `pick` one when several match.
 */
sealed trait JoinDirection { def name: String }
object JoinDirection {
  case object Forward extends JoinDirection { val name = "forward" }
  case object Reverse extends JoinDirection { val name = "reverse" }
  val byName: Map[String, JoinDirection] = List(Forward, Reverse).map(d => d.name -> d).toMap
}

sealed trait Cardinality { def name: String }
object Cardinality {
  /** At most one matching record: its fields are copied to the top level of the record. */
  case object AtMostOne extends Cardinality { val name = "at_most_one" }
  /** Every matching record: an array of objects under one name. */
  case object Many extends Cardinality { val name = "many" }
  /** Whether a matching record exists: one value under one name. */
  case object Exists extends Cardinality { val name = "exists" }

  val all: List[Cardinality] = List(AtMostOne, Many, Exists)
  val byName: Map[String, Cardinality] = all.map(c => c.name -> c).toMap
}

/** An order on one field of the matching records: `descending` puts the largest first. */
case class RecordOrder(field: String, descending: Boolean)
object RecordOrder {
  /** `latest_by:<field>` (largest first) or `earliest_by:<field>` (smallest first); None for anything else. */
  def parse(text: String): Option[RecordOrder] = text.split(":", 2) match {
    case Array("latest_by", field) if field.trim.nonEmpty   => Some(RecordOrder(field.trim, descending = true))
    case Array("earliest_by", field) if field.trim.nonEmpty => Some(RecordOrder(field.trim, descending = false))
    case _ => None
  }
}

/**
 * A join as an author writes it.
 *
 *  - `entity`: the other entity; `on`: the `reference:` field that links the two, on either of them.
 *  - `direction`: `forward` or `reverse`; needed only when both are possible (see [[JoinDirection]]).
 *  - `cardinality`: `at_most_one`, `many` or `exists`. Optional for a forward join (`at_most_one`).
 *  - `where`: filters on the other entity's records, in the list endpoint's grammar (`eq`, `in`,
 *    `between`, ...), applied before the cardinality.
 *  - `fields`: (name in the result, field of the other record) pairs: for `at_most_one` they become
 *    fields of the record; for `many`, the fields of each element. Not used by `exists`.
 *  - `as`: the name of the result of `many` (the array) and `exists` (the value).
 *  - `pick`: for a reverse `at_most_one`, the rule that chooses one record when several match.
 *  - `order`: optional for `many`, the order of the array; by record id when absent.
 *  - `trueValue` / `falseValue`: for `exists`; JSON true and false when absent.
 */
case class JoinRequest(
  entity: String,
  on: String,
  direction: Option[String] = None,
  cardinality: Option[String] = None,
  where: List[Filter] = Nil,
  fields: List[(String, String)] = Nil,
  as: Option[String] = None,
  pick: Option[String] = None,
  order: Option[String] = None,
  trueValue: Option[JValue] = None,
  falseValue: Option[JValue] = None
)

/** A join the planner has checked, with its direction decided. `order` is the parsed `pick` or `order`. */
case class Join(
  entity: String,
  entityIdField: String,
  on: String,
  direction: JoinDirection,
  cardinality: Cardinality,
  where: List[Filter],
  fields: List[(String, String)],
  as: Option[String],
  order: Option[RecordOrder],
  trueValue: JValue,
  falseValue: JValue,
  entityFieldTypes: Map[String, DynamicEntityFieldType]
) {
  /** The names this join adds to the record. */
  def resultNames: List[String] = cardinality match {
    case Cardinality.AtMostOne => fields.map(_._1)
    case _                     => as.toList
  }

}

/**
 * This is what the join planner needs to know about one Dynamic Entity: the names of its declared
 * properties, the name of the id field its records carry, every `reference:<Target>` field it
 * declares (field name -> target entity, indexed or not), the declared types of its fields (for a
 * `where` filter or an order), and which fields are declared `"indexed": true`.
 */
case class JoinEntityInfo(
  propertyNames: Set[String],
  idFieldName: String,
  referenceTargets: Map[String, String],
  fieldTypes: Map[String, DynamicEntityFieldType] = Map.empty,
  indexedFieldNames: Set[String] = Set.empty
)

object JoinEntityInfo {
  def of(info: DynamicEntityInfo): JoinEntityInfo =
    JoinEntityInfo(info.propertyNames.toSet, info.idName, info.allReferenceFields, info.declaredFieldTypes, info.indexedFields.keySet)
}

/** A checked set of joins for one parent entity, ready to apply to pages of its records. */
case class JoinPlan(parentEntityName: String, parentIdField: String, joins: List[Join]) {

  /** Add every join's result to each record of the page. */
  def apply(records: List[JObject], bankId: Option[String], callerUserId: Option[String]): List[JObject] =
    RecordJoiner.join(records, joins, parentIdField, bankId, callerUserId)
}

/**
 * This object checks joins against the entity definitions before any record is read, so an author
 * learns of a mistake from a clear message rather than from a null in every row.
 *
 * For each join, in order:
 *  1. the other entity exists in the same space, and the caller may read it;
 *  2. the direction: `on` must be a `reference:` field of the parent pointing at the other entity
 *     (forward), or of the other entity pointing at the parent (reverse); when both hold, `direction`
 *     must choose;
 *  3. a reverse join's `on` field is declared `"indexed": true`, because the records that refer to a
 *     page of parents are looked up by it, which the query projection serves from an index (a forward
 *     join reads the other records by id, and needs no index);
 *  4. every `where` filter names a declared field the caller may read, with an operator and value valid
 *     for its type (the same rules as the list endpoint's `obp_filter`);
 *  5. the cardinality suits the direction, the result has the shape the cardinality needs (see
 *     [[JoinRequest]]), every copied field is declared, and a `pick` or `order` names a declared field
 *     the caller may read (ordering by a field the caller cannot see would reveal it);
 *  6. every name it adds to the record is a usable field name, not taken by the parent's own fields
 *     or an earlier join.
 */
object JoinPlanner {

  private val fieldNamePattern = "^[A-Za-z_][A-Za-z0-9_]{0,254}$".r

  /**
   * As [[plan]], reading the definitions from the stored Dynamic Entities of one space (`bankId`, None
   * for the system space). `callerMayReadEntity` decides whether the caller may read another entity at
   * all ([[DynamicEntityInfo.mayReadRecords]] is the rule a GET applies); field-level read restrictions
   * are judged with [[DynamicEntityInfo.mayReadField]] for `callerUserId`.
   */
  def planFor(
    bankId: Option[String],
    parentEntityName: String,
    requests: List[JoinRequest],
    callerUserId: Option[String],
    callerMayReadEntity: String => Boolean
  ): Either[QueryError, JoinPlan] = {
    def infoOf(entityName: String): Option[JoinEntityInfo] =
      DynamicEntityHelper.definitionOf(bankId, entityName).map(JoinEntityInfo.of)
    for {
      parent <- infoOf(parentEntityName).toRight(QueryError(s"There is no Dynamic Entity '$parentEntityName' in this space."))
      joins <- plan(parentEntityName, parent, requests, infoOf, callerMayReadEntity,
                 (entity, field) => DynamicEntityInfo.mayReadField(bankId, entity, field, callerUserId))
    } yield JoinPlan(parentEntityName, parent.idFieldName, joins)
  }

  def plan(
    parentEntityName: String,
    parent: JoinEntityInfo,
    requests: List[JoinRequest],
    entityInfoOf: String => Option[JoinEntityInfo],
    callerMayReadEntity: String => Boolean,
    callerMayReadField: (String, String) => Boolean
  ): Either[QueryError, List[Join]] = {
    val parentFieldNames = parent.propertyNames + parent.idFieldName
    requests.zipWithIndex.foldLeft(Right(Nil): Either[QueryError, List[Join]]) {
      case (Left(error), _) => Left(error)
      case (Right(planned), (request, index)) =>
        val taken = parentFieldNames ++ planned.flatMap(_.resultNames)
        planOne(parentEntityName, parent, taken, request, index, entityInfoOf, callerMayReadEntity, callerMayReadField)
          .map(join => planned :+ join)
    }
  }

  private def planOne(
    parentEntityName: String,
    parent: JoinEntityInfo,
    taken: Set[String],
    request: JoinRequest,
    index: Int,
    entityInfoOf: String => Option[JoinEntityInfo],
    callerMayReadEntity: String => Boolean,
    callerMayReadField: (String, String) => Boolean
  ): Either[QueryError, Join] = {
    val position = s"Join ${index + 1} ('${request.entity}' on '${request.on}')"
    val entity = request.entity
    for {
      other <- entityInfoOf(entity).toRight(QueryError(s"$position: there is no Dynamic Entity '$entity' in this space."))
      _ <- check(callerMayReadEntity(entity), s"$position: you may not read '$entity', so it cannot be joined to '$parentEntityName'.")
      direction <- directionOf(position, parentEntityName, parent, entity, other, request)
      _ <- check(direction == JoinDirection.Forward || other.indexedFieldNames.contains(request.on),
             s"$position: '${request.on}' on '$entity' must be declared \"indexed\": true, because the '$entity' records of a page of " +
             s"'$parentEntityName' are looked up by it. Add \"indexed\": true to that field and let the index build.")
      _ <- firstError(request.where.map(filter => whereError(position, entity, other, filter, callerMayReadField)))
      cardinality <- cardinalityOf(position, parentEntityName, entity, direction, request)
      order <- shapeAndOrder(position, entity, other, direction, cardinality, request, callerMayReadField)
      _ <- namesError(position, cardinality, request, taken)
    } yield Join(entity, other.idFieldName, request.on, direction, cardinality, request.where, request.fields, request.as, order,
      request.trueValue.getOrElse(JBool(true)), request.falseValue.getOrElse(JBool(false)), other.fieldTypes)
  }

  /** Which way `on` links the two entities: inferred, or checked when the author gave `direction`. */
  private def directionOf(position: String, parentEntityName: String, parent: JoinEntityInfo, entity: String,
                          other: JoinEntityInfo, request: JoinRequest): Either[QueryError, JoinDirection] = {
    val on = request.on
    val forwardPossible = parent.referenceTargets.get(on).contains(entity)
    val reversePossible = other.referenceTargets.get(on).contains(parentEntityName)
    def forwardMissing = s"'$parentEntityName' has no field '$on' typed 'reference:$entity'"
    def reverseMissing = s"'$entity' has no field '$on' typed 'reference:$parentEntityName'"
    request.direction match {
      case Some(text) =>
        JoinDirection.byName.get(text).toRight(QueryError(s"$position: direction must be 'forward' or 'reverse'; got '$text'.")).flatMap {
          case JoinDirection.Forward if !forwardPossible => Left(QueryError(s"$position: direction is forward, but $forwardMissing."))
          case JoinDirection.Reverse if !reversePossible => Left(QueryError(s"$position: direction is reverse, but $reverseMissing."))
          case chosen => Right(chosen)
        }
      case None =>
        (forwardPossible, reversePossible) match {
          case (true, false) => Right(JoinDirection.Forward)
          case (false, true) => Right(JoinDirection.Reverse)
          case (true, true) => Left(QueryError(
            s"$position: '$on' links the two either way: forward (the '$parentEntityName' record's '$on' names a '$entity') or " +
            s"reverse ('$entity' records whose '$on' names the '$parentEntityName'). Say which with \"direction\": \"forward\" or \"reverse\"."))
          case (false, false) => Left(QueryError(
            s"$position: '$on' must be a reference field linking '$parentEntityName' and '$entity', on either of them, but " +
            s"$forwardMissing and $reverseMissing."))
        }
    }
  }

  private def cardinalityOf(position: String, parentEntityName: String, entity: String, direction: JoinDirection,
                            request: JoinRequest): Either[QueryError, Cardinality] = {
    val stated: Either[QueryError, Option[Cardinality]] = request.cardinality match {
      case None => Right(None)
      case Some(text) => Cardinality.byName.get(text).map(Option(_)).toRight(QueryError(
        s"$position: cardinality must be one of ${Cardinality.all.map(_.name).mkString(", ")}; got '$text'."))
    }
    stated.flatMap { cardinality =>
      (direction, cardinality) match {
        case (JoinDirection.Forward, Some(Cardinality.Many)) => Left(QueryError(
          s"$position: this join follows '$parentEntityName.${request.on}', which names at most one '$entity', so it cannot be 'many'."))
        case (JoinDirection.Forward, None) => Right(Cardinality.AtMostOne)
        case (JoinDirection.Reverse, None) => Left(QueryError(
          s"$position: this join finds the '$entity' records whose '${request.on}' names the '$parentEntityName', and there can be " +
          s"several, so it needs \"cardinality\": ${Cardinality.all.map(_.name).mkString(", ")}."))
        case (_, Some(chosen)) => Right(chosen)
      }
    }
  }

  private def whereError(position: String, entity: String, other: JoinEntityInfo, filter: Filter,
                         callerMayReadField: (String, String) => Boolean): Option[QueryError] =
    if (!other.fieldTypes.contains(filter.field))
      Some(QueryError(s"$position: the where filter names '${filter.field}', which '$entity' does not declare with a type that can be filtered."))
    else if (!callerMayReadField(entity, filter.field))
      Some(QueryError(s"$position: you may not read '${filter.field}' on '$entity', so it cannot be filtered on."))
    else {
      val scalarSpecs = other.fieldTypes.map { case (name, fieldType) => name -> FieldSpec(fieldType, OperatorMatrix.SCALAR) }
      QueryPlanner.validateFilter(filter, scalarSpecs).map(e => QueryError(s"$position: ${e.message}"))
    }

  private def shapeAndOrder(position: String, entity: String, other: JoinEntityInfo, direction: JoinDirection,
                            cardinality: Cardinality, request: JoinRequest,
                            callerMayReadField: (String, String) => Boolean): Either[QueryError, Option[RecordOrder]] = {
    val copyable = other.propertyNames + other.idFieldName
    def copiedFieldsDeclared: Either[QueryError, Unit] =
      request.fields.map(_._2).find(f => !copyable.contains(f))
        .map(f => QueryError(s"$position: '$entity' has no field '$f'.")).toLeft(())
    def parsedOrder(text: String, what: String): Either[QueryError, RecordOrder] =
      for {
        order <- RecordOrder.parse(text).toRight(QueryError(
                   s"$position: $what must be 'latest_by:<field>' or 'earliest_by:<field>'; got '$text'."))
        _ <- check(other.fieldTypes.contains(order.field), s"$position: $what names '${order.field}', which '$entity' does not declare with a type that can be ordered.")
        _ <- check(callerMayReadField(entity, order.field), s"$position: you may not read '${order.field}' on '$entity', so it cannot be ordered by.")
      } yield order
    def absent(value: Option[_], name: String, reason: String): Either[QueryError, Unit] =
      check(value.isEmpty, s"$position: '$name' is not used $reason.")
    val withCardinality = s"with cardinality ${cardinality.name}"
    val oneAtMost = "by a join that can find at most one record"

    cardinality match {
      case Cardinality.AtMostOne =>
        for {
          _ <- check(request.fields.nonEmpty, s"$position: at_most_one needs 'fields', the fields to copy from the matching record.")
          _ <- absent(request.as, "as", withCardinality)
          _ <- absent(request.order, "order", withCardinality)
          _ <- absent(request.trueValue.orElse(request.falseValue), "true_value / false_value", withCardinality)
          _ <- copiedFieldsDeclared
          order <- direction match {
                     case JoinDirection.Forward => absent(request.pick, "pick", oneAtMost).map(_ => None)
                     case JoinDirection.Reverse =>
                       request.pick.toRight(QueryError(
                         s"$position: at_most_one needs 'pick' ('latest_by:<field>' or 'earliest_by:<field>'), the rule that chooses one record " +
                         s"when several '$entity' records name the same record in '${request.on}'."))
                         .flatMap(parsedOrder(_, "pick")).map(Option(_))
                   }
        } yield order
      case Cardinality.Many =>
        for {
          _ <- check(request.fields.nonEmpty, s"$position: many needs 'fields', the fields of each element of the array.")
          _ <- absent(request.pick, "pick", withCardinality)
          _ <- absent(request.trueValue.orElse(request.falseValue), "true_value / false_value", withCardinality)
          _ <- copiedFieldsDeclared
          elementNames = request.fields.map(_._1)
          _ <- elementNames.find(n => !fieldNamePattern.matches(n))
                 .map(n => QueryError(s"$position: element field name '$n' must be made of letters, digits and underscores, not starting with a digit.")).toLeft(())
          _ <- check(elementNames.distinct.size == elementNames.size, s"$position: an element field name is used twice.")
          order <- request.order.map(parsedOrder(_, "order").map(Option(_))).getOrElse(Right(None))
        } yield order
      case Cardinality.Exists =>
        for {
          _ <- check(request.fields.isEmpty, s"$position: 'fields' is not used $withCardinality.")
          _ <- absent(request.pick, "pick", withCardinality)
          _ <- absent(request.order, "order", withCardinality)
        } yield None
    }
  }

  private def namesError(position: String, cardinality: Cardinality, request: JoinRequest, taken: Set[String]): Either[QueryError, Unit] = {
    val added: Either[QueryError, List[String]] = cardinality match {
      case Cardinality.AtMostOne => Right(request.fields.map(_._1))
      case _ => request.as.toRight(QueryError(s"$position: ${cardinality.name} needs 'as', the name of its result.")).map(List(_))
    }
    added.flatMap { names =>
      names.find(n => !fieldNamePattern.matches(n))
        .map(n => QueryError(s"$position: '$n' must be a field name made of letters, digits and underscores, not starting with a digit."))
        .orElse(names.find(taken.contains).map(n => QueryError(s"$position: '$n' is already a field of the result. Choose another name.")))
        .orElse(if (names.distinct.size != names.size) Some(QueryError(s"$position: a result name is used twice.")) else None)
        .toLeft(())
    }
  }

  private def check(condition: Boolean, message: => String): Either[QueryError, Unit] =
    if (condition) Right(()) else Left(QueryError(message))

  private def firstError(results: List[Option[QueryError]]): Either[QueryError, Unit] =
    results.flatten.headOption.toLeft(())
}
