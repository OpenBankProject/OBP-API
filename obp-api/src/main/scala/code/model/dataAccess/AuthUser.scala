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
package code.model.dataAccess

import code.UserRefreshes.UserRefreshes
import code.accountholders.AccountHolders
import code.api._
import code.api.cache.Caching
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.util.APIUtil._
import code.api.util.CommonFunctions.validUri
import code.api.util.CommonsEmailWrapper._
import code.api.util.ErrorMessages._
import code.api.util._
import code.bankconnectors.Connector
import code.context.UserAuthContextProvider
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.token.TokensOpenIDConnect
import code.users.{UserAgreementProvider, Users}
import code.util.Helper
import code.util.Helper.{MdcLoggable, ObpS}
import code.util.HydraUtil._
import code.views.Views
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common._
import net.liftweb.mapper._
import net.liftweb.util._
import org.apache.commons.lang3.StringUtils
import sh.ory.hydra.api.AdminApi
import sh.ory.hydra.model.AcceptLoginRequest

import java.util.UUID.randomUUID
import scala.concurrent.Future
import scala.xml.{Elem, NodeSeq, Text}

/**
 * An O-R mapped "User" class that includes first name, last name, password
  *
  * 1 AuthUser : is used for authentication, only for webpage Login in stuff
  *   1) It is MegaProtoUser, has lots of methods for validation username, password, email ....
  *      Such as lost password, reset password ..... 
  *      Lift have some helper methods to make these things easily. 
  *   
  *  
  * 
  * 2 ResourceUser: is only a normal LongKeyedMapper 
  *   1) All the accounts, transactions ,roles, views, accountHolders, customers... should be linked to ResourceUser.userId_ field.
  *   2) The consumer keys, tokens are also belong ResourceUser
  *  
  * 
  * 3 RelationShips:
  *   1)When `Sign up` new user --> create AuthUser --> call AuthUser.save --> create ResourceUser user.
  *      They share the same username and email.
  *   2)AuthUser `user` field as the Foreign Key to link to Resource User. 
  *      one AuthUser <---> one ResourceUser 
  *
 */
class AuthUser extends MegaProtoUser[AuthUser] with CreatedUpdated with MdcLoggable {
  def getSingleton = AuthUser // what's the "meta" server

  object user extends MappedLongForeignKey(this, ResourceUser)
  
  object passwordShouldBeChanged extends MappedBoolean(this)

  override lazy val firstName = new MyFirstName
  
  protected class MyFirstName extends MappedString(this, 100) {
    def isEmpty(msg: => String)(value: String): List[FieldError] =
      value match {
        case null                  => List(FieldError(this, Text(msg))) // issue 179
        case e if e.trim.isEmpty   => List(FieldError(this, Text(msg))) // issue 179
        case _                     => Nil
      }
    
    override def displayName = fieldOwner.firstNameDisplayName
    override val fieldId = Some(Text("txtFirstName"))
    override def validations = isEmpty(Helper.i18n("Please.enter.your.first.name")) _ :: super.validations
  }
  
  override lazy val lastName = new MyLastName

  protected class MyLastName extends MappedString(this, 100) {
    def isEmpty(msg: => String)(value: String): List[FieldError] =
      value match {
        case null                  => List(FieldError(this, Text(msg))) // issue 179
        case e if e.trim.isEmpty   => List(FieldError(this, Text(msg))) // issue 179
        case _                     => Nil
      }

    override def displayName = fieldOwner.lastNameDisplayName
    override val fieldId = Some(Text("txtLastName"))
    override def validations = isEmpty(Helper.i18n("Please.enter.your.last.name")) _ :: super.validations
  }
  
