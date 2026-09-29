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

package code.platformapp

import java.util.Date

import net.liftweb.common.{Box, Full}
import net.liftweb.db.DB
import net.liftweb.mapper._
import net.liftweb.util.DefaultConnectionIdentifier
import net.liftweb.util.Helpers.tryo

object PlatformAppDbProvider extends PlatformAppProvider {

  override def createPlatformApp(consumerId: String, label: String, markedByUserId: String): Box[PlatformAppTrait] =
    tryo {
      PlatformApp.create
        .PlatformAppId(java.util.UUID.randomUUID().toString)
        .ConsumerId(consumerId)
        .Label(label)
        .MarkedByUserId(markedByUserId)
        .saveMe()
    }

  override def getPlatformApp(consumerId: String): Box[PlatformAppTrait] =
    PlatformApp.find(By(PlatformApp.ConsumerId, consumerId))

  override def getPlatformApps(): Box[List[PlatformAppTrait]] =
    tryo { PlatformApp.findAll(OrderBy(PlatformApp.Label, Ascending)) }

  override def deletePlatformApp(consumerId: String): Box[Boolean] =
    PlatformApp.find(By(PlatformApp.ConsumerId, consumerId)).flatMap { app =>
      tryo {
        DB.use(DefaultConnectionIdentifier) { _ =>
          PlatformAppRequiredScope.bulkDelete_!!(By(PlatformAppRequiredScope.ConsumerId, consumerId))
          app.delete_!
        }
      }
    }

  override def declareRequiredScopes(
    consumerId: String,
    version: Option[String],
    scopes: List[PlatformAppRequiredScopeInput]
  ): Box[PlatformAppTrait] =
    PlatformApp.find(By(PlatformApp.ConsumerId, consumerId)).flatMap { app =>
      tryo {
        // One transaction: a reader never sees the old declaration half replaced.
        DB.use(DefaultConnectionIdentifier) { _ =>
          PlatformAppRequiredScope.bulkDelete_!!(By(PlatformAppRequiredScope.ConsumerId, consumerId))
          scopes.foreach { s =>
            PlatformAppRequiredScope.create
              .ConsumerId(consumerId)
              .RoleName(s.roleName)
              .BankId(s.bankId)
              .NeededFor(s.neededFor)
              .IsOptional(s.isOptional)
              .saveMe()
          }
          app.DeclaredAt(new Date()).DeclaredVersion(version.getOrElse("")).LastUpdate(new Date()).saveMe()
        }
      }
    }

  override def getRequiredScopes(consumerId: String): Box[List[PlatformAppRequiredScopeTrait]] =
    tryo {
      PlatformAppRequiredScope.findAll(
        By(PlatformAppRequiredScope.ConsumerId, consumerId),
        OrderBy(PlatformAppRequiredScope.id, Ascending))
    }
}

class PlatformApp extends PlatformAppTrait with LongKeyedMapper[PlatformApp] with IdPK {
  def getSingleton = PlatformApp

  object PlatformAppId extends MappedString(this, 36) {
    override def dbColumnName = "platform_app_id"
  }
  object ConsumerId extends MappedString(this, 250) {
    override def dbColumnName = "consumer_id"
  }
  object Label extends MappedString(this, 100) {
    override def dbColumnName = "label"
  }
  object MarkedByUserId extends MappedString(this, 255) {
    override def dbColumnName = "marked_by_user_id"
  }
  object CreationDate extends MappedDateTime(this) {
    override def dbColumnName = "created_at"
    override def defaultValue = new Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def dbColumnName = "updated_at"
    override def defaultValue = new Date()
  }
  object DeclaredAt extends MappedDateTime(this) {
    override def dbColumnName = "declared_at"
  }
  object DeclaredVersion extends MappedString(this, 100) {
    override def dbColumnName = "declared_version"
  }

  override def consumerId: String = ConsumerId.get
  override def label: String = Label.get
  override def markedByUserId: String = MarkedByUserId.get
  override def markedAt: Date = CreationDate.get
  override def declaredAt: Option[Date] = Option(DeclaredAt.get)
  override def declaredVersion: Option[String] = Option(DeclaredVersion.get).filter(_.nonEmpty)
}

object PlatformApp extends PlatformApp with LongKeyedMetaMapper[PlatformApp] {
  override def dbTableName = "platform_app"
  override def dbIndexes = UniqueIndex(ConsumerId) :: super.dbIndexes
}

class PlatformAppRequiredScope extends PlatformAppRequiredScopeTrait
  with LongKeyedMapper[PlatformAppRequiredScope] with IdPK {
  def getSingleton = PlatformAppRequiredScope

  object ConsumerId extends MappedString(this, 250) {
    override def dbColumnName = "consumer_id"
  }
  object RoleName extends MappedString(this, 255) {
    override def dbColumnName = "role_name"
  }
  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
  }
  object NeededFor extends MappedString(this, 1000) {
    override def dbColumnName = "needed_for"
  }
  object IsOptional extends MappedBoolean(this) {
    override def dbColumnName = "is_optional"
    override def defaultValue = false
  }

  override def consumerId: String = ConsumerId.get
  override def roleName: String = RoleName.get
  override def bankId: String = BankId.get
  override def neededFor: String = NeededFor.get
  override def isOptional: Boolean = IsOptional.get
}

object PlatformAppRequiredScope extends PlatformAppRequiredScope
  with LongKeyedMetaMapper[PlatformAppRequiredScope] {
  override def dbTableName = "platform_app_required_scope"
  override def dbIndexes = Index(ConsumerId) :: super.dbIndexes
}
