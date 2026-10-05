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
import code.messageoutbox.MessageOutbox
import code.users.Users
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

object NotificationUtil extends MdcLoggable {
  /**
   * Queue the "you have been granted a Role" email in the message outbox; the relay sends it.
   *
   * The row is written in the caller's transaction, so a grant that is rolled back (a request that
   * fails or times out) emails nobody, and the SMTP send never runs on a request thread. Sending on
   * the shared pool instead let a burst of grants (a Group sync granting dozens of Roles to each
   * member) occupy every thread with blocking sends and stall the whole API.
   */
  def sendEmailRegardingAssignedRole(userId : String, entitlement: Entitlement): Unit =
    sendEmailRegardingAssignedRole(Users.users.vend.getUserByUserId(userId), entitlement)

  def sendEmailRegardingAssignedRole(user: Box[User], entitlement: Entitlement): Unit = {
    val queued = for {
      user <- user
      from <- APIUtil.getPropsValue("mail.api.consumer.registered.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
      row <- {
        val bodyOfMessage : String = s"""Dear ${user.name},
                                        |
                                        |You have been granted the entitlement to use ${entitlement.roleName} on ${Constant.HostName}
                                        |
                                        |Cheers
                                        |""".stripMargin
        tryo(MessageOutbox.enqueueEmail(
          subjectId = entitlement.entitlementId,
          subjectIdType = MessageOutbox.SUBJECT_TYPE_ENTITLEMENT_ID,
          operationName = MessageOutbox.OPERATION_ROLE_GRANTED_EMAIL,
          CommonsEmailWrapper.EmailContent(
            from = from,
            to = List(user.emailAddress),
            subject = s"You have been granted the role: ${entitlement.roleName}",
            textContent = Some(bodyOfMessage)
          )
        ))
      }
    } yield row
    if(queued.isEmpty) {
      val info =
        s"""
           |Sending email is omitted.
           |User: $user
           |Props mail.api.consumer.registered.sender.address: ${APIUtil.getPropsValue("mail.api.consumer.registered.sender.address")}
           |Reason: $queued
           |""".stripMargin
      this.logger.warn(info)
    }
  }

}
