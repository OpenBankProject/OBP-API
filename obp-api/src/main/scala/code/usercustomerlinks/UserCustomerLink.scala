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

package code.usercustomerlinks

import java.util.Date

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future


object UserCustomerLink extends SimpleInjector {

  val userCustomerLink = new Inject(() => buildOne) {}

  def buildOne: UserCustomerLinkProvider = MappedUserCustomerLinkProvider 

}

trait UserCustomerLinkProvider {
  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinksByCustomerId(customerId: String): List[UserCustomerLink]
  def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink]
  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinks: Box[List[UserCustomerLink]]
  def bulkDeleteUserCustomerLinks(): Boolean
  def deleteUserCustomerLink(userCustomerLinkId: String): Future[Box[Boolean]]
}

trait UserCustomerLink {
  def userCustomerLinkId: String
  def userId: String
  def customerId: String
  def dateInserted: Date
  def isActive: Boolean
}