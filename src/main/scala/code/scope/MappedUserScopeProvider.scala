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