/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.snippet

import code.model._
import code.model.dataAccess.AuthUser
import net.liftweb.common.{Empty, Full, Loggable}
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.Helpers._
import net.liftweb.util.{CssSel, FieldError, Helpers, Props}
import code.consumer.Consumers

class ConsumerRegistration extends Loggable {

  private object nameVar extends RequestVar("")
  private object redirectionURLVar extends RequestVar("")
  private object authenticationURLVar extends RequestVar("")
  private object appTypeVar extends RequestVar[Consumer.appType.enum.AppType](Consumer.appType.enum.values.head)
  private object descriptionVar extends RequestVar("")
  private object devEmailVar extends RequestVar("")
  private object appType extends RequestVar("Web")
  private object submitButtonDefenseFlag extends RequestVar("")




  // Can be used to show link to an online form to collect more information about the App / Startup
  val registrationMoreInfoUrl = Props.get("webui_post_consumer_registration_more_info_url", "")

  val registrationMoreInfoText : String = registrationMoreInfoUrl match {
    case "" => ""
    case _  =>  Props.get("webui_post_consumer_registration_more_info_text", "Please tell us more your Application and / or Startup using this link.")
  }

  
  def registerForm = {

    val appTypes = Consumer.appType.enum.values.toList.map { appType =>
      val id = appType.toString
      (id, id)
    }

    def submitButtonDefense: Unit = {
      submitButtonDefenseFlag("true")
    }

    def registerWithoutWarnings =
      register &
      "#registration-errors" #> ""

    def register = {
      ".register" #> {
          ".appTypeClass" #> SHtml.select(appTypes, Empty, appType(_)) &
          ".appNameClass" #> SHtml.text(nameVar.is, nameVar(_)) & 
          ".appRedirectUrlClass" #> SHtml.text(redirectionURLVar, redirectionURLVar(_)) &
          ".appDevClass" #> SHtml.text(devEmailVar, devEmailVar(_)) &
          ".appDescClass" #> SHtml.textarea(descriptionVar, descriptionVar (_)) &
          ".appUserAuthenticationUrlClass" #> SHtml.text(authenticationURLVar.is, authenticationURLVar(_)) &
          "type=submit" #> SHtml.submit("Send", () => submitButtonDefense)
      } &
      ".success" #> ""
    }

    def showResults(consumer : Consumer) = {
      val urlOAuthEndpoint = Props.get("hostname", "") + "/oauth/initiate"
      val urlDirectLoginEndpoint = Props.get("hostname", "") + "/my/logins/direct"
      //thanks for registering, here's your key, etc.
      ".app-consumer_id *" #> consumer.id.get &
      ".app-name *" #> consumer.name.get &
      ".app-redirect-url *" #> consumer.redirectURL &
      ".app-user-authentication-url *" #> consumer.userAuthenticationURL &
      ".app-type *" #> consumer.appType.get.toString &
      ".app-description *" #> consumer.description.get &
      ".app-developer *" #> consumer.developerEmail.get &
      ".auth-key *" #> consumer.key.get &
      ".secret-key *" #> consumer.secret.get &
      ".registration" #> "" &
      ".oauth-endpoint a *" #> urlOAuthEndpoint &
      ".oauth-endpoint a [href]" #> urlOAuthEndpoint &
      ".directlogin-endpoint a *" #> urlDirectLoginEndpoint &
      ".directlogin-endpoint a [href]" #> urlDirectLoginEndpoint &
      ".post-consumer-registration-more-info-link a *" #> registrationMoreInfoText &
      ".post-consumer-registration-more-info-link a [href]" #> registrationMoreInfoUrl
    }

    def saveAndShowResults(consumer : Consumer) = {
      val c = Consumers.consumers.vend.updateConsumer(consumer.id, Some(Helpers.randomString(40).toLowerCase), Some(Helpers.randomString(40).toLowerCase), Some(true), None, None, None, None, None, None)
      val result = c match {
        case Full(x) => x
        case _       => consumer
      }
      notifyRegistrationOccurred(result)
      sendEmailToDeveloper(result)

      showResults(result)
    }

