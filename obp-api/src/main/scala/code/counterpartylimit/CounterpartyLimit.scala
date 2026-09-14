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

package code.counterpartylimit

import com.openbankproject.commons.model.CounterpartyLimitTrait
import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import scala.concurrent.Future

object CounterpartyLimitProvider extends SimpleInjector {
  val counterpartyLimit = new Inject(() => buildOne) {}
  def buildOne: CounterpartyLimitProviderTrait =  MappedCounterpartyLimitProvider
}

trait CounterpartyLimitProviderTrait {
  def getCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[CounterpartyLimitTrait]]
  
  def deleteCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[Boolean]]
  
  def createOrUpdateCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    currency: String,
    maxSingleAmount: BigDecimal,
    maxMonthlyAmount: BigDecimal,
    maxNumberOfMonthlyTransactions: Int,
    maxYearlyAmount: BigDecimal,
    maxNumberOfYearlyTransactions: Int,
    maxTotalAmount: BigDecimal,
    maxNumberOfTransactions: Int
  ): Future[Box[CounterpartyLimitTrait]]
}

