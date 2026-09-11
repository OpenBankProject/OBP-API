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
import code.api.util.CommonsEmailWrapper._
import code.api.util.ErrorMessages._
import code.api.util._
import code.bankconnectors.Connector
import code.context.UserAuthContextProvider
import code.model.toUserExtended
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.token.TokensOpenIDConnect
import code.users.{UserAgreementProvider, Users}
import code.util.Helper
import code.util.Helper.{MdcLoggable, ObpS}
import code.views.Views
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.util.Helpers.tryo
import org.mindrot.jbcrypt.BCrypt
import org.apache.commons.lang3.StringUtils

import java.util.UUID.randomUUID
import scala.concurrent.Future
import scala.xml.{Elem, NodeSeq, Text}

/**
  * 1 AuthUser: used for authentication only - the credentials and the sign-up, email-validation and
  *   password-reset flows around them.
  *
  * 2 ResourceUser: everything else. All the accounts, transactions, roles, views, accountHolders,
  *   customers... are linked to its userId field, and the consumer keys and tokens belong to it too.
  *
  * 3 RelationShips:
  *   1) When `Sign up` new user --> create AuthUser --> call AuthUser.save --> create ResourceUser.
  *      They share the same username and email.
  *   2) AuthUser's `user` field is the foreign key to the ResourceUser.
  *      one AuthUser <---> one ResourceUser
 */
/**
 * The login half of a user: a username, a password, an email address and the ResourceUser they
 * belong to.
 *
 * 1 AuthUser is used for authentication only - the credentials and the sign-up/validation flow.
 * 2 ResourceUser is what the rest of the API hangs off: accounts, transactions, roles, views,
 *   account holders, customers, consumers and tokens all reference its userId.
 * 3 Signing up creates an AuthUser, whose save creates the matching ResourceUser; they share a
 *   username and email, and `user` holds RESOURCEUSER.ID.
 *
 * The password lives in two columns because that is how MappedPassword stored it and how
 * v_oidc_users - the view OBP-OIDC and the Keycloak provider authenticate against - reads it back.
 * See AuthUser.hashPassword for the format.
 */
