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

package code.tenant

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Tenants extends SimpleInjector {
  val tenant = new Inject(() => buildOne) {}

  def buildOne: TenantProvider = MappedTenantProvider
}

/**
 * A Tenant is a customer of the operator of this OBP instance: an organisation that uses it. Tenants belong to
 * no bank; later, banks will name the Tenant they belong to. The tenant_id is
 * unique and immutable.
 */
trait TenantProvider {
  def createTenant(
    tenantId: String,
    name: String,
    website: Option[String],
    logoUrl: Option[String],
    status: String,
    visibility: String,
    createdByUserId: String
  ): Box[TenantTrait]

  def getTenant(tenantId: String): Box[TenantTrait]

  def getTenants(): Future[Box[List[TenantTrait]]]

  def updateTenant(
    tenantId: String,
    name: Option[String],
    website: Option[String],
    logoUrl: Option[String],
    status: Option[String],
    visibility: Option[String]
  ): Box[TenantTrait]

  def deleteTenant(tenantId: String): Box[Boolean]
}

trait TenantTrait {
  def tenantId: String
  def name: String
  def website: Option[String]
  def logoUrl: Option[String]
  def status: String
  def visibility: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}
