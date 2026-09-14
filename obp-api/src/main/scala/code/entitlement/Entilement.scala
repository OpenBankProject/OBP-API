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

package code.entitlement

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future

object Entitlement extends SimpleInjector {

  val entitlement = new Inject(() => buildOne) {}

  def buildOne: EntitlementProvider = MappedEntitlementsProvider

}

trait EntitlementProvider {
  def getEntitlement(
      bankId: String,
      userId: String,
      roleName: String
  ): Box[Entitlement]
  def getEntitlementById(entitlementId: String): Box[Entitlement]
  def getEntitlementsByUserId(userId: String): Box[List[Entitlement]]
  def getEntitlementsByUserIdFuture(
      userId: String
  ): Future[Box[List[Entitlement]]]
  def getEntitlementsByBankId(bankId: String): Future[Box[List[Entitlement]]]
  def deleteEntitlement(entitlement: Box[Entitlement]): Box[Boolean]
  def getEntitlements(): Box[List[Entitlement]]
  def getEntitlementsByRole(roleName: String): Box[List[Entitlement]]
  def getEntitlementsFuture(): Future[Box[List[Entitlement]]]
  def getEntitlementsByRoleFuture(
      roleName: String
  ): Future[Box[List[Entitlement]]]
  def getEntitlementsByGroupId(groupId: String): Future[Box[List[Entitlement]]]
  def addEntitlement(
      bankId: String,
      userId: String,
      roleName: String,
      createdByProcess: String = "manual",
      // Audit only — who granted (the logged-in granter, or the user
      // themselves on self-grant flows). None for system processes, where
      // createdByProcess carries the provenance. Authorization is the
      // calling endpoint's responsibility, not this method's.
      grantedByUserId: Option[String] = None,
      groupId: Option[String] = None
  ): Box[Entitlement]
  def deleteDynamicEntityEntitlement(
      entityName: String,
      bankId: Option[String]
  ): Box[Boolean]
  def deleteEntitlements(entityNames: List[String]): Box[Boolean]
}

trait Entitlement {
  def entitlementId: String
  def bankId: String
  def userId: String
  def roleName: String
  def createdByProcess: String
  def entitlementRequestId: Option[String]
  def groupId: Option[String]

  /** user_id of the granter, when the grant was made by a person (directly
    * or as a self-grant). None for system-process grants and virtual
    * entitlements. */
  def grantedByUserId: Option[String]
}
