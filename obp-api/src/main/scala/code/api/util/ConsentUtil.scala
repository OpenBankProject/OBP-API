package code.api.util

import java.text.SimpleDateFormat
import java.util.Date

import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{ConsentAccessJson, PostConsentJson}
import code.api.v3_1_0.{PostConsentEntitlementJsonV310, PostConsentBodyCommonJson, PostConsentViewJsonV310}
import code.api.{Constant, RequestHeader}
import code.bankconnectors.Connector
import code.consent
import code.consent.{ConsentStatus, Consents, MappedConsent}
import code.consumer.Consumers
import code.context.{ConsentAuthContextProvider, UserAuthContextProvider}
import code.entitlement.Entitlement
import code.model.Consumer
import code.users.Users
import code.util.HydraUtil
import code.views.Views
import com.nimbusds.jwt.JWTClaimsSet
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.JsonParser.ParseException
import net.liftweb.json.{Extraction, MappingException, compactRender, parse}
import net.liftweb.mapper.By
import sh.ory.hydra.model.OAuth2TokenIntrospection

import scala.collection.immutable.{List, Nil}
import scala.concurrent.Future

case class ConsentJWT(createdByUserId: String,
                      sub: String, // An identifier for the user, unique among all OBP-API users and never reused
                      iss: String, // The Issuer Identifier for the Issuer of the response.
                      aud: String, // Identifies the audience that this ID token is intended for. It must be one of the OBP-API client IDs of your application. 
                      jti: String, // (JWT ID) claim provides a unique identifier for the JWT.(OBP use jti as consentId)
                      iat: Long, // The "iat" (issued at) claim identifies the time at which the JWT was issued. Represented in Unix time (integer seconds).
                      nbf: Long, // The "nbf" (not before) claim identifies the time before which the JWT MUST NOT be accepted for processing. Represented in Unix time (integer seconds).
                      exp: Long, // The "exp" (expiration time) claim identifies the expiration time on or after which the JWT MUST NOT be accepted for processing. Represented in Unix time (integer seconds).
                      name: Option[String],
                      email: Option[String],
                      entitlements: List[Role],
                      views: List[ConsentView],
                      access: Option[ConsentAccessJson]) {
  def toConsent(): Consent = {
    Consent(
      createdByUserId=this.createdByUserId, 
      subject=this.sub, 
      issuer=this.iss,
      consumerKey=this.aud, 
      consentId=this.jti, //OBP use jti as consentId
      issuedAt=this.iat, 
      validFrom=this.nbf, 
      validTo=this.exp,
      name=this.name, 
      email=this.email, 
      entitlements=this.entitlements,
      views=this.views,
      access = this.access
    )
  }
}

case class Role(role_name: String, 
                bank_id: String
               )
case class ConsentView(bank_id: String, 
                       account_id: String,
                       view_id : String
                      )

case class Consent(createdByUserId: String,
                   subject: String,
                   issuer: String,
                   consumerKey: String,
                   consentId: String,
                   issuedAt: Long,
                   validFrom: Long,
                   validTo: Long,
                   name: Option[String],
                   email: Option[String],
                   entitlements: List[Role],
                   views: List[ConsentView],
                   access: Option[ConsentAccessJson]
                  ) {
  def toConsentJWT(): ConsentJWT = {
    ConsentJWT(
      createdByUserId=this.createdByUserId,
      sub=this.subject,
      iss=this.issuer,
      aud=this.consumerKey,
      jti=this.consentId,
      iat=this.issuedAt,
      nbf=this.validFrom,
      exp=this.validTo,
      name=this.name,
      email=this.email,
      entitlements=this.entitlements,
      views=this.views,
      access = this.access
    )
  }
}

object Consent {

  final lazy val challengeAnswerAtTestEnvironment = "123"

  /**
    * Purpose of this helper function is to get the Consumer-Key value from a Request Headers.
    * @return the Consumer-Key value from a Request Header as a String
    */
  def getConsumerKey(requestHeaders: List[HTTPParam]): Option[String] = {
    requestHeaders.toSet.filter(_.name == RequestHeader.`Consumer-Key`).toList match {
      case x :: Nil => Some(x.values.mkString(", "))
      case _ => None
    }
  }
  
