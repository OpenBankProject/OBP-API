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
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

object MappedTenantProvider extends TenantProvider {

  override def createTenant(
    tenantId: String,
    name: String,
    website: Option[String],
    logoUrl: Option[String],
    status: String,
    visibility: String,
    createdByUserId: String
  ): Box[TenantTrait] = {
    tryo {
      Tenant.create
        .TenantId(tenantId)
        .Name(name)
        .Website(website.getOrElse(""))
        .LogoUrl(logoUrl.getOrElse(""))
        .Status(status)
        .Visibility(visibility)
        .CreatedByUserId(createdByUserId)
        .saveMe()
    }
  }

  override def getTenant(tenantId: String): Box[TenantTrait] = {
    Tenant.find(By(Tenant.TenantId, tenantId))
  }

  override def getTenants(): Future[Box[List[TenantTrait]]] = {
    Future {
      tryo { Tenant.findAll() }
    }
  }

  override def updateTenant(
    tenantId: String,
    name: Option[String],
    website: Option[String],
    logoUrl: Option[String],
    status: Option[String],
    visibility: Option[String]
  ): Box[TenantTrait] = {
    Tenant.find(By(Tenant.TenantId, tenantId)).flatMap { tenant =>
      tryo {
        name.foreach(v => tenant.Name(v))
        website.foreach(v => tenant.Website(v))
        logoUrl.foreach(v => tenant.LogoUrl(v))
        status.foreach(v => tenant.Status(v))
        visibility.foreach(v => tenant.Visibility(v))
        tenant.LastUpdate(new java.util.Date())
        tenant.saveMe()
      }
    }
  }

  override def deleteTenant(tenantId: String): Box[Boolean] = {
    Tenant.find(By(Tenant.TenantId, tenantId)).flatMap { tenant =>
      tryo {
        tenant.delete_!
      }
    }
  }
}

class Tenant extends TenantTrait with LongKeyedMapper[Tenant] with IdPK {

  def getSingleton = Tenant

  object TenantId extends MappedString(this, 64)
  object Name extends MappedString(this, 255)
  object Website extends MappedString(this, 1024)
  object LogoUrl extends MappedString(this, 1024)
  object Status extends MappedString(this, 32)
  object Visibility extends MappedString(this, 32)
  object CreatedByUserId extends MappedString(this, 255)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def tenantId: String = TenantId.get
  override def name: String = Name.get
  override def website: Option[String] = {
    val v = Website.get
    if (v == null || v.isEmpty) None else Some(v)
  }
  override def logoUrl: Option[String] = {
    val v = LogoUrl.get
    if (v == null || v.isEmpty) None else Some(v)
  }
  override def status: String = Status.get
  override def visibility: String = Visibility.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def updatedAt: java.util.Date = LastUpdate.get
}

object Tenant extends Tenant with LongKeyedMetaMapper[Tenant] {
  override def dbTableName = "Tenant"
  override def dbIndexes = UniqueIndex(TenantId) :: super.dbIndexes
}
