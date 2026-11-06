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

package code.domainapi

import java.util.Date
import java.util.concurrent.atomic.AtomicReference

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

/**
 * A Domain API publishes the dynamic endpoints of one space (a bank, or SYS for the system space) under a
 * base path of its own, such as `carbon-registry/v1`, without OBP's own URL structure in front of them.
 * It only renames: a call under the base path is rewritten to the existing URL of the endpoint and runs
 * exactly as that call would, with the same authentication, Roles and access checks.
 * See the Glossary item "Domain APIs" and ideas/DOMAIN_APIS.md.
 */
object DomainApis extends SimpleInjector {
  val domainApiProvider = new Inject(() => buildOne) {}
  def buildOne: DomainApiProvider = DomainApiDbProvider
}

trait DomainApiTrait {
  def domainApiId: String
  /** A bank id, or SYS for the system space. Never empty. */
  def bankId: String
  /** The path the space's endpoints are published under, without a leading or trailing slash. */
  def basePath: String
  /** The full semantic version, MAJOR.MINOR.PATCH. */
  def version: String
  def title: String
  def description: String
  /** Whether anyone, without logging in, may find it through GET /obp/v7.0.0/banks/BANK_ID/domain-apis. */
  def isPubliclyDiscoverable: Boolean
  def createdByUserId: String
  def createdAt: Date
  def updatedAt: Date
}

/** A Domain API as the request path reads it: held in memory, so a request does not query the table. */
case class DomainApiRoute(domainApiId: String, bankId: String, basePath: String, version: String,
                          title: String, description: String) {
  val basePathSegments: List[String] = basePath.split("/").toList
}

trait DomainApiProvider {
  def create(bankId: String, basePath: String, version: String, title: String, description: String,
             isPubliclyDiscoverable: Boolean, createdByUserId: String): Box[DomainApiTrait]
  def get(bankId: String, domainApiId: String): Box[DomainApiTrait]
  def getAll(bankId: String): Box[List[DomainApiTrait]]
  /** The Domain APIs of a space that are publicly discoverable. */
  def getPubliclyDiscoverable(bankId: String): Box[List[DomainApiTrait]]
  /** The publicly discoverable Domain APIs of every space. */
  def getPubliclyDiscoverableInEverySpace(): Box[List[DomainApiTrait]]
  /** Every Domain API of every space, read from the table (not the request path's cached list). */
  def getAllInEverySpace(): Box[List[DomainApiTrait]]
  def update(bankId: String, domainApiId: String, basePath: String, version: String, title: String,
             description: String, isPubliclyDiscoverable: Boolean): Box[DomainApiTrait]
  def delete(bankId: String, domainApiId: String): Box[Boolean]
  /** Every Domain API of every space, for the request path; see [[DomainApiDbProvider.routes]]. */
  def routes(): List[DomainApiRoute]
}

object DomainApiDbProvider extends DomainApiProvider {

  /**
   * This is how long a node keeps its in-memory list of Domain APIs before reading the table again.
   *
   * Every request passes the Domain API front door, so the list cannot be read from the database per
   * request. A node forgets its list at once when it writes a Domain API itself; another node of the same
   * installation sees the change within this many seconds.
   */
  val cacheTtlSeconds: Int = APIUtil.getPropsAsIntValue("domain_api.cache.ttl.seconds", 10)

  private case class Snapshot(routes: List[DomainApiRoute], loadedAt: Long)
  private val snapshot = new AtomicReference[Option[Snapshot]](None)

  private def forget(): Unit = snapshot.set(None)

  override def routes(): List[DomainApiRoute] = {
    val now = System.currentTimeMillis()
    snapshot.get() match {
      case Some(s) if now - s.loadedAt < cacheTtlSeconds * 1000L => s.routes
      case _ =>
        val loaded = tryo(DomainApi.findAll()).openOr(Nil).map(d =>
          DomainApiRoute(d.domainApiId, d.bankId, d.basePath, d.version, d.title, d.description))
        snapshot.set(Some(Snapshot(loaded, now)))
        loaded
    }
  }

  override def create(bankId: String, basePath: String, version: String, title: String, description: String,
                      isPubliclyDiscoverable: Boolean, createdByUserId: String): Box[DomainApiTrait] = {
    val created = tryo {
      val now = new Date()
      DomainApi.insert(DomainApi(
        domainApiId = APIUtil.generateUUID(),
        bankId = bankId,
        basePath = basePath,
        version = version,
        title = title,
        descriptionRaw = description,
        isPubliclyDiscoverable = isPubliclyDiscoverable,
        // A consent user's Domain API belongs to the person the Consent is for (UserReference).
        createdByUserId = code.users.Users.users.vend.attributedUserId(createdByUserId, code.users.UserReference.DomainApi_CreatedByUserId).openOr(createdByUserId),
        createdAt = now,
        updatedAt = now))
    }
    forget()
    created
  }

