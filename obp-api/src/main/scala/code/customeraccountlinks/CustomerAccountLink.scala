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

package code.customeraccountlinks
import com.openbankproject.commons.model.CustomerAccountLinkTrait
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object CustomerAccountLinkX extends SimpleInjector {

  val customerAccountLink = new Inject(() => buildOne) {}

  def buildOne: CustomerAccountLinkProvider = MappedCustomerAccountLinkProvider

}

trait CustomerAccountLinkProvider {
  def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getOrCreateCustomerAccountLink(customerId: String, bankId: String,  accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLinksByCustomerId(customerId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLinksByAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLinkById(customerAccountLinkId: String): Box[CustomerAccountLinkTrait]
  def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]]
  def bulkDeleteCustomerAccountLinks(): Boolean
  def deleteCustomerAccountLinkById(customerAccountLinkId: String): Future[Box[Boolean]]
}