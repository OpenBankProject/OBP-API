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

package code.DynamicData

import org.json4s._
import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import java.util.Date
import org.json4s.JObject
import net.liftweb.util.SimpleInjector

object DynamicDataProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(() => buildOne) {}

  def buildOne: MappedDynamicDataProvider.type = MappedDynamicDataProvider
}

trait DynamicDataT {
  def dynamicDataId: Option[String]
  def dynamicEntityName: String
  def dataJson: String
  def bankId: Option[String]
  def userId: Option[String]
  def isPersonalEntity: Boolean
  /**
   * When the record was first saved. None for a record saved before the timestamp columns existed,
   * and for an implementation that does not store timestamps.
   */
  def createdDate: Option[Date] = None
  /**
   * When the record was last saved. None for a record not saved since the timestamp columns were
   * added, and for an implementation that does not store timestamps.
   */
  def updatedDate: Option[Date] = None
  /**
   * The user who made the call that first saved the record. That is an agent's own user id when an
   * agent made the call for somebody else. None for a record saved before the column existed, and for
   * an implementation that does not store it.
   */
  def createdByUserId: Option[String] = None
  /**
   * The user the call that first saved the record was made for. It equals createdByUserId when nobody
   * was delegating. None in the same cases as createdByUserId.
   */
  def createdByOnBehalfOfUserId: Option[String] = None
  /** As createdByUserId, for the call that last saved the record. */
  def updatedByUserId: Option[String] = None
  /** As createdByOnBehalfOfUserId, for the call that last saved the record. */
  def updatedByOnBehalfOfUserId: Option[String] = None
}

case class DynamicDataCommons(dynamicEntityName: String,
                                dataJson: String,
                                dynamicDataId: Option[String] = None,
                                bankId: Option[String],
                                userId: Option[String],
                                isPersonalEntity: Boolean
                               ) extends DynamicDataT with JsonFieldReName

object DynamicDataCommons extends Converter[DynamicDataT, DynamicDataCommons]


trait DynamicDataProvider {
  def save(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT]
  def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT]
  def get(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT]
  def getAllDataJson(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[JObject]
  def getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[DynamicDataT]
  def delete(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[Boolean]
  def existsData(bankId: Option[String], dynamicEntityName: String, userId: Option[String], isPersonalEntity: Boolean): Boolean

  // Community access methods - return ALL records regardless of userId/IsPersonalEntity
  def getAllCommunity(bankId: Option[String], entityName: String): List[DynamicDataT]
  def getAllDataJsonCommunity(bankId: Option[String], entityName: String): List[JObject]
  def getCommunity(bankId: Option[String], entityName: String, id: String): Box[DynamicDataT]

  /**
   * The records of one entity in one space whose ids are in `ids`, whoever owns them. An id with no
   * record is left out of the answer rather than reported. This exists so that a response can carry
   * each record's metadata after the records themselves were read by some other route, such as the
   * connector or the projection tables; it is not an access check.
   */
  def getByIds(bankId: Option[String], entityName: String, ids: List[String]): List[DynamicDataT]

  // Community mutation methods - operate on a row regardless of owner (used by row-level access,
  // where the ACL, not ownership, decides who may update/delete). Preserve the row's existing
  // userId / isPersonalEntity so its provenance is unchanged. callerUserId is the user making the
  // call, recorded as the record's last writer.
  def updateCommunity(bankId: Option[String], entityName: String, requestBody: JObject, id: String, callerUserId: Option[String]): Box[DynamicDataT]
  def deleteCommunity(bankId: Option[String], entityName: String, id: String): Box[Boolean]
}






