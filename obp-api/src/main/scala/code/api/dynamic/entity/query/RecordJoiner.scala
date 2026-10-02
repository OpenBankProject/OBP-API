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

import cats.effect.unsafe.implicits.{global => ioRuntime}
import code.DynamicData.{DynamicDataAccessProvider, DynamicDataProvider}
import code.api.dynamic.entity.helper.{DynamicEntityHelper, DynamicEntityInfo}
import code.api.dynamic.entity.projection.{IndexingCapabilities, ProjectionDb, ProjectionNaming, ProjectionProvisioner, ProjectionStore}
import com.openbankproject.commons.util.JsonAliases
import org.json4s.JsonAST._

/**
 * This object applies planned [[Join]]s to a page of records.
 *
 * It runs after the page has been filtered, sorted and paginated, and never adds, removes or reorders
 * a record of the page, so it cannot change which records a query returns.
 *
 * The other records are read in one batch per (entity, `on` field, direction) for the whole page:
 *  - forward: by id (the provider's `getByIds`), on any storage backend, so the `on` field needs no
 *    index;
 *  - reverse: through the entity's query projection when it is enabled and the `on` column is ready,
 *    so the lookup uses its index; otherwise by reading the entity's shared records and selecting in
 *    memory, which is correct everywhere, only slower.
 * `where` and the ordering are evaluated in memory on the records read, so they may use fields that
 * are not indexed. Then both directions are merged the same way: the matching records, filtered by
 * `where`, give one record's fields, a list, or a yes/no.
 *
 * What a caller can see through a join is no more than what it could read directly:
 *  - only shared records of the other entity are used, never a User's personal record, whoever owns it;
 *  - for an entity with row-level access, only the records the caller's access list lets them read;
 *  - a field declared `read_role_required` is copied only for a caller holding its read Role, and a field
 *    declared `hide_field_from_public_access` only for a caller who reaches the entity other than
 *    through its public access.
 * Whether the caller may read the other entity at all is checked by [[JoinPlanner]]. A copied value is
 * JSON null when there is no matching record, when it may not be read, when it lacks the field, or when
 * the field may not be read; these are deliberately indistinguishable, so a join never reveals that a
 * record the caller cannot read exists.
 */
object RecordJoiner {

  /** The key a batch of other records is read under. */
  case class Link(entity: String, on: String, direction: JoinDirection)

  /** Other records by link, then by the parent id they belong to. */
  type LinkedRecords = Map[Link, Map[String, List[JObject]]]

  def join(records: List[JObject], joins: List[Join], parentIdField: String,
           bankId: Option[String], callerUserId: Option[String], consumerId: String): List[JObject] =
    if (joins.isEmpty || records.isEmpty) records
    else {
      val links = joins.map(linkOf).distinct
      val linked: LinkedRecords = links.map { link =>
        link -> (link.direction match {
          case JoinDirection.Forward => fetchForward(bankId, link, records, callerUserId)
          case JoinDirection.Reverse => fetchReverse(bankId, link, records.flatMap(idOf(_, parentIdField)).distinct, callerUserId)
        })
      }.toMap
      merge(records, joins, parentIdField, linked, DynamicEntityInfo.fieldReader(bankId, callerUserId, consumerId))
    }

  def linkOf(join: Join): Link = Link(join.entity, join.on, join.direction)

  /**
   * This adds each join's result to each record, from other records already read. It reads nothing, so
   * it is the part to test without a database. `fieldReadable(entity, field)` says whether the caller
   * may see that field.
   *
   * The other records belong to a parent record by the parent's id: for a forward join that is the id
   * its `on` field names, held in the parent; for a reverse join, the parent's own id.
   */
  def merge(records: List[JObject], joins: List[Join], parentIdField: String, linked: LinkedRecords,
            fieldReadable: (String, String) => Boolean): List[JObject] = {
    val readable: Map[(String, String), Boolean] =
      joins.flatMap(j => j.fields.map(f => (j.entity, f._2))).distinct.map(key => key -> fieldReadable(key._1, key._2)).toMap
    def copy(join: Join, from: JObject, field: String): JValue =
      if (!readable((join.entity, field))) JNull
      else from \ field match { case JNothing => JNull; case value => value }

    records.map { record =>
      val added = joins.flatMap { join =>
        val key = join.direction match {
          case JoinDirection.Forward => idOf(record, join.on)
          case JoinDirection.Reverse => idOf(record, parentIdField)
        }
        val matching = key.flatMap(k => linked.get(linkOf(join)).flatMap(_.get(k))).getOrElse(Nil)
          .filter(other => InMemoryQueryExecutor.matchesAll(other, join.where, join.entityFieldTypes))
        join.cardinality match {
          case Cardinality.Exists =>
            List(JField(join.as.getOrElse(""), if (matching.nonEmpty) join.trueValue else join.falseValue))
          case Cardinality.AtMostOne =>
            val chosen = ordered(matching, join).headOption
            join.fields.map { case (name, field) => JField(name, chosen.map(copy(join, _, field)).getOrElse(JNull)) }
          case Cardinality.Many =>
            val elements = ordered(matching, join).map(other =>
              JObject(join.fields.map { case (name, field) => JField(name, copy(join, other, field)) }))
            List(JField(join.as.getOrElse(""), JArray(elements)))
        }
      }
      JObject(record.obj ++ added)
    }
  }

