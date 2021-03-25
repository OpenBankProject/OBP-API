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
import code.api.util.APIUtil.{hasAnOAuthHeader, isValidStrongPassword, logger, _}
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.{APIFailure, DirectLogin, GatewayLogin, OAuthHandshake}
import code.bankconnectors.Connector
import code.context.UserAuthContextProvider
import code.loginattempts.LoginAttempt
import code.users.Users
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.views.Views
import com.openbankproject.commons.model.{User, _}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.mapper._
import net.liftweb.util.Mailer.{BCC, From, Subject, To}
import net.liftweb.util._

import scala.collection.immutable.List
import scala.xml.{Elem, NodeSeq, Text}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import org.apache.commons.lang3.StringUtils
import code.util.HydraUtil._
import sh.ory.hydra.model.AcceptLoginRequest
import net.liftweb.http.S.fmapFunc

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
  *   1)When `Sign up` new user --> create AuthUser --> call AuthUser.save() --> create ResourceUser user.
  *      They share the same username and email.
  *   2)AuthUser `user` field as the Foreign Key to link to Resource User. 
  *      one AuthUser <---> one ResourceUser 
  *
 */
class AuthUser extends MegaProtoUser[AuthUser] with MdcLoggable {
  def getSingleton = AuthUser // what's the "meta" server

  object user extends MappedLongForeignKey(this, ResourceUser)

  override lazy val firstName = new MyFirstName
  
  protected class MyFirstName extends MappedString(this, 32) {
    def isEmpty(msg: => String)(value: String): List[FieldError] =
      value match {
        case null                  => List(FieldError(this, Text(msg))) // issue 179
        case e if e.trim.isEmpty   => List(FieldError(this, Text(msg))) // issue 179
        case _                     => Nil
      }
    
    override def displayName = fieldOwner.firstNameDisplayName
    override val fieldId = Some(Text("txtFirstName"))
    override def validations = isEmpty(Helper.i18n("Please.enter.your.first.name")) _ :: super.validations

    override def _toForm: Box[Elem] =
      fmapFunc({s: List[String] => this.setFromAny(s)}){name =>
        Full(appendFieldId(<input type={formInputType} 
                                  maxlength={maxLen.toString}
                                  aria-labelledby={displayName} 
                                  aria-describedby={uniqueFieldId.getOrElse("")}
                                  name={name}
                                  value={get match {case null => "" case s => s.toString}}/>))
      }
  }
  
  override lazy val lastName = new MyLastName

  protected class MyLastName extends MappedString(this, 32) {
    def isEmpty(msg: => String)(value: String): List[FieldError] =
      value match {
        case null                  => List(FieldError(this, Text(msg))) // issue 179
        case e if e.trim.isEmpty   => List(FieldError(this, Text(msg))) // issue 179
        case _                     => Nil
      }

    override def displayName = fieldOwner.lastNameDisplayName
    override val fieldId = Some(Text("txtLastName"))
    override def validations = isEmpty(Helper.i18n("Please.enter.your.last.name")) _ :: super.validations

    override def _toForm: Box[Elem] =
      fmapFunc({s: List[String] => this.setFromAny(s)}){name =>
        Full(appendFieldId(<input type={formInputType}
                                  maxlength={maxLen.toString}
                                  aria-labelledby={displayName}
                                  aria-describedby={uniqueFieldId.getOrElse("")}
                                  name={name}
                                  value={get match {case null => "" case s => s.toString}}/>))
      }
    
  }
  
