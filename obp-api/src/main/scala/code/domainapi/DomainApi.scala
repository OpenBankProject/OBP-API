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

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.mapper._
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
             createdByUserId: String): Box[DomainApiTrait]
  def get(bankId: String, domainApiId: String): Box[DomainApiTrait]
  def getAll(bankId: String): Box[List[DomainApiTrait]]
  /** Every Domain API of every space, read from the table (not the request path's cached list). */
  def getAllInEverySpace(): Box[List[DomainApiTrait]]
  def update(bankId: String, domainApiId: String, basePath: String, version: String, title: String,
             description: String): Box[DomainApiTrait]
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
                      createdByUserId: String): Box[DomainApiTrait] = {
    val created = tryo {
      DomainApi.create
        .DomainApiId(APIUtil.generateUUID())
        .BankId(bankId)
        .BasePath(basePath)
        .Version(version)
        .Title(title)
        .Description(description)
        .CreatedByUserId(createdByUserId)
        .saveMe()
    }
    forget()
    created
  }

  override def get(bankId: String, domainApiId: String): Box[DomainApiTrait] =
    DomainApi.find(By(DomainApi.BankId, bankId), By(DomainApi.DomainApiId, domainApiId))

  override def getAll(bankId: String): Box[List[DomainApiTrait]] =
    tryo(DomainApi.findAll(By(DomainApi.BankId, bankId), OrderBy(DomainApi.BasePath, Ascending)))

  override def getAllInEverySpace(): Box[List[DomainApiTrait]] =
    tryo(DomainApi.findAll(OrderBy(DomainApi.BasePath, Ascending)))

  override def update(bankId: String, domainApiId: String, basePath: String, version: String, title: String,
                      description: String): Box[DomainApiTrait] = {
    val updated = DomainApi.find(By(DomainApi.BankId, bankId), By(DomainApi.DomainApiId, domainApiId)).flatMap { row =>
      tryo {
        row.BasePath(basePath).Version(version).Title(title).Description(description).UpdatedAt(new Date()).saveMe()
      }
    }
    forget()
    updated
  }

  override def delete(bankId: String, domainApiId: String): Box[Boolean] = {
    val deleted = DomainApi.find(By(DomainApi.BankId, bankId), By(DomainApi.DomainApiId, domainApiId)).flatMap(row => tryo(row.delete_!))
    forget()
    deleted
  }
}

class DomainApi extends DomainApiTrait with LongKeyedMapper[DomainApi] with IdPK {
  def getSingleton = DomainApi

  object DomainApiId extends MappedString(this, 36) {
    override def dbColumnName = "domain_api_id"
  }
  // SYS for the system space, never NULL: see DynamicEntitySpace.
  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
    override def defaultValue = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  }
  object BasePath extends MappedString(this, 255) {
    override def dbColumnName = "base_path"
  }
  object Version extends MappedString(this, 50) {
    override def dbColumnName = "version"
  }
  object Title extends MappedString(this, 255) {
    override def dbColumnName = "title"
  }
  object Description extends MappedString(this, 2000) {
    override def dbColumnName = "description"
  }
  object CreatedByUserId extends MappedString(this, 255) {
    override def dbColumnName = "created_by_user_id"
  }
  object CreatedAt extends MappedDateTime(this) {
    override def dbColumnName = "created_at"
    override def defaultValue = new Date()
  }
  object UpdatedAt extends MappedDateTime(this) {
    override def dbColumnName = "updated_at"
    override def defaultValue = new Date()
  }

  override def domainApiId: String = DomainApiId.get
  override def bankId: String = BankId.get
  override def basePath: String = BasePath.get
  override def version: String = Version.get
  override def title: String = Title.get
  override def description: String = Option(Description.get).getOrElse("")
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: Date = CreatedAt.get
  override def updatedAt: Date = UpdatedAt.get
}

object DomainApi extends DomainApi with LongKeyedMetaMapper[DomainApi] {
  override def dbTableName = "domain_api"
  override def dbIndexes = UniqueIndex(DomainApiId) :: UniqueIndex(BasePath) :: Index(BankId) :: super.dbIndexes
}
