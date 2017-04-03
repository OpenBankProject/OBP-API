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
package code.model.dataAccess

import java.util.UUID

import code.api.util.APIUtil.isValidStrongPassword
import code.api.util.{APIUtil, ErrorMessages}
import code.api.{DirectLogin, OAuthHandshake}
import code.bankconnectors.{Connector, InboundUser}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.mapper._
import net.liftweb.util.Mailer.{BCC, From, Subject, To}
import net.liftweb.util._

import scala.xml.{NodeSeq, Text}
import code.loginattempts.LoginAttempt
import code.users.Users
import code.util.Helper
import net.liftweb.util


/**
 * An O-R mapped "User" class that includes first name, last name, password
  *
  *
  * // TODO Document the difference between this and AuthUser / ResourceUser
  *
 */
class AuthUser extends MegaProtoUser[AuthUser] with Logger {
  def getSingleton = AuthUser // what's the "meta" server

  object resourceUserId extends MappedString(this, 100) {
    override def defaultValue = java.util.UUID.randomUUID.toString
  }

  //object user extends MappedLongForeignKey(this, ResourceUser)
  lazy val user = new resourceUser()
  class resourceUser {
    def apply( a: AuthUser ) : Box[ResourceUser]  = {
      Users.users.vend.getResourceUserByUserId(a.resourceUserId.get)
    }
    def obj : Box[ResourceUser] = Users.users.vend.getResourceUserByUserId(resourceUserId.get)
    def get : Long = Users.users.vend.getResourceUserByUserId(resourceUserId.get).get.id.get
    def defined_? : Boolean = Users.users.vend.getResourceUserByUserId(resourceUserId.get).isDefined
  }

  /**
    * The username field for the User.
    */
  //lazy val username: userName = new userName()
  //class userName extends MappedString(this, 64) {
  //  override def displayName = S.?("username")
  //  override def dbIndexed_? = true
  //  override def validations = valUnique(S.?("unique.username")) _ :: super.validations
  //  override val fieldId = Some(Text("txtUsername"))
  //}

  def username: String = Users.users.vend.getResourceUserByUserId(resourceUserId.get) match {
    case Full(u) => u.name
    case Empty => println("++++++++++++++++++++++++"); ""
  }
  //class Username {
    //private final var username_ = Users.users.vend.getResourceUserByUserId(resourceUserId.get).get.name
    //def apply( u: String ) : Box[String]  = {
    //    Users.users.vend.getUserByUserId(resourceUserId.get).map { u =>
    //    u.name
    //  }
    //}
    //def obj : Box[String] = Users.users.vend.getUserByUserId(resourceUserId.get).map { u =>
    //  u.name
    //}
    //def get : String = Users.users.vend.getResourceUserByUserId(resourceUserId.get).get.name
    //def defined_? : Boolean = Users.users.vend.getResourceUserByUserId(resourceUserId.get).isDefined
  //}

  override lazy val password = new MyPasswordNew
  
  class MyPasswordNew extends MappedPassword(this) {
    
    override def displayName = fieldOwner.passwordDisplayName
    
    private var passwordValue = ""
    private var invalidPw = false
    private var invalidMsg = ""
    
    override def setFromAny(f: Any): String = {
      f match {
        case a: Array[String] if a.length == 2 && a(0) == a(1) =>
          passwordValue = a.head.toString
          if (isValidStrongPassword(passwordValue))
            invalidPw = false
          else {
            invalidPw = true
            invalidMsg = S.?(ErrorMessages.InvalidStrongPasswordFormat)
          }
          this.set(a.head)

        case l: List[String] if l.length == 2 && l.head == l(1) =>
          passwordValue = l.head.toString
          if (isValidStrongPassword(passwordValue))
            invalidPw = false
          else {
            invalidPw = true
            invalidMsg = S.?(ErrorMessages.InvalidStrongPasswordFormat)
          
          this.set(l.head)
        }
        case _ =>
          invalidPw = true
          invalidMsg = S.?("passwords.do.not.match")
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
      Props.get("hostname","")
    } else if ( provider.get == "" || provider.get == Props.get("hostname","") ) {
      Props.get("hostname","")
    } else {
      provider.get
    }
  }

  def getResourceUsersByEmail(userEmail: String) : List[ResourceUser] = {
    Users.users.vend.getUserByEmail(userEmail) match {
      case Full(userList) => userList
      case Empty => List()
    }
  }

