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

package code.bankattribute

/* For ProductAttribute */

import code.api.util.APIUtil
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.BankAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.concurrent.Future

object BankAttributeX extends SimpleInjector {

  val bankAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: BankAttributeProviderTrait = BankAttributeProvider

  // Helper to get the count out of an option
  def countOfBankAttribute(listOpt: Option[List[BankAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait BankAttributeProviderTrait extends MdcLoggable {

  def getBankAttributesFromProvider(bankId: BankId): Future[Box[List[BankAttribute]]]

  def getBankAttributeById(bankAttributeId: String): Future[Box[BankAttribute]]

  def createOrUpdateBankAttribute(bankId : BankId,
                                  bankAttributeId: Option[String],
                                  name: String,
                                  attributType: BankAttributeType.Value,
                                  value: String,
                                  isActive: Option[Boolean]): Future[Box[BankAttribute]]
  def deleteBankAttribute(bankAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}