  /**
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
  class userName extends MappedString(this, 64) {
    def isEmpty(msg: => String)(value: String): List[FieldError] =
      value match {
        case null                  => List(FieldError(this, Text(msg))) // issue 179
        case e if e.trim.isEmpty   => List(FieldError(this, Text(msg))) // issue 179
        case _                     => Nil
      }
    def usernameIsValid(msg: => String)(e: String) = e match {
      case null                                             => List(FieldError(this, Text(msg)))
      case e if e.trim.isEmpty                              => List(FieldError(this, Text(msg)))
      case e if usernameRegex.findFirstMatchIn(e).isDefined => Nil
      case _                                                => List(FieldError(this, Text(msg)))
    }
    override def displayName = S.?("Username")
    override def dbIndexed_? = true
    override def validations = isEmpty(Helper.i18n("Please.enter.your.username")) _ ::
                               usernameIsValid(Helper.i18n("invalid.username")) _ ::
                               valUnique(Helper.i18n("unique.username")) _ ::
                               valUniqueExternally(Helper.i18n("unique.username")) _ :: 
                               super.validations
    override val fieldId = Some(Text("txtUsername"))

    override def _toForm: Box[Elem] =
      fmapFunc({s: List[String] => this.setFromAny(s)}){name =>
        Full(appendFieldId(<input type={formInputType}
                                  maxlength={maxLen.toString}
                                  aria-labelledby={displayName}
                                  aria-describedby={uniqueFieldId.getOrElse("")}
                                  name={name}
                                  value={get match {case null => "" case s => s.toString}}/>))
      }
    
    /**
     * Make sure that the field is unique in the CBS
     */
    def valUniqueExternally(msg: => String)(uniqueUsername: String): List[FieldError] ={
      if (APIUtil.getPropsAsBoolValue("connector.user.authentication", false)) {
        Connector.connector.vend.checkExternalUserExists(uniqueUsername, None).map(_.sub) match {
          case Full(returnedUsername) => // Get the username via connector
            if(uniqueUsername == returnedUsername) { // Username is NOT unique
              List(FieldError(this, Text(msg))) // provide the error message
            } else { 
              Nil // All good. Allow username creation
            }
          case ParamFailure(message,_,_,APIFailure(errorMessage, errorCode)) if errorMessage.contains("NO DATA") => // Cannot get the username via connector
            Nil // All good. Allow username creation
          case _ => // Any other case we provide error message
            List(FieldError(this, Text(msg)))
        }
      } else {
        Nil // All good. Allow username creation
      }
    }
      
      
  }

  override lazy val password = new MyPasswordNew
  
  lazy val signupPasswordRepeatText = getWebUiPropsValue("webui_signup_body_password_repeat_text", S.?("repeat"))
 
  class MyPasswordNew extends MappedPassword(this) {
    lazy val preFilledPassword = if (APIUtil.getPropsAsBoolValue("allow_pre_filled_password", true)) {get.toString} else ""
    override def _toForm: Box[NodeSeq] = {
      S.fmapFunc({s: List[String] => this.setFromAny(s)}){funcName =>
        Full(
          <span>
            {appendFieldId(<input id="textPassword" aria-labelledby="Password" aria-describedby={uniqueFieldId.getOrElse("")} type={formInputType} name={funcName} value={preFilledPassword}/> ) }
            <div id="signup-error" class="alert alert-danger hide">
              <span data-lift={s"Msg?id=${uniqueFieldId.getOrElse("")}&errorClass=error"}/>
            </div>
            <div id ="repeat-password">{signupPasswordRepeatText}</div>
            <input id="textPasswordRepeat" aria-labelledby="Password Repeat" aria-describedby={uniqueFieldId.getOrElse("")}  type={formInputType} name={funcName} value={preFilledPassword}/>
            <div id="signup-error" class="alert alert-danger hide">
              <span data-lift={s"Msg?id=${uniqueFieldId.getOrElse("")}_repeat&errorClass=error"}/>
            </div>
        </span>)
      }
    }
    
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
            invalidPw = true;
            invalidMsg = Helper.i18n("please.enter.your.password")
            S.error("authuser_password_repeat", Text(Helper.i18n("please.re-enter.your.password")))
          case false =>
            if (isValidStrongPassword(passwordValue))
              invalidPw = false
            else {
              invalidPw = true
              invalidMsg = S.?(ErrorMessages.InvalidStrongPasswordFormat.split(':')(1))
              S.error("authuser_password_repeat", Text(invalidMsg))
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
          invalidPw = true;
          invalidMsg = Helper.i18n("passwords.do.not.match")
          S.error("authuser_password_repeat", Text(invalidMsg))
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
  class userProvider extends MappedString(this, 64) {
    override def displayName = S.?("provider")
    override val fieldId = Some(Text("txtProvider"))
  }


  def getProvider() = {
    if(provider.get == null) {
      APIUtil.getPropsValue("hostname","")
    } else if ( provider.get == "" || provider.get == APIUtil.getPropsValue("hostname","") ) {
      APIUtil.getPropsValue("hostname","")
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

  def getResourceUsers(): List[ResourceUser] = {
    Users.users.vend.getAllUsers match {
      case Full(userList) => userList
      case _ => List()
    }
  }

  def getResourceUserByUsername(username: String) : Box[User] = {
    Users.users.vend.getUserByUserName(username)
  }

  override def save(): Boolean = {
    if(! (user defined_?)){
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
    super.save()
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
      case e if (!isEmailValid(e))  => List(FieldError(this, Text(S.?("invalid.email.address"))))
      case _                     => Nil
    }
    override def _toForm: Box[Elem] =
      fmapFunc({s: List[String] => this.setFromAny(s)}){name =>
        Full(appendFieldId(<input type={formInputType}
                                  maxlength={maxLen.toString}
                                  aria-labelledby={displayName}
                                  aria-describedby={uniqueFieldId.getOrElse("")}
                                  name={name}
                                  value={get match {case null => "" case s => s.toString}}/>))
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

  val connector = APIUtil.getPropsValue("connector").openOrThrowException("no connector set")
  val starConnectorSupportedTypes = APIUtil.getPropsValue("starConnector_supported_types","")

  override def emailFrom = APIUtil.getPropsValue("mail.users.userinfo.sender.address", "sender-not-set")

  override def screenWrap = Full(<lift:surround with="default" at="content"><lift:bind /></lift:surround>)
  // define the order fields will appear in forms and output
  override def fieldOrder = List(id, firstName, lastName, email, username, password, provider)
  override def signupFields = List(firstName, lastName, email, username, password)

  // If we want to validate email addresses set this to false
  override def skipEmailValidation = APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", true)

  override def loginXhtml = {
    val loginXml = Templates(List("templates-hidden","_login")).map({
        "form [action]" #> {S.uri} &
        "#loginText * " #> {S.?("log.in")} &
        "#usernameText * " #> {S.?("username")} &
        "#passwordText * " #> {S.?("password")} &
        "#login_challenge [value]" #> S.param("login_challenge").getOrElse("") &
        "autocomplete=off [autocomplete] " #> APIUtil.getAutocompleteValue &
        "#recoverPasswordLink * " #> {
          "a [href]" #> {lostPasswordPath.mkString("/", "/", "")} &
          "a *" #> {S.?("recover.password")}
        } &
        "#SignUpLink * " #> {
          "a [href]" #> {AuthUser.signUpPath.foldLeft("")(_ + "/" + _)} &
          "a *" #> {S.?("sign.up")}
        }
      })

    <div>{loginXml getOrElse NodeSeq.Empty}</div>
  }
  
  /**
    * Find current ResourceUser from the server. 
    * This method has no parameters, it depends on different login types:
    *  AuthUser:  AuthUser.currentUser
    *  OAuthHandshake: OAuthHandshake.getUser
    *  DirectLogin: DirectLogin.getUser
    * to get the current Resourceuser .
    *
    */
  def getCurrentUser: Box[User] = {
    val authorization = S.request.map(_.header("Authorization")).flatten
    for {
      resourceUser <- if (AuthUser.currentUser.isDefined)
        //AuthUser.currentUser.get.user.foreign // this will be issue when the resource user is in remote side
        Users.users.vend.getUserByUserName(AuthUser.currentUser.openOrThrowException(ErrorMessages.attemptedToOpenAnEmptyBox).username.get)
      else if (hasDirectLoginHeader(authorization))
        DirectLogin.getUser
      else if (hasAnOAuthHeader(authorization)) {
        OAuthHandshake.getUser
      } else if (hasGatewayHeader(authorization)){
        GatewayLogin.getUser
      } else {
        logger.debug(ErrorMessages.CurrentUserNotFoundException)
        Failure(ErrorMessages.CurrentUserNotFoundException)
        //This is a big problem, if there is no current user from here.
        //throw new RuntimeException(ErrorMessages.CurrentUserNotFoundException)
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
       case Full(user) if user.provider.contains("google") => user.emailAddress
       case Full(user) if user.provider.contains("yahoo") => user.emailAddress
       case Full(user) => user.name
       case _ => "" //TODO need more error handling for different user cases
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


  /**
   * Overridden to use the hostname set in the props file
   */
  override def sendPasswordReset(name: String) {
    findUserByUsernameLocally(name).toList ::: findUsersByEmailLocally(name) map {
      // reason of case parameter name is "u" instead of "user": trait AuthUser have constant mumber name is "user"
      // So if the follow case paramter name is "user" will cause compile warnings
      case u if u.validated_? =>
        u.resetUniqueId().save
        //NOTE: here, if server_mode = portal, so we need modify the resetLink to portal_hostname, then developer can get proper response..
        val resetPasswordLinkProps = APIUtil.getPropsValue("hostname", "ERROR")
        val resetPasswordLink = APIUtil.getPropsValue("portal_hostname", resetPasswordLinkProps)+
          passwordResetPath.mkString("/", "/", "/")+urlEncode(u.getUniqueId())
        Mailer.sendMail(From(emailFrom),Subject(passwordResetEmailSubject + " - " + u.username),
          To(u.getEmail) ::
            generateResetEmailBodies(u, resetPasswordLink) :::
            (bccEmail.toList.map(BCC(_))) :_*)
      case u =>
        sendValidationEmail(u)
    }
    // In order to prevent any leakage of information we use the same message for all cases
    S.notice(userNameNotFoundString)
    S.redirectTo(homePage)
  }

  override def lostPasswordXhtml = {
    <div id="recover-password" tabindex="-1">
          <h1>Recover Password</h1>
          <div id="recover-password-explanation">Enter your email address or username and we'll email you a link to reset your password</div>
          <form action={S.uri} method="post">
            <div class="form-group">
              <label>Username or email address</label> <span id="recover-password-email"><input id="email" type="text" /></span>
            </div>
            <div id="recover-password-submit">
              <input type="submit" />
            </div>
          </form>
    </div>
  }

  override def lostPassword = {
    val bind =
          "#email" #> SHtml.text("", sendPasswordReset _) &
          "type=submit" #> lostPasswordSubmitButton(S.?("submit"))

    bind(lostPasswordXhtml)
  }

  //override def def passwordResetMailBody(user: TheUserType, resetLink: String): Elem = { }

  /**
   * Overridden to use the hostname set in the props file
   */
  override def sendValidationEmail(user: TheUserType) {
    val resetLink = APIUtil.getPropsValue("hostname", "ERROR")+"/"+validateUserPath.mkString("/")+
      "/"+urlEncode(user.getUniqueId())

    val email: String = user.getEmail

    val msgXml = signupMailBody(user, resetLink)

    Mailer.sendMail(From(emailFrom),Subject(signupMailSubject),
      To(user.getEmail) ::
        generateValidationEmailBodies(user, resetLink) :::
        (bccEmail.toList.map(BCC(_))) :_* )
  }
  
   def grantDefaultEntitlementsToAuthUser(user: TheUserType) = {
     tryo{getResourceUserByUsername(user.username.get).head.userId} match {
       case Full(userId)=>APIUtil.grantDefaultEntitlementsToNewUser(userId)
       case _ => logger.error("Can not getResourceUserByUsername here, so it breaks the grantDefaultEntitlementsToNewUser process.")
     }
   }
  
  override def validateUser(id: String): NodeSeq = findUserByUniqueId(id) match {
    case Full(user) if !user.validated_? =>
      user.setValidated(true).resetUniqueId().save
      grantDefaultEntitlementsToAuthUser(user)
      logUserIn(user, () => {
        S.notice(S.?("account.validated"))
        S.redirectTo(homePage)
      })

    case _ => S.error(S.?("invalid.validation.link")); S.redirectTo(homePage)
  }
  
  override def actionsAfterSignup(theUser: TheUserType, func: () => Nothing): Nothing = {
    theUser.setValidated(skipEmailValidation).resetUniqueId()
    theUser.save
    if (!skipEmailValidation) {
      sendValidationEmail(theUser)
      S.notice(S.?("sign.up.message"))
      func()
    } else {
      grantDefaultEntitlementsToAuthUser(theUser)
      logUserIn(theUser, () => {
        S.notice(S.?("welcome"))
        func()
      })
    }
  }
  /**
   * Set this to redirect to a certain page after a failed login
   */
  object failedLoginRedirect extends SessionVar[Box[String]](Empty) {
    override lazy val __nameSalt = Helpers.nextFuncName
  }


  def agreeTermsDiv = {
    val agreeTermsHtml = getWebUiPropsValue("webui_agree_terms_html", "")
    if(agreeTermsHtml.isEmpty){
      val url = getWebUiPropsValue("webui_agree_terms_url", "")
      if (url.isEmpty) {
        s""
      } else {
        scala.xml.Unparsed(s"""<div id="signup-agree-terms" class="checkbox"><label><input type="checkbox" />I hereby agree to the <a href="$url" title="T &amp; C">Terms and Conditions</a></label></div>""")
      }
    } else{
      scala.xml.Unparsed(s"""$agreeTermsHtml""")
    }
  }

  def legalNoticeDiv = {
    val agreeTermsHtml = getWebUiPropsValue("webui_legal_notice_html_text", "")
    if(agreeTermsHtml.isEmpty){
      s""
    } else{
      scala.xml.Unparsed(s"""$agreeTermsHtml""")
    }
  }
  
  def agreePrivacyPolicy = {
    val url = getWebUiPropsValue("webui_agree_privacy_policy_url", "")
    val text = getWebUiPropsValue("webui_agree_privacy_policy_html_text", s"""<div id="signup-agree-privacy-policy"><label>By submitting this information you consent to processing your data by TESOBE GmbH according to our <a href="$url" title="Privacy Policy">Privacy Policy</a>. TESOBE shall use this information to send you emails and provide customer support.</label></div>""")
    if (url.isEmpty) {
      s""
    } else {
      scala.xml.Unparsed(s"""$text""")
    }
  }

  def signupFormTitle = getWebUiPropsValue("webui_signup_form_title_text", S.?("sign.up"))
  
  override def signupXhtml (user:AuthUser) =  {
    <div id="signup" tabindex="-1">
      <form method="post" action={S.uriAndQueryString.getOrElse(S.uri)}>
          <h1>{signupFormTitle}</h1>
          {legalNoticeDiv}
          <div id="signup-general-error" class="alert alert-danger hide"><span data-lift="Msg?id=error"/></div>
          {localForm(user, false, signupFields)}
          {agreeTermsDiv}
          {agreePrivacyPolicy}
          <div id="signup-submit">
            <input type="submit" />
          </div>
      </form>
    </div>
  }


  override def localForm(user: TheUserType, ignorePassword: Boolean, fields: List[FieldPointerType]): NodeSeq = {
    for {
      pointer <- fields
      field <- computeFieldFromPointer(user, pointer).toList
      if field.show_? && (!ignorePassword || !pointer.isPasswordField_?)
      form <- field.toForm.toList
    } yield {
      if(field.uniqueFieldId.getOrElse("") == "authuser_password") {
        <div class="form-group">
          <label>{field.displayName}</label>
          {form}
        </div>
      } else {
        <div class="form-group">
          <label>{field.displayName}</label>
          {form}
          <div id="signup-error" class="alert alert-danger hide"><span data-lift={s"Msg?id=${field.uniqueFieldId.getOrElse("")}&errorClass=error"}/></div>
        </div>
      }
    }
      
  }

  def userLoginFailed = {
    logger.info("failed: " + failedLoginRedirect.get)
    // variable redir is from failedLoginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
    // val currentUrl = S.uriAndQueryString.getOrElse("/")
    // AuthUser.failedLoginRedirect.set(Full(Helpers.appendParams(currentUrl, List((FailedLoginParam, "true")))))
    val redir = failedLoginRedirect.get

    //Check the internal redirect, in case for open redirect issue.
    // variable redir is from loginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
    // val currentUrl = S.uriAndQueryString.getOrElse("/")
    // AuthUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
    if (Helper.isValidInternalRedirectUrl(redir.toString)) {
        S.redirectTo(redir.toString)
    } else {
      S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
      logger.info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
    }
    S.error("login", S.?("Invalid Username or Password"))
  }




  def getResourceUserId(username: String, password: String): Box[Long] = {
    findUserByUsernameLocally(username) match {
      // We have a user from the local provider.
      case Full(user) if (user.getProvider() == APIUtil.getPropsValue("hostname","")) =>
        if (
          user.validated_? &&
          // User is NOT locked AND the password is good
          ! LoginAttempt.userIsLocked(username) &&
          user.testPassword(Full(password)))
            {
              // We logged in correctly, so reset badLoginAttempts counter (if it exists)
              LoginAttempt.resetBadLoginAttempts(username)
              Full(user.user.get) // Return the user.
            }
        // User is unlocked AND password is bad
        else if (
          user.validated_? &&
          ! LoginAttempt.userIsLocked(username) &&
          ! user.testPassword(Full(password))
        ) {
          LoginAttempt.incrementBadLoginAttempts(username)
          Empty
        }
        // User is locked
        else if (LoginAttempt.userIsLocked(username))
        {
          LoginAttempt.incrementBadLoginAttempts(username)
          logger.info(ErrorMessages.UsernameHasBeenLocked)
          //TODO need to fix, use Failure instead, it is used to show the error message to the GUI
          Full(usernameLockedStateCode)
        }
        else {
          // Nothing worked, so just increment bad login attempts
          LoginAttempt.incrementBadLoginAttempts(username)
          Empty
        }
      // We have a user from an external provider.
      case Full(user) if (user.getProvider() != APIUtil.getPropsValue("hostname","")) =>
        APIUtil.getPropsAsBoolValue("connector.user.authentication", false) match {
            case true if !LoginAttempt.userIsLocked(username) =>
              val userId =
                for {
                  authUser <- checkExternalUserViaConnector(username, password)
                  resourceUser <- tryo {
                    authUser.user
                  }
                } yield {
                  LoginAttempt.resetBadLoginAttempts(username)
                  resourceUser.get
                }
              userId match {
                case Full(l: Long) => Full(l)
                case _ =>
                  LoginAttempt.incrementBadLoginAttempts(username)
                  Empty
              }
            case false =>
              LoginAttempt.incrementBadLoginAttempts(username)
              Empty
          }
      // Everything else.
      case _ =>
        LoginAttempt.incrementBadLoginAttempts(username)
        Empty
    }
  }

  /**
    * This method is belong to AuthUser, it is used for authentication(Login stuff)
    * 1 get the user over connector.
    * 2 check whether it is existing in AuthUser table in obp side. 
    * 3 if not existing, will create new AuthUser. 
    * @return Return the authUser
    */
  @deprecated("we have @checkExternalUserViaConnector method ","01-07-2020")
  def getUserFromConnector(name: String, password: String):Box[AuthUser] = {
    Connector.connector.vend.getUser(name, password) match {
      case Full(InboundUser(extEmail, extPassword, extUsername)) => {
        val extProvider = connector
        val user = findUserByUsernameLocally(name) match {
          // Check if the external user is already created locally
          case Full(user) if user.validated_?
            // && user.provider == extProvider
            => {
            // Return existing user if found
            logger.info("external user already exists locally, using that one")
            user
          }

          // If not found, create a new user
          case _ =>
            // Create AuthUser using fetched data from connector
            // assuming that user's email is always validated
            logger.info("external user "+ extEmail +" does not exist locally, creating one")
            val newUser = AuthUser.create
              .firstName(extUsername)
              .email(extEmail)
              .username(extUsername)
              // No need to store password, so store dummy string instead
              .password(generateUUID())
              .provider(extProvider)
              .validated(true)
            // Return created user
            newUser.saveMe()
        }
        Full(user)
      }
      case _ => {
        Empty
      }
    }
  }
  /**
    * This method is belong to AuthUser, it is used for authentication(Login stuff)
    * 1 get the user over connector.
    * 2 check whether it is existing in AuthUser table in obp side. 
    * 3 if not existing, will create new AuthUser. 
    * @return Return the authUser
    */
  def checkExternalUserViaConnector(username: String, password: String):Box[AuthUser] = {
    Connector.connector.vend.checkExternalUserCredentials(username, password, None) match {
      case Full(InboundExternalUser(aud, exp, iat, iss, sub, azp, email, emailVerified, name, userAuthContexts)) =>
        val user = findUserByUsernameLocally(sub) match { // Check if the external user is already created locally
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
      case _ =>
        Empty
    }
  }



def restoreSomeSessions(): Unit = {
  activeBrand()
}

  override protected def capturePreLoginState(): () => Unit = () => {restoreSomeSessions}

  //overridden to allow a redirection if login fails
  /**
    * Success cases: 
    *  case1: user validated && user not locked && user.provider from localhost && password correct --> Login in
    *  case2: user validated && user not locked && user.provider not localhost  && password correct --> Login in
    *  case3: user from remote && checked over connector --> Login in
    *  
    * Error cases:
    *  case1: user is locked --> UsernameHasBeenLocked
    *  case2: user.validated_? --> account.validation.error
    *  case3: right username but wrong password --> Invalid Login Credentials
    *  case4: wrong username   --> Invalid Login Credentials
    *  case5: UnKnow error     --> UnexpectedErrorDuringLogin
    */
  override def login: NodeSeq = {
    def redirectUri(): String = {
      loginRedirect.get match {
        case Full(url) =>
          loginRedirect(Empty)
          url
        case _ =>
          homePage
      }
    }
    //Check the internal redirect, in case for open redirect issue.
    // variable redirect is from loginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
    // val currentUrl = S.uriAndQueryString.getOrElse("/")
    // AuthUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
    def checkInternalRedirectAndLogUseIn(preLoginState: () => Unit, redirect: String, user: AuthUser) = {
      if (Helper.isValidInternalRedirectUrl(redirect)) {
        logUserIn(user, () => {
          S.notice(S.?("logged.in"))
          preLoginState()
          S.redirectTo(redirect)
        })
      } else {
        S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
        logger.info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
      }
    }

    def isObpProvider(user: AuthUser) = {
      user.getProvider() == APIUtil.getPropsValue("hostname", "")
    }

    def obpUserIsValidatedAndNotLocked(usernameFromGui: String, user: AuthUser) = {
      user.validated_? && !LoginAttempt.userIsLocked(usernameFromGui) &&
        isObpProvider(user)
    }

    def externalUserIsValidatedAndNotLocked(usernameFromGui: String, user: AuthUser) = {
      user.validated_? && !LoginAttempt.userIsLocked(usernameFromGui) &&
        !isObpProvider(user)
    }
    
    def loginAction = {
      if (S.post_?) {
        val usernameFromGui = S.param("username").getOrElse("")
        val passwordFromGui = S.param("password").getOrElse("")
        val usernameEmptyField = S.param("username").map(_.isEmpty()).getOrElse(true)
        val passwordEmptyField = S.param("password").map(_.isEmpty()).getOrElse(true)
        val emptyField = usernameEmptyField || passwordEmptyField
        emptyField match {
          case true =>
            if(usernameEmptyField) 
              S.error("login-form-username-error", Helper.i18n("please.enter.your.username"))
            if(passwordEmptyField) 
              S.error("login-form-password-error", Helper.i18n("please.enter.your.password"))
          case false =>
            findUserByUsernameLocally(usernameFromGui) match {
              case Full(user) if !user.validated_? =>
                S.error(S.?("account.validation.error"))
              
              // Check if user comes from localhost and
              case Full(user) if obpUserIsValidatedAndNotLocked(usernameFromGui, user) =>
                if(user.testPassword(Full(passwordFromGui))) { // if User is NOT locked and password is good
                  // Reset any bad attempt
                  LoginAttempt.resetBadLoginAttempts(usernameFromGui)
                  val preLoginState = capturePreLoginState()
                  logger.info("login redirect: " + loginRedirect.get)
                  val redirect = redirectUri()
                  checkInternalRedirectAndLogUseIn(preLoginState, redirect, user)
                } else { // If user is NOT locked AND password is wrong => increment bad login attempt counter.
                  LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
                  S.error(Helper.i18n("invalid.login.credentials"))
                }

              // If user is locked, send the error to GUI
              case Full(user) if LoginAttempt.userIsLocked(usernameFromGui) =>
                LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
                S.error(S.?(ErrorMessages.UsernameHasBeenLocked))
    
              // Check if user came from kafka/obpjvm/stored_procedure and
              // if User is NOT locked. Then check username and password
              // from connector in case they changed on the south-side
              case Full(user) if externalUserIsValidatedAndNotLocked(usernameFromGui, user) && testExternalPassword(usernameFromGui, passwordFromGui) =>
                  // Reset any bad attempts
                  LoginAttempt.resetBadLoginAttempts(usernameFromGui)
                  val preLoginState = capturePreLoginState()
                  logger.info("login redirect: " + loginRedirect.get)
                  val redirect = redirectUri()
                  //This method is used for connector = kafka* || obpjvm*
                  //It will update the views and createAccountHolder ....
                  registeredUserHelper(user.username.get)
                  checkInternalRedirectAndLogUseIn(preLoginState, redirect, user)
    
              // If user cannot be found locally, try to authenticate user via connector
              case Empty if (APIUtil.getPropsAsBoolValue("connector.user.authentication", false) || 
                APIUtil.getPropsAsBoolValue("kafka.user.authentication", false) ||
                APIUtil.getPropsAsBoolValue("obpjvm.user.authentication", false)) =>
                
                val preLoginState = capturePreLoginState()
                logger.info("login redirect: " + loginRedirect.get)
                val redirect = redirectUri()
                externalUserHelper(usernameFromGui, passwordFromGui) match {
                    case Full(user: AuthUser) =>
                      LoginAttempt.resetBadLoginAttempts(usernameFromGui)
                      checkInternalRedirectAndLogUseIn(preLoginState, redirect, user)
                    case _ =>
                      LoginAttempt.incrementBadLoginAttempts(username.get)
                      Empty
                      S.error(Helper.i18n("invalid.login.credentials"))
                }
                
              //If there is NO the username, throw the error message.  
              case Empty => 
                S.error(Helper.i18n("invalid.login.credentials"))
              case _ =>
                LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
                S.error(S.?(ErrorMessages.UnexpectedErrorDuringLogin)) // Note we hit this if user has not clicked email validation link
            }
        }
      }
    }
    
    // In this function we bind submit button to loginAction function.
    // In case that unique token of submit button cannot be paired submit action will be omitted.
    // Implemented in order to prevent a CSRF attack
    def insertSubmitButton = {
      scala.xml.XML.loadString(loginSubmitButton(loginButtonText, loginAction _).toString().replace("type=\"submit\"","class=\"submit\" type=\"submit\""))
    }

    val bind =
          "submit" #> insertSubmitButton
   bind(loginXhtml)
  }

  override def logout = {
    logoutCurrentUser
    S.request match {
      case Full(a) =>  a.param("redirect") match {
        case Full(customRedirect) => S.redirectTo(customRedirect)
        case _ => S.redirectTo(homePage)
      }
      case _ => S.redirectTo(homePage)
    }
  }
  
  
  /**
    * The user authentications is not exciting in obp side, it need get the user via connector
    */
 def testExternalPassword(usernameFromGui: String, passwordFromGui: String): Boolean = {
   // TODO Remove kafka and obpjvm special cases
   if (connector.startsWith("kafka") || connector == "obpjvm") {
     getUserFromConnector(usernameFromGui, passwordFromGui) match {
       case Full(user:AuthUser) => true
       case _ => false
     }
   } else {
     checkExternalUserViaConnector(usernameFromGui, passwordFromGui) match {
       case Full(user:AuthUser) => true
       case _ => false
     }
   }
  }
  
  /**
    * This method will update the views and createAccountHolder ....
    */
  def externalUserHelper(name: String, password: String): Box[AuthUser] = {
    // TODO Remove kafka and obpjvm special cases
    if (connector.startsWith("kafka") || connector == "obpjvm") {
      for {
       user <- getUserFromConnector(name, password)
       u <- Users.users.vend.getUserByUserName(name)
      } yield {
        user
      }
    } else {
      for {
        user <- checkExternalUserViaConnector(name, password)
        u <- Users.users.vend.getUserByUserName(name)
      } yield {
        user
      }
    } 
  }
  
  
  /**
    * This method will update the views and createAccountHolder ....
    */
  def registeredUserHelper(username: String) = {
    if (connector.startsWith("kafka") || connector == "obpjvm") {
      for {
       u <- Users.users.vend.getUserByUserName(username)
       v <- Full (updateUserAccountViews(u, None))
      } yield v
    }
  }
  
  /**
    * This is a helper method 
    * update the views, accountHolders for OBP side when sign up new remote user
    * 
    */
  def updateUserAccountViews(user: User, callContext: Option[CallContext]): Unit = {
    //get all accounts from Kafka
    val accounts = Connector.connector.vend.getBankAccountsForUserLegacy(user.name,callContext).openOrThrowException(attemptedToOpenAnEmptyBox)
    logger.debug(s"-->AuthUser.updateUserAccountViews.accounts : ${accounts} ")

    updateUserAccountViews(user, accounts._1)
  }
  
  def updateUserAccountViewsFuture(user: User, callContext: Option[CallContext]) = {
    for{
      (accounts, _) <- Connector.connector.vend.getBankAccountsForUser(user.name,callContext) map {
        connectorEmptyResponse(_, callContext)
      }
    }yield {
      updateUserAccountViews(user, accounts)
      UserRefreshes.UserRefreshes.vend.createOrUpdateRefreshUser(user.userId)
    }
  }

  /**
    * This is a helper method
    * update the views, accountHolders for OBP side when sign up new remote user
    * This method can only be used by the original user(account holder).
    */
    def updateUserAccountViews(user: User, accounts: List[InboundAccount]): Unit = {
    for {
      account <- accounts
      viewId <- account.viewsToGenerate if(user.isOriginalUser) // for now, we support four views here: Owner, Accountant, Auditor, _Public, first three are system views, the last is custom view.
      bankAccountUID <- Full(BankIdAccountId(BankId(account.bankId), AccountId(account.accountId)))
      view <- Views.views.vend.getOrCreateAccountView(bankAccountUID, viewId)//this method will return both system views and custom views back.
    } yield {
      if (view.isSystem)//if the view is a system view, we will call `grantAccessToSystemView`
        Views.views.vend.grantAccessToSystemView(BankId(account.bankId), AccountId(account.accountId), view, user)
      else //otherwise, we will call `grantAccessToCustomView`
        Views.views.vend.grantAccessToCustomView(view.uid, user)
      AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user,bankAccountUID)
    }
  }
  /**
    * Find the authUser by author user name(authUser and resourceUser are the same).
    * Only search for the local database. 
    */
  protected def findUserByUsernameLocally(name: String): Box[TheUserType] = {
    find(By(this.username, name))
  }

  def passwordResetUrl(name: String, email: String, userId: String): String = {
    find(By(this.username, name)) match {
      case Full(authUser) if authUser.validated_? && authUser.email == email =>
        Users.users.vend.getUserByUserId(userId) match {
          case Full(u) if u.name == name && u.emailAddress == email =>
            authUser.resetUniqueId().save
            val resetLink = APIUtil.getPropsValue("hostname", "ERROR")+
              passwordResetPath.mkString("/", "/", "/")+urlEncode(authUser.getUniqueId())
            logger.warn(s"Password reset url is created for this user: $email")
            // TODO Notify via email appropriate persons 
            resetLink
          case _ => ""
        }
        case _ => ""
    }
  }
  /**
    * Find the authUsers by author email(authUser and resourceUser are the same).
    * Only search for the local database. 
    */
  protected def findUsersByEmailLocally(email: String): List[TheUserType] = {
    val usernames: List[String] = this.getResourceUsersByEmail(email).map(_.user.name)
    findAll(ByList(this.username, usernames))
  }
  lazy val signupSubmitButtonValue = getWebUiPropsValue("webui_signup_form_submit_button_value", S.?("sign.up"))

  //overridden to allow redirect to loginRedirect after signup. This is mostly to allow
  // loginFirst menu items to work if the user doesn't have an account. Without this,
  // if a user tries to access a logged-in only page, and then signs up, they don't get redirected
  // back to the proper page.
  override def signup = {
    val theUser: TheUserType = mutateUserOnSignup(createNewUserInstance())
    val theName = signUpPath.mkString("")

    //Check the internal redirect, in case for open redirect issue.
    // variable redir is from loginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
    // val currentUrl = S.uriAndQueryString.getOrElse("/")
    // AuthUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
    val loginRedirectSave = loginRedirect.is

    def testSignup() {
      validateSignup(theUser) match {
        case Nil =>
          //here we check loginRedirectSave (different from implementation in super class)
          val redir = loginRedirectSave match {
            case Full(url) =>
              loginRedirect(Empty)
              url
            case _ =>
              //if the register page url (user_mgt/sign_up?after-signup=link-to-customer) contains the parameter 
              //after-signup=link-to-customer,then it will redirect to the on boarding customer page.
              S.params("after-signup") match { 
                case url if (url.nonEmpty && url.head.equals("link-to-customer")) =>
                  "/add-user-auth-context-update-request"
                case _ =>
                  homePage
            }
          }
          if (Helper.isValidInternalRedirectUrl(redir.toString)) {
            actionsAfterSignup(theUser, () => {
              S.redirectTo(redir)
            })
          } else {
            S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
            logger.info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
          }

        case xs =>
          xs.foreach{
            e => S.error(e.field.uniqueFieldId.openOrThrowException("There is no uniqueFieldId."), e.msg)
          }
          signupFunc(Full(innerSignup _))
      }
    }

    def innerSignup = {
      val bind = "type=submit" #> signupSubmitButton(signupSubmitButtonValue, testSignup _)
      bind(signupXhtml(theUser))
    }

    innerSignup
  }
}
