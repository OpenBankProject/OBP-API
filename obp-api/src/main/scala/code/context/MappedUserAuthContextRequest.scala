package code.context

import code.util.{MappedUUID, UUIDString}
import net.liftweb.mapper._

import scala.util.Random

class MappedUserAuthContextRequest extends UserAuthContextRequest with LongKeyedMapper[MappedUserAuthContextRequest] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserAuthContextRequest

  object mUserAuthContextRequestId extends MappedUUID(this)
  object mUserId extends UUIDString(this)
  object mKey extends MappedString(this, 50)
  object mValue extends MappedString(this, 50)
  object mChallenge extends MappedString(this, 10)  {
    override def defaultValue = Random.nextInt(99999999).toString()
  }
  object mStatus extends MappedString(this, 20)

  override def userId = mUserId.get   
  override def key = mKey.get  
  override def value = mValue.get  
  override def userAuthContextRequestId = mUserAuthContextRequestId.get
  override def challenge: String = mChallenge.get
  override def status: String = mStatus.get
  
}

object MappedUserAuthContextRequest extends MappedUserAuthContextRequest with LongKeyedMetaMapper[MappedUserAuthContextRequest] {
  override def dbIndexes = UniqueIndex(mUserId, mKey) ::super.dbIndexes
}