  override def get(bankId: String, domainApiId: String): Box[DomainApiTrait] =
    DomainApi.find(bankId, domainApiId)

  override def getAll(bankId: String): Box[List[DomainApiTrait]] =
    tryo(DomainApi.query(fr"WHERE bank_id = ${Option(bankId)} ORDER BY base_path ASC"))

  override def getPubliclyDiscoverable(bankId: String): Box[List[DomainApiTrait]] =
    tryo(DomainApi.query(fr"WHERE bank_id = ${Option(bankId)} AND is_publicly_discoverable = ${true} ORDER BY base_path ASC"))

  override def getPubliclyDiscoverableInEverySpace(): Box[List[DomainApiTrait]] =
    tryo(DomainApi.query(fr"WHERE is_publicly_discoverable = ${true} ORDER BY base_path ASC"))

  override def getAllInEverySpace(): Box[List[DomainApiTrait]] =
    tryo(DomainApi.query(fr"ORDER BY base_path ASC"))

  override def update(bankId: String, domainApiId: String, basePath: String, version: String, title: String,
                      description: String, isPubliclyDiscoverable: Boolean): Box[DomainApiTrait] = {
    val updated = DomainApi.find(bankId, domainApiId).flatMap { row =>
      tryo {
        DomainApi.update(row.copy(basePath = basePath, version = version, title = title, descriptionRaw = description,
          isPubliclyDiscoverable = isPubliclyDiscoverable, updatedAt = new Date()))
      }
    }
    forget()
    updated
  }

  override def delete(bankId: String, domainApiId: String): Box[Boolean] = {
    val deleted = DomainApi.find(bankId, domainApiId).flatMap(row => tryo(DomainApi.delete(row.domainApiId)))
    forget()
    deleted
  }
}

/**
 * One row of domain_api. Upstream declares this as a Lift Mapper entity; here it is a Doobie store over
 * the table the changelog creates (db.changelog-develop-merge-3.yaml), unique on domain_api_id and on
 * base_path.
 *
 * bank_id is SYS for the system space, never NULL: see DynamicEntitySpace. is_publicly_discoverable is
 * false until its owner says otherwise: a Domain API is only listed publicly on purpose.
 */
case class DomainApi(
  domainApiId: String,
  bankId: String,
  basePath: String,
  version: String,
  title: String,
  private[domainapi] val descriptionRaw: String,
  isPubliclyDiscoverable: Boolean,
  createdByUserId: String,
  createdAt: Date,
  updatedAt: Date
) extends DomainApiTrait {
  override def description: String = Option(descriptionRaw).getOrElse("")
}

object DomainApi {

  private val selectColumns =
    fr"""SELECT domain_api_id, bank_id, base_path, version, title, description, is_publicly_discoverable,
                created_by_user_id, created_at, updated_at
         FROM domain_api"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Boolean], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def ts(d: Date): Option[java.sql.Timestamp] = Option(d).map(x => new java.sql.Timestamp(x.getTime))

  private def fromRow(r: Row): DomainApi =
    DomainApi(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, r._5.orNull, r._6.orNull,
      r._7.getOrElse(false), r._8.orNull, readDate(r._9), readDate(r._10))

  private[domainapi] def query(condition: Fragment): List[DomainApi] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAll(): List[DomainApi] = query(fr"ORDER BY id ASC")

  def find(bankId: String, domainApiId: String): Box[DomainApi] =
    query(fr"WHERE bank_id = ${Option(bankId)} AND domain_api_id = ${Option(domainApiId)} ORDER BY id ASC")
      .headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(d: DomainApi): DomainApi = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO domain_api (domain_api_id, bank_id, base_path, version, title, description,
              is_publicly_discoverable, created_by_user_id, created_at, updated_at)
            VALUES (${Option(d.domainApiId)}, ${Option(d.bankId)}, ${Option(d.basePath)}, ${Option(d.version)},
              ${Option(d.title)}, ${Option(d.descriptionRaw)}, ${d.isPubliclyDiscoverable},
              ${Option(d.createdByUserId)}, ${ts(d.createdAt)}, ${ts(d.updatedAt)})""".update.run)
    d
  }

  def update(d: DomainApi): DomainApi = {
    DoobieUtil.runUpdate(
      sql"""UPDATE domain_api SET bank_id = ${Option(d.bankId)}, base_path = ${Option(d.basePath)},
              version = ${Option(d.version)}, title = ${Option(d.title)}, description = ${Option(d.descriptionRaw)},
              is_publicly_discoverable = ${d.isPubliclyDiscoverable}, updated_at = ${ts(d.updatedAt)}
            WHERE domain_api_id = ${Option(d.domainApiId)}""".update.run)
    d
  }

  def delete(domainApiId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM domain_api WHERE domain_api_id = ${Option(domainApiId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM domain_api".update.run)
    ()
  }
}
