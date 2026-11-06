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

package code.entitlementrequest

import java.util.Date

import code.api.util.OBPQueryParam
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object EntitlementRequest extends SimpleInjector {

  val entitlementRequest = new Inject(() => buildOne) {}

  def buildOne: EntitlementRequestProvider = MappedEntitlementRequestsProvider
}

trait EntitlementRequestProvider {
  def addEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest]
  def addEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]]
  def getEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest]
  def getEntitlementRequestFuture(entitlementRequestId: String): Future[Box[EntitlementRequest]]
  def getEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]]
  def getEntitlementRequestsFuture(): Future[Box[List[EntitlementRequest]]]
  def getEntitlementRequestsFuture(userId: String): Future[Box[List[EntitlementRequest]]]
  def getEntitlementRequestsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]]
  def getEntitlementRequestsFuture(userId: String, queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]]
  def deleteEntitlementRequestFuture(entitlementRequestId: String): Future[Box[Boolean]]
}

trait EntitlementRequest {
  def entitlementRequestId: String

  def bankId: String

  def user: Box[User]

  def roleName: String

  def created: Date
}
