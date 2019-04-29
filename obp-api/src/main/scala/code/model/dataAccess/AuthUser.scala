/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.model.dataAccess

import code.accountholders.AccountHolders
import code.api.util.APIUtil.{hasAnOAuthHeader, isValidStrongPassword, _}
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.{DirectLogin, GatewayLogin, OAuthHandshake}
import code.bankconnectors.{Connector, InboundUser}
import code.loginattempts.LoginAttempt
import code.users.Users
import code.util.Helper
import code.views.Views
import com.openbankproject.commons.model.{User, _}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.mapper._
import net.liftweb.util.Mailer.{BCC, From, Subject, To}
import net.liftweb.util._

import scala.collection.immutable.List
import scala.xml.{NodeSeq, Text}


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
class AuthUser extends MegaProtoUser[AuthUser] with Logger {
  def getSingleton = AuthUser // what's the "meta" server

  object user extends MappedLongForeignKey(this, ResourceUser)

  /**
    * The username field for the User.
    */
  lazy val username: userName = new userName()
  class userName extends MappedString(this, 64) {
    override def displayName = S.?("username")
    override def dbIndexed_? = true
    override def validations = valUnique(S.?("unique.username")) _ :: super.validations
    override val fieldId = Some(Text("txtUsername"))
  }

  override lazy val password = new MyPasswordNew
  
  class MyPasswordNew extends MappedPassword(this) {
    
    override def displayName = fieldOwner.passwordDisplayName
    
    private var passwordValue = ""
    private var invalidPw = false
    private var invalidMsg = ""

    // TODO Remove double negative and abreviation.
    // TODO  “invalidPw” = false -> “strongPassword = true” etc.
    override def setFromAny(f: Any): String = {
      f match {
        case a: Array[String] if (a.length == 2 && a(0) == a(1)) => {
          passwordValue = a(0).toString
          if (isValidStrongPassword(passwordValue))
            invalidPw = false
          else {
            invalidPw = true
            invalidMsg = S.?(ErrorMessages.InvalidStrongPasswordFormat)
          }
          this.set(a(0))
        }
        case l: List[_] if (l.length == 2 && l.head.asInstanceOf[String] == l(1).asInstanceOf[String]) => {
          passwordValue = l(0).asInstanceOf[String]
          if (isValidStrongPassword(passwordValue))
            invalidPw = false
          else {
            invalidPw = true
            invalidMsg = S.?(ErrorMessages.InvalidStrongPasswordFormat)
          }
          
          this.set(l.head.asInstanceOf[String])
        }
        case _ => {
          invalidPw = true;
          invalidMsg = S.?("passwords.do.not.match")
        }
      }
      get
    }
    
