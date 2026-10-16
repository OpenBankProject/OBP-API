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

package code.dynamicentitywebhook

import code.dynamicEntity.DynamicEntityT
import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import org.json4s._

import scala.util.Try

/**
 * This object checks a webhook's events and field conditions against the Dynamic Entity they are for.
 *
 * A field condition names a field of the entity and, optionally, the value it must change from and
 * the value it must change to. Those values are only useful when they could actually be stored in the
 * field, so they are checked against the field's declared type, the same way a record's values are
 * checked when it is written.
 */
object DynamicEntityWebhookConditions {
  import DynamicEntityWebhooks._

  /** The entity's fields and their declared type names, e.g. "approved" -> "boolean". */
  def fieldTypes(entity: DynamicEntityT): Map[String, String] =
    Try(com.openbankproject.commons.util.JsonAliases.parse(entity.metadataJson) \ entity.entityName \ "properties").toOption match {
      case Some(JObject(fields)) =>
        fields.collect { case JField(name, definition: JObject) =>
          name -> ((definition \ "type") match { case JString(t) => t; case _ => "" })
        }.toMap
      case _ => Map.empty
    }

  /** Whether `value` could be stored in a field of this declared type. */
  private def fitsType(typeName: String, value: JValue): Boolean =
    value match {
      case JNothing | JNull => false
      case _ => DynamicEntityFieldType.withNameOption(typeName) match {
        case Some(fieldType) => fieldType.isJValueValid(value)
        // A reference to another entity, or a type this check does not know: its values are strings.
        case None => value.isInstanceOf[JString]
      }
    }

  /** Why `events` is not acceptable, or None when it is. */
  def eventsProblem(events: List[String]): Option[String] =
    if (events.isEmpty) Some("events is empty.")
    else if (events.exists(e => !allEvents.contains(e))) Some(s"Unknown events: ${events.filterNot(allEvents.contains).mkString(", ")}.")
    else if (events.distinct.size != events.size) Some("events names an event more than once.")
    else None

  /** Why these field conditions are not acceptable for `entity` and `events`, or None when they are. */
  def conditionsProblem(conditions: List[DynamicEntityWebhookFieldCondition], events: List[String], entity: DynamicEntityT): Option[String] = {
    val types = fieldTypes(entity)
    val fields = conditions.map(_.field)
    if (conditions.isEmpty) None
    else if (!events.contains(EventUpdated)) Some("field_conditions is given but events does not include UPDATED.")
    else if (fields.exists(f => f == null || f.trim.isEmpty)) Some("A condition has no field.")
    else if (fields.distinct.size != fields.size) Some("A field is named by more than one condition.")
    else conditions.collectFirst {
      case c if !types.contains(c.field) =>
        s"${entity.entityName} has no field ${c.field}."
      case c if c.from.exists(v => !fitsType(types(c.field), v)) =>
        s"The from value of ${c.field} does not fit its type ${types(c.field)}."
      case c if c.to.exists(v => !fitsType(types(c.field), v)) =>
        s"The to value of ${c.field} does not fit its type ${types(c.field)}."
      case c if c.from.isDefined && c.from == c.to =>
        s"The from and to values of ${c.field} are the same, so no change could match."
    }
  }

  /**
   * Why a webhook's existing conditions no longer fit its entity, as a deactivation reason, or None
   * when they still do. Used when an entity definition changes.
   */
  def deactivationReasonFor(conditions: List[DynamicEntityWebhookFieldCondition], entity: DynamicEntityT,
                            typesBefore: Map[String, String]): Option[String] = {
    val typesAfter = fieldTypes(entity)
    val named = conditions.map(_.field)
    if (named.exists(f => !typesAfter.contains(f))) Some(DeactivatedEntityFieldRemoved)
    else if (named.exists(f => typesBefore.get(f).exists(_ != typesAfter(f)))) Some(DeactivatedEntityFieldTypeChanged)
    else None
  }
}
