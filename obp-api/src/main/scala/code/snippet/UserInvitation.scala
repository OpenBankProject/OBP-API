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
import code.users.{UserAgreementProvider, UserInvitationProvider, Users}
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

import scala.collection.immutable.List

class UserInvitation extends MdcLoggable {

  private object firstNameVar extends RequestVar("")
  private object lastNameVar extends RequestVar("")
  private object companyVar extends RequestVar("")
  private object countryVar extends RequestVar("None")
  private object devEmailVar extends RequestVar("")
  private object usernameVar extends RequestVar("")
  private object termsCheckboxVar extends RequestVar(false)
  private object marketingInfoCheckboxVar extends RequestVar(false)
  private object privacyCheckboxVar extends RequestVar(false)
  
  val registrationConsumerButtonValue: String = getWebUiPropsValue("webui_post_user_invitation_submit_button_value", "Register as a Developer")
  val privacyConditionsValue: String = getWebUiPropsValue("webui_post_user_invitation_privacy_conditions_value", "Privacy conditions.")
  val termsAndConditionsValue: String = getWebUiPropsValue("webui_post_user_invitation_terms_and_conditions_value", "Terms and Conditions.")
  val termsAndConditionsCheckboxValue: String = getWebUiPropsValue("webui_post_user_invitation_terms_and_conditions_checkbox_value", "I agree to the above Developer Terms and Conditions")
  
  def registerForm: CssSel = {

    val secretLink = S.param("id").getOrElse("0")
    val userInvitation = UserInvitationProvider.userInvitationProvider.vend.getUserInvitationBySecretLink(secretLink.toLong)
    firstNameVar.set(userInvitation.map(_.firstName).getOrElse("None"))
    lastNameVar.set(userInvitation.map(_.lastName).getOrElse("None"))
    val email = userInvitation.map(_.email).getOrElse("None")
    devEmailVar.set(email)
    companyVar.set(userInvitation.map(_.company).getOrElse("None"))
    countryVar.set(userInvitation.map(_.country).getOrElse("None"))
    usernameVar.set(firstNameVar.is.toLowerCase + "." + lastNameVar.is.toLowerCase())

    def submitButtonDefense(): Unit = {
      if(Users.users.vend.getUserByUserName(usernameVar.is).isDefined) showErrorsForUsername()
      else if(userInvitation.map(_.status != "CREATED").getOrElse(false)) showErrorsForStatus()
      else if(privacyCheckboxVar.is == false) showErrorsForPrivacyConditions()
      else if(termsCheckboxVar.is == false) showErrorsForTermsAndConditions()
      else {
        // Resource User table
        createResourceUser(
          provider = "OBP-User-Invitation",
          providerId = Some(usernameVar.is),
          name = Some(firstNameVar.is + " " + lastNameVar.is),
          email = Some(email)
        ).map{ u =>
          // AuthUser table
          createAuthUser(user = u, firstName = firstNameVar.is, lastName = lastNameVar.is, password = "")
          // Use Agreement table
          UserAgreementProvider.userAgreementProvider.vend.createOrUpdateUserAgreement(
            u.userId, privacyConditionsValue, termsAndConditionsValue, marketingInfoCheckboxVar.is)
          // Set a new password
          val resetLink = AuthUser.passwordResetUrl(u.idGivenByProvider, u.emailAddress, u.userId) + "?action=set"
          S.redirectTo(resetLink)
        }
        
      }
    }

    def showError(usernameError: String) = {
      S.error("register-consumer-errors", usernameError)
      register &
        "#register-consumer-errors *" #> {
          ".error *" #>
            List(usernameError).map({ e =>
              ".errorContent *" #> e
            })
        }
    }

    def showErrorsForUsername() = {
      showError(Helper.i18n("unique.username"))
    }
    def showErrorsForStatus() = {
      showError(Helper.i18n("user.invitation.is.already.finished"))
    }
    def showErrorsForTermsAndConditions() = {
      showError(Helper.i18n("terms.and.conditions.are.not.selected"))
    }
    def showErrorsForPrivacyConditions() = {
      showError(Helper.i18n("privacy.conditions.are.not.selected"))
    }

    def register = {
      "form" #> {
          "#country" #> SHtml.text(countryVar.is, countryVar(_)) &
          "#firstName" #> SHtml.text(firstNameVar.is, firstNameVar(_)) &
          "#lastName" #> SHtml.text(lastNameVar.is, lastNameVar(_)) &
          "#companyName" #> SHtml.text(companyVar.is, companyVar(_)) &
          "#devEmail" #> SHtml.text(devEmailVar, devEmailVar(_)) &
          "#username" #> SHtml.text(usernameVar, usernameVar(_)) &
          "#privacy" #> SHtml.textarea(privacyConditionsValue, privacyConditionsValue => privacyConditionsValue) &
          "#privacy_checkbox" #> SHtml.checkbox(privacyCheckboxVar, privacyCheckboxVar(_)) &
          "#terms" #> SHtml.textarea(termsAndConditionsValue, termsAndConditionsValue => termsAndConditionsValue) &
          "#terms_checkbox" #> SHtml.checkbox(termsCheckboxVar, termsCheckboxVar(_)) &
          "#marketing_info_checkbox" #> SHtml.checkbox(marketingInfoCheckboxVar, marketingInfoCheckboxVar(_)) &
          "type=submit" #> SHtml.submit(s"$registrationConsumerButtonValue", () => submitButtonDefense)
      } &
      "#register-consumer-success" #> ""
    }
    register
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
