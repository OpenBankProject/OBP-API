package code.context

import code.util.{MappedUUID, UUIDString}
import net.liftweb.mapper._

class MappedUserAuthContext extends UserAuthContext with LongKeyedMapper[MappedUserAuthContext] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserAuthContext

  object mUserAuthContextId extends MappedUUID(this)
  object mUserId extends UUIDString(this)
  object mKey extends MappedString(this, 50)
  object mValue extends MappedString(this, 50)

  override def userId = mUserId.get   
  override def key = mKey.get  
  override def value = mValue.get  
  override def userAuthContextId = mUserAuthContextId.get  
  
}

object MappedUserAuthContext extends MappedUserAuthContext with LongKeyedMetaMapper[MappedUserAuthContext] {
  override def dbIndexes = UniqueIndex(mUserId, mKey) ::super.dbIndexes
}

