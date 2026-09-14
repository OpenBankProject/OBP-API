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

package code.cardattribute

/* For CardAttribute */

import code.api.util.APIUtil
import com.openbankproject.commons.model.enums.CardAttributeType
import com.openbankproject.commons.model.{AccountId, BankId, CardAttribute, ProductCode}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.concurrent.Future

object CardAttributeX extends SimpleInjector {

  val cardAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: CardAttributeProvider = MappedCardAttributeProvider
  // Helper to get the count out of an option
  def countOfCardAttribute(listOpt: Option[List[CardAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait CardAttributeProvider extends MdcLoggable {

  def getCardAttributesFromProvider(cardId: String): Future[Box[List[CardAttribute]]]

  def getCardAttributeById(cardAttributeId: String): Future[Box[CardAttribute]]

  def createOrUpdateCardAttribute(
    bankId: Option[BankId],
    cardId: Option[String],
    cardAttributeId: Option[String],
    name: String,
    attributeType: CardAttributeType.Value,
    value: String
  ): Future[Box[CardAttribute]]

  def deleteCardAttribute(cardAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}
