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

package code.api.util

import code.api.Constant
import code.entitlement.Entitlement
import code.users.Users
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common.Box


import scala.collection.immutable.List
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object NotificationUtil extends MdcLoggable {
  def sendEmailRegardingAssignedRole(userId : String, entitlement: Entitlement): Unit = {
    // Fire-and-forget: the user lookup and the SMTP send both block, and the
    // grant-entitlement response must not wait on them.
    Future {
      val user = Users.users.vend.getUserByUserId(userId)
      sendEmailRegardingAssignedRole(user, entitlement)
    }.failed.foreach(e =>
      logger.error(s"sendEmailRegardingAssignedRole says: failed for userId=$userId role=${entitlement.roleName}", e)
    )
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
      val emailContent = CommonsEmailWrapper.EmailContent(
        from = from,
        to = List(user.emailAddress),
        subject = s"You have been granted the role: ${entitlement.roleName}",
        textContent = Some(bodyOfMessage)
      )
      // Blocking SMTP send (Transport.send) — only call this off the request
      // thread; the userId overload above wraps it in a Future.
      CommonsEmailWrapper.sendTextEmail(emailContent)
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
