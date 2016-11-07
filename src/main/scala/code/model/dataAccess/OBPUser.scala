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

import code.api.{DirectLogin, OAuthHandshake}
import code.bankconnectors.KafkaMappedConnector
import code.bankconnectors.KafkaMappedConnector.KafkaInboundUser
import net.liftweb.common._
import net.liftweb.http.js.JsCmds.FocusOnLoad
import net.liftweb.http.{S, SHtml, SessionVar, Templates}
import net.liftweb.mapper._
import net.liftweb.util.Mailer.{BCC, From, Subject, To}
import net.liftweb.util._

import scala.xml.{NodeSeq, Text}

/**
 * An O-R mapped "User" class that includes first name, last name, password
  *
  *
  * // TODO Document the difference between this and User / APIUser
  *
 */
class OBPUser extends MegaProtoUser[OBPUser] with Logger {
  def getSingleton = OBPUser // what's the "meta" server

  object user extends MappedLongForeignKey(this, APIUser)

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

  def createUnsavedApiUser() : APIUser = {
    APIUser.create
      .name_(username)
      .email(email)
      .provider_(getProvider())
      .providerId(username)
  }

  def getApiUsersByEmail(userEmail: String) : List[APIUser] = {
    APIUser.findAll(By(APIUser.email, userEmail))
  }

  def getApiUserByUsername(username: String) : Box[APIUser] = {
    APIUser.find(By(APIUser.name_, username))
  }

  override def save(): Boolean = {
    if(! (user defined_?)){
      info("user reference is null. We will create an API User")
      val apiUser = createUnsavedApiUser()
      apiUser.save()
      user(apiUser)   //is this saving apiUser into a user field?
    }
    else {
      info("user reference is not null. Trying to update the API User")
      user.obj.map{ u =>{
          info("API User found ")
          u.name_(username)
          .email(email)
          .providerId(username)
          .save
        }
      }
    }
    super.save()
  }