  /**
   * Username is a valid email address or the regex below:
   * Regex to validate a username
   * 
   * ^(?=.{8,100}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$
   * └─────┬────┘└───┬──┘└─────┬─────┘└─────┬─────┘ └───┬───┘
   *       │         │         │            │           no _ or . at the end
   *       │         │         │            │
   *       │         │         │            allowed characters
   *       │         │         │
   *       │         │         no __ or _. or ._ or .. inside
   *       │         │
   *       │         no _ or . at the beginning
   *       │
   *       username is 8-100 characters long
   *       
   */
  private val usernameRegex = """^(?=.{8,100}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

  /**
    * The username field for the User.
    */
  lazy val username: userName = new userName()
  class userName extends MappedString(this, 100) {
    def isEmpty(msg: => String)(value: String): List[FieldError] =
      value match {
        case null                  => List(FieldError(this, Text(msg))) // issue 179
        case e if e.trim.isEmpty   => List(FieldError(this, Text(msg))) // issue 179
        case _                     => Nil
      }
    def usernameIsValid(msg: => String)(e: String) = e match {
      case null                                             => List(FieldError(this, Text(msg)))
      case e if e.trim.isEmpty                              => List(FieldError(this, Text(msg)))
      case e if emailRegex.findFirstMatchIn(e).isDefined    => Nil // Email is valid username
      case e if usernameRegex.findFirstMatchIn(e).isDefined => Nil
      case _                                                => List(FieldError(this, Text(msg)))
    }
    override def displayName = Helper.i18n("Username")
    @deprecated("Use UniqueIndex(username, provider)","27 December 2021")
    override def dbIndexed_? = false // We use more general index UniqueIndex(username, provider) :: super.dbIndexes
    override def validations = isEmpty(Helper.i18n("Please.enter.your.username")) _ ::
                               usernameIsValid(Helper.i18n("invalid.username")) _ ::
                               valUnique(Helper.i18n("unique.username")) _ ::
                               valUniqueExternally(Helper.i18n("unique.username")) _ ::
                               super.validations
    override val fieldId = Some(Text("txtUsername"))

    /**
     * Make sure that the field is unique in the CBS
     */
    def valUniqueExternally(msg: => String)(uniqueUsername: String): List[FieldError] ={
      if (APIUtil.getPropsAsBoolValue("connector.user.authentication", false)) {
        logger.info(s"valUniqueExternally: calling checkExternalUserExists for username: $uniqueUsername")
        val connectorResult = Connector.connector.vend.checkExternalUserExists(uniqueUsername, None)
        logger.info(s"valUniqueExternally: checkExternalUserExists returned: ${connectorResult.getClass.getSimpleName}")
        connectorResult.map(_.sub) match {
          case Full(returnedUsername) => // Get the username via connector
            logger.info(s"valUniqueExternally: checkExternalUserExists returned username: $returnedUsername")
            if(uniqueUsername == returnedUsername) { // Username is NOT unique
              logger.info(s"valUniqueExternally: username $uniqueUsername already exists externally")
              List(FieldError(this, Text(msg))) // provide the error message
            } else {
              logger.info(s"valUniqueExternally: username $uniqueUsername is unique (returned different: $returnedUsername)")
              Nil // All good. Allow username creation
            }
          case ParamFailure(message,_,_,APIFailure(errorMessage, errorCode)) if errorMessage.contains("NO DATA") => // Cannot get the username via connector
            logger.info(s"valUniqueExternally: checkExternalUserExists returned NO DATA for username: $uniqueUsername - allowing creation")
            Nil // All good. Allow username creation
          case Failure(failureMsg, exception, chain) =>
            logger.warn(s"valUniqueExternally: checkExternalUserExists failed for username: $uniqueUsername, message: $failureMsg, exception: ${exception.map(_.getMessage)}, chain: $chain")
            List(FieldError(this, Text(ErrorMessages.ExternalUserCheckFailed)))
          case Empty =>
            logger.warn(s"valUniqueExternally: checkExternalUserExists returned Empty for username: $uniqueUsername")
            List(FieldError(this, Text(ErrorMessages.ExternalUserCheckFailed)))
          case _ => // Any other case we provide error message
            logger.warn(s"valUniqueExternally: checkExternalUserExists returned unexpected result for username: $uniqueUsername")
            List(FieldError(this, Text(ErrorMessages.ExternalUserCheckFailed)))
        }
      } else {
        Nil // All good. Allow username creation
      }
    }
      
      
  }

  override lazy val password = new MyPasswordNew
  
  lazy val signupPasswordRepeatText = getWebUiPropsValue("webui_signup_body_password_repeat_text", "repeat")

  class MyPasswordNew extends MappedPassword(this) {
    lazy val preFilledPassword = if (APIUtil.getPropsAsBoolValue("allow_pre_filled_password", true)) {get.toString} else ""

    override def displayName = fieldOwner.passwordDisplayName
    
    private var passwordValue = ""
    private var invalidPw = false
    private var invalidMsg = ""

    // TODO Remove double negative and abreviation.
    // TODO  “invalidPw” = false -> “strongPassword = true” etc.
    override def setFromAny(f: Any): String = {
      def checkPassword() = {
        def isPasswordEmpty() = {
          if (passwordValue.isEmpty())
            true
          else {
            passwordValue match {
              case "*" | null | MappedPassword.blankPw =>
                true
              case _ =>
                false
            }
          }
        }
        isPasswordEmpty() match {
          case true =>
            invalidPw = true
            invalidMsg = Helper.i18n("please.enter.your.password")
          case false =>
            if (fullPasswordValidation(passwordValue))
              invalidPw = false
            else {
              invalidPw = true
              invalidMsg = ErrorMessages.InvalidStrongPasswordFormat.split(':')(1).trim
            }
        }
      }
      f match {
        case a: Array[String] if (a.length == 2 && a(0) == a(1)) => {
          passwordValue = a(0).toString
          checkPassword()
          this.set(a(0))
        }
        case l: List[_] if (l.length == 2 && l.head.asInstanceOf[String] == l(1).asInstanceOf[String]) => {
          passwordValue = l(0).asInstanceOf[String]
          checkPassword()
          this.set(l.head.asInstanceOf[String])
        }
        case _ => {
          invalidPw = true
          invalidMsg = Helper.i18n("passwords.do.not.match")
        }
      }
      get
    }
    
    override def validate: List[FieldError] = {
      if (!invalidPw && password.get != "*") super.validate
      else if (invalidPw) List(FieldError(this, Text(invalidMsg))) ++ super.validate
      else List(FieldError(this, Text(Helper.i18n("please.enter.your.password")))) ++ super.validate
    }
    
  }

  /**
   * The provider field for the User.
   */
  lazy val provider: userProvider = new userProvider()
  class userProvider extends MappedString(this, 100) {
    override def displayName = "provider"
    override val fieldId = Some(Text("txtProvider"))
    override def validations = validUri(this) _ :: super.validations
    override def defaultValue: String = Constant.localIdentityProvider
  }


  def getProvider() = {
    if(provider.get == null || provider.get == "") {
      Constant.localIdentityProvider
    } else {
      provider.get
    }
  }

  def createUnsavedResourceUser() : ResourceUser = {
    val user = Users.users.vend.createUnsavedResourceUser(getProvider(), Some(username.get), Some(username.get), Some(email.get), None).openOrThrowException(attemptedToOpenAnEmptyBox)
    user
  }

  def getResourceUsersByEmail(userEmail: String) : List[ResourceUser] = {
    Users.users.vend.getUserByEmail(userEmail) match {
      case Full(userList) => userList
      case _ => List()
    }
  }

  def getResourceUserByProviderAndUsername(provider: String, username: String) : Box[User] = {
    Users.users.vend.getUserByProviderAndUsername(provider, username)
  }

  override def save(): Boolean = {
    if(! (user.defined_?)){
      logger.info("user reference is null. We will create a ResourceUser")
      val resourceUser = createUnsavedResourceUser()
      val savedUser = Users.users.vend.saveResourceUser(resourceUser)
      user(savedUser)   //is this saving resourceUser into a user field?
    }
    else {
      logger.info("user reference is not null. Trying to update the ResourceUser")
      Users.users.vend.getResourceUserByResourceUserId(user.get).map{ u =>{
          logger.info("API User found ")
          u.name_(username.get)
          .email(email.get)
          .providerId(username.get)
          .save
        }
      }
    }
    super.save
  }

  override def delete_!(): Boolean = {
    user.obj.map(u => Users.users.vend.deleteResourceUser(u.id.get))
    super.delete_!
  }

  // Regex to validate an email address as per W3C recommendations: https://www.w3.org/TR/html5/forms.html#valid-e-mail-address
  private val emailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def isEmailValid(e: String): Boolean = e match{
    case null                                           => false
    case e if e.trim.isEmpty                            => false
    case e if emailRegex.findFirstMatchIn(e).isDefined  => true
    case _                                              => false
  }

  // Override the validate method of MappedEmail class
  // There's no way to override the default emailPattern from MappedEmail object
  override lazy val email = new MyEmail(this, 48) {
    override def validations = super.validations
    override def dbIndexed_? = false
    override def validate = i_is_! match {
      case null                  => List(FieldError(this, Text(Helper.i18n("Please.enter.your.email"))))
      case e if e.trim.isEmpty   => List(FieldError(this, Text(Helper.i18n("Please.enter.your.email"))))
      case e if (!isEmailValid(e))  => List(FieldError(this, Text(Helper.i18n("invalid.email.address"))))
      case _                     => Nil
    }
  }
}

/**
 * The singleton that has methods for accessing the database
 */
object AuthUser extends AuthUser with MetaMegaProtoUser[AuthUser]{
import net.liftweb.util.Helpers._

  /**Marking the locked state to show different error message */
  val usernameLockedStateCode = Long.MaxValue
  /**Marking the email not validated state to show different error message */
  val userEmailNotValidatedStateCode = Long.MaxValue - 1
  /**Marking the auth-rate-limit-exceeded state to render a 429 instead of a 401 */
  val rateLimitExceededStateCode = Long.MaxValue - 2

  val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")
  val starConnectorSupportedTypes = APIUtil.getPropsValue("starConnector_supported_types","")

  override def dbIndexes: List[BaseIndex[AuthUser]] = UniqueIndex(username, provider) ::super.dbIndexes
  
  override def emailFrom = Constant.mailUsersUserinfoSenderAddress

  // screenWrap removed - API-only mode, no portal pages
  override def screenWrap = Empty
  // define the order fields will appear in forms and output
  override def fieldOrder = List(id, firstName, lastName, email, username, password, provider)
  override def signupFields = List(firstName, lastName, email, username, password)

  // To force validation of email addresses set this to false (default as of 29 June 2021)
  override def skipEmailValidation = APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", false)

  // Legacy Lift login UI - no longer used (API-only mode)
  // Login is handled via OIDC/DirectLogin APIs, not HTML forms
  override def loginXhtml = <div/>
  
  // Legacy Lift login method - no longer used (no frontend pages)
  // Authentication is now handled via DirectLogin API endpoints
  override def login: NodeSeq = <div/>


  // Update ResourceUser.LastUsedLocale only once per session in 60 seconds
  def updateComputedLocale(sessionId: String, computedLocale: String): Boolean = {
    /**
     * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
     * is just a temporary value field with UUID values in order to prevent any ambiguity.
     * The real value will be assigned by Macro during compile time at this line of a code:
     * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
     */
    import scala.concurrent.duration._
    val ttl: Duration = FiniteDuration(60, "second")
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(ttl) {
        logger.debug(s"AuthUser.updateComputedLocale(sessionId = $sessionId, computedLocale = $computedLocale)")
        getCurrentUser.map(_.userPrimaryKey.value) match {
          case Full(id) =>
            Users.users.vend.getResourceUserByResourceUserId(id).map {
              u =>
                u.LastUsedLocale(computedLocale).save
                logger.debug(s"ResourceUser.LastUsedLocale is saved for the resource user id: $id")
            }.isDefined
          case _ => true// There is no current user
        }
      }
    }
  }
  
  
  /**
    * Find current ResourceUser from the server.
    * This method has no parameters, it depends on different login types:
    *  AuthUser:  AuthUser.currentUser
    *  DirectLogin: DirectLogin.getUser
    * to get the current Resourceuser .
    *
    */
  def getCurrentUser: Box[User] = {
    val authorization: Box[String] = Empty
    val directLogin: Box[String] = Empty
    for {
      resourceUser <- if (AuthUser.currentUser.isDefined){
        //AuthUser.currentUser.get.user.foreign // this will be issue when the resource user is in remote side {
        val user = AuthUser.currentUser.openOrThrowException(ErrorMessages.attemptedToOpenAnEmptyBox)
        // In case that the provider is empty field we default to "local_identity_provider" or "hostname"
        val provider = 
          if(user.provider.get == null || user.provider.get.isEmpty) 
            Constant.localIdentityProvider 
          else 
            user.provider.get
        Users.users.vend.getUserByProviderAndUsername(provider, user.username.get)
      } else if (directLogin.isDefined) // Direct Login
        DirectLogin.getUser
      else if (hasDirectLoginHeader(authorization)) // Direct Login Deprecated
        DirectLogin.getUser
      else if (hasGatewayHeader(authorization)){
        GatewayLogin.getUser
      } else {
        logger.debug(ErrorMessages.CurrentUserNotFoundException)
        Failure(ErrorMessages.CurrentUserNotFoundException)
      }
    } yield {
      resourceUser
    }
  }
  /**
   * get current user.
    * Note: 1. it will call getCurrentUser method, 
    *          
   */
  def getCurrentUserUsername: String = {
     getCurrentUser match {
       case Full(user) if user.provider.contains("google")  && !user.emailAddress.isEmpty => user.emailAddress
       case Full(user) if user.provider.contains("yahoo")  && !user.emailAddress.isEmpty => user.emailAddress
       case Full(user) if user.provider.contains("microsoft")  && !user.emailAddress.isEmpty => user.emailAddress
       case Full(user) => user.name
       case _ => "" //TODO need more error handling for different user cases
     }
  }
  
  def getIDTokenOfCurrentUser(): String = {
    if(APIUtil.getPropsAsBoolValue("openid_connect.show_tokens", false)) {
      AuthUser.currentUser match {
        case Full(authUser) =>
          TokensOpenIDConnect.tokens.vend.getOpenIDConnectTokenByAuthUser(authUser.id.get).map(_.idToken).getOrElse("")
        case _ => ""
      }
    } else { 
      "This information is not allowed at this instance."
    }
  }  
  def getAccessTokenOfCurrentUser(): String = {
    if(APIUtil.getPropsAsBoolValue("openid_connect.show_tokens", false)) {
      AuthUser.currentUser match {
        case Full(authUser) =>
          TokensOpenIDConnect.tokens.vend.getOpenIDConnectTokenByAuthUser(authUser.id.get).map(_.accessToken).getOrElse("")
        case _ => ""
      }
    } else { 
      "This information is not allowed at this instance."
    }
  }
  
  /**
    *  get current user.userId
    *  Note: 1.resourceuser has two ids: id(Long) and userid_(String),
    *        
    * @return return userid_(String).
    */
  
  def getCurrentResourceUserUserId: String = {
    getCurrentUser match{
      case Full(user) => user.userId
      case _ => "" //TODO need more error handling for different user cases
    }
  }

  /**
    * The string that's generated when the user name is not found.  By
    * default: S.?("email.address.not.found")
    * The function is overridden in order to prevent leak of information at password reset page if username / email exists or do not exist.
    * I.e. we want to prevent case in which an anonymous user can get information from the message does some username/email exist or no in our system.
    */
  override def userNameNotFoundString: String = "Thank you. If we found a matching user, password reset instructions have been sent."


  // sendPasswordReset removed - legacy Lift method, replaced by API endpoint /obp/v6.0.0/users/password-reset-url
  override def sendPasswordReset(name: String) = {
    // No-op: Password reset now handled via RESTful API endpoints
  }

  // lostPasswordXhtml simplified - API-only mode, no portal pages
  // Password reset is handled via API endpoints
  override def lostPasswordXhtml = <div/>

  // lostPassword simplified - API-only mode, no portal pages
  override def lostPassword = NodeSeq.Empty

  //override def def passwordResetMailBody(user: TheUserType, resetLink: String): Elem = { }

  /**
   * Overridden to use the hostname set in the props file
   */
  override def sendValidationEmail(user: TheUserType) {
    APIUtil.getPropsValue("portal_external_url") match {
      case Full(portalUrl) =>
        // Create a JWT token with the uniqueId as subject and configurable expiry
        val expiryMinutes = APIUtil.getPropsAsIntValue("email_validation_token_expiry_minutes", 1440)
        val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
          .subject(user.getUniqueId())
          .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
          .issueTime(new java.util.Date())
          .build()
        val jwtToken = CertificateUtil.jwtWithHmacProtection(claimsSet)
        val validationLink = portalUrl+"/user-validation?token="+java.net.URLEncoder.encode(jwtToken, "UTF-8")
        val email: String = user.getEmail
        val textContent = Some(s"Welcome! Please validate your account by clicking the following link: $validationLink")
        val htmlContent = Some(s"<p>Welcome! Please validate your account by clicking the following link:</p><p><a href='$validationLink'>$validationLink</a></p>")
        val subjectContent = "Sign up confirmation"
        val emailContent = EmailContent(
          from = emailFrom,
          to = List(user.getEmail),
          bcc = bccEmail.toList,
          subject = subjectContent,
          textContent = textContent,
          htmlContent = htmlContent
        )
        sendHtmlEmail(emailContent) match {
          case Full(messageId) =>
            logger.debug(s"Validation email sent successfully with Message-ID: $messageId")
          case Empty =>
            logger.error("Failed to send validation email")
        }
      case _ =>
        logger.error("portal_external_url is not set in props. Cannot send validation email.")
    }
  }

   def grantDefaultEntitlementsToAuthUser(user: TheUserType) = {
     tryo{getResourceUserByProviderAndUsername(user.getProvider(), user.username.get).head.userId} match {
       case Full(userId)=>APIUtil.grantDefaultEntitlementsToNewUser(userId)
       case _ => logger.error("Can not getResourceUserByUsername here, so it breaks the grantDefaultEntitlementsToNewUser process.")
     }
   }

  override def validateUser(id: String): NodeSeq = {
    // Extract uniqueId from JWT token: verify signature and expiry
    val uniqueIdBox: Box[String] = tryo {
      val signedJWT = com.nimbusds.jwt.SignedJWT.parse(id)
      val expiration = signedJWT.getJWTClaimsSet.getExpirationTime
      if (expiration == null || expiration.before(new java.util.Date())) {
        throw new Exception("Token has expired")
      }
      if (!CertificateUtil.verifywtWithHmacProtection(id)) {
        throw new Exception("Invalid token signature")
      }
      signedJWT.getJWTClaimsSet.getSubject
    }

    val userBox = uniqueIdBox.flatMap(findUserByUniqueId)

    userBox match {
      case Full(user) if !user.validated_? =>
        user.setValidated(true).resetUniqueId().save
        grantDefaultEntitlementsToAuthUser(user)
      case _ =>
        logger.warn("validateUser: invalid or expired token")
    }
    NodeSeq.Empty
  }

  override def actionsAfterSignup(theUser: TheUserType, func: () => Nothing): Nothing = {
    theUser.setValidated(skipEmailValidation).resetUniqueId()
    theUser.save
    val privacyPolicyValue: String = getWebUiPropsValue("webui_privacy_policy", "")
    val termsAndConditionsValue: String = getWebUiPropsValue("webui_terms_and_conditions", "")
    // User Agreement table
    UserAgreementProvider.userAgreementProvider.vend.createUserAgreement(
      theUser.user.foreign.map(_.userId).getOrElse(""), "privacy_conditions", privacyPolicyValue)
    UserAgreementProvider.userAgreementProvider.vend.createUserAgreement(
      theUser.user.foreign.map(_.userId).getOrElse(""), "terms_and_conditions", termsAndConditionsValue)
    if (!skipEmailValidation) {
      sendValidationEmail(theUser)
      func()
    } else {
      grantDefaultEntitlementsToAuthUser(theUser)
      logUserIn(theUser, () => func())
    }
  }
  // agreeTermsDiv simplified - API-only mode, no portal pages
  def agreeTermsDiv = NodeSeq.Empty

  // legalNoticeDiv simplified - API-only mode, no portal pages
  def legalNoticeDiv = NodeSeq.Empty

  // agreePrivacyPolicy simplified - API-only mode, no portal pages
  def agreePrivacyPolicy = NodeSeq.Empty

  // enableDisableSignUpButton simplified - API-only mode, no portal pages
  def enableDisableSignUpButton = NodeSeq.Empty

  def signupFormTitle = getWebUiPropsValue("webui_signup_form_title_text", "sign.up")

  // signupXhtml simplified - API-only mode, no portal pages
  // Signup is handled via API endpoints, not HTML forms
  override def signupXhtml (user:AuthUser) = <div/>


  // localForm simplified - API-only mode, no portal pages
  override def localForm(user: TheUserType, ignorePassword: Boolean, fields: List[FieldPointerType]): NodeSeq = NodeSeq.Empty





  /**
    * Centralized authentication method that validates user credentials and returns the resource user ID.
    * 
    * This method implements a dual-path authentication strategy:
    * - **Local Provider Path**: Validates credentials against the local OBP database
    * - **External Provider Path**: Delegates validation to external authentication systems via connector
    * 
    * == Authentication Flow ==
    * 
    * === Local Provider Path (provider == localIdentityProvider or isEmpty) ===
    * 1. **User Lookup**: Search for user in local database by username and provider
    *    - If not found → increment bad login attempts → return Empty
   *    
    * 2. **Email Validation Check**: Verify user's email is validated
    *    - If not validated → return `userEmailNotValidatedStateCode`
   *    
    * 3. **Account Lock Check**: Check if user account is locked due to failed attempts
    *    - If locked → return `usernameLockedStateCode` (no attempt increment)
   *    
    * 4. **Password Validation**: Test provided password against stored hash
    *    - If correct → reset bad login attempts → return user ID
    *    - If incorrect → increment bad login attempts → return Empty
    * 
    * === External Provider Path (provider != localIdentityProvider) ===
    * 1. **Connector Authentication Check**: Verify `connector.user.authentication` property is enabled
    *    - If disabled → increment bad login attempts → return Empty
   *    
    * 2. **Account Lock Check**: Check if external user account is locked
    *    - If locked → return `usernameLockedStateCode` (no attempt increment)
   *    
    * 3. **External Validation**: Call `checkExternalUserViaConnector` to validate via connector
    *    - If successful → reset bad login attempts → return user ID
    *    - If failed → increment bad login attempts → return Empty
    * 
    * == Security Features ==
    * - **Login Attempt Tracking**: Failed authentications increment bad login attempt counter
    * - **Account Locking**: Users are locked after exceeding maximum failed attempts
    * - **Attempt Reset**: Successful authentication resets the bad login attempt counter
    * - **Email Validation**: Local users must have validated email addresses
    * - **Locked State Protection**: Locked accounts do not increment attempt counter further
    * 
    * == Return Values ==
    * - `Full(userId)`: Authentication successful, returns the resource user ID
    * - `Full(userEmailNotValidatedStateCode)`: User exists but email not validated (local only)
    * - `Full(usernameLockedStateCode)`: User account is locked due to failed attempts
    * - `Empty`: Authentication failed (user not found, wrong password, or connector failure)
    * 
    * == Special State Codes ==
    * - `userEmailNotValidatedStateCode`: Indicates email validation required
    * - `usernameLockedStateCode`: Indicates account is locked
    * 
    * == Parameter Validation ==
    * - Username and password must not be null or empty
    * - Provider is normalized: null or empty treated as localIdentityProvider
    * 
    * @param username The username to authenticate (must not be null or empty)
    * @param password The password to validate (must not be null or empty)
    * @param provider The authentication provider (defaults to localIdentityProvider)
    *                 - Use `Constant.localIdentityProvider` for local database authentication
    *                 - Use external provider name (e.g., "ldap", "oauth") for connector-based authentication
    *                 - null or empty values are normalized to localIdentityProvider
    * @return Box[Long] containing:
    *         - User ID on successful authentication
    *         - Special state code for email validation or account lock
    *         - Empty on authentication failure or invalid parameters
    * 
    * @see [[findAuthUserByUsernameAndProvider]] for local user lookup
    * @see [[checkExternalUserViaConnector]] for external authentication
    * @see [[LoginAttempt.userIsLocked]] for account lock checking
    * @see [[LoginAttempt.incrementBadLoginAttempts]] for failed attempt tracking
    * @see [[LoginAttempt.resetBadLoginAttempts]] for attempt counter reset
    */
  def getResourceUserId(username: String, password: String, provider: String): Box[Long] = {
    // ========================================================================
    // PARAMETER VALIDATION
    // ========================================================================
    if (username == null || username.trim.isEmpty) {
      logger.warn(s"getResourceUserId: invalid username (null or empty)")
      return Empty
    }
    if (password == null || password.isEmpty) {
      logger.warn(s"getResourceUserId: invalid password (null or empty)")
      return Empty
    }
    
    // Normalize provider: treat null or empty as localIdentityProvider
    val normalizedProvider = if (provider == null || provider.isEmpty) {
      Constant.localIdentityProvider
    } else {
      provider
    }
    
    logger.info(s"getResourceUserId says: starting for username: $username, provider: $normalizedProvider")

    // ========================================================================
    // PRE-CREDENTIAL RATE LIMIT
    // Disabled by default; controlled via auth.rate_limit.* props.
    // Returns sentinel for 429 translation; never blocks auth on Redis outage.
    // ========================================================================
    AuthRateLimiter.check(getRemoteIpAddress(), normalizedProvider, username) match {
      case Left(_)  => return Full(rateLimitExceededStateCode)
      case Right(_) => // continue
    }

    // ========================================================================
    // ROUTE DECISION: Local or External Provider?
    // ========================================================================
    if (normalizedProvider == Constant.localIdentityProvider) {
      // ========================================================================
      // LOCAL PROVIDER PATH: Validate against local database
      // ========================================================================
      logger.info(s"getResourceUserId says: using local provider authentication for username: $username")
      
      findAuthUserByUsernameAndProvider(username, Constant.localIdentityProvider) match {
        case Full(user) if !user.validated_? =>
          // User exists but email not validated
          logger.info(s"getResourceUserId says: user not validated, username: $username, provider: $normalizedProvider")
          Full(userEmailNotValidatedStateCode)
        
        case Full(user) if LoginAttempt.userIsLocked(Constant.localIdentityProvider, username) =>
          // User is locked - do NOT increment attempts (already locked)
          logger.info(s"getResourceUserId says: user is locked, username: $username, provider: $normalizedProvider")
          Full(usernameLockedStateCode)
        
        case Full(user) if user.testPassword(Full(password)) =>
          // Password correct - extract user ID safely
          logger.info(s"getResourceUserId says: password correct, username: $username, provider: $normalizedProvider")
          LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, username)
          user.user.obj match {
            case Full(resourceUser) =>
              Full(resourceUser.id.get)
            case _ =>
              logger.error(s"getResourceUserId: user.user foreign key not set for username: $username")
              Empty
          }
        
        case Full(user) =>
          // Password incorrect
          logger.info(s"getResourceUserId says: wrong password, username: $username, provider: $normalizedProvider")
          LoginAttempt.incrementBadLoginAttempts(Constant.localIdentityProvider, username)
          Empty
        
        case _ =>
          // User not found in local database
          logger.info(s"getResourceUserId says: user not found, username: $username, provider: $normalizedProvider")
          LoginAttempt.incrementBadLoginAttempts(Constant.localIdentityProvider, username)
          Empty
      }
      
    } else {
      // ========================================================================
      // EXTERNAL PROVIDER PATH: Validate via connector
      // ========================================================================
      logger.info(s"getResourceUserId says: using external provider authentication for username: $username, provider: $normalizedProvider")
      
      // Check if connector authentication is enabled
      // DEBUG: Log the actual property value being read
      val connectorAuthEnabled = APIUtil.getPropsAsBoolValue("connector.user.authentication", false)
      logger.info(s"getResourceUserId says: READ connector.user.authentication = $connectorAuthEnabled")
      
      if (!connectorAuthEnabled) {
        logger.info(s"getResourceUserId says: connector.user.authentication is false, username: $username, provider: $normalizedProvider")
        LoginAttempt.incrementBadLoginAttempts(normalizedProvider, username)
        Empty
      }
      // Check if user is locked - do NOT increment attempts (already locked)
      else if (LoginAttempt.userIsLocked(normalizedProvider, username)) {
        logger.info(s"getResourceUserId says: external user is locked, username: $username, provider: $normalizedProvider")
        Full(usernameLockedStateCode)
      }
      // Validate via connector
      else {
        logger.info(s"getResourceUserId says: calling checkExternalUserViaConnector for username: $username, provider: $normalizedProvider")
        
        // Call connector validation and safely extract user ID
        val connectorResult = checkExternalUserViaConnector(username, password).flatMap { authUser =>
          authUser.user.obj match {
            case Full(resourceUser) =>
              Full(resourceUser.id.get)
            case _ =>
              logger.error(s"getResourceUserId: external user.user foreign key not set for username: $username")
              Empty
          }
        }
        
        connectorResult match {
          case Full(userId) =>
            logger.info(s"getResourceUserId says: external connector auth succeeded, username: $username, provider: $normalizedProvider")
            LoginAttempt.resetBadLoginAttempts(normalizedProvider, username)
            Full(userId)
          
          case _ =>
            logger.info(s"getResourceUserId says: external connector auth failed, username: $username, provider: $normalizedProvider")
            LoginAttempt.incrementBadLoginAttempts(normalizedProvider, username)
            Empty
        }
      }
    }
  }

  /**
    * Validates external user credentials via connector and creates/retrieves local AuthUser.
    * 
    * This method is the primary entry point for external authentication. It performs the following:
    * 
    * 1. **Connector Validation**: Calls the connector's `checkExternalUserCredentials` to validate
    *    the username and password against the external identity provider or Core Banking System.
    * 
    * 2. **Local User Lookup**: If connector validation succeeds, checks if the user already exists
    *    in the local OBP database (AuthUser table) using `findAuthUserByUsernameAndProvider`.
    * 
    * 3. **Auto-Provisioning**: If the user doesn't exist locally, automatically creates a new AuthUser
    *    record with data from the connector response (email, name, provider, validation status).
    *    This also triggers creation of the associated ResourceUser via the `saveMe()` method.
    * 
    * 4. **User Auth Context**: If the connector returns user auth contexts (e.g., customer numbers),
    *    these are stored/updated in the UserAuthContext table for both new and existing users.
    * 
    * == Authentication Flow ==
    * ```
    * checkExternalUserViaConnector(username, password)
    *   │
    *   ├─> Connector.checkExternalUserCredentials(username, password)
    *   │   └─> Returns InboundExternalUser with: sub, iss, email, name, userAuthContexts
    *   │
    *   ├─> findAuthUserByUsernameAndProvider(sub, iss)
    *   │   ├─> User exists and validated? → Return existing user
    *   │   └─> User not found? → Create new AuthUser with connector data
    *   │
    *   └─> Update/Create UserAuthContexts if provided
    * ```
    * 
    * == Return Values ==
    * - `Full(AuthUser)`: Authentication successful, returns the AuthUser (existing or newly created)
    * - `Empty`: Connector validation failed (invalid credentials or connector error)
    * - `Failure`: Connector returned an error with details
    * 
    * == Side Effects ==
    * - May create new AuthUser record in database
    * - May create new ResourceUser record (via AuthUser.saveMe())
    * - May create/update UserAuthContext records
    * 
    * == Usage ==
    * This method is called by:
    * - `getResourceUserId()` for external provider authentication
    * - DirectLogin authentication flow for external users
    * 
    * @param username The username to authenticate against the external system
    * @param password The password to validate via the connector
    * @return Box[AuthUser] containing the authenticated user or Empty/Failure on error
    * 
    * @see [[getResourceUserId]] for the main authentication entry point
    * @see [[Connector.checkExternalUserCredentials]] for connector validation
    * @see [[findAuthUserByUsernameAndProvider]] for local user lookup
    */
  def checkExternalUserViaConnector(username: String, password: String):Box[AuthUser] = {
    logger.info(s"checkExternalUserViaConnector: calling checkExternalUserCredentials for username: $username")
    val connectorResult = Connector.connector.vend.checkExternalUserCredentials(username, password, None)
    logger.info(s"checkExternalUserViaConnector: checkExternalUserCredentials returned: ${connectorResult.getClass.getSimpleName}")
    connectorResult match {
      case Full(InboundExternalUser(aud, exp, iat, iss, sub, azp, email, emailVerified, name, userAuthContexts)) =>
        logger.info(s"checkExternalUserViaConnector: successful response for sub: $sub, iss: $iss, email: $email")
        val user = findAuthUserByUsernameAndProvider(sub, iss) match { // Check if the external user is already created locally
          case Full(user) if user.validated_? => // Return existing user if found
            logger.debug("external user already exists locally, using that one")
            userAuthContexts match {
              case Some(authContexts) => // Write user auth context to the database
                UserAuthContextProvider.userAuthContextProvider.vend.createOrUpdateUserAuthContexts(user.userIdAsString, authContexts)
              case None => // Do nothing
            }
            user
          case _ => // If not found, create a new user
            // Create AuthUser using fetched data from connector
            // assuming that user's email is always validated
            logger.debug("external user "+ sub + " does not exist locally, creating one")
            AuthUser.create
              .firstName(name.getOrElse(sub))
              .email(email.getOrElse(""))
              .username(sub)
              // No need to store password, so store dummy string instead
              .password(generateUUID())
              // TODO add field stating external password check only.
              .provider(iss)
              .validated(emailVerified.exists(_.equalsIgnoreCase("true")))
              .saveMe() //NOTE, we will create the resourceUser in the `saveMe()` method.
        }
        userAuthContexts match {
          case Some(authContexts) => { // Write user auth context to the database
              // get resourceUserId from AuthUser.
              val resourceUserId = user.user.foreign.map(_.userId).getOrElse("")
              // we try to catch this exception, the createOrUpdateUserAuthContexts can not break the login process.
              tryo {UserAuthContextProvider.userAuthContextProvider.vend.createOrUpdateUserAuthContexts(resourceUserId, authContexts)}
                .openOr(logger.error(s"${resourceUserId} checkExternalUserViaConnector.createOrUpdateUserAuthContexts throw exception! "))
          }
          case None => // Do nothing
        }
        Full(user)
      case Failure(msg, exception, chain) =>
        logger.warn(s"checkExternalUserViaConnector: checkExternalUserCredentials failed for username: $username, message: $msg, exception: ${exception.map(_.getMessage)}, chain: $chain")
        Empty
      case Empty =>
        logger.warn(s"checkExternalUserViaConnector: checkExternalUserCredentials returned Empty for username: $username")
        Empty
      case _ =>
        logger.warn(s"checkExternalUserViaConnector: checkExternalUserCredentials returned unexpected result for username: $username")
        Empty
    }
  }



def restoreSomeSessions(): Unit = {
  activeBrand()
}

  override protected def capturePreLoginState(): () => Unit = () => {restoreSomeSessions}


  override protected def loginMenuLocParams = Nil

  /**
   * A Space is an alias for the OBP Bank. Each Bank / Space can contain many Dynamic Endpoints. If a User belongs to a Space,
   * the User can use those endpoints but not modify them. If a User creates a Bank (aka Space) the user can create
   * and modify Dynamic Endpoints and other objects in that Bank / Space.
   *
   * @return
   */
  def mySpaces(user: AuthUser): List[BankId] = {
    //1st: first check the user is validated
    if (user.validated_?) {
      //userEmail = robert.uk.29@example.com
      // 2st get the email domain - `example.com`
      val emailDomain = StringUtils.substringAfterLast(user.email.get, "@")

      //3 return the bankIds
      emailDomainToSpaceMappings.collectFirst {
        case EmailDomainToSpaceMapping(`emailDomain`, ids) => ids.map(BankId(_));
      } getOrElse Nil

    } else {
      Nil
    }
  }

  def grantEntitlementsToUseDynamicEndpointsInSpaces(user: AuthUser) = {
    if(emailDomainToSpaceMappings.nonEmpty) {
      val createdByProcess = "grantEntitlementsToUseDynamicEndpointsInSpaces"
      val userId = user.user.obj.map(_.userId).getOrElse("")

      // user's already auto granted entitlements.
      val entitlementsGrantedByThisProcess = Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
        .map(_.filter(role => role.createdByProcess == createdByProcess))
        .getOrElse(Nil)

      def alreadyHasEntitlement(role:ApiRole, bankId: String): Boolean =
        entitlementsGrantedByThisProcess.exists(entitlement => entitlement.roleName == role.toString() && entitlement.bankId == bankId)

      //call mySpaces --> get BankIds --> listOfRolesToUseAllDynamicEndpointsAOneBank (at each bank)--> Grant roles (for each role)
      val allCurrentDynamicRoleToBankIdPairs: List[(ApiRole, String)] = for {
        BankId(bankId) <- mySpaces(user: AuthUser)
        role <- DynamicEndpointHelper.listOfRolesToUseAllDynamicEndpointsAOneBank(Some(bankId))
      } yield {
        if (!alreadyHasEntitlement(role, bankId)) {
          Entitlement.entitlement.vend.addEntitlement(bankId, userId, role.toString, createdByProcess)
        }

        role -> bankId
      }

      // if user's auto granted entitlement invalid, delete it.
      // invalid happens when some dynamic endpoints are removed, so the entitlements linked to the deleted dynamic endpoints are invalid.
      for {
        grantedEntitlement <- entitlementsGrantedByThisProcess
        grantedEntitlementRoleName = grantedEntitlement.roleName
        grantedEntitlementBankId = grantedEntitlement.bankId
      } {
        val isInValidEntitlement = !allCurrentDynamicRoleToBankIdPairs.exists { roleToBankIdPair =>
          val(role, roleBankId) = roleToBankIdPair
          role.toString() == grantedEntitlementRoleName && roleBankId == grantedEntitlementBankId
        }

        if(isInValidEntitlement) {
          Entitlement.entitlement.vend.deleteEntitlement(Full(grantedEntitlement))
        }
      }
    }
  }

  def grantEmailDomainEntitlementsToUser(user: AuthUser) = {
    if(emailDomainToEntitlementMappings.nonEmpty){
      val createdByProcess = "grantEmailDomainEntitlementsToUser"
      val userId = user.user.obj.map(_.userId).getOrElse("")

      // user's already auto granted entitlements.
      val entitlementsGrantedByThisProcess = Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
        .map(_.filter(role => role.createdByProcess == createdByProcess))
        .getOrElse(Nil)

      def alreadyHasEntitlement(bankId: String, roleName:String): Boolean =
        entitlementsGrantedByThisProcess.exists(entitlement => entitlement.roleName == roleName && entitlement.bankId == bankId)

      val allEntitlementsFromCurrentProps: List[(String, String)] = for{
        emailDomainToEntitlementMapping <- emailDomainToEntitlementMappings
        domain = emailDomainToEntitlementMapping.domain
        entitlement <- emailDomainToEntitlementMapping.entitlements if StringUtils.substringAfterLast(user.email.get, "@") == domain
        roleName = entitlement.role_name
        roleBankId = entitlement.bank_id
      } yield {
        if (!alreadyHasEntitlement(roleBankId, roleName)) {
          Entitlement.entitlement.vend.addEntitlement(roleBankId, userId, roleName, createdByProcess)
        }
        roleName -> roleBankId
      }

      // if user's auto granted entitlement invalid, delete it.
      // invalid happens when some dynamic endpoints are removed, so the entitlements linked to the deleted dynamic endpoints are invalid.
      for {
        grantedEntitlement <- entitlementsGrantedByThisProcess
        grantedEntitlementRoleName = grantedEntitlement.roleName
        grantedEntitlementBankId = grantedEntitlement.bankId
      } {
        val isInValidEntitlement = !allEntitlementsFromCurrentProps.exists { roleNameToBankIdPair =>
          val(roleName, roleBankId) = roleNameToBankIdPair
          roleName == grantedEntitlementRoleName && roleBankId == grantedEntitlementBankId
        }

        if(isInValidEntitlement) {
          Entitlement.entitlement.vend.deleteEntitlement(Full(grantedEntitlement))
        }
      }
    }
  }

  /**
   * This method is used for onboarding bank customer to OBP.
   *  1st: we will get all the accountsHeld from CBS side.
   *  2rd: we will create the account Holder, view and account accesses.
   */
  def refreshUser(user: User, callContext: Option[CallContext]) = {
    for{
      (accountsHeld, _) <- Connector.connector.vend.getBankAccountsForUser(user.provider, user.name,callContext) map {
        connectorEmptyResponse(_, callContext)
      }
      _ = logger.debug(s"--> for user($user): AuthUser.refreshUser.accountsHeld : ${accountsHeld}")

      success = refreshViewsAccountAccessAndHolders(user, accountsHeld, callContext)

    }yield {
      success
    }
  }

  @deprecated("This return Box, not a future, try to use @refreshUser instead. ","08-09-2023")
  def refreshUserLegacy(user: User, callContext: Option[CallContext]) = {
    for{
      (accountsHeld, _) <- Connector.connector.vend.getBankAccountsForUserLegacy(user.provider, user.name, callContext)

      _ = logger.debug(s"--> for user($user): AuthUser.refreshUserLegacy.accountsHeld : ${accountsHeld}")

      success = refreshViewsAccountAccessAndHolders(user, accountsHeld, callContext)

    }yield {
      success
    }
  }

  /**
    * This is a helper method
    * create/update/delete the views, accountAccess, accountHolders for OBP get accounts from CBS side.
    * This method can only be used by the original user(account holder).
   *  InboundAccount return many fields, but in this method, we only need bankId, accountId and viewId so far.
    */
    def refreshViewsAccountAccessAndHolders(user: User, accountsHeld: List[InboundAccount], callContext: Option[CallContext])  = {
      if(user.isOriginalUser){
        //first, we compare the accounts in obp  and the accounts in cbs,
        val (_, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccess(user)
        val obpAccountAccessBankAccountIds = privateAccountAccess.map(accountAccess =>BankIdAccountId(BankId(accountAccess.bank_id.get), AccountId(accountAccess.account_id.get))).toSet

        // This will return all account held for the user, no mater what the source is.
        val userOwnBankAccountIds = AccountHolders.accountHolders.vend.getAccountsHeldByUser(user)

        //The accounts from AccountAccess may contains other users' account info, so here we filter the accounts By account holder, only show the user's own accounts
        val obpBankAccountIds = obpAccountAccessBankAccountIds.filter(bankAccountId => userOwnBankAccountIds.contains(bankAccountId)).toSet

        //The accounts from AccountAccess may contains other users' account info, so here we filter the accounts By account holder, only show the user's own accounts
        val cbsBankAccountIds = accountsHeld.map(account =>BankIdAccountId(BankId(account.bankId),AccountId(account.accountId))).toSet

        //cbs removed this accounts, but OBP still contains the data for them, so we need to clean data in OBP side.
        val cbsRemovedBankAccountIds = obpBankAccountIds diff cbsBankAccountIds

        //cbs has new accounts which are not in obp yet, we need to create new data for these accounts.
        val csbNewBankAccountIds = cbsBankAccountIds diff obpBankAccountIds

        logger.debug("refreshViewsAccountAccessAndHolders.cbsRemovedBankAccountIds-------"+cbsRemovedBankAccountIds)
        logger.debug("refreshViewsAccountAccessAndHolders.csbNewBankAccountIds-------" + csbNewBankAccountIds)
        //1rd remove the deprecated accounts
        //TODO. need to double check if we need to clean accountidmapping table, account meta data (MappedTag) ....
        for{
          cbsRemovedBankAccountId <- cbsRemovedBankAccountIds
          _ = logger.debug("refreshViewsAccountAccessAndHolders.cbsRemovedBankAccountIds.cbsRemovedBankAccountId: start-------" + cbsRemovedBankAccountId)
          bankId = cbsRemovedBankAccountId.bankId
          accountId = cbsRemovedBankAccountId.accountId
          _ = Views.views.vend.revokeAccountAccessByUser(bankId, accountId, user, callContext)
          _ = AccountHolders.accountHolders.vend.deleteAccountHolder(user,cbsRemovedBankAccountId)
          cbsAccount = accountsHeld.find(cbsAccount =>cbsAccount.bankId == bankId.value && cbsAccount.accountId == accountId.value)
          viewId <- cbsAccount.map(_.viewsToGenerate).getOrElse(List.empty[String])
          _=UserRefreshes.UserRefreshes.vend.createOrUpdateRefreshUser(user.userId)
          success <- Views.views.vend.removeCustomView(ViewId(viewId), cbsRemovedBankAccountId)
          _ = logger.debug("refreshViewsAccountAccessAndHolders.cbsRemovedBankAccountIds.cbsRemovedBankAccountId: finish-------" + cbsRemovedBankAccountId)
        } yield {
          success
        }

        //2st: create views/accountAccess/accountHolders for the new coming accounts
        for {
          newBankAccountId <- csbNewBankAccountIds
          _ = logger.debug("refreshViewsAccountAccessAndHolders.csbNewBankAccountId.newBankAccountId: start-------" + newBankAccountId)
          _ = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user,newBankAccountId,Some("UserAuthContext"))
          bankId = newBankAccountId.bankId
          accountId = newBankAccountId.accountId
          newBankAccount = accountsHeld.find(cbsAccount =>cbsAccount.bankId == bankId.value && cbsAccount.accountId == accountId.value)
          viewId <- newBankAccount.map(_.viewsToGenerate).getOrElse(List.empty[String])
          view <- Views.views.vend.getOrCreateSystemViewFromCbs(viewId)//TODO, only support system views so far, may add custom views later.
          _=UserRefreshes.UserRefreshes.vend.createOrUpdateRefreshUser(user.userId)
          view <- if (view.isSystem) //if the view is a system view, we will call `grantAccessToSystemView`
              Views.views.vend.grantAccessToSystemView(bankId, accountId, view, user)
            else //otherwise, we will call `grantAccessToCustomView`
              Views.views.vend.grantAccessToCustomView(view.uid, user)
          _ = logger.debug("refreshViewsAccountAccessAndHolders.csbNewBankAccountId.newBankAccountId: finish-------" + newBankAccountId)
        } yield {
          view
        }

        //3rd: if the ids are not change, but views are changed, we still need compare the view for each account:
        if(cbsRemovedBankAccountIds.equals(csbNewBankAccountIds)) {
          for {
            bankAccountId <- obpBankAccountIds
            // we can not get the views from the `viewDefinition` table, because we can not delete system views at all. we need to read the view from accountAccess table.
            //obpViewsForAccount = MapperViews.availableViewsForAccount(bankAccountId).map(_.viewId.value)
            obpViewsForAccount = Views.views.vend.privateViewsUserCanAccessForAccount(user, bankAccountId).map(_.viewId.value)
            _ = logger.debug("refreshViewsAccountAccessAndHolders.obpViewsForAccount-------" + obpViewsForAccount)

            cbsViewsForAccount = accountsHeld.find(account => account.bankId.equals(bankAccountId.bankId.value) && account.accountId.equals(bankAccountId.accountId.value)).map(_.viewsToGenerate).getOrElse(Nil)
            _ = logger.debug("refreshViewsAccountAccessAndHolders.cbsViewsForAccount-------" + cbsViewsForAccount)
            //cbs removed these views, but OBP still contains the data for them, so we need to clean data in OBP side.
            cbsRemovedViewsForAccount = obpViewsForAccount diff cbsViewsForAccount
            _ = logger.debug("refreshViewsAccountAccessAndHolders.cbsRemovedViewsForAccount-------" + cbsRemovedViewsForAccount)
            _ = if(cbsRemovedViewsForAccount.nonEmpty){
              val cbsRemovedBankIdAccountIdViewIds = cbsRemovedViewsForAccount.map(view => BankIdAccountIdViewId(bankAccountId.bankId, bankAccountId.accountId, ViewId(view)))
              Views.views.vend.revokeAccessToMultipleViews(cbsRemovedBankIdAccountIdViewIds, user)
              cbsRemovedViewsForAccount.map(view =>Views.views.vend.removeCustomView(ViewId(view), bankAccountId))
              UserRefreshes.UserRefreshes.vend.createOrUpdateRefreshUser(user.userId)
            }
            //cbs has new views which are not in obp yet, we need to create new data for these accounts.
            csbNewViewsForAccount = cbsViewsForAccount diff obpViewsForAccount
            _ = logger.debug("refreshViewsAccountAccessAndHolders.csbNewViewsForAccount-------" + csbNewViewsForAccount)
            success = if(csbNewViewsForAccount.nonEmpty){
              for{
                newViewForAccount <- csbNewViewsForAccount
                _ = logger.debug("refreshViewsAccountAccessAndHolders.csbNewViewsForAccount.newViewForAccount start:-------" + newViewForAccount)
                view <- Views.views.vend.getOrCreateSystemViewFromCbs(newViewForAccount)//TODO, only support system views so far, may add custom views later.
                _ = UserRefreshes.UserRefreshes.vend.createOrUpdateRefreshUser(user.userId)
                view <- if (view.isSystem) //if the view is a system view, we will call `grantAccessToSystemView`
                  Views.views.vend.grantAccessToSystemView(bankAccountId.bankId, bankAccountId.accountId, view, user)
                else //otherwise, we will call `grantAccessToCustomView`
                  Views.views.vend.grantAccessToCustomView(view.uid, user)
                _ = logger.debug("refreshViewsAccountAccessAndHolders.csbNewViewsForAccount.newViewForAccount finish:-------" + newViewForAccount)
              }yield{
                view
              }
            }
          } yield {
            success
          }
        }
        true
      }
      else {
        false
      }
  }

  /*
          ┌────────────┐
          │FIND A USER │
          │AT MAPPER DB│
          └──────┬─────┘
                 │
                 │ Find by composite key:
                 │ (username, provider)
                 │
                 │ provider = parameter value
                 │
              ┌──▽──────────────────────┐
              │FIND USER BY COMPOSITE   │
              │KEY (username, provider) │
              └──────┬──────────────────┘
                     │
                ┌────▽────┐
                │BOX[USER]│
                └─────────┘
  */
  def findAuthUserByUsernameAndProvider(name: String, provider: String): Box[TheUserType] = {
    find(By(this.username, name), By(this.provider, provider))
  }
  def findAuthUserByPrimaryKey(key: Long): Box[TheUserType] = {
    find(By(this.user, key))
  }

  def passwordResetUrl(name: String, email: String, userId: String): String = {
    find(By(this.username, name)) match {
      case Full(authUser) if authUser.validated_? && authUser.email == email =>
        Users.users.vend.getUserByUserId(userId) match {
          case Full(u) if u.name == name && u.emailAddress == email =>
            authUser.resetUniqueId().save
            val resetLink = Constant.HostName+
              passwordResetPath.mkString("/", "/", "/")+java.net.URLEncoder.encode(authUser.getUniqueId(), "UTF-8")
            logger.warn(s"Password reset url is created for this user: $email")
            // TODO Notify via email appropriate persons 
            resetLink
          case _ => ""
        }
        case _ => ""
    }
  }

  // passwordResetXhtml simplified - API-only mode, no portal pages
  // Password reset is handled via POST /obp/v6.0.0/users/password API endpoint
  override def passwordResetXhtml = <div/>
  
  /**
    * Find the authUsers by author email(authUser and resourceUser are the same).
    * Only search for the local database. 
    */
  protected def findUsersByEmailLocally(email: String): List[TheUserType] = {
    val usernames: List[String] = this.getResourceUsersByEmail(email).map(_.user.name)
    findAll(ByList(this.username, usernames))
  }
  def signupSubmitButtonValue() = getWebUiPropsValue("webui_signup_form_submit_button_value", "sign.up")

  override def signup = NodeSeq.Empty

  def scrambleAuthUser(userPrimaryKey: UserPrimaryKey): Box[Boolean] = tryo {
    AuthUser.find(By(AuthUser.user, userPrimaryKey.value)) match {
      case Full(user) => 
        val scrambledUser = user.firstName(Helpers.randomString(16))
          .email(Helpers.randomString(10) + "@example.com")
          .username("DELETED-" + Helpers.randomString(16))
          .firstName(Helpers.randomString(16))
          .lastName(Helpers.randomString(16))
          .password(Helpers.randomString(40))
          .validated(false)
        scrambledUser.save
      case Empty => true // There is a resource user but no the correlated Auth user 
      case _ => false // Error case
    }
  }

  def validateAuthUser(userPrimaryKey: UserPrimaryKey): Box[AuthUser] = tryo {
    AuthUser.find(By(AuthUser.user, userPrimaryKey.value)) match {
      case Full(user) =>
        user.validated(true).saveMe()
    }
  }
  
  /**
   * Find a user by their unique validation token.
   * This is a public wrapper for the protected findUserByUniqueId method.
   * 
   * @param token The unique validation token (UUID string)
   * @return Box containing the AuthUser if found, Empty if not found, or Failure on error
   */
  def findUserByValidationToken(token: String): Box[AuthUser] = {
    findUserByUniqueId(token)
  }
  
  /**
   * Validate a user and reset their unique ID token.
   * This is a public wrapper that combines validation and token reset.
   * 
   * @param user The AuthUser to validate
   * @return The validated AuthUser with reset unique ID
   */
  def validateAndResetToken(user: AuthUser): AuthUser = {
    user.validated(true).resetUniqueId().save
    user
  }
  
}