  def getResourceUsers(): List[ResourceUser] = {
    Users.users.vend.getAllUsers match {
      case Full(userList) => userList
      case Empty => List()
    }
  }

  def getResourceUserByUsername(username: String) : Box[ResourceUser] = {
    Users.users.vend.getUserByUserName(username)
  }

  override def save(): Boolean = {
    //if(! (user defined_?)){
    //  info("Error - ResourceUser reference is null. Could not create AuthUser")
    //  false
    //}
    //else {
    //  info("ResourceUser reference is not null. Creating AuthUser")
      super.save()
    //}
  }

  override def delete_!(): Boolean = {
    user.obj.map(u => Users.users.vend.deleteResourceUser(u.id))
    super.delete_!
  }

  // Regex to validate an email address as per W3C recommendations: https://www.w3.org/TR/html5/forms.html#valid-e-mail-address
  private val emailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def isEmailValid(e: String): Boolean = e match{
    case null                                            => false
    case em if em.trim.isEmpty                           => false
    case em if emailRegex.findFirstMatchIn(em).isDefined => true
    case _                                               => false
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

  val connector = Props.get("connector").openOrThrowException("no connector set")

  override def emailFrom = Props.get("mail.users.userinfo.sender.address", "sender-not-set")

  override def screenWrap = Full(<lift:surround with="default" at="content"><lift:bind /></lift:surround>)
  // define the order fields will appear in forms and output
//  override def fieldOrder = List(id, firstName, lastName, email, username, password, provider)
//  override def signupFields = List(firstName, lastName, email, username, password)
  override def fieldOrder = List(id, firstName, lastName, email, password, provider)
  override def signupFields = List(firstName, lastName, email, password)

  // If we want to validate email addresses set this to false
  override def skipEmailValidation = Props.getBool("authUser.skipEmailValidation", true)

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
   * Find current user
   */
  def getCurrentUserUsername: String = {
    for {
      current <- AuthUser.currentUser
      username <- tryo{current.username}
      if (username.nonEmpty)
    } yield {
      return username
    }

    for {
      current <- OAuthHandshake.getUser
      username <- tryo{current.name}
      if username.nonEmpty
    } yield {
      return username
    }

    for {
      current <- DirectLogin.getUser
      username <- tryo{current.name}
      if username.nonEmpty
    } yield {
      return username
    }

    return ""
  }
  /**
    * Find current ResourceUser_UserId from AuthUser, it is only used for Consumer registration form to save the USER_ID when register new consumer.
    */
  //TODO may not be a good idea, need modify after refactoring User Models.
  def getCurrentResourceUserUserId: String = {
    for {
      current <- AuthUser.currentUser
      user <- Users.users.vend.getUserByResourceUserId(current.user.get)
    } yield {
      return user.userId
    }

    for {
      current <- OAuthHandshake.getUser
      userId <- tryo{current.userId}
      if userId.nonEmpty
    } yield {
      return userId
    }

    for {
      current <- DirectLogin.getUser
      userId <- tryo{current.userId}
      if userId.nonEmpty
    } yield {
      return userId
    }

     return ""
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
    findUserByUsername(name) match {
      case Full(u) if u.validated_? =>
        u.resetUniqueId().save()
        val resetLink = Props.get("hostname", "ERROR")+
          passwordResetPath.mkString("/", "/", "/")+urlEncode(u.getUniqueId())

        Mailer.sendMail(From(emailFrom),Subject(passwordResetEmailSubject),
          To(u.getEmail) ::
            generateResetEmailBodies(u, resetLink) ::: bccEmail.toList.map(BCC(_)) :_*)

        S.notice(S.?(userNameNotFoundString))
        S.redirectTo(homePage)

      case Full(u) =>
        sendValidationEmail(u)
        S.notice(S.?("account.validation.resent"))
        S.redirectTo(homePage)

      case _ => S.error(userNameNotFoundString)
    }
  }

  override def lostPasswordXhtml = {
    <div id="authorizeSection">
      <div id="userAccess">
        <div class="account account-in-content">
          Enter your email address or username and we'll email you a link to reset your password
          <form class="forgotPassword" action={S.uri} method="post">
            <div class="field username">
              <label>Username or email address</label> <user:email />
            </div>
            <div class="field buttons">
              <div class="button button-field">
                <user:submit />
              </div>
            </div>
          </form>
        </div>
      </div>
    </div>
  }

  override def lostPassword = {
    bind("user", lostPasswordXhtml,
      "email" -> SHtml.text("", sendPasswordReset _),
      "submit" -> lostPasswordSubmitButton(S.?("submit")))
  }

  //override def def passwordResetMailBody(user: TheUserType, resetLink: String): Elem = { }

  /**
   * Overriden to use the hostname set in the props file
   */
  override def sendValidationEmail(user: TheUserType) {
    val resetLink = Props.get("hostname", "ERROR")+"/"+validateUserPath.mkString("/")+
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
    val url = Props.get("webui_agree_terms_url", "")
    if (url.isEmpty) {
      s""
    } else {
      scala.xml.Unparsed(s"""<tr><td colspan="2"><input type="checkbox" id="agree-terms-input" /><span id="agree-terms-text">I hereby agree to the <a href="$url" title="T &amp; C">Terms and Conditions</a></span></td></tr>""")
    }
  }


  override def signupXhtml (user:AuthUser) =  {
    <div id="authorizeSection" class="signupSection">
      <div class="signup-error"><span class="lift:Msg?id=signup"/></div>
      <div>
        <form id="signupForm" method="post" action={S.uri}>
          <table>
            <tr>
              <td colspan="2">{ S.?("sign.up") }</td>
            </tr>
              {localForm(user, false, signupFields)}
              {agreeTerms}
            <tr>
              <td>&nbsp;</td>
              <td><user:submit/></td>
            </tr>
          </table>
        </form>
      </div>
    </div>
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


  def createAuthUser(mail: String, uname: String, pass: String): AuthUser = {
    val prov = getProvider()
    Users.users.vend.createResourceUser(
      prov,
      Some(prov),
      Some(uname),
      Some(mail),
      Some(resourceUserId.get))
    AuthUser.create
      .firstName(uname)
      .email(mail)
      //.username(uname)
      // No need to store password, so store dummy string instead
      .password(pass)
      .validated(true)
      .saveMe
  }


  def getResourceUserId(username: String, password: String): Box[Long] = {
    findUserByUsername(username) match {
      case Full(u) if u.getProvider() == Props.get("hostname","") =>
        if (
          u.validated_? &&
          // User is NOT locked AND the password is good
          ! LoginAttempt.userIsLocked(username) &&
          u.testPassword(Full(password)))
            {
              // We logged in correctly, so reset badLoginAttempts counter (if it exists)
              LoginAttempt.resetBadLoginAttempts(username)
              Full(u.user.get) // Return the user.
            }
        // User is unlocked AND password is bad
        else if (
          u.validated_? &&
          ! LoginAttempt.userIsLocked(username) &&
          ! u.testPassword(Full(password))
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

      case Full(u) if u.getProvider() != Props.get("hostname","") =>
          connector match {
            case Helper.matchAnyKafka() if  Props.getBool("kafka.user.authentication", false) &&
              ! LoginAttempt.userIsLocked(username) =>
                val userId = for {
                    kafkaUser <- getUserFromConnector(username, password)
                    kafkaUserId <- tryo{kafkaUser.user}
                  } yield {
                    LoginAttempt.resetBadLoginAttempts(username)
                    kafkaUserId.get
                  }
                userId match {
                  case Full(l:Long) => Full(l)
                  case _ =>
                    LoginAttempt.incrementBadLoginAttempts(username)
                    Empty
		}
            case "obpjvm" if  Props.getBool("obpjvm.user.authentication", false) &&
              ! LoginAttempt.userIsLocked(username) =>
                val userId = for { 
                    obpjvmUser <- getUserFromConnector(username, password)
                    obpjvmUserId <- tryo{obpjvmUser.user}
                  } yield {
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

        val user = findUserByUsername(name) match {
          // Check if the external user is already created locally
          case Full(u) if u.validated_?
            // && user.provider == extProvider
            => {
            // Return existing user if found
            info("external user already exists locally, using that one")
            Full(u)
          }

          // If not found, create new user
          case _ =>
            // Create AuthUser using fetched data from Kafka
            // assuming that user's email is always validated
            info("external user "+ extUsername +" with email "+ extEmail +" does not exist locally, creating one")
            val newUser = createAuthUser(extEmail, extUsername, extPassword)
            // Return created user
            Full(newUser)
        }
        user
      }
      case _ => Empty

    }
  }

  //overridden to allow a redirection if login fails
  override def login = {
    def loginAction = {
      if (S.post_?) {
        val usernameFromGui = S.param("username").getOrElse("")
        val passwordFromGui = S.param("password").getOrElse("")
        findUserByUsername(usernameFromGui) match {
          // Check if user came from localhost and
          // if User is NOT locked and password is good
          case Full(u) if u.validated_? &&
            u.getProvider() == Props.get("hostname","") &&
            ! LoginAttempt.userIsLocked(usernameFromGui) &&
            u.testPassword(Full(passwordFromGui)) => {
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
              registeredUserHelper(u.username)
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
          }

          // Check if user came from kafka/obpjvm and
          // if User is NOT locked. Then check username and password
          // from connector in case they changed on the south-side
          case Full(u) if u.validated_? &&
            u.getProvider() != Props.get("hostname","") &&
            ! LoginAttempt.userIsLocked(usernameFromGui) &&
            testExternalPassword(Full(usernameFromGui), Full(passwordFromGui)).getOrElse(false) => {
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
              registeredUserHelper(u.username)
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
            }

          // If user is unlocked AND bad password, increment bad login attempt counter.
          case Full(u) if u.validated_? &&
            u.getProvider() == Props.get("hostname","") &&
            ! LoginAttempt.userIsLocked(usernameFromGui) &&
            ! u.testPassword(Full(passwordFromGui)) =>
              LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
              S.error(S.?("Invalid Login Credentials")) // TODO constant /  i18n for this string

          // If user is locked, send the error to GUI
          case Full(u) if LoginAttempt.userIsLocked(usernameFromGui) =>
            LoginAttempt.incrementBadLoginAttempts(usernameFromGui)
            S.error(S.?(ErrorMessages.UsernameHasBeenLocked))

          case Full(u) if ! u.validated_? =>
            S.error(S.?("account.validation.error")) // Note: This does not seem to get hit when user is not validated.

          // If not found locally, try to authenticate user via Kafka, if enabled in props
          case Empty if (connector.startsWith("kafka") || connector == "obpjvm") &&
            (Props.getBool("kafka.user.authentication", false) ||
            Props.getBool("obpjvm.user.authentication", false)) =>
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
                case au:AuthUser =>
                  LoginAttempt.resetBadLoginAttempts(usernameFromGui)
                  //Check the internal redirect, in case for open redirect issue.
                  // variable redir is from loginRedirect, it is set-up in OAuthAuthorisation.scala as following code:
                  // val currentUrl = S.uriAndQueryString.getOrElse("/")
                  // AuthUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
                  if (Helper.isValidInternalRedirectUrl(redir.toString)) {
                    logUserIn(au, () => {
                      S.notice(S.?("logged.in"))
                      preLoginState()
                      S.redirectTo(redir)
                    })
                  } else {
                    S.error(S.?(ErrorMessages.InvalidInternalRedirectUrl))
                    info(ErrorMessages.InvalidInternalRedirectUrl + loginRedirect.get)
                  }
                case _ =>
                  LoginAttempt.incrementBadLoginAttempts(username)
                  Empty
              }
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

    bind("user", loginXhtml,
         "submit" -> insertSubmitButton)
  }


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


  def externalUserHelper(name: String, password: String): Box[AuthUser] = {
    if (connector.startsWith("kafka") || connector == "obpjvm") {
      for {
       user <- getUserFromConnector(name, password)
       u <- Users.users.vend.getUserByUserName(username)
       v <- tryo {Connector.connector.vend.updateUserAccountViews(u)}
      } yield {
        user
      }
    } else Empty 
  }


  def registeredUserHelper(username: String) = {
    if (connector.startsWith("kafka") || connector == "obpjvm") {
      for {
       u <- Users.users.vend.getUserByUserName(username)
       v <- tryo {Connector.connector.vend.updateUserAccountViews(u)}
      } yield v
    }
  }

  def findUserByUsername(name: String): Box[TheUserType] = {
    val findId:String = Users.users.vend.getUserByUserName(name).map { u =>
      u.userId
    } match {
      case Full(i) => i
      case Empty => ""
    }
    find(By(this.resourceUserId, findId))
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
          xs.foreach(e => S.error("signup", e.msg))
          signupFunc(Full(innerSignup _))
      }
    }

    def innerSignup = bind("user",
      signupXhtml(theUser),
      "submit" -> signupSubmitButton(S.?("sign.up"), testSignup _))

    innerSignup
  }
}
