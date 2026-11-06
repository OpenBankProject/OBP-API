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

package code.standingorders

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import com.openbankproject.commons.model.StandingOrderTrait
import scala.math.BigDecimal


object StandingOrders extends SimpleInjector {
  val provider = new Inject(() => buildOne) {}
  def buildOne: StandingOrderProvider = MappedStandingOrderProvider
}

trait StandingOrderProvider {
  def createStandingOrder(bankId: String,
                          accountId: String,
                          customerId: String,
                          userId: String,
                          counterpartyId: String,
                          amountValue: BigDecimal,
                          amountCurrency: String,
                          whenFrequency: String,
                          whenDetail: String,
                          dateSigned: Date,
                          dateStarts: Date,
                          dateExpires: Option[Date]
                       ): Box[StandingOrderTrait]
  def getStandingOrdersByCustomer(customerId: String) : List[StandingOrderTrait]
  def getStandingOrdersByUser(userId: String) : List[StandingOrderTrait]
}

