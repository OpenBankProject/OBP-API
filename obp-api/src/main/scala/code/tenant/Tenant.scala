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

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global

import java.util.Date
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
  ): Box[TenantTrait] =
    tryo(Tenant.insert(tenantId, name, website.getOrElse(""), logoUrl.getOrElse(""), status, visibility, createdByUserId))

  override def getTenant(tenantId: String): Box[TenantTrait] = Tenant.findByTenantId(tenantId)

  override def getTenants(): Future[Box[List[TenantTrait]]] =
    Future(tryo(Tenant.findAll()))

  override def updateTenant(
    tenantId: String,
    name: Option[String],
    website: Option[String],
    logoUrl: Option[String],
    status: Option[String],
    visibility: Option[String]
  ): Box[TenantTrait] =
    Tenant.findByTenantId(tenantId).flatMap { tenant =>
      tryo {
        Tenant.update(tenant.copy(
          name = name.getOrElse(tenant.name),
          websiteRaw = website.getOrElse(tenant.websiteRaw),
          logoUrlRaw = logoUrl.getOrElse(tenant.logoUrlRaw),
          status = status.getOrElse(tenant.status),
          visibility = visibility.getOrElse(tenant.visibility),
          updatedAt = new Date()))
      }
    }

  override def deleteTenant(tenantId: String): Box[Boolean] =
    Tenant.findByTenantId(tenantId).flatMap(_ => tryo(Tenant.delete(tenantId)))
}

/** A Tenant (formerly an Organisation). Empty website and logo url read back as None, as Mapper's did. */
case class Tenant(
  tenantId: String,
  name: String,
  private[tenant] val websiteRaw: String,
  private[tenant] val logoUrlRaw: String,
  status: String,
  visibility: String,
  createdByUserId: String,
  createdAt: Date,
  updatedAt: Date
) extends TenantTrait {
  override def website: Option[String] = Option(websiteRaw).filter(_.nonEmpty)
  override def logoUrl: Option[String] = Option(logoUrlRaw).filter(_.nonEmpty)
}

object Tenant {

  private val selectColumns =
    fr"""SELECT tenantid, name, website, logourl, status, visibility, createdbyuserid, creationdate, lastupdate
         FROM tenant"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def fromRow(r: Row): Tenant =
    Tenant(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, r._5.orNull, r._6.orNull, r._7.orNull,
      readDate(r._8), readDate(r._9))

  private def query(condition: Fragment): List[Tenant] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def ts(d: Date): java.sql.Timestamp = new java.sql.Timestamp(d.getTime)

  def findAll(): List[Tenant] = query(fr"ORDER BY id ASC")

  def findByTenantId(tenantId: String): Box[Tenant] =
    query(fr"WHERE tenantid = ${Option(tenantId)} ORDER BY id ASC").headOption match {
      case Some(t) => Full(t)
      case None => Empty
    }

  def insert(tenantId: String, name: String, website: String, logoUrl: String, status: String,
             visibility: String, createdByUserId: String): Tenant = {
    val now = new Date()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO tenant (tenantid, name, website, logourl, status, visibility, createdbyuserid,
              creationdate, lastupdate)
            VALUES (${Option(tenantId)}, ${Option(name)}, ${Option(website)}, ${Option(logoUrl)},
              ${Option(status)}, ${Option(visibility)}, ${Option(createdByUserId)}, ${ts(now)}, ${ts(now)})"""
        .update.run)
    Tenant(tenantId, name, website, logoUrl, status, visibility, createdByUserId, now, now)
  }

  def update(t: Tenant): Tenant = {
    DoobieUtil.runUpdate(
      sql"""UPDATE tenant SET name = ${Option(t.name)}, website = ${Option(t.websiteRaw)},
              logourl = ${Option(t.logoUrlRaw)}, status = ${Option(t.status)},
              visibility = ${Option(t.visibility)}, lastupdate = ${ts(t.updatedAt)}
            WHERE tenantid = ${Option(t.tenantId)}""".update.run)
    t
  }

  def setCreationDate(tenantId: String, creationDate: java.util.Date): Unit = {
    DoobieUtil.runUpdate(
      sql"UPDATE tenant SET creationdate = ${ts(creationDate)} WHERE tenantid = ${Option(tenantId)}".update.run)
    ()
  }

  def delete(tenantId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM tenant WHERE tenantid = ${Option(tenantId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM tenant".update.run)
    ()
  }
}