  override def delete_!(): Boolean = {
    user.obj.map{_.delete_!}
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
object OBPUser extends OBPUser with MetaMegaProtoUser[OBPUser]{
import net.liftweb.util.Helpers._

  override def emailFrom = Props.get("mail.users.userinfo.sender.address", "sender-not-set")

  override def dbTableName = "users" // define the DB table name

  override def screenWrap = Full(<lift:surround with="default" at="content"><lift:bind /></lift:surround>)
  // define the order fields will appear in forms and output
  override def fieldOrder = List(id, firstName, lastName, email, username, password, provider)
  override def signupFields = List(firstName, lastName, email, username, password)

  // comment this line out to require email validations
  override def skipEmailValidation = true

  override def loginXhtml = {
    val loginXml = Templates(List("templates-hidden","_login")).map({
        "form [action]" #> {S.uri} &
        "#loginText * " #> {S.?("log.in")} &
        "#usernameText * " #> {S.?("username")} &
        "#passwordText * " #> {S.?("password")} &
        "#recoverPasswordLink * " #> {
          "a [href]" #> {lostPasswordPath.mkString("/", "/", "")} &
          "a *" #> {S.?("recover.password")}
        } &
        "#SignUpLink * " #> {
          "a [href]" #> {OBPUser.signUpPath.foldLeft("")(_ + "/" + _)} &
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
      current <- OBPUser.currentUser
      username <- tryo{current.username.get}
      if (username.nonEmpty)
    } yield {
      return username
    }

    for {
      current <- OAuthHandshake.getUser
      username <- tryo{current.name}
      if (username.nonEmpty)
    } yield {
      return username
    }

    for {
      current <- DirectLogin.getUser
      username <- tryo{current.name}
      if (username.nonEmpty)
    } yield {
      return username
    }

    return ""
  }

  /**
   * Overridden to use the hostname set in the props file
   */
  override def sendPasswordReset(name: String) {
    findUserByUsername(name) match {
      case Full(user) if user.validated_? =>
        user.resetUniqueId().save
        val resetLink = Props.get("hostname", "ERROR")+
          passwordResetPath.mkString("/", "/", "/")+urlEncode(user.getUniqueId())

        Mailer.sendMail(From(emailFrom),Subject(passwordResetEmailSubject),
          To(user.getEmail) ::
            generateResetEmailBodies(user, resetLink) :::
            (bccEmail.toList.map(BCC(_))) :_*)

        S.notice(S.?("password.reset.email.sent"))
        S.redirectTo(homePage)

      case Full(user) =>
        sendValidationEmail(user)
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


  override def signupXhtml (user:OBPUser) =  {
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
    failedLoginRedirect.get.foreach(S.redirectTo(_))
    S.error("login", S.?("Invalid Username or Password"))
  }


  // What if we just want to return the userId without sending username/password??

  def getAPIUserId(name: String, password: String): Box[Long] = {
    findUserByUsername(name) match {
      case Full(user) => {
        if (user.validated_? &&
          user.getProvider() == Props.get("hostname","") &&
          user.testPassword(Full(password)))
        {
          Full(user.user)
        }
        else {
          if (Props.getBool("kafka.user.authentication", false)) {
          Props.get("connector").openOrThrowException("no connector set") match {
            case "kafka" =>
              for { kafkaUser <- getUserFromKafka(name, password)
                    kafkaUserId <- tryo{kafkaUser.user} } yield kafkaUserId.toLong
            case _ => Empty
          }
        } else {
            Empty }
        }
      }
      case _ => Empty 
    }
  }


  def getUserFromKafka(name: String, password: String):Box[OBPUser] = {
    KafkaMappedConnector.getUser(name, password) match {
      case Full(KafkaInboundUser(extEmail, extPassword, extUsername)) => {
        info("external user authenticated. login redir: " + loginRedirect.get)
        val redir = loginRedirect.get match {
          case Full(url) =>
            loginRedirect(Empty)
            url
          case _ =>
            homePage
        }

        val extProvider = Props.get("connector").openOrThrowException("no connector set")

        val user = findUserByUsername(name) match {
          // Check if the external user is already created locally
          case Full(user) if user.validated_? &&
            user.provider == extProvider => {
            // Return existing user if found
            info("external user already exists locally, using that one")
            user
          }

          // If not found, create new user
          case _ => {
            // Create OBPUser using fetched data from Kafka
            // assuming that user's email is always validated
            info("external user "+ extEmail +" does not exist locally, creating one")
            val newUser = OBPUser.create
              .firstName(extUsername)
              .email(extEmail)
              .username(extUsername)
              // No need to store password, so store dummy string instead
              .password(UUID.randomUUID().toString)
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

  //overridden to allow a redirection if login fails
  override def login = {
    if (S.post_?) {
      S.param("username").
      flatMap(name => findUserByUsername(name)) match {
        case Full(user) if user.validated_? &&
          // Check if user came from localhost
          user.getProvider() == Props.get("hostname","") &&
          user.testPassword(S.param("password")) => {
            val preLoginState = capturePreLoginState()
            info("login redir: " + loginRedirect.get)
            val redir = loginRedirect.get match {
              case Full(url) =>
                loginRedirect(Empty)
                url
              case _ =>
                homePage
            }
            registeredUserHelper(user.username)
            logUserIn(user, () => {
              S.notice(S.?("logged.in"))
              preLoginState()
              S.redirectTo(redir)
            })
          }

        case Full(user) if !user.validated_? =>
          S.error(S.?("account.validation.error"))

        case _ => if (Props.get("connector").openOrThrowException("no connector set") == "kafka")
        {
          // If not found locally, try to authenticate user via Kafka, if enabled in props
          if (Props.getBool("kafka.user.authentication", false)) {
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
              user_ <- externalUserHelper(S.param("username").getOrElse(""), S.param("password").getOrElse(""))
            } yield {
              logUserIn(user_, () => {
                S.notice(S.?("logged.in"))
                preLoginState()
                S.redirectTo(redir)
              })
            }
          } else {
            S.error(S.?("account.validation.error"))
          }
        } else {
          S.error(S.?("account.validation.error"))
        }
      }
    }

    bind("user", loginXhtml,
         "username" -> (FocusOnLoad(<input type="text" name="username"/>)),
         "password" -> (<input type="password" name="password"/>),
         "submit" -> loginSubmitButton(S.?("log.in")))
  }


  def externalUserHelper(name: String, password: String): Box[OBPUser] = {
    if (Props.get("connector").openOrThrowException("no connector set") == "kafka") {
      for {
       user <- getUserFromKafka(name, password)
       u <- APIUser.find(By(APIUser.name_, user.username))
       v <- tryo {KafkaMappedConnector.updateUserAccountViews(u)}
      } yield {
        user
      }
    } else Empty 
  }


  def registeredUserHelper(username: String) = {
    if (Props.get("connector").openOrThrowException("no connector set") == "kafka") {
      for {
       u <- APIUser.find(By(APIUser.name_, username))
       v <- tryo {KafkaMappedConnector.updateUserAccountViews(u)}
      } yield v
    }
  }

  protected def findUserByUsername(name: String): Box[TheUserType] = {
    find(By(this.username, name))
  }

  //overridden to allow redirect to loginRedirect after signup. This is mostly to allow
  // loginFirst menu items to work if the user doesn't have an account. Without this,
  // if a user tries to access a logged-in only page, and then signs up, they don't get redirected
  // back to the proper page.
  override def signup = {
    val theUser: TheUserType = mutateUserOnSignup(createNewUserInstance())
    val theName = signUpPath.mkString("")

    //save the intented login redirect here, as it gets wiped (along with the session) on login
    val loginRedirectSave = loginRedirect.is

    def testSignup() {
      validateSignup(theUser) match {
        case Nil =>
          actionsAfterSignup(theUser, () => {
            //here we check loginRedirectSave (different from implementation in super class)
            val redir = loginRedirectSave match {
              case Full(url) =>
                loginRedirect(Empty)
                url
              case _ =>
                homePage
            }
            S.redirectTo(redir)
          })

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
