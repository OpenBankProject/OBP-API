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

import code.api.DirectLogin
import code.api.util.ErrorMessages.CreateConsumerError
import code.api.util.{APIUtil, ErrorMessages}
import code.consumer.Consumers
import code.model._
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Empty, Failure, Full}
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.Helpers._
import net.liftweb.util.{CssSel, FieldError, Helpers}
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import sh.ory.hydra.model.OAuth2Client

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.seqAsJavaListConverter


class ConsumerRegistration extends MdcLoggable {

  private object nameVar extends RequestVar("")
  private object redirectionURLVar extends RequestVar("")
  private object authenticationURLVar extends RequestVar("")
  private object appTypeVar extends RequestVar[AppType](AppType.Web)
  private object descriptionVar extends RequestVar("")
  private object devEmailVar extends RequestVar("")
  private object appType extends RequestVar("Web")
  private object submitButtonDefenseFlag extends RequestVar("")




  // Can be used to show link to an online form to collect more information about the App / Startup
  val registrationMoreInfoUrl = getWebUiPropsValue("webui_post_consumer_registration_more_info_url", "")
  
  val registrationConsumerButtonValue = getWebUiPropsValue("webui_post_consumer_registration_submit_button_value", "Register consumer")

  val registrationMoreInfoText : String = registrationMoreInfoUrl match {
    case "" => ""
    case _  =>  getWebUiPropsValue("webui_post_consumer_registration_more_info_text", "Please tell us more your Application and / or Startup using this link.")
  }

  
  def registerForm = {

    val appTypes = List((AppType.Web.toString, AppType.Web.toString), (AppType.Mobile.toString, AppType.Mobile.toString))

    def submitButtonDefense: Unit = {
      submitButtonDefenseFlag("true")
    }

    def registerWithoutWarnings =
      register &
      "#register-consumer-errors" #> ""

    def register = {
      "form" #> {
          "#appType" #> SHtml.select(appTypes, Empty, appType(_)) &
          "#appName" #> SHtml.text(nameVar.is, nameVar(_)) & 
          "#appRedirectUrl" #> SHtml.text(redirectionURLVar, redirectionURLVar(_)) &
          "#appDev" #> SHtml.text(devEmailVar, devEmailVar(_)) &
          "#appDesc" #> SHtml.textarea(descriptionVar, descriptionVar (_)) &
          "#appUserAuthenticationUrl" #> SHtml.text(authenticationURLVar.is, authenticationURLVar(_)) &
          "type=submit" #> SHtml.submit(s"$registrationConsumerButtonValue", () => submitButtonDefense)
      } &
      "#register-consumer-success" #> ""
    }

