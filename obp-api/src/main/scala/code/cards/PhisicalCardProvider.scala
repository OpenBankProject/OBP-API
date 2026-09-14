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

package code.cards

import java.util.Date

import code.api.util.{CallContext, OBPQueryParam}
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box

import scala.collection.immutable.List

object PhysicalCard extends SimpleInjector {

  val physicalCardProvider = new Inject(() => buildOne) {}

  def buildOne: PhysicalCardProvider = MappedPhysicalCardProvider

}

trait PhysicalCardProvider {

  def createPhysicalCard(
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
    issueNumber: String,
    serialNumber: String,
    validFrom: Date,
    expires: Date,
    enabled: Boolean,
    cancelled: Boolean,
    onHotList: Boolean,
    technology: String,
    networks: List[String],
    allows: List[String],
    accountId: String,
    bankId: String,
    replacement: Option[CardReplacementInfo],
    pinResets: List[PinResetInfo],
    collected: Option[CardCollectionInfo],
    posted: Option[CardPostedInfo],
    customerId: String,
    cvv: String,
    brand: String,
    callContext: Option[CallContext]
  ): Box[MappedPhysicalCard]

  def updatePhysicalCard(
    cardId: String,
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
    issueNumber: String,
    serialNumber: String,
    validFrom: Date,
    expires: Date,
    enabled: Boolean,
    cancelled: Boolean,
    onHotList: Boolean,
    technology: String,
    networks: List[String],
    allows: List[String],
    accountId: String,
    bankId: String,
    replacement: Option[CardReplacementInfo],
    pinResets: List[PinResetInfo],
    collected: Option[CardCollectionInfo],
    posted: Option[CardPostedInfo],
    customerId: String,
    callContext: Option[CallContext]
  ): Box[PhysicalCardTrait]
  
  def getPhysicalCards(user: User): List[MappedPhysicalCard]

  def getPhysicalCardsForBank(bank: Bank, user: User, queryParams: List[OBPQueryParam]): List[PhysicalCardTrait]

  def getPhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext]) : Box[PhysicalCardTrait]
  
  def deletePhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext]) : Box[Boolean]
  
  def getPhysicalCardByCardNumber(bankCardNumber: String,  callContext:Option[CallContext]) : Box[PhysicalCardTrait]


}