  private def verifyHmacSignedJwt(jwtToken: String, c: MappedConsent): Boolean = {
    JwtUtil.verifyHmacSignedJwt(jwtToken, c.secret)
  }

  private def checkConsumerIsActiveAndMatched(consent: ConsentJWT, requestHeaderConsumerKey: Option[String]): Box[Boolean] = {
    Consumers.consumers.vend.getConsumerByConsumerId(consent.aud) match {
      case Full(consumerFromConsent) if consumerFromConsent.isActive.get == true => // Consumer is active
        APIUtil.getPropsValue(nameOfProperty = "consumer_validation_method_for_consent", defaultValue = "CONSUMER_KEY_VALUE") match {
          case "CONSUMER_KEY_VALUE" =>
            requestHeaderConsumerKey match {
              case Some(reqHeaderConsumerKey) =>
                if (reqHeaderConsumerKey == consumerFromConsent.key.get)
                  Full(true) // This consent can be used by current application
                else // This consent can NOT be used by current application
                  Failure(ErrorMessages.ConsentDoesNotMatchConsumer)
              case None => Failure(ErrorMessages.ConsumerKeyHeaderMissing) // There is no header `Consumer-Key` in request headers
            }
          case "NONE" => // This instance does not require validation method
            Full(true)
          case _ => // This instance does not specify validation method
            Failure(ErrorMessages.ConsumerValidationMethodForConsentNotDefined)
        }
      case Full(consumer) if consumer.isActive.get == false => // Consumer is NOT active
        Failure(ErrorMessages.ConsumerAtConsentDisabled + " aud: " + consent.aud)
      case _ => // There is NO Consumer
        Failure(ErrorMessages.ConsumerAtConsentCannotBeFound + " aud: " + consent.aud)
    }
  }

  private def checkConsent(consent: ConsentJWT, consentIdAsJwt: String, calContext: CallContext): Box[Boolean] = {
    Consents.consentProvider.vend.getConsentByConsentId(consent.jti) match {
      case Full(c) if c.mStatus == ConsentStatus.ACCEPTED.toString | c.mStatus == ConsentStatus.VALID.toString =>
        verifyHmacSignedJwt(consentIdAsJwt, c) match {
          case true =>
            (System.currentTimeMillis / 1000) match {
              case currentTimeInSeconds if currentTimeInSeconds < consent.nbf =>
                Failure(ErrorMessages.ConsentNotBeforeIssue)
              case currentTimeInSeconds if currentTimeInSeconds > consent.exp =>
                Failure(ErrorMessages.ConsentExpiredIssue)
              case _ =>
                val requestHeaderConsumerKey = getConsumerKey(calContext.requestHeaders)
                checkConsumerIsActiveAndMatched(consent, requestHeaderConsumerKey)
            }
          case false =>
            Failure(ErrorMessages.ConsentVerificationIssue)
        }
      case Full(c) if c.mStatus != ConsentStatus.ACCEPTED.toString =>
        Failure(s"${ErrorMessages.ConsentStatusIssue}${ConsentStatus.ACCEPTED.toString}.")
      case _ => 
        Failure(ErrorMessages.ConsentNotFound)
    }
  }

  private def getOrCreateUser(subject: String, issuer: String, consentId: Option[String], name: Option[String], email: Option[String]): Future[(Box[User], Boolean)] = {
    Users.users.vend.getOrCreateUserByProviderIdFuture(
      provider = issuer,
      idGivenByProvider = subject,
      consentId = consentId,
      name = name,
      email = email
    )
  }
  private def getOrCreateUserOldStyle(subject: String, issuer: String, consentId: Option[String], name: Option[String], email: Option[String]): Box[User] = {
    Users.users.vend.getUserByProviderId(provider = issuer, idGivenByProvider = subject).or { // Find a user
      Users.users.vend.createResourceUser( // Otherwise create a new one
        provider = issuer,
        providerId = Some(subject),
        createdByConsentId = consentId,
        name = name,
        email = email,
        userId = None
      )
    }
  }

