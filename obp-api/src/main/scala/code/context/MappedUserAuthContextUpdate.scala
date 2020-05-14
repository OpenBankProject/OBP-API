package code.context

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.UserAuthContextUpdate
import net.liftweb.mapper._

import scala.util.Random

class MappedUserAuthContextUpdate extends UserAuthContextUpdate with LongKeyedMapper[MappedUserAuthContextUpdate] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserAuthContextUpdate

  object mUserAuthContextUpdateId extends MappedUUID(this)
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
  override def userAuthContextUpdateId = mUserAuthContextUpdateId.get
  override def challenge: String = mChallenge.get
  override def status: String = mStatus.get
  
}

object MappedUserAuthContextUpdate extends MappedUserAuthContextUpdate with LongKeyedMetaMapper[MappedUserAuthContextUpdate] {
  override def dbIndexes = super.dbIndexes
}



