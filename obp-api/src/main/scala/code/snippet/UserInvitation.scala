/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.snippet

import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.{UserInvitationProvider, Users}
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List

class UserInvitation extends MdcLoggable {

  private object firstNameVar extends RequestVar("")
  private object lastNameVar extends RequestVar("")
  private object companyVar extends RequestVar("")
  private object countryVar extends RequestVar("None")
  private object devEmailVar extends RequestVar("")
  private object usernameVar extends RequestVar("")
  
  // Can be used to show link to an online form to collect more information about the App / Startup
  val registrationMoreInfoUrl = getWebUiPropsValue("webui_post_user_invitation_more_info_url", "")
  
  val registrationConsumerButtonValue: String = getWebUiPropsValue("webui_post_user_invitation_submit_button_value", "Register as a Developer")

  
  def registerForm: CssSel = {

    val secretLink = S.param("id").getOrElse("0")
    val userInvitation = UserInvitationProvider.userInvitationProvider.vend.getUserInvitationBySecretLink(secretLink.toLong)
    val firstName = userInvitation.map(_.firstName).getOrElse("None")
    firstNameVar.set(firstName)
    val lastName = userInvitation.map(_.lastName).getOrElse("None")
    lastNameVar.set(lastName)
    val email = userInvitation.map(_.email).getOrElse("None")
    devEmailVar.set(email)
    companyVar.set(userInvitation.map(_.company).getOrElse("None"))
    countryVar.set(userInvitation.map(_.country).getOrElse("Bahrain"))
    val username = firstName.toLowerCase + "." + lastName.toLowerCase()
    usernameVar.set(username)

    def submitButtonDefense(): Unit = {
      val username = firstNameVar.is + "." + lastNameVar.is
      createResourceUser(
        provider = "OBP-User-Invitation", 
        providerId = Some(username), 
        name = Some(firstName + " " + lastName), 
        email = Some(email)
      ).map{ u =>
        createAuthUser(user = u, firstName = firstName, lastName = lastName, password = "")
        val resetLink = AuthUser.passwordResetUrl(u.idGivenByProvider, u.emailAddress, u.userId)
        S.redirectTo(resetLink)
      } 
      
    }

    def showErrorsForUsername() = {
      val usernameError = Helper.i18n("unique.username")
      S.error("register-consumer-errors", usernameError)
      register &
        "#register-consumer-errors *" #> {
          ".error *" #>
            List(usernameError).map({ e=>
              ".errorContent *" #> e
            })
        }
    }

    def register = {
      "form" #> {
          "#country" #> SHtml.text(countryVar.is, countryVar(_)) &
          "#firstName" #> SHtml.text(firstNameVar.is, firstNameVar(_)) &
          "#lastName" #> SHtml.text(lastNameVar.is, lastNameVar(_)) &
          "#companyName" #> SHtml.text(companyVar.is, companyVar(_)) &
          "#devEmail" #> SHtml.text(devEmailVar, devEmailVar(_)) &
          "#username" #> SHtml.text(usernameVar, usernameVar(_)) &
          "type=submit" #> SHtml.submit(s"$registrationConsumerButtonValue", () => submitButtonDefense)
      } &
      "#register-consumer-success" #> ""
    }
    if(Users.users.vend.getUserByUserName(username).isDefined) showErrorsForUsername() else register
  }

  private def createAuthUser(user: User, firstName: String, lastName: String, password: String): Box[AuthUser] = tryo {
    val newUser = AuthUser.create
      .firstName(firstName)
      .lastName(lastName)
      .email(user.emailAddress)
      .user(user.userPrimaryKey.value)
      .username(user.idGivenByProvider)
      .provider(user.provider)
      .password(password)
      .validated(true)
    // Save the user
    newUser.saveMe()
  }

  private def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String]): Box[ResourceUser] = {
    Users.users.vend.createResourceUser(
      provider = provider,
      providerId = providerId,
      createdByConsentId = None,
      name = name,
      email = email,
      userId = None
    )
  }
  
}
