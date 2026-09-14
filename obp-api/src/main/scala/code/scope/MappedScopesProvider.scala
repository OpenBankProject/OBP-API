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

package code.scope

import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.Box
import net.liftweb.mapper._

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedScopesProvider extends ScopeProvider {
  override def getScope(bankId: String, consumerId: String, roleName: String): Box[Scope] = {
    // Return a Box so we can handle errors later.
    MappedScope.find(
      By(MappedScope.mBankId, bankId),
      By(MappedScope.mConsumerId, consumerId),
      By(MappedScope.mRoleName, roleName)
    )
  }

  override def getScopeById(scopeId: String): Box[Scope] = {
    // Return a Box so we can handle errors later.
    MappedScope.find(
      By(MappedScope.mScopeId, scopeId)
    )
  }

  override def getScopesByConsumerId(consumerId: String): Box[List[Scope]] = {
    // Return a Box so we can handle errors later.
    Some(MappedScope.findAll(
      By(MappedScope.mConsumerId, consumerId),
      OrderBy(MappedScope.updatedAt, Descending)))
  }
  override def getScopesByConsumerIdFuture(consumerId: String): Future[Box[List[Scope]]] = {
    // Return a Box so we can handle errors later.
    Future {
      getScopesByConsumerId(consumerId)
    }
  }

  override def getScopes: Box[List[Scope]] = {
    // Return a Box so we can handle errors later.
    Some(MappedScope.findAll(OrderBy(MappedScope.updatedAt, Descending)))
  }

  override def getScopesFuture(): Future[Box[List[Scope]]] = {
    Future {
      getScopes()
    }
  }

  override def deleteScope(scope: Box[Scope]): Box[Boolean] = {
    // Return a Box so we can handle errors later.
    for {
      findScope <- scope
      bankId <- Some(findScope.bankId)
      consumerId <- Some(findScope.consumerId)
      roleName <- Some(findScope.roleName)
      foundScope <-  MappedScope.find(
        By(MappedScope.mBankId, bankId),
        By(MappedScope.mConsumerId, consumerId),
        By(MappedScope.mRoleName, roleName)
      )
    }
      yield {
        MappedScope.delete_!(foundScope)
      }
  }

  override def addScope(bankId: String, consumerId: String, roleName: String): Box[Scope] = {
    // Return a Box so we can handle errors later.
    val addScope = MappedScope.create
      .mBankId(bankId)
      .mConsumerId(consumerId)
      .mRoleName(roleName)
      .saveMe()
    Some(addScope)
  }
}

class MappedScope extends Scope 
  with LongKeyedMapper[MappedScope] with IdPK with CreatedUpdated {

  def getSingleton = MappedScope

  object mScopeId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mConsumerId extends UUIDString(this)
  object mRoleName extends MappedString(this, 255)

  override def scopeId: String = mScopeId.get.toString
  override def bankId: String = mBankId.get
  override def consumerId: String = mConsumerId.get
  override def roleName: String = mRoleName.get
}


object MappedScope extends MappedScope with LongKeyedMetaMapper[MappedScope] {
  override def dbIndexes = UniqueIndex(mScopeId) :: super.dbIndexes
}