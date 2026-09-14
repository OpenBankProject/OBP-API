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

package code.regulatedentities.attribute

/* For ProductAttribute */

import com.openbankproject.commons.model.{RegulatedEntityId, BankId}
import com.openbankproject.commons.model.enums.RegulatedEntityAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object RegulatedEntityAttributeX extends SimpleInjector {

  val regulatedEntityAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: RegulatedEntityAttributeProviderTrait = RegulatedEntityAttributeProvider

  // Helper to get the count out of an option
  def countOfRegulatedEntityAttribute(listOpt: Option[List[RegulatedEntityAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait RegulatedEntityAttributeProviderTrait {

  def getRegulatedEntityAttributes(regulatedEntityId: RegulatedEntityId): Future[Box[List[RegulatedEntityAttribute]]]

  def getRegulatedEntityAttributeById(regulatedEntityAttributeId: String): Future[Box[RegulatedEntityAttribute]]

  def createOrUpdateRegulatedEntityAttribute(
    regulatedEntityId: RegulatedEntityId,
    regulatedEntityAttributeId: Option[String],
    name: String,
    attributeType: RegulatedEntityAttributeType.Value,
    value: String,
    isActive: Option[Boolean]): Future[Box[RegulatedEntityAttribute]]
  
  def deleteRegulatedEntityAttribute(regulatedEntityAttributeId: String): Future[Box[Boolean]]
  
  def deleteRegulatedEntityAttributesByRegulatedEntityId(regulatedEntityId: RegulatedEntityId): Future[Box[Boolean]]
}
