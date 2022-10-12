package code.api.util

import code.api.Constant
import code.entitlement.Entitlement
import code.users.Users
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.util.Mailer
import net.liftweb.util.Mailer._

import scala.collection.immutable.List

object NotificationUtil extends MdcLoggable {
  def sendEmailRegardingAssignedRole(userId : String, entitlement: Entitlement): Unit = {
    val user = Users.users.vend.getUserByUserId(userId)
    sendEmailRegardingAssignedRole(user, entitlement)
  }
  def sendEmailRegardingAssignedRole(user: Box[User], entitlement: Entitlement): Unit = {
    val mailSent = for {
      user <- user
      from <- APIUtil.getPropsValue("mail.api.consumer.registered.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
    } yield {
      val bodyOfMessage : String = s"""Dear ${user.name},
                                      |
                                      |You have been granted the entitlement to use ${entitlement.roleName} on ${Constant.HostName}
                                      |
                                      |Cheers
                                      |""".stripMargin
      val params = PlainMailBodyType(bodyOfMessage) :: List(To(user.emailAddress))
      val subjectOfMessage = "You have been granted the role"
      //this is an async call
      Mailer.sendMail(
        From(from),
        Subject(subjectOfMessage),
        params :_*
      )
    }
    if(mailSent.isEmpty) {
      val info =
        s"""
           |Sending email is omitted.
           |User: $user
           |Props mail.api.consumer.registered.sender.address: ${APIUtil.getPropsValue("mail.api.consumer.registered.sender.address")}
           |""".stripMargin
      this.logger.warn(info)
    }
  }

}
