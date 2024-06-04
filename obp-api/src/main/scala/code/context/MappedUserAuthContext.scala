package code.context

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.UserAuthContext
import net.liftweb.mapper._

class MappedUserAuthContext extends UserAuthContext with LongKeyedMapper[MappedUserAuthContext] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserAuthContext

  object mUserAuthContextId extends MappedUUID(this)
  object mUserId extends UUIDString(this)
  object mKey extends MappedString(this, 4000)
  object mValue extends MappedString(this, 4000)
  object mConsumerId extends MappedString(this, 255)

  override def userId = mUserId.get   
  override def key = mKey.get  
  override def value = mValue.get  
  override def userAuthContextId = mUserAuthContextId.get  
  override def timeStamp = createdAt.get  
  override def consumerId = mConsumerId.get

}

object MappedUserAuthContext extends MappedUserAuthContext with LongKeyedMetaMapper[MappedUserAuthContext] {
  override def dbIndexes = UniqueIndex(mUserId, mKey, createdAt) :: super.dbIndexes
}