    def showResults(consumer : Consumer) = {
      val urlOAuthEndpoint = APIUtil.getPropsValue("hostname", "") + "/oauth/initiate"
      val urlDirectLoginEndpoint = APIUtil.getPropsValue("hostname", "") + "/my/logins/direct"

      val registerConsumerSuccessMessageWebpage = getWebUiPropsValue(
        "webui_register_consumer_success_message_webpage", 
        "Thanks for registering your consumer with the Open Bank Project API! Here is your developer information. Please save it in a secure location.")
      //thanks for registering, here's your key, etc.
      "#register-consumer-success-message *" #> registerConsumerSuccessMessageWebpage &
      "#app-consumer_id *" #> consumer.consumerId.get &
      "#app-name *" #> consumer.name.get &
      "#app-redirect-url *" #> consumer.redirectURL &
      "#app-user-authentication-url *" #> consumer.userAuthenticationURL &
      "#app-type *" #> consumer.appType.get.toString &
      "#app-description *" #> consumer.description.get &
      "#app-developer *" #> consumer.developerEmail.get &
      "#auth-key *" #> consumer.key.get &
      "#secret-key *" #> consumer.secret.get &
      "#oauth-endpoint a *" #> urlOAuthEndpoint &
      "#oauth-endpoint a [href]" #> urlOAuthEndpoint &
      "#directlogin-endpoint a *" #> urlDirectLoginEndpoint &
      "#directlogin-endpoint a [href]" #> urlDirectLoginEndpoint &
      "#post-consumer-registration-more-info-link a *" #> registrationMoreInfoText &
      "#post-consumer-registration-more-info-link a [href]" #> registrationMoreInfoUrl & {
        if(AuthUser.createHydraClient) {
          "#hydra-client-info-title *" #>"OAuth2" &
          "#admin_url *" #> AuthUser.hydraAdminUrl &
            "#client_id *" #> {consumer.key.get} &
            "#client_secret *" #> consumer.secret.get &
            "#client_scope" #> {
              val lastIndex = AuthUser.hydraConsents.length - 1
              AuthUser.hydraConsents.zipWithIndex.map { kv =>
                  ".client-scope-value *" #> {
                    val (scope, index) = kv
                    if(index == lastIndex) {
                      scope
                    } else {
                      s"$scope,\\"
                    }
                  }
              }
            }
//          & "#base_url *" #> S.getRequestHeader("Referer")
//              .map(StringUtils.substringBeforeLast(_, S.uri))
//              .getOrElse("")
        } else {
          "#hydra-client-info-title *" #> "" &
            "#hydra-client-info *" #> ""
        }
      } &
      "#register-consumer-input" #> "" & {
        val hasDummyUsers = getWebUiPropsValue("webui_dummy_user_logins", "").nonEmpty
        val isShowDummyUserTokens = getWebUiPropsValue("webui_show_dummy_user_tokens", "false").toBoolean
        if(hasDummyUsers && isShowDummyUserTokens) {
          "#create-directlogin a [href]" #> s"dummy-user-tokens?consumer_key=${consumer.key.get}"
        } else {
          "#dummy-user-tokens" #> ""
        }
      }
    }

    def showRegistrationResults(result : Consumer) = {

      notifyRegistrationOccurred(result)
      sendEmailToDeveloper(result)

      showResults(result)
    }

    def showErrors(errors : List[FieldError]) = {
      val errorsString = errors.map(_.msg.toString)
      errorsString.map(errorMessage => S.error("register-consumer-errors", errorMessage))
      register &
      "#register-consumer-errors *" #> {
        ".error *" #>
          errorsString.map({ e=>
            ".errorContent *" #> e
        })
      }
    }

    def showUnknownErrors(errors : List[String]) = {
      errors.map(errorMessage => S.error("register-consumer-errors", errorMessage))
      register &
        "#register-consumer-errors *" #> {
          ".error *" #>
            errors.map({ e=>
              ".errorContent *" #> e
            })
        }
    }
    def showValidationErrors(errors : List[String]): CssSel = {
      errors.filter(errorMessage => (errorMessage.contains("name") || errorMessage.contains("Name")) ).map(errorMessage => S.error("consumer-registration-app-name-error", errorMessage))
      errors.filter(errorMessage => (errorMessage.contains("description") || errorMessage.contains("Description"))).map(errorMessage => S.error("consumer-registration-app-description-error", errorMessage))
      errors.filter(errorMessage => (errorMessage.contains("email")|| errorMessage.contains("Email"))).map(errorMessage => S.error("consumer-registration-app-developer-error", errorMessage))
      errors.filter(errorMessage => (errorMessage.contains("redirect")|| errorMessage.contains("Redirect"))).map(errorMessage => S.error("consumer-registration-app-redirect-url-error", errorMessage))
      //Here show not filed related errors to the general part.
      val unknownErrors: Seq[String] = errors
        .filterNot(errorMessage => (errorMessage.contains("name") || errorMessage.contains("Name")))
        .filterNot(errorMessage => (errorMessage.contains("description") || errorMessage.contains("Description")))
        .filterNot(errorMessage => (errorMessage.contains("email") || errorMessage.contains("Email")))
        .filterNot(errorMessage => (errorMessage.contains("redirect") || errorMessage.contains("Redirect")))
      unknownErrors.map(errorMessage => S.error("register-consumer-errors", errorMessage))
      register &
        "#register-consumer-errors *" #> {
          ".error *" #>
            unknownErrors.map({ e=>
              ".errorContent *" #> e
            })
        }
    }

    //TODO this should be used somewhere else, it is check the empty of description for the hack attack from GUI.
    def showErrorsForDescription (descriptionError : String) = {
      S.error("register-consumer-errors", descriptionError)
      register &
        "#register-consumer-errors *" #> {
          ".error *" #>
            List(descriptionError).map({ e=>
              ".errorContent *" #> e
            })
        }
    }

    def analyseResult = {

      def withNameOpt(s: String): Option[AppType] = Some(AppType.valueOf(s))

      val appTypeSelected = withNameOpt(appType.is)
      logger.debug("appTypeSelected: " + appTypeSelected)
      nameVar.set(nameVar.is)
      appTypeVar.set(appTypeSelected.get)
      descriptionVar.set(descriptionVar.is)
      devEmailVar.set(devEmailVar.is)
      redirectionURLVar.set(redirectionURLVar.is)

      if(submitButtonDefenseFlag.isEmpty)
        showErrorsForDescription("The 'Register' button random name has been modified !")
      else{
        val consumer = Consumers.consumers.vend.createConsumer(
          Some(Helpers.randomString(40).toLowerCase),
          Some(Helpers.randomString(40).toLowerCase),
          Some(true),
          Some(nameVar.is),
          appTypeSelected,
          Some(descriptionVar.is),
          Some(devEmailVar.is),
          Some(redirectionURLVar.is),
          Some(AuthUser.getCurrentResourceUserUserId))
        logger.debug("consumer: " + consumer)
        consumer match {
          case Full(x) if AuthUser.createHydraClient =>
            try {
              val oAuth2Client = new OAuth2Client()
              oAuth2Client.setClientId(x.key.get)
              oAuth2Client.setClientSecret(x.secret.get)
              val allConsents = "openid" :: "offline" :: AuthUser.hydraConsents
              oAuth2Client.setScope(allConsents.mkString(" "))
              oAuth2Client.setGrantTypes(("authorization_code" :: "refresh_token" :: Nil).asJava)

              oAuth2Client.setResponseTypes(("code" :: "id_token" :: Nil).asJava)
              oAuth2Client.setRedirectUris(List(x.redirectURL.get).asJava)
              oAuth2Client.setTokenEndpointAuthMethod("client_secret_post")
              AuthUser.hydraAdmin.createOAuth2Client(oAuth2Client)

              showRegistrationResults(x)
            } catch {
              case e: Exception =>
                logger.error(s"Create hydra client fail", e)
                Consumers.consumers.vend.deleteConsumer(x)
                showValidationErrors(s"$CreateConsumerError, fail when create hydra client." :: Nil)
            }
          case Full(x) => showRegistrationResults(x)
          case Failure(msg, _, _) => showValidationErrors(msg.split(";").toList)
          case _ => showUnknownErrors(List(ErrorMessages.UnknownError))
        }
      }
    }

    if(S.post_?) analyseResult
    else registerWithoutWarnings

  }

  def sendEmailToDeveloper(registered : Consumer) = {
    import net.liftweb.util.Mailer
    import net.liftweb.util.Mailer._

    val mailSent = for {
      send : String <- APIUtil.getPropsValue("mail.api.consumer.registered.notification.send") if send.equalsIgnoreCase("true")
      from <- APIUtil.getPropsValue("mail.api.consumer.registered.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
    } yield {

      // Only send consumer key / secret by email if we explicitly want that.
      val sendSensitive : Boolean = APIUtil.getPropsAsBoolValue("mail.api.consumer.registered.notification.send.sensistive", false)
      val consumerKeyOrMessage : String = if (sendSensitive) registered.key.get else "Configured so sensitive data is not sent by email (Consumer Key)."
      val consumerSecretOrMessage : String = if (sendSensitive) registered.secret.get else "Configured so sensitive data is not sent by email (Consumer Secret)."

      val thisApiInstance = APIUtil.getPropsValue("hostname", "unknown host")
      val apiExplorerUrl = getWebUiPropsValue("webui_api_explorer_url", "unknown host")
      val directLoginDocumentationUrl = getWebUiPropsValue("webui_direct_login_documentation_url", apiExplorerUrl + "/glossary#Direct-Login")
      val oauthDocumentationUrl = getWebUiPropsValue("webui_oauth_1_documentation_url", apiExplorerUrl + "/glossary#OAuth-1.0a")
      val oauthEndpointUrl = thisApiInstance + "/oauth/initiate"

      val directLoginEndpointUrl = thisApiInstance + "/my/logins/direct"
      val registrationMessage = s"Thank you for registering a Consumer on $thisApiInstance. \n" +
        s"Email: ${registered.developerEmail.get} \n" +
        s"App name: ${registered.name.get} \n" +
        s"App type: ${registered.appType.get.toString} \n" +
        s"App description: ${registered.description.get} \n" +
        s"Consumer Key: ${consumerKeyOrMessage} \n" +
        s"Consumer Secret : ${consumerSecretOrMessage} \n" +
        s"OAuth Endpoint: ${oauthEndpointUrl} \n" +
        s"OAuth Documentation: ${directLoginDocumentationUrl} \n" +
        s"Direct Login Endpoint: ${directLoginEndpointUrl} \n" +
        s"Direct Login Documentation: ${oauthDocumentationUrl} \n" +
        s"$registrationMoreInfoText: $registrationMoreInfoUrl"

      val params = PlainMailBodyType(registrationMessage) :: List(To(registered.developerEmail.get))

      val webuiRegisterConsumerSuccessMssageEmail : String = getWebUiPropsValue(
        "webui_register_consumer_success_message_email", 
        "Thank you for registering to use the Open Bank Project API.") 

      //this is an async call
      Mailer.sendMail(
        From(from),
        Subject(webuiRegisterConsumerSuccessMssageEmail),
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
      from <- APIUtil.getPropsValue("mail.api.consumer.registered.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
      // no spaces, comma separated e.g. mail.api.consumer.registered.notification.addresses=notify@example.com,notify2@example.com,notify3@example.com
      toAddressesString <- APIUtil.getPropsValue("mail.api.consumer.registered.notification.addresses") ?~ "Could not send mail: Missing props param for 'to'"
    } yield {

      val thisApiInstance = APIUtil.getPropsValue("hostname", "unknown host")
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

  def showDummyCustomerTokens(): CssSel = {
    val consumerKeyBox = S.param("consumer_key")
    // The following will check the login user and the user from the consumerkey. we do not want to share consumerkey to others.
    val loginUserId = AuthUser.getCurrentUser.map(_.userId).openOr("")
    val userCreatedByUserId = consumerKeyBox.map(Consumers.consumers.vend.getConsumerByConsumerKey(_)).flatten.map(_.createdByUserId.get).openOr("")
    if(!loginUserId.equals(userCreatedByUserId)) 
      return "#dummy-user-tokens ^" #> "The consumer key in the URL is not created by the current login user, please create consumer for this user first!"
    
    val dummyUsersInfo = getWebUiPropsValue("webui_dummy_user_logins", "")
    val isShowDummyUserTokens = getWebUiPropsValue("webui_show_dummy_user_tokens", "false").toBoolean
    // (username, password) -> authHeader
    val userNameToAuthInfo: Map[(String, String), String] = (isShowDummyUserTokens, consumerKeyBox, dummyUsersInfo) match {
      case(true, Full(consumerKey), dummyCustomers) if dummyCustomers.nonEmpty => {
        val regex = """(?s)\{.*?"user_name"\s*:\s*"(.+?)".+?"password"\s*:\s*"(.+?)".+?\}""".r
        val matcher = regex.pattern.matcher(dummyCustomers)
        var tokens = ListMap[(String, String), String]()
        while(matcher.find()) {
          val userName = matcher.group(1)
          val password = matcher.group(2)
          val (code, token) = DirectLogin.createToken(Map(("username", userName), ("password", password), ("consumer_key", consumerKey)))
          val authHeader = code match {
            case 200 => (userName, password) -> s"""Authorization: DirectLogin token="$token""""
            case _ => (userName, password) ->  "username or password is invalid, generate token fail"
          }
          tokens += authHeader
        }
        tokens
      }
      case _ => Map.empty[(String, String), String]
    }

    val elements = userNameToAuthInfo.map{ pair =>
        val ((userName, password), authHeader) = pair
            <div class="row">
              <div class="col-xs-12 col-sm-4">
                username: <br/>
                {userName} <br/>
                password: <br/>
                {password}
              </div>
              <div class="col-xs-12 col-sm-8">
                {authHeader}
               </div>
            </div>
      }

    "#dummy-user-tokens ^" #> elements
  }
}