case class AuthUser(
  id: Long = 0L,
  firstName: String = "",
  lastName: String = "",
  email: String = "",
  username: String = "",
  passwordPw: String = AuthUser.unsetPassword,
  passwordSlt: String = "",
  provider: String = Constant.localIdentityProvider,
  uniqueId: String = Helpers.randomString(32),
  superUser: Boolean = false,
  validated: Boolean = false,
  passwordShouldBeChanged: Boolean = false,
  locale: String = java.util.Locale.getDefault.toString,
  timezone: String = java.util.TimeZone.getDefault.getID,
  user: Long = 0L,
  createdAt: java.util.Date = null,
  updatedAt: java.util.Date = null
) extends MdcLoggable {

  def getProvider() = {
    if(provider == null || provider.isEmpty) Constant.localIdentityProvider else provider
  }

  def getEmail: String = email
  def getUniqueId(): String = uniqueId
  def validated_? : Boolean = validated
  def setValidated(value: Boolean): AuthUser = copy(validated = value)
  def resetUniqueId(): AuthUser = copy(uniqueId = Helpers.randomString(32))

  /** Hashes `plain` into the two password columns, as MappedPassword did on every set. */
  def withPassword(plain: String): AuthUser = {
    val (pw, salt) = AuthUser.hashPassword(plain)
    copy(passwordPw = pw, passwordSlt = salt)
  }

  /** What MappedPassword.match_? did: bcrypt when the hash is prefixed, the legacy digest else. */
  def testPassword(toMatch: Box[String]): Boolean =
    toMatch.map(AuthUser.matchPassword(_, passwordPw, passwordSlt)).openOr(false)

  def createUnsavedResourceUser() : ResourceUser = {
    val user = Users.users.vend.createUnsavedResourceUser(getProvider(), Some(username), Some(username), Some(email), None).openOrThrowException(attemptedToOpenAnEmptyBox)
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

  /**
   * Writes the row and keeps the ResourceUser beside it in step, which is what the Mapper override
   * did: an AuthUser without one gets a ResourceUser created and its key stored, and one that
   * already has it gets that user's name, email and provider id refreshed.
   *
   * Returns the persisted row - the caller needs it, because the id and the ResourceUser key are
   * assigned here and an immutable row cannot carry them back on its own.
   */
  def saveMe(): AuthUser = AuthUser.saveWithResourceUser(this)

  def save: Boolean = { AuthUser.saveWithResourceUser(this); true }

  def delete_! : Boolean = {
    ResourceUser.findByPrimaryKey(user).map(u => Users.users.vend.deleteResourceUser(u.id))
    AuthUser.delete(id)
  }
}

/**
 * The singleton that has methods for accessing the database
 */
object AuthUser extends MdcLoggable {
import net.liftweb.util.Helpers._

  /**Marking the locked state to show different error message */
  val usernameLockedStateCode = Long.MaxValue
  /**Marking the email not validated state to show different error message */
  val userEmailNotValidatedStateCode = Long.MaxValue - 1
  /**Marking the auth-rate-limit-exceeded state to render a 429 instead of a 401 */
  val rateLimitExceededStateCode = Long.MaxValue - 2

  val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")
  val starConnectorSupportedTypes = APIUtil.getPropsValue("starConnector_supported_types","")

  def emailFrom = Constant.mailUsersUserinfoSenderAddress

  /** ProtoUser's default: nothing is blind-copied on the emails this object sends. */
  def bccEmail: Box[String] = Empty

  /** ProtoUser computed this from basePath; the one live caller builds a logout link out of it. */
  val logoutPath: List[String] = List("user_mgt", "logout")

  // To force validation of email addresses set this to false (default as of 29 June 2021)
  def skipEmailValidation = APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", false)

  def userNameNotFoundString: String = "Thank you. If we found a matching user, password reset instructions have been sent."

  /**
   * The password columns, exactly as MappedPassword wrote and read them.
   *
   * A set bcrypts the value and splits the 60-character result: "b;" plus its first 44 characters
   * into PASSWORD_PW, the remaining 16 into PASSWORD_SLT. Verification concatenates them back.
   * Rows written before bcrypt keep a salted digest instead, and are still accepted - that legacy
   * branch is why the salt is compared rather than ignored.
   *
   * v_oidc_users reads both columns straight out of the table, so this format is a contract with
   * OBP-OIDC and the Keycloak user storage provider, not an implementation detail.
   */
  val unsetPassword = "*"

  def hashPassword(plain: String): (String, String) = plain match {
    case null => (unsetPassword, "")
    case value if value.length > 4 =>
      val bcrypted = BCrypt.hashpw(value, BCrypt.gensalt())
      ("b;" + bcrypted.substring(0, 44), bcrypted.substring(44))
    case _ => (unsetPassword, "")
  }

  def matchPassword(plain: String, passwordPw: String, passwordSlt: String): Boolean = {
    val pw = if (passwordPw == null) "" else passwordPw
    val salt = if (passwordSlt == null) "" else passwordSlt
    if (pw.startsWith("b;")) BCrypt.checkpw(plain, pw.substring(2) + salt)
    else Helpers.secureEquals(Helpers.hash("{" + plain + "} salt={" + salt + "}"), pw)
  }

  /**
   * The field validations Mapper ran on save, in field-declaration order and with the same
   * messages, because sign-up and the bootstrap paths report them to the caller.
   *
   * The username rules are the interesting ones: it must be present, must look like either an
   * email address or the documented username shape, must be unique here, and - when
   * connector.user.authentication is on - must not already exist in the core banking system.
   */
  def validate(row: AuthUser): List[String] = {
    def isBlank(value: String) = value == null || value.trim.isEmpty
    val firstNameErrors = if (isBlank(row.firstName)) List(Helper.i18n("Please.enter.your.first.name")) else Nil
    val lastNameErrors = if (isBlank(row.lastName)) List(Helper.i18n("Please.enter.your.last.name")) else Nil
    val emailErrors =
      if (isBlank(row.email)) List(Helper.i18n("Please.enter.your.email"))
      else if (!isEmailValid(row.email)) List(Helper.i18n("invalid.email.address"))
      else Nil
    val usernameErrors =
      if (isBlank(row.username)) List(Helper.i18n("Please.enter.your.username"))
      else if (!isUsernameValid(row.username)) List(Helper.i18n("invalid.username"))
      else if (findByUsername(row.username).exists(_.id != row.id)) List(Helper.i18n("unique.username"))
      else validateUsernameIsUniqueExternally(row.username)
    val passwordErrors =
      if (row.passwordPw == unsetPassword || isBlank(row.passwordPw)) List(Helper.i18n("please.enter.your.password"))
      else Nil
    val providerErrors =
      if (isBlank(row.provider) || tryo(new java.net.URI(row.provider)).isDefined) Nil
      else List("provider must be a valid URI")
    firstNameErrors ::: lastNameErrors ::: emailErrors ::: usernameErrors ::: passwordErrors :::
      providerErrors
  }

  // Regex to validate an email address as per W3C recommendations: https://www.w3.org/TR/html5/forms.html#valid-e-mail-address
  private val emailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  /**
   * Username is a valid email address or the regex below:
   *
   * ^(?=.{8,100}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$
   * └─────┬────┘└───┬──┘└─────┬─────┘└─────┬─────┘ └───┬───┘
   *       │         │         │            │           no _ or . at the end
   *       │         │         │            allowed characters
   *       │         │         no __ or _. or ._ or .. inside
   *       │         no _ or . at the beginning
   *       username is 8-100 characters long
   */
  private val usernameRegex = """^(?=.{8,100}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

  def isEmailValid(e: String): Boolean = e match {
    case null                                           => false
    case e if e.trim.isEmpty                            => false
    case e if emailRegex.findFirstMatchIn(e).isDefined  => true
    case _                                              => false
  }

  def isUsernameValid(value: String): Boolean = value match {
    case null                                                 => false
    case e if e.trim.isEmpty                                  => false
    case e if emailRegex.findFirstMatchIn(e).isDefined        => true // Email is valid username
    case e if usernameRegex.findFirstMatchIn(e).isDefined      => true
    case _                                                    => false
  }

  /** Make sure that the username is unique in the CBS. */
  private def validateUsernameIsUniqueExternally(uniqueUsername: String): List[String] = {
    if (APIUtil.getPropsAsBoolValue("connector.user.authentication", false)) {
      logger.info(s"valUniqueExternally: calling checkExternalUserExists for username: $uniqueUsername")
      val connectorResult = Connector.connector.vend.checkExternalUserExists(uniqueUsername, None)
      logger.info(s"valUniqueExternally: checkExternalUserExists returned: ${connectorResult.getClass.getSimpleName}")
      connectorResult.map(_.sub) match {
        case Full(returnedUsername) => // Get the username via connector
          logger.info(s"valUniqueExternally: checkExternalUserExists returned username: $returnedUsername")
          if(uniqueUsername == returnedUsername) { // Username is NOT unique
            logger.info(s"valUniqueExternally: username $uniqueUsername already exists externally")
            List(Helper.i18n("unique.username")) // provide the error message
          } else {
            logger.info(s"valUniqueExternally: username $uniqueUsername is unique (returned different: $returnedUsername)")
            Nil // All good. Allow username creation
          }
        case ParamFailure(message,_,_,APIFailure(errorMessage, errorCode)) if errorMessage.contains("NO DATA") => // Cannot get the username via connector
          logger.info(s"valUniqueExternally: checkExternalUserExists returned NO DATA for username: $uniqueUsername - allowing creation")
          Nil // All good. Allow username creation
        case Failure(failureMsg, exception, chain) =>
          logger.warn(s"valUniqueExternally: checkExternalUserExists failed for username: $uniqueUsername, message: $failureMsg, exception: ${exception.map(_.getMessage)}, chain: $chain")
          List(ErrorMessages.ExternalUserCheckFailed)
        case Empty =>
          logger.warn(s"valUniqueExternally: checkExternalUserExists returned Empty for username: $uniqueUsername")
          List(ErrorMessages.ExternalUserCheckFailed)
        case _ => // Any other case we provide error message
          logger.warn(s"valUniqueExternally: checkExternalUserExists returned unexpected result for username: $uniqueUsername")
          List(ErrorMessages.ExternalUserCheckFailed)
      }
    } else {
      Nil // All good. Allow username creation
    }
  }

  /**
   * The logged-in user, as ProtoUser tracked it.
   *
   * OBP authenticates through DirectLogin and OAuth rather than a Lift session, so this is normally
   * Empty and getCurrentUser falls through to those mechanisms; it is kept because the sign-up flow
   * still sets it and getCurrentUser still reads it. A per-thread holder, which is what the
   * webkit-free RequestVar it replaces already was.
   */
  private val currentUserHolder = new ThreadLocal[Box[AuthUser]]()

  def currentUser: Box[AuthUser] = Option(currentUserHolder.get).getOrElse(Empty)

  def logUserIn(who: AuthUser): Unit = currentUserHolder.set(Full(who))

  def logUserOut(): Unit = currentUserHolder.remove()

  // ---------------------------------------------------------------------------------------------
  // Store
  // ---------------------------------------------------------------------------------------------

  private val selectColumns =
    fr"""SELECT id, firstname, lastname, email, username, password_pw, password_slt, provider,
                uniqueid, superuser, validated, passwordshouldbechanged, locale, timezone, user_c,
                createdat, updatedat
         FROM authuser"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[Boolean],
    Option[Boolean], Option[Boolean], Option[String], Option[String], Option[Long],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def readDate(value: Option[java.sql.Timestamp]): java.util.Date =
    value.map(t => new java.util.Date(t.getTime)).orNull

  private def fromRow(row: Row): AuthUser = row match {
    case (id, firstName, lastName, email, username, passwordPw, passwordSlt, provider, uniqueId,
          superUser, validated, passwordShouldBeChanged, locale, timezone, user, createdAt,
          updatedAt) =>
      AuthUser(
        id = id,
        firstName = firstName.orNull,
        lastName = lastName.orNull,
        email = email.orNull,
        username = username.orNull,
        passwordPw = passwordPw.orNull,
        passwordSlt = passwordSlt.orNull,
        provider = provider.orNull,
        uniqueId = uniqueId.orNull,
        superUser = superUser.getOrElse(false),
        validated = validated.getOrElse(false),
        passwordShouldBeChanged = passwordShouldBeChanged.getOrElse(false),
        locale = locale.orNull,
        timezone = timezone.orNull,
        // The foreign key is NULL while an AuthUser has no ResourceUser; 0 is what the Mapper
        // field read that as.
        user = user.getOrElse(0L),
        createdAt = readDate(createdAt),
        updatedAt = readDate(updatedAt))
  }

  private def query(condition: Fragment): List[AuthUser] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def one(condition: Fragment): Box[AuthUser] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByPrimaryKey(id: Long): Box[AuthUser] = one(fr"WHERE id = $id")
  def findByUsername(username: String): Box[AuthUser] = one(fr"WHERE username = ${opt(username)}")
  def findByUsernameAndProvider(username: String, provider: String): Box[AuthUser] =
    one(fr"WHERE username = ${opt(username)} AND provider = ${opt(provider)}")
  def findByResourceUserPrimaryKey(userPrimaryKey: Long): Box[AuthUser] =
    one(fr"WHERE user_c = $userPrimaryKey")
  def findByUniqueId(uniqueId: String): Box[AuthUser] = one(fr"WHERE uniqueid = ${opt(uniqueId)}")
  def findAllByEmail(email: String): List[AuthUser] = query(fr"WHERE email = ${opt(email)}")
  def findAllByUsername(username: String): List[AuthUser] = query(fr"WHERE username = ${opt(username)}")
  def findAll(): List[AuthUser] = query(Fragment.empty)
  def count(): Long = DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM authuser".query[Long].unique)

  /** Rows whose provider was never filled in - what populateMissingProviderWithLocalIdentity repairs. */
  def findAllWithoutProvider(): List[AuthUser] =
    query(fr"WHERE provider IS NULL OR provider = ''")

  /**
   * What MappedEmail's setFilter did on every set - `notNull :: toLower :: trim`.
   *
   * The Lift entity declared this column as MappedEmail, so the normalisation lived in the field
   * type and the entity never mentioned it; carrying the column across as a plain String dropped it
   * silently, and `" Bob@Example.COM "` began persisting verbatim. ResourceUser's half of the same
   * migration kept it (ResourceUser.normalizeEmail), so the two copies of one user's address had
   * been disagreeing about case and whitespace. Reused rather than re-implemented so they cannot
   * drift apart again. AuthUserEmailNormalisationTest covers insert and update.
   */
  private def normalisedEmail(row: AuthUser): String = ResourceUser.normalizeEmail(row.email)

  /**
   * The resourceuser FK as a bindable parameter.
   *
   * `user_c` is a nullable BIGINT and an AuthUser that has not been linked yet is a legitimate row,
   * so the unlinked case has to bind SQL NULL. Written inline in the interpolator - as
   * `${if (row.user > 0L) Some(row.user) else None}` - it was not bound as a parameter at all: the
   * database rejected the statement with a syntax error at that position, and because it is one
   * statement the whole insert failed, not just the FK column. Naming the value in a method with a
   * declared `Option[Long]` result is what makes it bind. AuthUserUnboundInsertTest covers it: it
   * fails with the syntax error on the inline form and passes on this one.
   */
  private def userFk(row: AuthUser): Option[Long] =
    if (row.user > 0L) Some(row.user) else None

  def insert(row: AuthUser): AuthUser = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO authuser
            (firstname, lastname, email, username, password_pw, password_slt, provider, uniqueid,
             superuser, validated, passwordshouldbechanged, locale, timezone, user_c,
             createdat, updatedat)
            VALUES (${opt(row.firstName)}, ${opt(row.lastName)}, ${opt(normalisedEmail(row))},
             ${opt(row.username)}, ${opt(row.passwordPw)}, ${opt(row.passwordSlt)},
             ${opt(row.provider)}, ${opt(row.uniqueId)}, ${row.superUser}, ${row.validated},
             ${row.passwordShouldBeChanged}, ${opt(row.locale)}, ${opt(row.timezone)},
             ${userFk(row)}, $now, $now)"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    // email carries the normalised value too, not just the row in the database: returning the
    // caller's raw string would hand back an object that disagrees with what was just stored.
    row.copy(id = id, email = normalisedEmail(row),
      createdAt = new java.util.Date(now.getTime), updatedAt = new java.util.Date(now.getTime))
  }

  def update(row: AuthUser): AuthUser = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE authuser
            SET firstname = ${opt(row.firstName)}, lastname = ${opt(row.lastName)},
                email = ${opt(normalisedEmail(row))}, username = ${opt(row.username)},
                password_pw = ${opt(row.passwordPw)}, password_slt = ${opt(row.passwordSlt)},
                provider = ${opt(row.provider)}, uniqueid = ${opt(row.uniqueId)},
                superuser = ${row.superUser}, validated = ${row.validated},
                passwordshouldbechanged = ${row.passwordShouldBeChanged},
                locale = ${opt(row.locale)}, timezone = ${opt(row.timezone)},
                user_c = ${userFk(row)}, updatedat = $now
            WHERE id = ${row.id}"""
        .update.run)
    row.copy(email = normalisedEmail(row), updatedAt = new java.util.Date(now.getTime))
  }

  def delete(id: Long): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM authuser WHERE id = $id".update.run) > 0

  def deleteAllByUsername(username: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM authuser WHERE username = ${opt(username)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM authuser".update.run)
    ()
  }

  /**
   * Writes an AuthUser and the ResourceUser beside it, as the Mapper save override did: one without
   * a ResourceUser gets it created and its key stored, one that has it gets that user's name, email
   * and provider id refreshed from these credentials.
   */
  def saveWithResourceUser(row: AuthUser): AuthUser = {
    val withResourceUser =
      if (row.user == 0L) {
        logger.info("user reference is null. We will create a ResourceUser")
        val resourceUser = row.createUnsavedResourceUser()
        Users.users.vend.saveResourceUser(resourceUser) match {
          case Full(saved) => row.copy(user = saved.id)
          case _ => row
        }
      } else {
        logger.info("user reference is not null. Trying to update the ResourceUser")
        Users.users.vend.getResourceUserByResourceUserId(row.user).map { u =>
          Users.users.vend.saveResourceUser(u.copy(
            name = row.username,
            emailAddress = ResourceUser.normalizeEmail(row.email),
            idGivenByProvider = row.username))
        }
        row
      }
    if (withResourceUser.id == 0L) insert(withResourceUser) else update(withResourceUser)
  }


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
    val cacheKey = ("code.model.dataAccess.AuthUser", "updateComputedLocale", List(sessionId, computedLocale).mkString("_"))
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(ttl) {
      logger.debug(s"AuthUser.updateComputedLocale(sessionId = $sessionId, computedLocale = $computedLocale)")
      getCurrentUser.map(_.userPrimaryKey.value) match {
        case Full(id) =>
          Users.users.vend.getResourceUserByResourceUserId(id).map {
            u =>
              ResourceUser.update(u.copy(lastUsedLocale = Option(computedLocale)))
              logger.debug(s"ResourceUser.LastUsedLocale is saved for the resource user id: $id")
          }.isDefined
        case _ => true// There is no current user
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
          if(user.provider == null || user.provider.isEmpty) 
            Constant.localIdentityProvider 
          else 
            user.provider
        Users.users.vend.getUserByProviderAndUsername(provider, user.username)
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
          TokensOpenIDConnect.tokens.vend.getOpenIDConnectTokenByAuthUser(authUser.id).map(_.idToken).getOrElse("")
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
          TokensOpenIDConnect.tokens.vend.getOpenIDConnectTokenByAuthUser(authUser.id).map(_.accessToken).getOrElse("")
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
   * Sends the sign-up validation email, using the hostname set in the props file.
   */
  def sendValidationEmail(user: AuthUser): Unit = {
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

   def grantDefaultEntitlementsToAuthUser(user: AuthUser) = {
     tryo{user.getResourceUserByProviderAndUsername(user.getProvider(), user.username).head.userId} match {
       case Full(userId)=>APIUtil.grantDefaultEntitlementsToNewUser(userId)
       case _ => logger.error("Can not getResourceUserByUsername here, so it breaks the grantDefaultEntitlementsToNewUser process.")
     }
   }

  def validateUser(id: String): NodeSeq = {
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

    val userBox = uniqueIdBox.flatMap(findByUniqueId)

    userBox match {
      case Full(user) if !user.validated_? =>
        val validated = user.setValidated(true).resetUniqueId().saveMe()
        grantDefaultEntitlementsToAuthUser(validated)
      case _ =>
        logger.warn("validateUser: invalid or expired token")
    }
    NodeSeq.Empty
  }

  def signupFormTitle = getWebUiPropsValue("webui_signup_form_title_text", "sign.up")





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
          ResourceUser.findByPrimaryKey(user.user) match {
            case Full(resourceUser) =>
              Full(resourceUser.id)
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
          ResourceUser.findByPrimaryKey(authUser.user) match {
            case Full(resourceUser) =>
              Full(resourceUser.id)
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
                UserAuthContextProvider.userAuthContextProvider.vend.createOrUpdateUserAuthContexts(user.id.toString, authContexts)
              case None => // Do nothing
            }
            user
          case _ => // If not found, create a new user
            // Create AuthUser using fetched data from connector
            // assuming that user's email is always validated
            logger.debug("external user "+ sub + " does not exist locally, creating one")
            AuthUser(
              firstName = name.getOrElse(sub),
              email = email.getOrElse(""),
              username = sub,
              // TODO add field stating external password check only.
              provider = iss,
              validated = emailVerified.exists(_.equalsIgnoreCase("true")))
              // No need to store a real password, so store a dummy one instead
              .withPassword(generateUUID())
              .saveMe() //NOTE, we will create the resourceUser in the `saveMe()` method.
        }
        userAuthContexts match {
          case Some(authContexts) => { // Write user auth context to the database
              // get resourceUserId from AuthUser.
              val resourceUserId = ResourceUser.findByPrimaryKey(user.user).map(_.userId).getOrElse("")
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
      val emailDomain = StringUtils.substringAfterLast(user.email, "@")

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
      val userId = ResourceUser.findByPrimaryKey(user.user).map(_.userId).getOrElse("")

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
      val userId = ResourceUser.findByPrimaryKey(user.user).map(_.userId).getOrElse("")

      // user's already auto granted entitlements.
      val entitlementsGrantedByThisProcess = Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
        .map(_.filter(role => role.createdByProcess == createdByProcess))
        .getOrElse(Nil)

      def alreadyHasEntitlement(bankId: String, roleName:String): Boolean =
        entitlementsGrantedByThisProcess.exists(entitlement => entitlement.roleName == roleName && entitlement.bankId == bankId)

      val allEntitlementsFromCurrentProps: List[(String, String)] = for{
        emailDomainToEntitlementMapping <- emailDomainToEntitlementMappings
        domain = emailDomainToEntitlementMapping.domain
        entitlement <- emailDomainToEntitlementMapping.entitlements if StringUtils.substringAfterLast(user.email, "@") == domain
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
        val obpAccountAccessBankAccountIds = privateAccountAccess.map(accountAccess =>BankIdAccountId(BankId(accountAccess.bankId), AccountId(accountAccess.accountId))).toSet

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
  def findAuthUserByUsernameAndProvider(name: String, provider: String): Box[AuthUser] = {
    findByUsernameAndProvider(name, provider)
  }
  def findAuthUserByPrimaryKey(key: Long): Box[AuthUser] = {
    findByResourceUserPrimaryKey(key)
  }

  def passwordResetUrl(name: String, email: String, userId: String): String = {
    findByUsername(name) match {
      case Full(authUser) if authUser.validated_? && authUser.email == email =>
        Users.users.vend.getUserByUserId(userId) match {
          case Full(u) if u.name == name && u.emailAddress == email =>
            val withNewToken = authUser.resetUniqueId().saveMe()
            val resetLink = Constant.HostName+
              passwordResetPath.mkString("/", "/", "/")+java.net.URLEncoder.encode(withNewToken.getUniqueId(), "UTF-8")
            logger.warn(s"Password reset url is created for this user: $email")
            // TODO Notify via email appropriate persons 
            resetLink
          case _ => ""
        }
      case _ => ""
    }
  }

  // passwordResetXhtml simplified - API-only mode, no portal pages
  /** ProtoUser computed this from basePath; the reset link above is built out of it. */
  val passwordResetPath: List[String] = List("user_mgt", "reset_password")

  def signupSubmitButtonValue() = getWebUiPropsValue("webui_signup_form_submit_button_value", "sign.up")

  def scrambleAuthUser(userPrimaryKey: UserPrimaryKey): Box[Boolean] = tryo {
    AuthUser.findByResourceUserPrimaryKey(userPrimaryKey.value) match {
      case Full(user) =>
        val scrambledUser = user.copy(
          email = Helpers.randomString(10) + "@example.com",
          username = "DELETED-" + Helpers.randomString(16),
          firstName = Helpers.randomString(16),
          lastName = Helpers.randomString(16),
          validated = false).withPassword(Helpers.randomString(40))
        scrambledUser.save
      case Empty => true // There is a resource user but no the correlated Auth user 
      case _ => false // Error case
    }
  }

  def validateAuthUser(userPrimaryKey: UserPrimaryKey): Box[AuthUser] = tryo {
    AuthUser.findByResourceUserPrimaryKey(userPrimaryKey.value) match {
      case Full(user) =>
        user.setValidated(true).saveMe()
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
    findByUniqueId(token)
  }
  
  /**
   * Validate a user and reset their unique ID token.
   * This is a public wrapper that combines validation and token reset.
   * 
   * @param user The AuthUser to validate
   * @return The validated AuthUser with reset unique ID
   */
  def validateAndResetToken(user: AuthUser): AuthUser =
    user.setValidated(true).resetUniqueId().saveMe()
  
}
