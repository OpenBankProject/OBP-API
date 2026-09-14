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

package code.customerlinks

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object CustomerLinkX extends SimpleInjector {

  val customerLink = new Inject(() => buildOne) {}

  def buildOne: CustomerLinkProvider = MappedCustomerLinkProvider

}

trait CustomerLinkProvider {
  def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String): Box[CustomerLinkTrait]
  def getCustomerLinkById(customerLinkId: String): Box[CustomerLinkTrait]
  def getCustomerLinksByBankId(bankId: String): Box[List[CustomerLinkTrait]]
  def getCustomerLinksByCustomerId(customerId: String): Box[List[CustomerLinkTrait]]
  def updateCustomerLinkById(customerLinkId: String, relationshipTo: String): Box[CustomerLinkTrait]
  def deleteCustomerLinkById(customerLinkId: String): Future[Box[Boolean]]
  def bulkDeleteCustomerLinks(): Boolean
}

trait CustomerLinkTrait {
  def customerLinkId: String
  def bankId: String
  def customerId: String
  def otherBankId: String
  def otherCustomerId: String
  def relationshipTo: String
  def dateInserted: Date
  def dateUpdated: Date
}
