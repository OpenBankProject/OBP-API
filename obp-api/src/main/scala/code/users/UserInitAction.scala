package code.users

trait UserInitActionTrait {
  def userId: String
  def actionName: String
  def actionValue: String
  def success: Boolean
}

/** One user-init-action row, standing in for the Lift entity in return types. */
case class UserInitActionRow(
  userId: String,
  actionName: String,
  actionValue: String,
  success: Boolean
) extends UserInitActionTrait