    override def validate: List[FieldError] = {
      if (super.validate.nonEmpty) super.validate
      else if (!invalidPw && password.get != "*") Nil
      else if (invalidPw) List(FieldError(this, Text(invalidMsg)))
      else List(FieldError(this, Text(S.?("password.must.be.set"))))
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

  def getResourceUserByUsername(username: String) : Box[ResourceUser] = {
    Users.users.vend.getUserByUserName(username)
  }

  override def save(): Boolean = {
    if(! (user defined_?)){
      info("user reference is null. We will create a ResourceUser")
      val resourceUser = createUnsavedResourceUser()
      val savedUser = Users.users.vend.saveResourceUser(resourceUser)
      user(savedUser)   //is this saving resourceUser into a user field?
    }
    else {
      info("user reference is not null. Trying to update the ResourceUser")
      Users.users.vend.getResourceUserByResourceUserId(user.get).map{ u =>{
          info("API User found ")
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
    override def validate = if (isEmailValid(i_is_!)) Nil else List(FieldError(this, Text(S.?("invalid.email.address"))))
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
        debug(ErrorMessages.CurrentUserNotFoundException)
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
     getCurrentUser match{
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
      case user if user.validated_? =>
        user.resetUniqueId().save
        val resetLink = APIUtil.getPropsValue("hostname", "ERROR")+
          passwordResetPath.mkString("/", "/", "/")+urlEncode(user.getUniqueId())
        Mailer.sendMail(From(emailFrom),Subject(passwordResetEmailSubject + " - " + user.username),
          To(user.getEmail) ::
            generateResetEmailBodies(user, resetLink) :::
            (bccEmail.toList.map(BCC(_))) :_*)
      case user =>
        sendValidationEmail(user)
      case _ => 
        // Avoid any action
    }
    // In order to prevent any leakage of information we use the same message for all cases
    S.notice(userNameNotFoundString)
    S.redirectTo(homePage)
  }

  override def lostPasswordXhtml = {
    <div id="recover-password">
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
   * Overriden to use the hostname set in the props file
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

  /**
   * Set this to redirect to a certain page after a failed login
   */
  object failedLoginRedirect extends SessionVar[Box[String]](Empty) {
    override lazy val __nameSalt = Helpers.nextFuncName
  }


  def agreeTerms = {
    val url = APIUtil.getPropsValue("webui_agree_terms_url", "")
    if (url.isEmpty) {
      s""
    } else {
      scala.xml.Unparsed(s"""<div id="signup-agree-terms" class="checkbox"><label><input type="checkbox" />I hereby agree to the <a href="$url" title="T &amp; C">Terms and Conditions</a></label></div>""")
    }
  }

  def agreePrivacyPolicy = {
    val url = APIUtil.getPropsValue("webui_agree_privacy_policy_url", "")
    if (url.isEmpty) {
      s""
    } else {
      scala.xml.Unparsed(s"""<div id="signup-agree-privacy-policy"><label>By submitting this information you consent to processing your data by TESOBE Ltd according to our <a href="$url" title="Privacy Policy">Privacy Policy</a>. TESOBE shall use this information to send you emails and provide customer support.</label></div>""")
    }
  }

  override def signupXhtml (user:AuthUser) =  {
    <div id="signup">
      <form method="post" action={S.uri}>
          <h1>Sign Up</h1>
          <div id="signup-error" class="alert alert-danger hide"><span data-lift="Msg?id=error"/></div>
          {localForm(user, false, signupFields)}
          {agreeTerms}
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
    } yield <div class="form-group"><label>{field.displayName}</label> {form}</div>
  }

  def userLoginFailed = {
    info("failed: " + failedLoginRedirect.get)
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
      info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
    }
    S.error("login", S.?("Invalid Username or Password"))
  }




  def getResourceUserId(username: String, password: String): Box[Long] = {
    findUserByUsernameLocally(username) match {
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
          info(ErrorMessages.UsernameHasBeenLocked)
          //TODO need to fix, use Failure instead, it is used to show the error message to the GUI
          Full(usernameLockedStateCode)
        }
        else {
          // Nothing worked, so just increment bad login attempts
          LoginAttempt.incrementBadLoginAttempts(username)
          Empty
        }

      case Full(user) if (user.getProvider() != APIUtil.getPropsValue("hostname","")) =>
          connector match {
            case Helper.matchAnyKafka() if ( APIUtil.getPropsAsBoolValue("kafka.user.authentication", false) &&
              ! LoginAttempt.userIsLocked(username) ) =>
                val userId = for { kafkaUser <- getUserFromConnector(username, password)
                  kafkaUserId <- tryo{kafkaUser.user} } yield {
                    LoginAttempt.resetBadLoginAttempts(username)
                    kafkaUserId.get
                }
                userId match {
                  case Full(l:Long) => Full(l)
                  case _ =>
                    LoginAttempt.incrementBadLoginAttempts(username)
                    Empty
		}
            case "obpjvm" if ( APIUtil.getPropsAsBoolValue("obpjvm.user.authentication", false) &&
              ! LoginAttempt.userIsLocked(username) ) =>
                val userId = for { obpjvmUser <- getUserFromConnector(username, password)
                  obpjvmUserId <- tryo{obpjvmUser.user} } yield {
                    LoginAttempt.resetBadLoginAttempts(username)
                    obpjvmUserId.get
                }
                userId match {
                  case Full(l:Long) => Full(l)
                  case _ =>
                    LoginAttempt.incrementBadLoginAttempts(username)
                    Empty
              }
            case _ =>
              LoginAttempt.incrementBadLoginAttempts(username)
              Empty
          }

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
  def getUserFromConnector(name: String, password: String):Box[AuthUser] = {
    Connector.connector.vend.getUser(name, password) match {
      case Full(InboundUser(extEmail, extPassword, extUsername)) => {
        info("external user authenticated. login redir: " + loginRedirect.get)
        val redir = loginRedirect.get match {
          case Full(url) =>
            loginRedirect(Empty)
            url
          case _ =>
            homePage
        }

        val extProvider = connector

        val user = findUserByUsernameLocally(name) match {
          // Check if the external user is already created locally
          case Full(user) if user.validated_?
            // && user.provider == extProvider
            => {
            // Return existing user if found
            info("external user already exists locally, using that one")
            user
          }

          // If not found, create new user
          case _ => {
            // Create AuthUser using fetched data from Kafka
            // assuming that user's email is always validated
            info("external user "+ extEmail +" does not exist locally, creating one")
            val newUser = AuthUser.create
              .firstName(extUsername)
              .email(extEmail)
              .username(extUsername)
              // No need to store password, so store dummy string instead
              .password(generateUUID())
              .provider(extProvider)
              .validated(true)
            // Save the user in order to be able to log in
            newUser.save()
            // Return created user
            newUser
          }
        }
        Full(user)
      }
      case _ => {
        Empty
      }
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
  override def login = {
    def loginAction = {
      if (S.post_?) {
        val usernameFromGui = S.param("username").getOrElse("")
        val passwordFromGui = S.param("password").getOrElse("")
        findUserByUsernameLocally(usernameFromGui) match {
          // Check if user came from localhost and
          // if User is NOT locked and password is good
          case Full(user) if user.validated_? &&
            user.getProvider() == APIUtil.getPropsValue("hostname","") &&
            ! LoginAttempt.userIsLocked(usernameFromGui) &&
            user.testPassword(Full(passwordFromGui)) => {
              // Reset any bad attempts
              LoginAttempt.resetBadLoginAttempts(usernameFromGui)
              val preLoginState = capturePreLoginState()
              info("login redir: " + loginRedirect.get)
              val redir = loginRedirect.get match {
                case Full(url) =>
                  loginRedirect(Empty)
                  url
                case _ =>
                  homePage
              }
//              registeredUserHelper(user.username)
            //Check the internal redirect, in case for open redirect issue.
            // variable redir is from loginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
            // val currentUrl = S.uriAndQueryString.getOrElse("/")
            // AuthUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
            if (Helper.isValidInternalRedirectUrl(redir.toString)) {
              logUserIn(user, () => {
                S.notice(S.?("logged.in"))
                preLoginState()
                S.redirectTo(redir)
              })
            } else {
              S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
              info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
            }
          }

          // Check if user came from kafka/obpjvm and
          // if User is NOT locked. Then check username and password
          // from connector in case they changed on the south-side
          case Full(user) if user.validated_? &&
            user.getProvider() != APIUtil.getPropsValue("hostname","") &&
            ! LoginAttempt.userIsLocked(usernameFromGui) &&
            testExternalPassword(Full(user.username.get), Full(passwordFromGui)).getOrElse(false) => {
              // Reset any bad attempts
              LoginAttempt.resetBadLoginAttempts(usernameFromGui)
              val preLoginState = capturePreLoginState()
              info("login redir: " + loginRedirect.get)
              val redir = loginRedirect.get match {
                case Full(url) =>
                  loginRedirect(Empty)
                  url
                case _ =>
                  homePage
              }
              //This method is used for connector = kafka* || obpjvm*
              //It will update the views and createAccountHolder ....
              registeredUserHelper(user.username.get)
              //Check the internal redirect, in case for open redirect issue.
              // variable redir is from loginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
              // val currentUrl = S.uriAndQueryString.getOrElse("/")
              // AuthUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
              if (Helper.isValidInternalRedirectUrl(redir.toString)) {
                logUserIn(user, () => {
                  S.notice(S.?("logged.in"))
                  preLoginState()
                  S.redirectTo(redir)
                })
              } else {
                S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
                info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
              }
            }

          // If user is unlocked AND bad password, increment bad login attempt counter.
          case Full(user) if user.validated_? &&
            user.getProvider() == APIUtil.getPropsValue("hostname","") &&
            ! LoginAttempt.userIsLocked(usernameFromGui) &&
            ! user.testPassword(Full(passwordFromGui)) =>
              LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
              S.error(S.?("Invalid Login Credentials")) // TODO constant /  i18n for this string

          // If user is locked, send the error to GUI
          case Full(user) if LoginAttempt.userIsLocked(usernameFromGui) =>
            LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
            S.error(S.?(ErrorMessages.UsernameHasBeenLocked))

          case Full(user) if !user.validated_? =>
            S.error(S.?("account.validation.error")) // Note: This does not seem to get hit when user is not validated.

          // If not found locally, try to authenticate user via Kafka, if enabled in props
          case Empty if (connector.startsWith("kafka") || connector == "obpjvm") &&
            (APIUtil.getPropsAsBoolValue("kafka.user.authentication", false) ||
            APIUtil.getPropsAsBoolValue("obpjvm.user.authentication", false)) =>
              val preLoginState = capturePreLoginState()
              info("login redir: " + loginRedirect.get)
              val redir = loginRedirect.get match {
                case Full(url) =>
                  loginRedirect(Empty)
                  url
                case _ =>
                  homePage
              }
              for {
                user_ <- externalUserHelper(usernameFromGui, passwordFromGui)
              } yield {
                user_
              } match {
                case u:AuthUser =>
                  LoginAttempt.resetBadLoginAttempts(usernameFromGui)
                  //Check the internal redirect, in case for open redirect issue.
                  // variable redir is from loginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
                  // val currentUrl = S.uriAndQueryString.getOrElse("/")
                  // AuthUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
                  if (Helper.isValidInternalRedirectUrl(redir.toString)) {
                    logUserIn(u, () => {
                      S.notice(S.?("logged.in"))
                      preLoginState()
                      S.redirectTo(redir)
                    })
                  } else {
                    S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
                    info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
                  }
                case _ =>
                  LoginAttempt.incrementBadLoginAttempts(username.get)
                  Empty
              }

          //If the username is not exiting, throw the error message.  
          case Empty => S.error(S.?("Invalid Login Credentials"))
            
          case _ =>
            LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
            S.error(S.?(ErrorMessages.UnexpectedErrorDuringLogin)) // Note we hit this if user has not clicked email validation link
        }
      }
    }

    // In this function we bind submit button to loginAction function.
    // In case that unique token of submit button cannot be paired submit action will be omitted.
    // Implemented in order to prevent a CSRF attack
    def insertSubmitButton = {
      scala.xml.XML.loadString(loginSubmitButton(S.?("Login"), loginAction _).toString().replace("type=\"submit\"","class=\"submit\" type=\"submit\""))
    }

    val bind =
          "submit" #> insertSubmitButton
   bind(loginXhtml)
  }
  
  
  /**
    * The user authentications is not exciting in obp side, it need get the user from south-side
    */
 def testExternalPassword(usernameFromGui: Box[String], passwordFromGui: Box[String]): Box[Boolean] = {
   if (connector.startsWith("kafka") || connector == "obpjvm") {
     val res = for {
       username <- usernameFromGui
       password <- passwordFromGui
       user <- getUserFromConnector(username, password)
     } yield user match {
       case user:AuthUser => true
       case _ => false
     }
     res
   } else Empty
  }
  
  /**
    * This method will update the views and createAccountHolder ....
    */
  def externalUserHelper(name: String, password: String): Box[AuthUser] = {
    if (connector.startsWith("kafka") || connector == "obpjvm") {
      for {
       user <- getUserFromConnector(name, password)
       //u <- user.user.foreign  // this will be issue when the resource user is in remote side
       u <- Users.users.vend.getUserByUserName(name)
       v <- Full (updateUserAccountViews(u, None))
      } yield {
        user
      }
    } else Empty 
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
    val accounts = Connector.connector.vend.getBankAccountsByUsername(user.name,callContext).openOrThrowException(attemptedToOpenAnEmptyBox)
    debug(s"-->AuthUser.updateUserAccountViews.accounts : ${accounts} ")

    updateUserAccountViews(user, accounts._1)
  }

  /**
    * This is a helper method
    * update the views, accountHolders for OBP side when sign up new remote user
    *
    */
  def updateUserAccountViews(user: User, accounts: List[InboundAccountCommon]): Unit = {
    for {
      account <- accounts
      viewId <- account.viewsToGenerate
      bankAccountUID <- Full(BankIdAccountId(BankId(account.bankId), AccountId(account.accountId)))
      view <- Views.views.vend.getOrCreateAccountView(bankAccountUID, viewId)
    } yield {
      Views.views.vend.addPermission(view.uid, user)
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
  /**
    * Find the authUsers by author email(authUser and resourceUser are the same).
    * Only search for the local database. 
    */
  protected def findUsersByEmailLocally(email: String): List[TheUserType] = {
    val usernames: List[String] = this.getResourceUsersByEmail(email).map(_.user.name)
    findAll(ByList(this.username, usernames))
  }

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
              homePage
          }
          if (Helper.isValidInternalRedirectUrl(redir.toString)) {
            actionsAfterSignup(theUser, () => {
              S.redirectTo(redir)
            })
          } else {
            S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
            info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
          }

        case xs =>
          xs.foreach(e => S.error("error", e.msg))
          signupFunc(Full(innerSignup _))
      }
    }

    def innerSignup = {
      val bind = "type=submit" #> signupSubmitButton(S.?("sign.up"), testSignup _)
      bind(signupXhtml(theUser))
    }

    innerSignup
  }
}
