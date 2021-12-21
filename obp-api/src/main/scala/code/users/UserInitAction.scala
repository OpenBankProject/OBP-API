package code.users

import code.util.MappedUUID
import net.liftweb.mapper._

class UserInitAction extends UserInitActionTrait with LongKeyedMapper[UserInitAction] with IdPK with CreatedUpdated {
  def getSingleton = UserInitAction

  object UserId extends MappedUUID(this)
  object ActionName extends MappedString(this, 100)
  object ActionValue extends MappedString(this, 100)
  object Success extends MappedBoolean(this)

  override def userId: String = UserId.get
  override def actionName: String = ActionName.get
  override def actionValue: String = ActionValue.get
  override def success: Boolean = Success.get
}

object UserInitAction extends UserInitAction with LongKeyedMetaMapper[UserInitAction] {
  override def dbIndexes: List[BaseIndex[UserInitAction]] = UniqueIndex(UserId, ActionName, ActionValue) :: super.dbIndexes
}

trait UserInitActionTrait {
  def userId: String
  def actionName: String
  def actionValue: String
  def success: Boolean
}
