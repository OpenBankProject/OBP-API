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

package code.atms

/* For atms */

// Need to import these one by one because in same package!

import code.api.util.OBPQueryParam
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List

object Atms extends SimpleInjector {

  case class Atm (
    atmId : AtmId,
    bankId : BankId,
    name : String,
    address : Address,
    location : Location,
    meta : Meta,

    OpeningTimeOnMonday : Option[String],
    ClosingTimeOnMonday : Option[String],

    OpeningTimeOnTuesday : Option[String],
    ClosingTimeOnTuesday : Option[String],

    OpeningTimeOnWednesday : Option[String],
    ClosingTimeOnWednesday : Option[String],

    OpeningTimeOnThursday : Option[String],
    ClosingTimeOnThursday: Option[String],

    OpeningTimeOnFriday : Option[String],
    ClosingTimeOnFriday : Option[String],

    OpeningTimeOnSaturday : Option[String],
    ClosingTimeOnSaturday : Option[String],

    OpeningTimeOnSunday: Option[String],
    ClosingTimeOnSunday : Option[String],

    isAccessible : Option[Boolean],

    locatedAt : Option[String],
    moreInfo : Option[String],
    hasDepositCapability : Option[Boolean],
    supportedLanguages : Option[List[String]] = None,
    services: Option[List[String]] = None,
    accessibilityFeatures: Option[List[String]] = None,
    supportedCurrencies: Option[List[String]] = None,
    notes: Option[List[String]] = None,
    locationCategories: Option[List[String]] = None,
    minimumWithdrawal: Option[String] = None,
    branchIdentification: Option[String] = None,
    siteIdentification: Option[String] = None,
    siteName: Option[String] = None,
    cashWithdrawalNationalFee: Option[String] = None,
    cashWithdrawalInternationalFee: Option[String] = None,
    balanceInquiryFee: Option[String] = None,
    atmType: Option[String] = None,
    phone: Option[String] = None,

  ) extends AtmT

  val atmsProvider = new Inject(() => buildOne) {}

  def buildOne: AtmsProvider = MappedAtmsProvider

  // Helper to get the count out of an option
  def countOfAtms (listOpt: Option[List[AtmT]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait AtmsProvider extends MdcLoggable {


  /*
  Common logic for returning atms.
   */
  final def getAtms(bankId : BankId, queryParams: List[OBPQueryParam]) : Option[List[AtmT]] = {
    // If we get atms filter them
    getAtmsFromProvider(bankId,queryParams)
  }

  /*
  Return one Atm
   */
  final def getAtm(bankId: BankId, branchId : AtmId) : Option[AtmT] = {
    getAtmFromProvider(bankId,branchId)
  }

  protected def getAtmFromProvider(bankId: BankId, branchId : AtmId) : Option[AtmT]
  protected def getAtmsFromProvider(bank : BankId, queryParams: List[OBPQueryParam]) : Option[List[AtmT]]
  def createOrUpdateAtm(atm: AtmT): Box[AtmT]
  def deleteAtm(atm: AtmT): Box[Boolean]
// End of Trait
}