  /**
   * Matching records in the join's order, or by record id when it has none. A record whose order field
   * is missing, or not of its declared type, comes after every record that has one, whichever
   * direction; ties are broken by record id, so the result never depends on the order the records were
   * read in.
   */
  private def ordered(records: List[JObject], join: Join): List[JObject] = {
    def id(record: JObject): String = idOf(record, join.entityIdField).getOrElse("")
    join.order match {
      case None => records.sortBy(id)
      case Some(order) =>
        val fieldType = join.entityFieldTypes(order.field)
        def present(record: JObject): Boolean = InMemoryQueryExecutor.compareValues(fieldType, record \ order.field, JNothing) < 0
        def compare(a: JObject, b: JObject): Int = (present(a), present(b)) match {
          case (true, true) =>
            val byValue = InMemoryQueryExecutor.compareValues(fieldType, a \ order.field, b \ order.field)
            val directed = if (order.descending) -byValue else byValue
            if (directed != 0) directed else id(a).compareTo(id(b))
          case (true, false)  => -1
          case (false, true)  => 1
          case (false, false) => id(a).compareTo(id(b))
        }
        records.sortWith((a, b) => compare(a, b) < 0)
    }
  }

  /** The records the page's `on` fields name, readable by the caller, by their own id. */
  def fetchForward(bankId: Option[String], link: Link, records: List[JObject], callerUserId: Option[String]): Map[String, List[JObject]] = {
    val ids = records.flatMap(idOf(_, link.on)).distinct
    if (ids.isEmpty) Map.empty
    else {
      val readable = rowAccess(bankId, link.entity, callerUserId)
      DynamicDataProvider.connectorMethodProvider.vend.getByIds(bankId, link.entity, ids)
        .filterNot(_.isPersonalEntity)
        .flatMap(row => row.dynamicDataId.filter(readable).map(id => id -> List(JsonAliases.parse(row.dataJson).asInstanceOf[JObject])))
        .toMap
    }
  }

  /** The records whose `on` field names one of `parentIds`, readable by the caller, by that parent id. */
  def fetchReverse(bankId: Option[String], link: Link, parentIds: List[String], callerUserId: Option[String]): Map[String, List[JObject]] =
    if (parentIds.isEmpty) Map.empty
    else {
      val readable = rowAccess(bankId, link.entity, callerUserId)
      val rows: List[(String, JObject)] =
        if (IndexingCapabilities.projectionEnabled && ProjectionProvisioner.readyFields(bankId, link.entity).contains(link.on))
          parentIds.grouped(1000).toList.flatMap { someIds =>
            ProjectionDb.run(ProjectionStore.readByReference(ProjectionNaming.tableName(bankId, link.entity), ProjectionNaming.columnName(link.on),
              bankId, link.entity, someIds)).unsafeRunSync()(ioRuntime)
          }.map { case (id, json) => id -> JsonAliases.parse(json).asInstanceOf[JObject] }
        else {
          val wanted = parentIds.toSet
          DynamicDataProvider.connectorMethodProvider.vend.getAll(bankId, link.entity, None, isPersonalEntity = false)
            .flatMap(row => row.dynamicDataId.map(_ -> JsonAliases.parse(row.dataJson).asInstanceOf[JObject]))
            .filter { case (_, other) => idOf(other, link.on).exists(wanted.contains) }
        }
      rows.collect { case (id, other) if readable(id) => other }.groupBy(other => idOf(other, link.on).getOrElse(""))
    }

  /**
   * This says, record id by record id, whether the caller may read a record of `entityName` that is
   * otherwise in scope. For an entity without row-level access every record is allowed; for one with
   * it, only the records the caller's access list lets them read, and none for an anonymous caller.
   * The access list is read once, when this is called.
   */
  private def rowAccess(bankId: Option[String], entityName: String, callerUserId: Option[String]): String => Boolean =
    if (!DynamicEntityHelper.definitionOf(bankId, entityName).exists(_.useRowLevelAccess)) _ => true
    else {
      val allowed = callerUserId.map(DynamicDataAccessProvider.provider.vend.getReadableDynamicDataIds(bankId, entityName, _).toSet).getOrElse(Set.empty)
      allowed.contains
    }

  private def idOf(record: JObject, field: String): Option[String] =
    record \ field match {
      case JString(id) if id.trim.nonEmpty => Some(id)
      case _ => None
    }
}
