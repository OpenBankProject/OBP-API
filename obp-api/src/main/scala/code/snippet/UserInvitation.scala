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

import java.time.{Duration, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.Date

import code.api.Constant
import code.api.util.{APIUtil, SecureRandomUtil}
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users
import code.users.{UserAgreementProvider, UserInvitationProvider, Users}
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.{CssSel, Helpers}
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
  private object consentForCollectingCheckboxVar extends RequestVar(false)
  private object consentForCollectingMandatoryCheckboxVar extends RequestVar(true)
  private object privacyCheckboxVar extends RequestVar(false)
  
  val ttl = APIUtil.getPropsAsLongValue("user_invitation.ttl.seconds", 86400)
  
  val registrationConsumerButtonValue: String = getWebUiPropsValue("webui_post_user_invitation_submit_button_value", "Register as a Developer")
  val privacyConditionsValue: String = getWebUiPropsValue("webui_privacy_policy", "")
  val termsAndConditionsValue: String = getWebUiPropsValue("webui_terms_and_conditions", "")
  val termsAndConditionsCheckboxValue: String = getWebUiPropsValue("webui_post_user_invitation_terms_and_conditions_checkbox_value", "I agree to the above Developer Terms and Conditions")
  val personalDataCollectionConsentCountryWaiverList = getWebUiPropsValue("personal_data_collection_consent_country_waiver_list", "").split(",").toList.map(_.trim)
  
  def registerForm: CssSel = {

    val secretLink: Box[Long] = tryo {
      S.param("id").getOrElse("0").toLong
    }
    val userInvitation: Box[users.UserInvitation] = UserInvitationProvider.userInvitationProvider.vend.getUserInvitationBySecretLink(secretLink.getOrElse(0))
    firstNameVar.set(userInvitation.map(_.firstName).getOrElse("None"))
    lastNameVar.set(userInvitation.map(_.lastName).getOrElse("None"))
    val email = userInvitation.map(_.email).getOrElse("None")
    devEmailVar.set(email)
    companyVar.set(userInvitation.map(_.company).getOrElse("None"))
    countryVar.set(userInvitation.map(_.country).getOrElse("None"))
    // Propose the username only for the first time. In case an end user manually change it we must not override it.
    if(usernameVar.isEmpty) usernameVar.set(firstNameVar.is.toLowerCase + "." + lastNameVar.is.toLowerCase())
    if(personalDataCollectionConsentCountryWaiverList.exists(_.toLowerCase == countryVar.is.toLowerCase) == true) {
      consentForCollectingMandatoryCheckboxVar.set(false)
    } else {
      consentForCollectingMandatoryCheckboxVar.set(true)
    }
    
    def submitButtonDefense(): Unit = {
      val verifyingTime = ZonedDateTime.now(ZoneOffset.UTC)
      val createdAt = userInvitation.map(_.createdAt.get).getOrElse(time(239932800))
      val timeOfCreation = ZonedDateTime.ofInstant(createdAt.toInstant(), ZoneId.systemDefault())
      val timeDifference = Duration.between(verifyingTime, timeOfCreation)
      logger.debug("User invitation TTL time: " + ttl)
      logger.debug("User invitation expiration time: " + timeDifference.abs.getSeconds)
      
      if(secretLink.isEmpty || userInvitation.isEmpty) showErrorsForSecretLink()
      else if(userInvitation.map(_.status != "CREATED").getOrElse(false)) showErrorsForStatus()
      else if(timeDifference.abs.getSeconds > ttl) showErrorsForTtl()
      else if(AuthUser.currentUser.isDefined) showErrorYouMustBeLoggedOff()
      else if(Users.users.vend.getUserByUserName(usernameVar.is).isDefined) showErrorsForUsername()
      else if(privacyCheckboxVar.is == false) showErrorsForPrivacyConditions()
      else if(termsCheckboxVar.is == false) showErrorsForTermsAndConditions()
      else if(personalDataCollectionConsentCountryWaiverList.exists(_.toLowerCase == countryVar.is.toLowerCase) == false && consentForCollectingCheckboxVar.is == false) showErrorsForConsentForCollectingPersonalData()
      else {
        // Resource User table
        createResourceUser(
          provider = Constant.localIdentityProvider, // TODO Make provider an enum
          providerId = Some(usernameVar.is),
          name = Some(usernameVar.is),
          email = Some(email),
          userInvitationId = userInvitation.map(_.userInvitationId).toOption,
          company = userInvitation.map(_.company).toOption,
          lastMarketingAgreementSignedDate = if(marketingInfoCheckboxVar.is) Some(new Date()) else None
        ).map{ u =>
          // AuthUser table
          createAuthUser(user = u, firstName = firstNameVar.is, lastName = lastNameVar.is) match {
            case Failure(msg,_,_) =>
              Users.users.vend.deleteResourceUser(u.id.get)
              showError(msg)
            case _ =>
              // User Agreement table
              UserAgreementProvider.userAgreementProvider.vend.createOrUpdateUserAgreement(
                u.userId, "privacy_conditions", privacyConditionsValue)
              UserAgreementProvider.userAgreementProvider.vend.createOrUpdateUserAgreement(
                u.userId, "terms_and_conditions", termsAndConditionsValue)
              UserAgreementProvider.userAgreementProvider.vend.createOrUpdateUserAgreement(
                u.userId, "accept_marketing_info", marketingInfoCheckboxVar.is.toString)
              UserAgreementProvider.userAgreementProvider.vend.createOrUpdateUserAgreement(
                u.userId, "consent_for_collecting_personal_data", consentForCollectingCheckboxVar.is.toString)
              // Set the status of the user invitation to "FINISHED"
              UserInvitationProvider.userInvitationProvider.vend.updateStatusOfUserInvitation(userInvitation.map(_.userInvitationId).getOrElse(""), "FINISHED")
              // Set a new password
              // Please note that the query parameter is used to alter the message at password reset page i.e. at next code:
              // <h1>{if(S.queryString.isDefined) Helper.i18n("set.your.password") else S.?("reset.your.password")}</h1>
              // placed into function AuthZUser.passwordResetXhtml
              val resetLink = AuthUser.passwordResetUrl(u.idGivenByProvider, u.emailAddress, u.userId) + "?action=set"
              S.redirectTo(resetLink)
          }
        }
        
      }
    }

    def showError(usernameError: String) = {
      S.error("data-area-errors", usernameError)
      register &
        "#data-area-errors *" #> {
          ".error *" #>
            List(usernameError).map({ e =>
              ".errorContent *" #> e
            })
        }
    }

    def showErrorsForSecretLink() = {
      showError(Helper.i18n("your.secret.link.is.not.valid"))
    }
    def showErrorsForUsername() = {
      showError(Helper.i18n("your.username.is.not.unique"))
    }
    def showErrorsForStatus() = {
      showError(Helper.i18n("user.invitation.is.already.finished"))
    }
    def showErrorsForTtl() = {
      showError(Helper.i18n("user.invitation.is.expired"))
    }
    def showErrorYouMustBeLoggedOff() = {
      showError(Helper.i18n("you.must.be.logged.off"))
    }
    def showErrorsForTermsAndConditions() = {
      showError(Helper.i18n("terms.and.conditions.are.not.selected"))
    }
    def showErrorsForPrivacyConditions() = {
      showError(Helper.i18n("privacy.conditions.are.not.selected"))
    }
    def showErrorsForConsentForCollectingPersonalData() = {
      showError(Helper.i18n("consent.to.collect.personal.data.is.not.selected"))
    }

    def register = {
      "form" #> {
          "#country" #> SHtml.text(countryVar.is, countryVar(_)) &
          "#firstName" #> SHtml.text(firstNameVar.is, firstNameVar(_)) &
          "#lastName" #> SHtml.text(lastNameVar.is, lastNameVar(_)) &
          "#companyName" #> SHtml.text(companyVar.is, companyVar(_)) &
          "#devEmail" #> SHtml.text(devEmailVar, devEmailVar(_)) &
          "#username" #> SHtml.text(usernameVar, usernameVar(_)) &
          "#privacy_checkbox" #> SHtml.checkbox(privacyCheckboxVar, privacyCheckboxVar(_)) &
          "#terms_checkbox" #> SHtml.checkbox(termsCheckboxVar, termsCheckboxVar(_)) &
          "#marketing_info_checkbox" #> SHtml.checkbox(marketingInfoCheckboxVar, marketingInfoCheckboxVar(_)) &
          "#consent_for_collecting_checkbox" #> SHtml.checkbox(consentForCollectingCheckboxVar, consentForCollectingCheckboxVar(_), "id" -> "consent_for_collecting_checkbox") &
          "#consent_for_collecting_mandatory" #> SHtml.checkbox(consentForCollectingMandatoryCheckboxVar, consentForCollectingMandatoryCheckboxVar(_), "id" -> "consent_for_collecting_mandatory", "hidden" -> "true") &
          "type=submit" #> SHtml.submit(s"$registrationConsumerButtonValue", () => submitButtonDefense)
      } &
      "#data-area-success" #> ""
    }
    userInvitation match {
      case Full(payload) if payload.status == "CREATED" => // All good
      case _ =>
        // Clear all data
        firstNameVar.set("None")
        lastNameVar.set("None")
        devEmailVar.set("None")
        companyVar.set("None")
        countryVar.set("None")
        usernameVar.set("None")
        // and the redirect
        S.redirectTo("/user-invitation-invalid")
    }
    if(AuthUser.currentUser.isDefined) 
      S.redirectTo("/user-invitation-warning") 
    else 
      register
  }

  private def createAuthUser(user: User, firstName: String, lastName: String): Box[AuthUser] = {
    val newUser = AuthUser.create
      .firstName(firstName)
      .lastName(lastName)
      .email(user.emailAddress)
      .user(user.userPrimaryKey.value)
      .username(user.name)
      .provider(user.provider)
      .password(SecureRandomUtil.alphanumeric(10))
      .validated(true)
    newUser.validate match {
      case Nil =>
        // Save the user
        Full(newUser.saveMe())
      case xs => S.error(xs)
        Failure(xs.map(i => i.msg).mkString(";"))
    }
    
  }

  private def createResourceUser(provider: String, 
                                 providerId: Option[String], 
                                 name: Option[String], 
                                 email: Option[String], 
                                 userInvitationId: Option[String],
                                 company: Option[String],
                                 lastMarketingAgreementSignedDate: Option[Date],
                                ): Box[ResourceUser] = {
    Users.users.vend.createResourceUser(
      provider = provider,
      providerId = providerId,
      createdByConsentId = None,
      name = name,
      email = email,
      userId = None,
      createdByUserInvitationId = userInvitationId,
      company = company,
      lastMarketingAgreementSignedDate = lastMarketingAgreementSignedDate
    )
  }
  
}
