package code.users

import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.By

object UserInitActionProvider extends MdcLoggable {
  def createOrUpdateInitAction(userId: String, actionName: String, actionValue: String, success: Boolean): Box[UserInitAction] = {
    UserInitAction.find(
      By(UserInitAction.UserId, userId),
      By(UserInitAction.ActionName, actionName),
      By(UserInitAction.ActionValue, actionValue)
    ) match {
      case Full(action) => Some(action.Success(success).saveMe())
      case _ =>
        Some(
          UserInitAction.create
            .UserId(userId)
            .ActionName(actionName)
            .ActionValue(actionValue)
            .Success(success)
            .saveMe()
        )
    }
  }
}
