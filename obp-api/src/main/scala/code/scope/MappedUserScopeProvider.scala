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

import code.util.UUIDString
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

object MappedUserScopeProvider extends UserScopeProvider {

  override def addUserScope(scopeId: String, userId: String): Box[UserScope] = {
     Full(MappedUserScope.create
      .mScopeId(scopeId)
      .mUserId(userId)
      .saveMe())
  }

  override def deleteUserScope(scopeId: String, userId: String): Box[Boolean] = {
    MappedUserScope.find(
      By(MappedUserScope.mScopeId, scopeId),
      By(MappedUserScope.mUserId, userId)
    ).map(_.delete_!)
  }

  override def getUserScope(scopeId: String, userId: String): Box[UserScope] = {
    MappedUserScope.find(
      By(MappedUserScope.mScopeId, scopeId),
      By(MappedUserScope.mUserId, userId)
    )
  }

  override def getUserScopesByScopeId(scopeId: String): Box[List[UserScope]] = {
    Full(MappedUserScope.findAll(
      By(MappedUserScope.mScopeId, scopeId),
      OrderBy(MappedUserScope.updatedAt, Descending)))
  }


  override def getUserScopesByUserId(userId: String): Box[List[UserScope]] = {
    Full(MappedUserScope.findAll(
      By(MappedUserScope.mUserId, userId),
      OrderBy(MappedUserScope.updatedAt, Descending)))
  }

}

class MappedUserScope extends UserScope with LongKeyedMapper[MappedUserScope] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserScope

  object mScopeId extends UUIDString(this)
  object mUserId extends UUIDString(this)

  override def scopeId: String = mScopeId.get.toString
  override def userId: String = mUserId.get
}

object MappedUserScope extends MappedUserScope with LongKeyedMetaMapper[MappedUserScope] {
  override def dbIndexes = UniqueIndex(mScopeId, mUserId) :: super.dbIndexes
}