    def showErrors(errors : List[FieldError]) = {
      val errorsString = errors.map(_.msg.toString)
      register &
      "#registration-errors *" #> {
        ".error *" #>
          errorsString.map({ e=>
            ".errorContent *" #> e
        })
      }
    }

    //TODO this should be used somewhere else, it is check the empty of description for the hack attack from GUI.
    def showErrorsForDescription (descriptioinError : String) = {
      register &
        "#registration-errors *" #> {
          ".error *" #>
            List(descriptioinError).map({ e=>
              ".errorContent *" #> e
            })
        }
    }

    def analyseResult = {

      def withNameOpt(s: String): Option[Consumer.appType.enum.AppType] = Consumer.appType.enum.values.find(_.toString == s)

      val appTypeSelected = withNameOpt(appType.is)

      val consumer = Consumers.consumers.vend.createConsumer(None, None, None, Some(nameVar.is), Some(appTypeSelected.get), Some(descriptionVar.is), Some(devEmailVar.is), Some(redirectionURLVar.is), Some(AuthUser.getCurrentResourceUserUserId))

      val errors = consumer.get.validate
      nameVar.set(nameVar.is)
      appTypeVar.set(appTypeSelected.get)
      descriptionVar.set(descriptionVar.is)
      devEmailVar.set(devEmailVar.is)
      redirectionURLVar.set(redirectionURLVar.is)

      if(submitButtonDefenseFlag.isEmpty)
        showErrorsForDescription("The 'Send' button random name has been modified !")
      else if(descriptionVar.isEmpty)
        showErrorsForDescription("Description of the application can not be empty !")
      else if(errors.isEmpty)
        saveAndShowResults(consumer.get)
      else
        showErrors(errors)
    }

    if(S.post_?) analyseResult
    else registerWithoutWarnings

  }

  def sendEmailToDeveloper(registered : Consumer) = {
    import net.liftweb.util.Mailer
    import net.liftweb.util.Mailer._

    val mailSent = for {
      send : String <- Props.get("mail.api.consumer.registered.notification.send") if send.equalsIgnoreCase("true")
      from <- Props.get("mail.api.consumer.registered.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
    } yield {

      // Only send consumer key / secret by email if we explicitly want that.
      val sendSensitive : Boolean = Props.getBool("mail.api.consumer.registered.notification.send.sensistive", false)
      val consumerKeyOrMessage : String = if (sendSensitive) registered.key.get else "Configured so sensitive data is not sent by email (Consumer Key)."
      val consumerSecretOrMessage : String = if (sendSensitive) registered.secret.get else "Configured so sensitive data is not sent by email (Consumer Secret)."

      val thisApiInstance = Props.get("hostname", "unknown host")
      val urlOAuthEndpoint = thisApiInstance + "/oauth/initiate"
      val urlDirectLoginEndpoint = thisApiInstance + "/my/logins/direct"
      val registrationMessage = s"Thank you for registering a Consumer on $thisApiInstance. \n" +
        s"Email: ${registered.developerEmail.get} \n" +
        s"App name: ${registered.name.get} \n" +
        s"App type: ${registered.appType.get.toString} \n" +
        s"App description: ${registered.description.get} \n" +
        s"Consumer Key: ${consumerKeyOrMessage} \n" +
        s"Consumer Secret : ${consumerSecretOrMessage} \n" +
        s"OAuth Endpoint: ${urlOAuthEndpoint} \n" +
        s"OAuth Documentation: https://github.com/OpenBankProject/OBP-API/wiki/OAuth-1.0-Server \n" +
        s"Direct Login Endpoint: ${urlDirectLoginEndpoint} \n" +
        s"Direct Login Documentation: https://github.com/OpenBankProject/OBP-API/wiki/Direct-Login \n" +
        s"$registrationMoreInfoText: $registrationMoreInfoUrl" 

      val params = PlainMailBodyType(registrationMessage) :: List(To(registered.developerEmail.get))

      val subject1 : String = "Thank you for registering to use the Open Bank Project API."
      val subject2 : String = if (sendSensitive) "This email contains your API keys." else "This email does NOT contain your API keys."
      val subject : String = s"$subject1 $subject2"

      //this is an async call
      Mailer.sendMail(
        From(from),
        Subject(subject1),
        params :_*
      )
    }

    if(mailSent.isEmpty)
      this.logger.warn(s"Sending email with API consumer registration data is omitted: $mailSent")

  }

  // This is to let the system administrators / API managers know that someone has registered a consumer key.
  def notifyRegistrationOccurred(registered : Consumer) = {
    import net.liftweb.util.Mailer
    import net.liftweb.util.Mailer._

    val mailSent = for {
      // e.g mail.api.consumer.registered.sender.address=no-reply@example.com
      from <- Props.get("mail.api.consumer.registered.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
      // no spaces, comma separated e.g. mail.api.consumer.registered.notification.addresses=notify@example.com,notify2@example.com,notify3@example.com
      toAddressesString <- Props.get("mail.api.consumer.registered.notification.addresses") ?~ "Could not send mail: Missing props param for 'to'"
    } yield {

      val thisApiInstance = Props.get("hostname", "unknown host")
      val registrationMessage = s"New user signed up for API keys on $thisApiInstance. \n" +
      		s"Email: ${registered.developerEmail.get} \n" +
      		s"App name: ${registered.name.get} \n" +
      		s"App type: ${registered.appType.get.toString} \n" +
      		s"App description: ${registered.description.get}"

      //technically doesn't work for all valid email addresses so this will mess up if someone tries to send emails to "foo,bar"@example.com
      val to = toAddressesString.split(",").toList
      val toParams = to.map(To(_))
      val params = PlainMailBodyType(registrationMessage) :: toParams

      //this is an async call
      Mailer.sendMail(
        From(from),
        Subject(s"New API user registered on $thisApiInstance"),
        params :_*
      )
    }

    //if Mailer.sendMail wasn't called (note: this actually isn't checking if the mail failed to send as that is being done asynchronously)
    if(mailSent.isEmpty)
      this.logger.warn(s"API consumer registration failed: $mailSent")

  }

}