  private def addEntitlements(user: User, consent: ConsentJWT): Box[User] = {
    def addConsentEntitlements(existingEntitlements: List[Entitlement], entitlement: Role): (Role, String) = {
      ApiRole.availableRoles.exists(_.toString == entitlement.role_name) match { // Check a role name
        case true =>
          val role = ApiRole.valueOf(entitlement.role_name)
          existingEntitlements.exists(_.roleName == entitlement.role_name) match { // Check is a role already added to a user
            case false =>
              val bankId = if (role.requiresBankId) entitlement.bank_id else ""
              Entitlement.entitlement.vend.addEntitlement(bankId, user.userId, entitlement.role_name) match {
                case Full(_) => (entitlement, "AddedOrExisted")
                case _ => (entitlement, "Cannot add the entitlement: " + entitlement)
              }
            case true =>
              (entitlement, "AddedOrExisted")
          }
        case false =>
          (entitlement, "There is no entitlement's name: " + entitlement)
      }
    }

    val entitlements: List[Role] = consent.entitlements
    Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId) match {
      case Full(existingEntitlements) =>
        val triedToAdd =
          for {
            entitlement <- entitlements
          } yield {
            addConsentEntitlements(existingEntitlements, entitlement)
          }
        val failedToAdd: List[(Role, String)] = triedToAdd.filter(_._2 != "AddedOrExisted")
        failedToAdd match {
          case Nil => Full(user)
          case _ => Failure("The entitlements cannot be added. " + failedToAdd.map(_._1).mkString(", "))
        }
      case _ =>
        Failure("Cannot get entitlements for user id: " + user.userId)
    }

  }

  private def grantAccessToViews(user: User, consent: ConsentJWT): Box[User] = {
    for {
      view <- consent.views
    } yield {
      val viewIdBankIdAccountId = ViewIdBankIdAccountId(ViewId(view.view_id), BankId(view.bank_id), AccountId(view.account_id))
      Views.views.vend.revokeAccess(viewIdBankIdAccountId, user)
    }
    val result = 
      for {
        view <- consent.views
      } yield {
        val viewIdBankIdAccountId = ViewIdBankIdAccountId(ViewId(view.view_id), BankId(view.bank_id), AccountId(view.account_id))
        Views.views.vend.systemView(ViewId(view.view_id)) match {
          case Full(systemView) =>
            Views.views.vend.grantAccessToSystemView(BankId(view.bank_id), AccountId(view.account_id), systemView, user)
          case _ => 
            // It's not system view
            Views.views.vend.grantAccessToCustomView(viewIdBankIdAccountId, user)
        }
        "Added"
      }
    if (result.forall(_ == "Added")) Full(user) else Failure("Cannot add permissions to the user with id: " + user.userId)
  }
 
  private def hasConsentInternalOldStyle(consentIdAsJwt: String, calContext: CallContext): Box[User] = {
    implicit val dateFormats = CustomJsonFormats.formats

    def applyConsentRules(consent: ConsentJWT): Box[User] = {
      // 1. Get or Create a User
      getOrCreateUserOldStyle(consent.sub, consent.iss, Some(consent.toConsent().consentId), None, None) match {
        case (Full(user)) =>
          // 2. Assign entitlements to the User
          addEntitlements(user, consent) match {
            case (Full(user)) =>
              // 3. Assign views to the User
              grantAccessToViews(user, consent)
            case everythingElse =>
              everythingElse
          }
        case _ =>
          Failure("Cannot create or get the user based on: " + consentIdAsJwt)
      }
    }

    JwtUtil.getSignedPayloadAsJson(consentIdAsJwt) match {
      case Full(jsonAsString) =>
        try {
          val consent = net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]
          checkConsent(consent, consentIdAsJwt, calContext) match { // Check is it Consent-JWT expired
            case (Full(true)) => // OK
              applyConsentRules(consent)
            case failure@Failure(_, _, _) => // Handled errors
              failure
            case _ => // Unexpected errors
              Failure(ErrorMessages.ConsentCheckExpiredIssue)
          }
        } catch { // Possible exceptions
          case e: ParseException => Failure("ParseException: " + e.getMessage)
          case e: MappingException => Failure("MappingException: " + e.getMessage)
          case e: Exception => Failure("parsing failed: " + e.getMessage)
        }
      case failure@Failure(_, _, _) =>
        failure
      case _ =>
        Failure("Cannot extract data from: " + consentIdAsJwt)
    }
  } 
  
  private def hasConsentInternal(consentIdAsJwt: String, callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    implicit val dateFormats = CustomJsonFormats.formats

    def applyConsentRules(consent: ConsentJWT): Future[(Box[User], Option[CallContext])] = {
      val cc = callContext
      // 1. Get or Create a User
      getOrCreateUser(consent.sub, consent.iss, Some(consent.jti), None, None) map {
        case (Full(user), newUser) =>
          // 2. Assign entitlements to the User
          addEntitlements(user, consent) match {
            case (Full(user)) =>
              // 3. Copy Auth Context to the User
              copyAuthContextOfConsentToUser(consent.jti, user.userId, newUser) match {
                case Full(_) =>
                  // 4. Assign views to the User
                  (grantAccessToViews(user, consent), Some(cc))
                case failure@Failure(_, _, _) => // Handled errors
                  (failure, Some(callContext))
                case _ =>
                  (Failure(ErrorMessages.UnknownError), Some(cc))
              }
          }
        case _ =>
          (Failure("Cannot create or get the user based on: " + consentIdAsJwt), Some(cc))
      }
    }

    JwtUtil.getSignedPayloadAsJson(consentIdAsJwt) match {
      case Full(jsonAsString) =>
        try {
          val consent = net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]
          checkConsent(consent, consentIdAsJwt, callContext) match { // Check is it Consent-JWT expired
            case (Full(true)) => // OK
              applyConsentRules(consent)
            case failure@Failure(_, _, _) => // Handled errors
              Future(failure, Some(callContext))
            case _ => // Unexpected errors
              Future(Failure(ErrorMessages.ConsentCheckExpiredIssue), Some(callContext))
          }
        } catch { // Possible exceptions
          case e: ParseException => Future(Failure("ParseException: " + e.getMessage), Some(callContext))
          case e: MappingException => Future(Failure("MappingException: " + e.getMessage), Some(callContext))
          case e: Exception => Future(Failure("parsing failed: " + e.getMessage), Some(callContext))
        }
      case failure@Failure(_, _, _) =>
        Future(failure, Some(callContext))
      case _ =>
        Future(Failure("Cannot extract data from: " + consentIdAsJwt), Some(callContext))
    }
  }
  
  private def hasConsentOldStyle(consentIdAsJwt: String, callContext: CallContext): (Box[User], CallContext) = {
    (hasConsentInternalOldStyle(consentIdAsJwt, callContext), callContext)
  }  
  private def hasConsent(consentIdAsJwt: String, callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    hasConsentInternal(consentIdAsJwt, callContext)
  }
  
  def applyRules(consentId: Option[String], callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    val allowed = APIUtil.getPropsAsBoolValue(nameOfProperty="consents.allowed", defaultValue=false)
    (consentId, allowed) match {
      case (Some(consentId), true) => hasConsent(consentId, callContext)
      case (_, false) => Future((Failure(ErrorMessages.ConsentDisabled), Some(callContext)))
      case (None, _) => Future((Failure(ErrorMessages.ConsentHeaderNotFound), Some(callContext)))
    }
  }

  private def copyAuthContextOfConsentToUser(consentId: String, userId: String, newUser: Boolean): Box[List[UserAuthContext]] = {
    if(newUser) {
      val authContexts = ConsentAuthContextProvider.consentAuthContextProvider.vend.getConsentAuthContextsBox(consentId)
        .map(_.map(i => BasicUserAuthContext(i.key, i.value)))
      UserAuthContextProvider.userAuthContextProvider.vend.createOrUpdateUserAuthContexts(userId, authContexts.getOrElse(Nil))
    } else {
      Full(Nil)
    }
  }
  private def hasBerlinGroupConsentInternal(consentId: String, callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    implicit val dateFormats = CustomJsonFormats.formats

    def applyConsentRules(consent: ConsentJWT): Future[(Box[User], Option[CallContext])] = {
      val cc = callContext
      // 1. Get or Create a User
      getOrCreateUser(consent.sub, consent.iss, Some(consent.toConsent().consentId), None, None) map {
        case (Full(user), newUser) =>
          // 2. Assign entitlements to the User
          addEntitlements(user, consent) match {
            case Full(user) =>
              // 3. Copy Auth Context to the User
              copyAuthContextOfConsentToUser(consent.jti, user.userId, newUser) match {
                case Full(_) =>
                  // 4. Assign views to the User
                  (grantAccessToViews(user, consent), Some(cc))
                case failure@Failure(_, _, _) => // Handled errors
                  (failure, Some(callContext))
                case _ =>
                  (Failure(ErrorMessages.UnknownError), Some(cc))
              }
          }
        case _ =>
          (Failure("Cannot create or get the user based on: " + consentId), Some(cc))
      }
    }
    
    def checkFrequencyPerDay(storedConsent: consent.Consent) = {
      def isSameDay(date1: Date, date2: Date): Boolean = {
        val fmt = new SimpleDateFormat("yyyyMMdd")
        fmt.format(date1).equals(fmt.format(date2))
      }
      var usesSoFarTodayCounter = storedConsent.usesSoFarTodayCounter
      storedConsent.recurringIndicator match {
        case false => // The consent is for one access to the account data
          if(usesSoFarTodayCounter == 0) // Maximum value is "1".
            (true, 0) // All good
          else 
            (false, 1) // Exceeded rate limit
        case true => // The consent is for recurring access to the account data
          if(!isSameDay(storedConsent.usesSoFarTodayCounterUpdatedAt, new Date())) {
            usesSoFarTodayCounter = 0 // Reset counter
          }
          if(usesSoFarTodayCounter < storedConsent.frequencyPerDay)
            (true, usesSoFarTodayCounter) // All good
          else
            (false, storedConsent.frequencyPerDay) // Exceeded rate limit
      }
    }

    // 1st we need to find a Consent via the field MappedConsent.consentId
    Consents.consentProvider.vend.getConsentByConsentId(consentId) match {
      case Full(storedConsent) =>
        // This function MUST be called only once per call. I.e. it's date dependent
        val (canBeUsed, currentCounterState) = checkFrequencyPerDay(storedConsent)
        if(canBeUsed) {
          JwtUtil.getSignedPayloadAsJson(storedConsent.jsonWebToken) match {
            case Full(jsonAsString) =>
              try {
                val consent = net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]
                checkConsent(consent, storedConsent.jsonWebToken, callContext) match { // Check is it Consent-JWT expired
                  case (Full(true)) => // OK
                    // Update MappedConsent.usesSoFarTodayCounter field
                    Consents.consentProvider.vend.updateBerlinGroupConsent(consentId, currentCounterState + 1)
                    applyConsentRules(consent)
                  case failure@Failure(_, _, _) => // Handled errors
                    Future(failure, Some(callContext))
                  case _ => // Unexpected errors
                    Future(Failure(ErrorMessages.ConsentCheckExpiredIssue), Some(callContext))
                }
              } catch { // Possible exceptions
                case e: ParseException => Future(Failure("ParseException: " + e.getMessage), Some(callContext))
                case e: MappingException => Future(Failure("MappingException: " + e.getMessage), Some(callContext))
                case e: Exception => Future(Failure("parsing failed: " + e.getMessage), Some(callContext))
              }
            case failure@Failure(_, _, _) =>
              Future(failure, Some(callContext))
            case _ =>
              Future(Failure("Cannot extract data from: " + consentId), Some(callContext))
          }
        } else {
          Future(Failure(ErrorMessages.TooManyRequests + s" ${RequestHeader.`Consent-ID`}: $consentId"), Some(callContext))
        }
      case failure@Failure(_, _, _) =>
        Future(failure, Some(callContext))
      case _ =>
        Future(Failure(ErrorMessages.ConsentNotFound + s" ($consentId)"), Some(callContext))
    }
  }
  private def hasBerlinGroupConsent(consentId: String, callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    hasBerlinGroupConsentInternal(consentId, callContext)
  }
  def applyBerlinGroupRules(consentId: Option[String], callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    val allowed = APIUtil.getPropsAsBoolValue(nameOfProperty="consents.allowed", defaultValue=false)
    (consentId, allowed) match {
      case (Some(consentId), true) => hasBerlinGroupConsent(consentId, callContext)
      case (_, false) => Future((Failure(ErrorMessages.ConsentDisabled), Some(callContext)))
      case (None, _) => Future((Failure(ErrorMessages.ConsentHeaderNotFound), Some(callContext)))
    }
  }  
  def applyRulesOldStyle(consentId: Option[String], callContext: CallContext): (Box[User], CallContext) = {
    val allowed = APIUtil.getPropsAsBoolValue(nameOfProperty="consents.allowed", defaultValue=false)
    (consentId, allowed) match {
      case (Some(consentId), true) => hasConsentOldStyle(consentId, callContext)
      case (_, false) => (Failure(ErrorMessages.ConsentDisabled), callContext)
      case (None, _) => (Failure(ErrorMessages.ConsentHeaderNotFound), callContext)
    }
  }
  
  
  def createConsentJWT(user: User,
                       consent: PostConsentBodyCommonJson,
                       secret: String,
                       consentId: String,
                       consumerId: Option[String],
                       validFrom: Option[Date],
                       timeToLive: Long): String = {
    
    lazy val currentConsumerId = Consumer.findAll(By(Consumer.createdByUserId, user.userId)).map(_.consumerId.get).headOption.getOrElse("")
    val currentTimeInSeconds = System.currentTimeMillis / 1000
    val timeInSeconds = validFrom match {
      case Some(date) => date.getTime() / 1000
      case _ => currentTimeInSeconds
    }
    // Write Consent's Auth Context to the DB
    val authContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(user.userId)
      .map(_.map(i => BasicUserAuthContext(i.key, i.value)))
    ConsentAuthContextProvider.consentAuthContextProvider.vend.createOrUpdateConsentAuthContexts(consentId, authContexts.getOrElse(Nil))
      
    // 1. Add views
    // Please note that consents can only contain Views that the User already has access to.
    val views: Seq[ConsentView] = 
      for {
        view <- Views.views.vend.getPermissionForUser(user).map(_.views).getOrElse(Nil)
        if consent.everything || consent.views.exists(_ == PostConsentViewJsonV310(view.bankId.value,view.accountId.value, view.viewId.value))
      } yield  {
        ConsentView(
          bank_id = view.bankId.value,
          account_id = view.accountId.value,
          view_id = view.viewId.value
        )
      }
    // 2. Add Roles
    // Please note that consents can only contain Roles that the User already has access to.
    val entitlements: Seq[Role] = 
      for {
        entitlement <- Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).getOrElse(Nil)
        if consent.everything || consent.entitlements.exists(_ == PostConsentEntitlementJsonV310(entitlement.bankId,entitlement.roleName))
      } yield  {
        Role(entitlement.roleName, entitlement.bankId)
      }
    val json = ConsentJWT(
      createdByUserId=user.userId,
      sub=APIUtil.generateUUID(),
      iss=Constant.HostName,
      aud=consumerId.getOrElse(currentConsumerId),
      jti=consentId,
      iat=currentTimeInSeconds,
      nbf=timeInSeconds,
      exp=timeInSeconds + timeToLive,
      name=None,
      email=None,
      entitlements=entitlements.toList,
      views=views.toList,
      access = None
    )
    
    implicit val formats = CustomJsonFormats.formats
    val jwtPayloadAsJson = compactRender(Extraction.decompose(json))
    val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
    CertificateUtil.jwtWithHmacProtection(jwtClaims, secret)
  }

  def createBerlinGroupConsentJWT(user: Option[User],
                                  consent: PostConsentJson,
                                  secret: String,
                                  consentId: String,
                                  consumerId: Option[String],
                                  validUntil: Option[Date]): Future[String] = {

    val currentTimeInSeconds = System.currentTimeMillis / 1000
    val validUntilTimeInSeconds = validUntil match {
      case Some(date) => date.getTime() / 1000
      case _ => currentTimeInSeconds
    }
    // Write Consent's Auth Context to the DB
    user map { u =>
      val authContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(u.userId)
        .map(_.map(i => BasicUserAuthContext(i.key, i.value)))
      ConsentAuthContextProvider.consentAuthContextProvider.vend.createOrUpdateConsentAuthContexts(consentId, authContexts.getOrElse(Nil))
    }
    
    // 1. Add access
    val accounts: List[Future[ConsentView]] = consent.access.accounts.getOrElse(Nil) map { account =>
      Connector.connector.vend.getBankAccountByIban(account.iban.getOrElse(""), None) map { bankAccount =>
        ConsentView(
          bank_id = bankAccount._1.map(_.bankId.value).getOrElse(""),
          account_id = bankAccount._1.map(_.accountId.value).getOrElse(""),
          view_id = Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID
        )
      }
    }
    val balances: List[Future[ConsentView]] = consent.access.balances.getOrElse(Nil) map { account =>
      Connector.connector.vend.getBankAccountByIban(account.iban.getOrElse(""), None) map { bankAccount =>
        ConsentView(
          bank_id = bankAccount._1.map(_.bankId.value).getOrElse(""),
          account_id = bankAccount._1.map(_.accountId.value).getOrElse(""),
          view_id = Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID
        )
      }
    }
    val transactions: List[Future[ConsentView]] = consent.access.transactions.getOrElse(Nil) map { account =>
      Connector.connector.vend.getBankAccountByIban(account.iban.getOrElse(""), None) map { bankAccount =>
        ConsentView(
          bank_id = bankAccount._1.map(_.bankId.value).getOrElse(""),
          account_id = bankAccount._1.map(_.accountId.value).getOrElse(""),
          view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID
        )
      }
    }

    Future.sequence(accounts ::: balances ::: transactions) map { views =>
      val json = ConsentJWT(
        createdByUserId = user.map(_.userId).getOrElse(""),
        sub = APIUtil.generateUUID(),
        iss = Constant.HostName,
        aud = consumerId.getOrElse(""),
        jti = consentId,
        iat = currentTimeInSeconds,
        nbf = currentTimeInSeconds,
        exp = validUntilTimeInSeconds,
        name = None,
        email = None,
        entitlements = Nil,
        views = views,
        access = Some(consent.access)
      )
      implicit val formats = CustomJsonFormats.formats
      val jwtPayloadAsJson = compactRender(Extraction.decompose(json))
      val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
      CertificateUtil.jwtWithHmacProtection(jwtClaims, secret)
    }
  }
  
  def createUKConsentJWT(
    user: Option[User],
    bankId: Option[String],
    accountIds: Option[List[String]],
    permissions: List[String],
    expirationDateTime: Date,
    transactionFromDateTime: Date,
    transactionToDateTime: Date,
    secret: String,
    consentId: String,
    consumerId: Option[String]
  ): String = {

    val createdByUserId = user.map(_.userId).getOrElse("None")
    val currentConsumerId = Consumer.findAll(By(Consumer.createdByUserId, createdByUserId)).map(_.consumerId.get).headOption.getOrElse("")
    val currentTimeInSeconds = System.currentTimeMillis / 1000
    val validUntilTimeInSeconds = expirationDateTime.getTime() / 1000
    // Write Consent's Auth Context to the DB
    user map { u =>
      val authContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(u.userId)
        .map(_.map(i => BasicUserAuthContext(i.key, i.value)))
      ConsentAuthContextProvider.consentAuthContextProvider.vend.createOrUpdateConsentAuthContexts(consentId, authContexts.getOrElse(Nil))
    }
    
    // 1. Add views
    val consentViews: List[ConsentView] = if (bankId.isDefined && accountIds.isDefined) {
      permissions.map {
        permission =>
          accountIds.get.map(
            accountId =>
              ConsentView(
                bank_id = bankId.getOrElse(null),
                account_id = accountId,
                view_id = permission
              ))
      }.flatten
    } else {
      permissions.map {
        permission =>
          ConsentView(
            bank_id = null,
            account_id = null,
            view_id = permission
          )
      }
    }

    val json = ConsentJWT(
      createdByUserId = createdByUserId,
      sub = APIUtil.generateUUID(),
      iss = Constant.HostName,
      aud = consumerId.getOrElse(currentConsumerId),
      jti = consentId,
      iat = currentTimeInSeconds,
      nbf = currentTimeInSeconds,
      exp = validUntilTimeInSeconds,
      name = None,
      email = None,
      entitlements = Nil,
      views = consentViews,
      access = None
    )
    
    implicit val formats = CustomJsonFormats.formats
    val jwtPayloadAsJson = compactRender(Extraction.decompose(json))
    val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
    CertificateUtil.jwtWithHmacProtection(jwtClaims, secret)
  }

  private def checkConsumerIsActiveAndMatchedUK(consent: ConsentJWT, consumerIdOfLoggedInUser: Option[String]): Box[Boolean] = {
    Consumers.consumers.vend.getConsumerByConsumerId(consent.aud) match {
      case Full(consumerFromConsent) if consumerFromConsent.isActive.get == true => // Consumer is active
        consumerIdOfLoggedInUser match {
          case Some(consumerId) =>
            if (consumerId == consumerFromConsent.consumerId.get)
              Full(true) // This consent can be used by current application
            else // This consent can NOT be used by current application
              Failure(ErrorMessages.ConsentDoesNotMatchConsumer)
          case None => Failure(ErrorMessages.ConsumerNotFound) // Consumer cannot be found by logged in user
        }
      case Full(consumerFromConsent) if consumerFromConsent.isActive.get == false => // Consumer is NOT active
        Failure(ErrorMessages.ConsumerAtConsentDisabled + " aud: " + consent.aud)
      case _ => // There is NO Consumer
        Failure(ErrorMessages.ConsumerAtConsentCannotBeFound + " aud: " + consent.aud)
    }
  }


  def checkUKConsent(user: User, calContext: Option[CallContext]): Box[Boolean] = {
      val accessToken = calContext.flatMap(_.authReqHeaderField)
        .map(_.replaceFirst("Bearer\\s+", ""))
        .getOrElse(throw new RuntimeException("Not found http request header 'Authorization', it is mandatory."))
    val introspectOAuth2Token: OAuth2TokenIntrospection = HydraUtil.hydraAdmin.introspectOAuth2Token(accessToken, null)
    if(!introspectOAuth2Token.getActive) {
      return Failure(ErrorMessages.ConsentExpiredIssue)
    }

    val boxedConsent: Box[MappedConsent] = {
      val accessExt = introspectOAuth2Token.getExt.asInstanceOf[java.util.Map[String, String]]
      val consentId = accessExt.get("consent_id")
      Consents.consentProvider.vend.getConsentByConsentId(consentId)
    }

    boxedConsent match {
      case Full(c) if c.mStatus == ConsentStatus.AUTHORISED.toString =>
        System.currentTimeMillis match {
          case currentTimeMillis if currentTimeMillis < c.creationDateTime.getTime =>
            Failure(ErrorMessages.ConsentNotBeforeIssue)
          case _ =>
            val consumerIdOfLoggedInUser: Option[String] = calContext.flatMap(_.consumer.map(_.consumerId.get))
            implicit val dateFormats = CustomJsonFormats.formats
            val consent: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(c.jsonWebToken)
              .map(parse(_).extract[ConsentJWT])
            checkConsumerIsActiveAndMatchedUK(
              consent.openOrThrowException("Parsing of the consent failed."), 
              consumerIdOfLoggedInUser
            )
        }
      case Full(c) if c.mStatus != ConsentStatus.AUTHORISED.toString =>
        Failure(s"${ErrorMessages.ConsentStatusIssue}${ConsentStatus.AUTHORISED.toString}.")
      case _ =>
        Failure(ErrorMessages.ConsentNotFound)
    }
  }


  def filterByBankId(consents: List[MappedConsent], bankId: BankId): List[MappedConsent] = {
    implicit val formats = CustomJsonFormats.formats
    val consentsOfBank =
      consents.filter { consent =>
        val jsonWebTokenAsCaseClass: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken)
          .map(parse(_).extract[ConsentJWT])
        jsonWebTokenAsCaseClass match {
          case Full(consentJWT) => consentJWT.views.map(_.bank_id).contains(bankId.value)
          case _ => false
        }
      }
    consentsOfBank
  }

}
