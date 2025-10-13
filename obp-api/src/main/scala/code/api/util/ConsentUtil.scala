package code.api.util

import code.accountholders.AccountHolders
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{ConsentAccessJson, PostConsentJson}
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canCreateEntitlementAtOneBank}
import code.api.util.BerlinGroupSigning.getHeaderValue
import code.api.util.ErrorMessages._
import code.api.v3_1_0.{PostConsentBodyCommonJson, PostConsentEntitlementJsonV310, PostConsentViewJsonV310}
import code.api.v5_0_0.HelperInfoJson
import code.api.{APIFailure, APIFailureNewStyle, Constant, RequestHeader}
import code.bankconnectors.Connector
import code.consent
import code.consent.ConsentStatus.ConsentStatus
import code.consent.{ConsentStatus, Consents, MappedConsent}
import code.consumer.Consumers
import code.context.{ConsentAuthContextProvider, UserAuthContextProvider}
import code.entitlement.Entitlement
import code.model.Consumer
import code.model.dataAccess.BankAccountRouting
import code.scheduler.ConsentScheduler.currentDate
import code.users.Users
import code.util.Helper.MdcLoggable
import code.util.HydraUtil
import code.views.Views
import com.nimbusds.jwt.JWTClaimsSet
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiStandards
import net.liftweb.common._
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.JsonParser.ParseException
import net.liftweb.json.{Extraction, MappingException, compactRender, parse}
import net.liftweb.mapper.By
import net.liftweb.util.Props
import sh.ory.hydra.model.OAuth2TokenIntrospection

import java.text.SimpleDateFormat
import java.util.Date
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
                      request_headers: List[HTTPParam],
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
      request_headers=this.request_headers,
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
                       view_id : String,
                       helper_info: Option[HelperInfoJson]//this is only for VRP consent.
                      )

case class Consent(createdByUserId: String,
                   subject: String,
                   issuer: String,
                   consumerKey: String,
                   consentId: String,
                   issuedAt: Long,
                   validFrom: Long,
                   validTo: Long,
                   request_headers: List[HTTPParam],
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
      request_headers=this.request_headers,
      name=this.name,
      email=this.email,
      entitlements=this.entitlements,
      views=this.views,
      access = this.access
    )
  }
}

object Consent extends MdcLoggable {

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

  /**
   * Retrieves the current Consumer using either the MTLS (QWAC) certificate or the TPP signature certificate (QSealC).
   * This method checks the request headers for the relevant PEM certificates and searches for the corresponding Consumer.
   *
   * @param callContext The request context containing headers.
   * @return A Box containing the Consumer if found, otherwise Empty.
   */
  def getCurrentConsumerViaTppSignatureCertOrMtls(callContext: CallContext): Box[Consumer] = {
    { // Attempt to get the Consumer via the TPP-Signature-Certificate (Qualified Electronic Seal Certificate - QSealC)
      val tppSignatureCert: String = APIUtil.getRequestHeader(RequestHeader.`TPP-Signature-Certificate`, callContext.requestHeaders)
      if (tppSignatureCert.isEmpty) {
        logger.debug(s"| No `TPP-Signature-Certificate` header found |")
        Empty // No `TPP-Signature-Certificate` header found, continue to MTLS check
      } else {
        logger.debug(s"Get Consumer By RequestHeader.`TPP-Signature-Certificate`: $tppSignatureCert")
        Consumers.consumers.vend.getConsumerByPemCertificate(tppSignatureCert)
      }
    }.or { // If TPP certificate is not available, try to get Consumer via MTLS (Qualified Website Authentication Certificate - QWAC)
      val psd2Cert: String = APIUtil.getRequestHeader(RequestHeader.`PSD2-CERT`, callContext.requestHeaders)
      if (psd2Cert.isEmpty) {
        logger.debug(s"| No `PSD2-CERT` header found |")
        Empty // No `PSD2-CERT` header found
      } else {
        val consumerByPsd2Cert: Box[Consumer] = {
          // First, try to find the Consumer using the original certificate value
          logger.debug(s"Get Consumer By RequestHeader.`PSD2-CERT`: $psd2Cert")
          Consumers.consumers.vend.getConsumerByPemCertificate(psd2Cert)
        }.or {
          // If the original value lookup fails, normalize the certificate and try again
          val normalizedCert = CertificateUtil.normalizePemX509Certificate(psd2Cert)
          logger.debug(s"Get Consumer By RequestHeader.`PSD2-CERT` (normalized): $normalizedCert")
          Consumers.consumers.vend.getConsumerByPemCertificate(normalizedCert)
        }
        consumerByPsd2Cert
      }
    }
  }


  private def verifyHmacSignedJwt(jwtToken: String, c: MappedConsent): Boolean = {
    logger.debug(s"code.api.util.Consent.verifyHmacSignedJwt beginning:: jwtToken($jwtToken), MappedConsent($c)")
    val result = JwtUtil.verifyHmacSignedJwt(jwtToken, c.secret)
    logger.debug(s"code.api.util.Consent.verifyHmacSignedJwt result:: result($result)")
    result
  }

  private def removeBreakLines(input: String) = input
    .replace("\n", "")
    .replace("\r", "")
  private def checkConsumerIsActiveAndMatched(consent: ConsentJWT, callContext: CallContext): Box[Boolean] = {
    val consumerBox = Consumers.consumers.vend.getConsumerByConsumerId(consent.aud)
    logger.debug(s"code.api.util.Consent.checkConsumerIsActiveAndMatched.getConsumerByConsumerId consumerBox:: consumerBox($consumerBox)")
    consumerBox match {
      case Full(consumerFromConsent) if consumerFromConsent.isActive.get == true => // Consumer is active
        val validationMethod = APIUtil.getPropsValue(nameOfProperty = "consumer_validation_method_for_consent", defaultValue = "CONSUMER_CERTIFICATE")
        if(validationMethod != "CONSUMER_CERTIFICATE" && Props.mode == Props.RunModes.Production) {
          logger.warn(s"consumer_validation_method_for_consent is not set to CONSUMER_CERTIFICATE! The current value is: ${validationMethod}")
        }
        validationMethod match {
          case "CONSUMER_KEY_VALUE" =>
            val requestHeaderConsumerKey = getConsumerKey(callContext.requestHeaders)
            logger.debug(s"code.api.util.Consent.checkConsumerIsActiveAndMatched.consumerBox.requestHeaderConsumerKey:: requestHeaderConsumerKey($requestHeaderConsumerKey)")
            requestHeaderConsumerKey match {
              case Some(reqHeaderConsumerKey) =>
                if (reqHeaderConsumerKey == consumerFromConsent.key.get)
                  Full(true) // This consent can be used by current application
                else // This consent can NOT be used by current application
                  Failure(s"${ErrorMessages.ConsentDoesNotMatchConsumer} CONSUMER_KEY_VALUE")
              case None => Failure(ErrorMessages.ConsumerKeyHeaderMissing) // There is no header `Consumer-Key` in request headers
            }
          case "CONSUMER_CERTIFICATE" =>
            val clientCert: String = APIUtil.`getPSD2-CERT`(callContext.requestHeaders).getOrElse(SecureRandomUtil.csprng.nextLong().toString)
            logger.debug(s"| Consent.checkConsumerIsActiveAndMatched | clientCert | $clientCert |")
            logger.debug(s"| Consent.checkConsumerIsActiveAndMatched | consumerFromConsent.clientCertificate | ${consumerFromConsent.clientCertificate} |")
            if (removeBreakLines(clientCert) == removeBreakLines(consumerFromConsent.clientCertificate.get)) {
              logger.debug(s"| removeBreakLines(clientCert) == removeBreakLines(consumerFromConsent.clientCertificate.get | true |")
              Full(true) // This consent can be used by current application
            } else { // This consent can NOT be used by current application
              Failure(s"${ErrorMessages.ConsentDoesNotMatchConsumer} CONSUMER_CERTIFICATE")
            }
          case "TPP_SIGNATURE_CERTIFICATE" =>
            val tppSignatureCertificate = getHeaderValue(RequestHeader.`TPP-Signature-Certificate`, callContext.requestHeaders)
            logger.debug(s"| Consent.checkConsumerIsActiveAndMatched | tppSignatureCertificate | $tppSignatureCertificate |")
            logger.debug(s"| Consent.checkConsumerIsActiveAndMatched | consumerFromConsent.clientCertificate | ${consumerFromConsent.clientCertificate} |")
            if (removeBreakLines(tppSignatureCertificate) == removeBreakLines(consumerFromConsent.clientCertificate.get)) {
              logger.debug(s"""| removeBreakLines(tppSignatureCertificate) == removeBreakLines(consumerFromConsent.clientCertificate.get | true |""")
              Full(true) // This consent can be used by current application
            } else { // This consent can NOT be used by current application
              Failure(s"${ErrorMessages.ConsentDoesNotMatchConsumer} TPP_SIGNATURE_CERTIFICATE")
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

  private def tppIsConsentHolder(consumerIdFromConsent: String, callContext: CallContext): Boolean = {
    val consumerIdFromCurrentCall = callContext.consumer.map(_.consumerId.get).orNull
    logger.debug(s"consumerIdFromConsent == consumerIdFromCurrentCall ($consumerIdFromConsent == $consumerIdFromCurrentCall)")
    consumerIdFromConsent == consumerIdFromCurrentCall
  }

  private def checkConsent(consent: ConsentJWT, consentIdAsJwt: String, callContext: CallContext): Box[Boolean] = {
    logger.debug(s"code.api.util.Consent.checkConsent beginning: consent($consent), consentIdAsJwt($consentIdAsJwt)")
    val consentBox = Consents.consentProvider.vend.getConsentByConsentId(consent.jti)
    logger.debug(s"code.api.util.Consent.checkConsent.getConsentByConsentId: consentBox($consentBox)")
    val result = consentBox match {
      case Full(c) =>
        if (!tppIsConsentHolder(c.mConsumerId.get, callContext)) { // Always check TPP first
          ErrorUtil.apiFailureToBox(ErrorMessages.ConsentNotFound, 401)(Some(callContext))
        } else if (!verifyHmacSignedJwt(consentIdAsJwt, c)) { // verify signature
          Failure(ErrorMessages.ConsentVerificationIssue)
        } else {
          // Then check time constraints
          val currentTimeInSeconds = System.currentTimeMillis / 1000
          if (currentTimeInSeconds < consent.nbf) {
            Failure(ErrorMessages.ConsentNotBeforeIssue)
          } else if (currentTimeInSeconds > consent.exp) {
            ErrorUtil.apiFailureToBox(ErrorMessages.ConsentExpiredIssue, 401)(Some(callContext))
          } else {
            // Then check consent status
            if (c.apiStandard == ConstantsBG.berlinGroupVersion1.apiStandard &&
              c.status.toLowerCase != ConsentStatus.valid.toString) {
              Failure(s"${ErrorMessages.ConsentStatusIssue}${ConsentStatus.valid.toString}.")
            } else if ((c.apiStandard == ApiStandards.obp.toString || c.apiStandard.isBlank) &&
              c.mStatus.toString.toUpperCase != ConsentStatus.ACCEPTED.toString) {
              Failure(s"${ErrorMessages.ConsentStatusIssue}${ConsentStatus.ACCEPTED.toString}.")
            } else {
              logger.debug(s"start code.api.util.Consent.checkConsent.checkConsumerIsActiveAndMatched(consent($consent))")
              val consumerResult = checkConsumerIsActiveAndMatched(consent, callContext)
              logger.debug(s"end code.api.util.Consent.checkConsent.checkConsumerIsActiveAndMatched: result($consumerResult)")
              consumerResult
            }
          }
        }
      case _ =>
        Failure(ErrorMessages.ConsentNotFound)
    }
    logger.debug(s"code.api.util.Consent.checkConsent.result: result($result)")
    result
  }

  private def getOrCreateUser(subject: String, issuer: String, consentId: Option[String], name: Option[String], email: Option[String]): Future[(Box[User], Boolean)] = {
    logger.debug(s"getOrCreateUser(subject($subject), issuer($issuer), consentId($consentId), name($name), email($email))")
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
        userId = None,
        createdByUserInvitationId = None,
        company = None,
        lastMarketingAgreementSignedDate = None
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
                case _ =>
                  (entitlement, CannotAddEntitlement + entitlement)
              }
            case true =>
              (entitlement, "AddedOrExisted")
          }
        case false =>
          (entitlement, InvalidEntitlement + entitlement)
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
          case _   =>
            //Here, we do not throw an exception, just log the error.
            logger.error(CannotAddEntitlement + failedToAdd.map(i => (i._1, i._2)).mkString(", "))
            Full(user)
        }
      case _ =>
        Failure(CannotGetEntitlements + user.userId)
    }

  }

  private def grantAccessToViews(user: User, consent: ConsentJWT): Box[User] = {
    for {
      view <- consent.views
    } yield {
      val bankIdAccountIdViewId = BankIdAccountIdViewId(BankId(view.bank_id), AccountId(view.account_id),ViewId(view.view_id))
      Views.views.vend.revokeAccess(bankIdAccountIdViewId, user)
    }
    val result: List[Box[View]] = {
      for {
        view <- consent.views
      } yield {
        val bankIdAccountIdViewId = BankIdAccountIdViewId(BankId(view.bank_id), AccountId(view.account_id),ViewId(view.view_id))
        Views.views.vend.systemView(ViewId(view.view_id)) match {
          case Full(systemView) =>
            Views.views.vend.grantAccessToSystemView(BankId(view.bank_id), AccountId(view.account_id), systemView, user)
          case _ =>
            // It's not system view
            Views.views.vend.grantAccessToCustomView(bankIdAccountIdViewId, user)
        }
      }
    }
    val errorMessages: List[String] = result.filterNot(_.isDefined).map {
      case ParamFailure(_, _, _, APIFailure(msg, httpCode)) => msg
      case Failure(message, _, _) => message
    }
    if (errorMessages.isEmpty) Full(user) else Failure(CouldNotAssignAccountAccess + errorMessages.mkString(", "))
  }

  private def applyConsentRulesCommonOldStyle(consentIdAsJwt: String, calContext: CallContext): Box[User] = {
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
          logger.debug(s"applyConsentRulesCommonOldStyle.getSignedPayloadAsJson.Start of net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]: $jsonAsString")
          val consent = net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]
          logger.debug(s"applyConsentRulesCommonOldStyle.getSignedPayloadAsJson.End of net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]: $consent")
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
          case e: Exception => Failure(ErrorUtil.extractFailureMessage(e))
        }
      case failure@Failure(_, _, _) =>
        failure
      case _ =>
        Failure("Cannot extract data from: " + consentIdAsJwt)
    }
  }

  private def applyConsentRulesCommon(consentAsJwt: String, callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    implicit val dateFormats = CustomJsonFormats.formats

    def applyConsentRules(consent: ConsentJWT): Future[(Box[User], Option[CallContext])] = {
      val temp = callContext
      // updated context if createdByUserId is present
      val cc = if (consent.createdByUserId.nonEmpty) {
        val onBehalfOfUser = Users.users.vend.getUserByUserId(consent.createdByUserId)
        temp.copy(onBehalfOfUser = onBehalfOfUser.toOption)
      } else {
        temp
      }
      if (cc.onBehalfOfUser.nonEmpty &&
        APIUtil.getPropsAsBoolValue(nameOfProperty = "experimental_become_user_that_created_consent", defaultValue = false)) {
        logger.info("experimental_become_user_that_created_consent = true")
        logger.info(s"${cc.onBehalfOfUser.map(_.userId).getOrElse("")} is logged on instead of Consent user")
        Future(cc.onBehalfOfUser, Some(cc)) // Just propagate on behalf of user back
      } else {
        logger.info("experimental_become_user_that_created_consent = false")
        logger.info(s"Getting Consent user (consent.sub: ${consent.sub}, consent.iss: ${consent.iss})")
        // 1. Get or Create a User
        getOrCreateUser(consent.sub, consent.iss, Some(consent.jti), None, None) map {
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
                    (failure, Some(cc))
                  case _ =>
                    (Failure(ErrorMessages.UnknownError), Some(cc))
                }
              case failure@Failure(msg, exp, chain) => // Handled errors
                (Failure(msg), Some(cc))
              case _ =>
                (Failure(CannotAddEntitlement + consentAsJwt), Some(cc))
            }
          case _ =>
            (Failure(CannotGetOrCreateUser + consentAsJwt), Some(cc))
        }
      }
    }


    JwtUtil.getSignedPayloadAsJson(consentAsJwt) match {
      case Full(jsonAsString) =>
        try {
          logger.debug(s"applyConsentRulesCommon.Start of net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]: $jsonAsString")
          val consent = net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]
          logger.debug(s"applyConsentRulesCommon.End of net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]: $consent")
          checkConsent(consent, consentAsJwt, callContext) match { // Check is it Consent-JWT expired
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
          case e: Exception => Future(Failure(ErrorUtil.extractFailureMessage(e)), Some(callContext))
        }
      case failure@Failure(_, _, _) =>
        Future(failure, Some(callContext))
      case _ =>
        Future(Failure("Cannot extract data from: " + consentAsJwt), Some(callContext))
    }
  }

  def applyRules(consentJwt: Option[String], callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    val allowed = APIUtil.getPropsAsBoolValue(nameOfProperty="consents.allowed", defaultValue=false)
    (consentJwt, allowed) match {
      case (Some(jwt), true) => applyConsentRulesCommon(jwt, callContext)
      case (_, false) => Future((Failure(ErrorMessages.ConsentDisabled), Some(callContext)))
      case (None, _) => Future((Failure(ErrorMessages.ConsentHeaderNotFound), Some(callContext)))
    }
  }

  def getConsentJwtValueByConsentId(consentId: String): Option[MappedConsent] = {
    APIUtil.checkIfStringIsUUID(consentId) match {
      case true => // String is a UUID
        Consents.consentProvider.vend.getConsentByConsentId(consentId) match {
          case Full(consent) => Some(consent)
          case _ => None // It's not valid UUID value
        }
      case false => None // It's not UUID at all
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
  private def applyBerlinGroupConsentRulesCommon(consentId: String, callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    implicit val dateFormats = CustomJsonFormats.formats

    def applyConsentRules(consent: ConsentJWT, callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
      val cc = callContext
      // 1. Get or Create a User
      getOrCreateUser(consent.sub, consent.iss, Some(consent.toConsent().consentId), None, None) map {
        case (Full(user), newUser) =>
          // 2. Assign entitlements (Roles) to the User
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
            case failure@Failure(msg, exp, chain) => // Handled errors
              (Failure(msg), Some(cc))
            case _ =>
              (Failure(CannotAddEntitlement + consentId), Some(cc))
          }
        case _ =>
          (Failure(CannotGetOrCreateUser + consentId), Some(cc))
      }
    }

    def checkFrequencyPerDay(storedConsent: consent.ConsentTrait) = {
      if(BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(callContext.requestHeaders)) {
        def isSameDay(date1: Date, date2: Date): Boolean = {
          val fmt = new SimpleDateFormat("yyyyMMdd")
          fmt.format(date1).equals(fmt.format(date2))
        }

        var usesSoFarTodayCounter = storedConsent.usesSoFarTodayCounter
        storedConsent.recurringIndicator match {
          case false => // The consent is for one access to the account data
            if (usesSoFarTodayCounter == 0) // Maximum value is "1".
              (true, 0) // All good
            else
              (false, 1) // Exceeded rate limit
          case true => // The consent is for recurring access to the account data
            if (!isSameDay(storedConsent.usesSoFarTodayCounterUpdatedAt, new Date())) {
              usesSoFarTodayCounter = 0 // Reset counter
            }
            if (usesSoFarTodayCounter < storedConsent.frequencyPerDay)
              (true, usesSoFarTodayCounter) // All good
            else
              (false, storedConsent.frequencyPerDay) // Exceeded rate limit
        }
      } else {
        (true, 0) // All good
      }
    }

    // 1st we need to find a Consent via the field MappedConsent.consentId
    Consents.consentProvider.vend.getConsentByConsentId(consentId) match {
      case Full(storedConsent) =>
        val user = Users.users.vend.getUserByUserId(storedConsent.userId)
        logger.debug(s"applyBerlinGroupConsentRulesCommon.storedConsent.user : $user")
        val updatedCallContext = callContext.copy(consenter = user)
        // This function MUST be called only once per call. I.e. it's date dependent
        val (canBeUsed, currentCounterState) = checkFrequencyPerDay(storedConsent)
        if(canBeUsed) {
          JwtUtil.getSignedPayloadAsJson(storedConsent.jsonWebToken) match {
            case Full(jsonAsString) =>
              try {
                logger.debug(s"applyBerlinGroupConsentRulesCommon.Start of net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]: $jsonAsString")
                val consent = net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]
                logger.debug(s"applyBerlinGroupConsentRulesCommon.End of net.liftweb.json.parse(jsonAsString).extract[ConsentJWT]: $consent")
                val consentBox = checkConsent(consent, storedConsent.jsonWebToken, updatedCallContext)
                logger.debug(s"End of net.liftweb.json.parse(jsonAsString).extract[ConsentJWT].checkConsent.consentBox: $consent")
                consentBox match { // Check is it Consent-JWT expired
                  case (Full(true)) => // OK
                    if(BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(callContext.requestHeaders)) {
                      // Update MappedConsent.usesSoFarTodayCounter field
                      val consentUpdatedBox = Consents.consentProvider.vend.updateBerlinGroupConsent(consentId, currentCounterState + 1)
                      logger.debug(s"applyBerlinGroupConsentRulesCommon.consentUpdatedBox: $consentUpdatedBox")
                    }
                    applyConsentRules(consent, updatedCallContext)
                  case failure@Failure(_, _, _) => // Handled errors
                    Future(failure, Some(updatedCallContext))
                  case _ => // Unexpected errors
                    Future(Failure(ErrorMessages.ConsentCheckExpiredIssue), Some(updatedCallContext))
                }
              } catch { // Possible exceptions
                case e: ParseException =>
                  logger.debug(s"code.api.util.JwtUtil.getSignedPayloadAsJson.ParseException: $e")
                  Future(Failure("ParseException: " + e.getMessage), Some(updatedCallContext))
                case e: MappingException =>
                  logger.debug(s"code.api.util.JwtUtil.getSignedPayloadAsJson.MappingException: $e")
                  Future(Failure("MappingException: " + e.getMessage), Some(updatedCallContext))
                case e: Throwable =>
                  logger.debug(s"code.api.util.JwtUtil.getSignedPayloadAsJson.Throwable: $e")
                  Future(Failure(ErrorUtil.extractFailureMessage(e)), Some(updatedCallContext))
              }
            case failure@Failure(_, _, _) =>
              Future(failure, Some(updatedCallContext))
            case _ =>
              Future(Failure("Cannot extract data from: " + consentId), Some(updatedCallContext))
          }
        } else {
          val errorMessage = ErrorMessages.TooManyRequests + s" ${RequestHeader.`Consent-ID`}: $consentId"
          Future(fullBoxOrException(Empty ~> APIFailureNewStyle(errorMessage, 429, Some(callContext.toLight))), Some(callContext))
        }
      case failure@Failure(_, _, _) =>
        Future(failure, Some(callContext))
      case _ =>
        val errorMessage = ErrorMessages.ConsentNotFound + s" ($consentId)"
        Future(fullBoxOrException(Empty ~> APIFailureNewStyle(errorMessage, 400, Some(callContext.toLight))), Some(callContext))
    }
  }
  def applyBerlinGroupRules(consentId: Option[String], callContext: CallContext): Future[(Box[User], Option[CallContext])] = {
    val allowed = APIUtil.getPropsAsBoolValue(nameOfProperty="consents.allowed", defaultValue=false)
    (consentId, allowed) match {
      case (Some(consentId), true) => applyBerlinGroupConsentRulesCommon(consentId, callContext)
      case (_, false) => Future((Failure(ErrorMessages.ConsentDisabled), Some(callContext)))
      case (None, _) => Future((Failure(ErrorMessages.ConsentHeaderNotFound), Some(callContext)))
    }
  }  
  def applyRulesOldStyle(consentId: Option[String], callContext: CallContext): (Box[User], CallContext) = {
    val allowed = APIUtil.getPropsAsBoolValue(nameOfProperty="consents.allowed", defaultValue=false)
    (consentId, allowed) match {
      case (Some(consentId), true) => (applyConsentRulesCommonOldStyle(consentId, callContext), callContext)
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
                       timeToLive: Long,
                       helperInfo: Option[HelperInfoJson] //this is only used for VRP consent, all the others are NONE.
  ): String = {
    
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
    val allUserViews = Views.views.vend.getPermissionForUser(user).map(_.views).getOrElse(Nil)
    val views = consent.bank_id match {
      case Some(bankId) =>
        // Filter out roles for other banks
        allUserViews.filterNot { i =>
          !i.bankId.value.isEmpty() && i.bankId.value != bankId
        }
      case None =>
        allUserViews
    }
    val viewsToAdd: Seq[ConsentView] = 
      for {
        view <- views
        if consent.everything || consent.views.exists(_ == PostConsentViewJsonV310(view.bankId.value,view.accountId.value, view.viewId.value))
      } yield  {
        ConsentView(
          bank_id = view.bankId.value,
          account_id = view.accountId.value,
          view_id = view.viewId.value,
          helper_info = helperInfo
        )
      }
    // 2. Add Roles
    // Please note that consents can only contain Roles that the User already has access to.
    val allUserEntitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).getOrElse(Nil)
    val entitlements = consent.bank_id match {
      case Some(bankId) =>
        // Filter out roles for other banks
        allUserEntitlements.filterNot { i => 
          !i.bankId.isEmpty() && i.bankId != bankId
        }
      case None =>
        allUserEntitlements
    }
    val entitlementsToAdd: Seq[Role] = 
      for {
        entitlement <- entitlements
        if !(entitlement.roleName == canCreateEntitlementAtOneBank.toString())
        if !(entitlement.roleName == canCreateEntitlementAtAnyBank.toString())
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
      request_headers = Nil,
      name=None,
      email=None,
      entitlements=entitlementsToAdd.toList,
      views=viewsToAdd.toList,
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
                                  validUntil: Option[Date], 
                                  callContext: Option[CallContext]): Future[Box[String]] = {

    val currentTimeInSeconds = System.currentTimeMillis / 1000
    val validUntilTimeInSeconds = validUntil.map(_.getTime / 1000).getOrElse(currentTimeInSeconds)

    // Write Consent's Auth Context to DB
    user.foreach { u =>
      val authContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(u.userId)
        .map(_.map(i => BasicUserAuthContext(i.key, i.value)))
      ConsentAuthContextProvider.consentAuthContextProvider.vend.createOrUpdateConsentAuthContexts(consentId, authContexts.getOrElse(Nil))
    }

    // Helper to get ConsentView or fail box
    def getConsentView(ibanOpt: Option[String], viewId: String): Future[Box[ConsentView]] = {
      val iban = ibanOpt.getOrElse("")
      Connector.connector.vend.getBankAccountByIban(iban, callContext).map { bankAccount =>
        logger.debug(s"createBerlinGroupConsentJWT.bankAccount: $bankAccount")
        val error = s"${InvalidConnectorResponse} IBAN: $iban ${handleBox(bankAccount._1)}"
        bankAccount._1 match {
          case Full(acc) =>
            Full(ConsentView(
              bank_id = acc.bankId.value,
              account_id = acc.accountId.value,
              view_id = viewId,
              None
            ))
          case _ =>
            ErrorUtil.apiFailureToBox(error, 400)(callContext)
        }
      }
    }

    // Prepare lists of future boxes
    val allAccesses = consent.access.accounts.getOrElse(Nil) :::
      consent.access.balances.getOrElse(Nil) :::
      consent.access.transactions.getOrElse(Nil)

    val accounts: List[Future[Box[ConsentView]]] = allAccesses.distinct.map { account =>
      getConsentView(account.iban, Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID)
    }

    val balances: List[Future[Box[ConsentView]]] = consent.access.balances.getOrElse(Nil).map { account =>
      getConsentView(account.iban, Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID)
    }
    val transactions: List[Future[Box[ConsentView]]] = consent.access.transactions.getOrElse(Nil).map { account =>
      getConsentView(account.iban, Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
    }

    // Collect optional headers
    val headers = callContext.map(_.requestHeaders).getOrElse(Nil)
    val tppRedirectUri = headers.find(_.name == RequestHeader.`TPP-Redirect-URI`)
    val tppNokRedirectUri = headers.find(_.name == RequestHeader.`TPP-Nok-Redirect-URI`)
    val xRequestId = headers.find(_.name == RequestHeader.`X-Request-ID`)
    val psuDeviceId = headers.find(_.name == RequestHeader.`PSU-Device-ID`)
    val psuIpAddress = headers.find(_.name == RequestHeader.`PSU-IP-Address`)
    val psuGeoLocation = headers.find(_.name == RequestHeader.`PSU-Geo-Location`)

    def sequenceBoxes[A](boxes: List[Box[A]]): Box[List[A]] = {
      boxes.foldRight(Full(Nil): Box[List[A]]) { (box, acc) =>
        for {
          x <- box
          xs <- acc
        } yield x :: xs
      }
    }

    // Combine and build final JWT
    Future.sequence(accounts ::: balances ::: transactions).map { listOfBoxes =>
      sequenceBoxes(listOfBoxes).map { views =>
      val json = ConsentJWT(
          createdByUserId = user.map(_.userId).getOrElse(""),
          sub = APIUtil.generateUUID(),
          iss = Constant.HostName,
          aud = consumerId.getOrElse(""),
          jti = consentId,
          iat = currentTimeInSeconds,
          nbf = currentTimeInSeconds,
          exp = validUntilTimeInSeconds,
          request_headers = List(
            tppRedirectUri, tppNokRedirectUri, xRequestId, psuDeviceId, psuIpAddress, psuGeoLocation
          ).flatten,
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
  }
  def updateAccountAccessOfBerlinGroupConsentJWT(access: ConsentAccessJson,
                                                 consent: MappedConsent,
                                                 callContext: Option[CallContext]): Future[Box[String]] = {
    implicit val dateFormats = CustomJsonFormats.formats
    val payloadToUpdate: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken) // Payload as JSON string
      .map(net.liftweb.json.parse(_).extract[ConsentJWT]) // Extract case class


    // 1. Add access
    val accounts: List[Future[ConsentView]] = access.accounts.getOrElse(Nil) map { account =>
      Connector.connector.vend.getBankAccountByIban(account.iban.getOrElse(""), callContext) map { bankAccount =>
        logger.debug(s"createBerlinGroupConsentJWT.accounts.bankAccount: $bankAccount")
        val error = s"${InvalidConnectorResponse} IBAN: ${account.iban.getOrElse("")} ${handleBox(bankAccount._1)}"
        ConsentView(
          bank_id = bankAccount._1.map(_.bankId.value).getOrElse(""),
          account_id = bankAccount._1.map(_.accountId.value).openOrThrowException(error),
          view_id = Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID,
          None
        )
      }
    }
    val balances: List[Future[ConsentView]] = access.balances.getOrElse(Nil) map { account =>
      Connector.connector.vend.getBankAccountByIban(account.iban.getOrElse(""), callContext) map { bankAccount =>
        logger.debug(s"createBerlinGroupConsentJWT.balances.bankAccount: $bankAccount")
        val error = s"${InvalidConnectorResponse} IBAN: ${account.iban.getOrElse("")} ${handleBox(bankAccount._1)}"
        ConsentView(
          bank_id = bankAccount._1.map(_.bankId.value).getOrElse(""),
          account_id = bankAccount._1.map(_.accountId.value).openOrThrowException(error),
          view_id = Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID,
          None
        )
      }
    }
    val transactions: List[Future[ConsentView]] = access.transactions.getOrElse(Nil) map { account =>
      Connector.connector.vend.getBankAccountByIban(account.iban.getOrElse(""), callContext) map { bankAccount =>
        logger.debug(s"createBerlinGroupConsentJWT.transactions.bankAccount: $bankAccount")
        val error = s"${InvalidConnectorResponse} IBAN: ${account.iban.getOrElse("")} ${handleBox(bankAccount._1)}"
        ConsentView(
          bank_id = bankAccount._1.map(_.bankId.value).getOrElse(""),
          account_id = bankAccount._1.map(_.accountId.value).openOrThrowException(error),
          view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID,
          None
        )
      }
    }

    Future.sequence(accounts ::: balances ::: transactions) map { views =>
      if(views.isEmpty) {
        Empty
      } else {
        val updatedPayload = payloadToUpdate.map(i =>
          i.copy(views = views) // Update the field "views"
            .copy(access = Some(access)) // Update the field "access"
        )
        val jwtPayloadAsJson = compactRender(Extraction.decompose(updatedPayload))
        val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
        Full(CertificateUtil.jwtWithHmacProtection(jwtClaims, consent.secret))
      }
    }
  }
  def updateViewsOfBerlinGroupConsentJWT(user: User,
                                         consent: MappedConsent,
                                         callContext: Option[CallContext]): Future[Box[MappedConsent]] = {
    implicit val dateFormats = CustomJsonFormats.formats
    val payloadToUpdate: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken) // Payload as JSON string
      .map(net.liftweb.json.parse(_).extract[ConsentJWT]) // Extract case class

    val availableAccountsUserIbans: List[String] = payloadToUpdate match {
      case Full(consentJwt) =>
        val availableAccountsUserIbans: List[String] =
          if (consentJwt.access.map(_.availableAccounts.contains("allAccounts")).isDefined) {
            // Get all accounts held by the current user
            val userAccounts: List[BankIdAccountId] =
              AccountHolders.accountHolders.vend.getAccountsHeldByUser(user, Some(null)).toList
            userAccounts.flatMap { acc =>
              BankAccountRouting.find(
                By(BankAccountRouting.BankId, acc.bankId.value),
                By(BankAccountRouting.AccountId, acc.accountId.value),
                By(BankAccountRouting.AccountRoutingScheme, "IBAN")
              ).map(_.AccountRoutingAddress.get)
            }
          } else {
            val emptyList: List[String] = Nil
            emptyList
          }
        availableAccountsUserIbans
      case _ =>
        val emptyList: List[String] = Nil
        emptyList
    }


    // 1. Add access
    val availableAccounts: List[Future[ConsentView]] = availableAccountsUserIbans.distinct map { iban =>
      Connector.connector.vend.getBankAccountByIban(iban, callContext) map { bankAccount =>
        logger.debug(s"createBerlinGroupConsentJWT.accounts.bankAccount: $bankAccount")
        val error = s"${InvalidConnectorResponse} IBAN: ${iban} ${handleBox(bankAccount._1)}"
        ConsentView(
          bank_id = bankAccount._1.map(_.bankId.value).getOrElse(""),
          account_id = bankAccount._1.map(_.accountId.value).openOrThrowException(error),
          view_id = Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID,
          None
        )
      }
    }

    Future.sequence(availableAccounts) map { views =>
      if(views.isEmpty) {
        Empty
      } else {
        val updatedPayload = payloadToUpdate.map(i =>
          i.copy(views = views) // Update the field "views"
        )
        val jwtPayloadAsJson = compactRender(Extraction.decompose(updatedPayload))
        val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
        val jwt = CertificateUtil.jwtWithHmacProtection(jwtClaims, consent.secret)
        Consents.consentProvider.vend.setJsonWebToken(consent.consentId, jwt)
      }
    }
  }

  def updateUserIdOfBerlinGroupConsentJWT(createdByUserId: String,
                                          consent: MappedConsent,
                                          callContext: Option[CallContext]): Box[String] = {
    implicit val dateFormats = CustomJsonFormats.formats
    val payloadToUpdate: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken) // Payload as JSON string
      .map(net.liftweb.json.parse(_).extract[ConsentJWT]) // Extract case class

    val updatedPayload = payloadToUpdate.map(i => i.copy(createdByUserId = createdByUserId)) // Update only the field "createdByUserId"
    val jwtPayloadAsJson = compactRender(Extraction.decompose(updatedPayload))
    val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
    Full(CertificateUtil.jwtWithHmacProtection(jwtClaims, consent.secret))
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
                view_id = permission,
                None
              ))
      }.flatten
    } else {
      permissions.map {
        permission =>
          ConsentView(
            bank_id = null,
            account_id = null,
            view_id = permission,
            None
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
      request_headers = Nil,
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
      case Full(c) if c.mStatus.toString().toUpperCase() == ConsentStatus.AUTHORISED.toString =>
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
      case Full(c) if c.mStatus.toString().toUpperCase() != ConsentStatus.AUTHORISED.toString =>
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
          // Views
          case Full(consentJWT) if consentJWT.views.isEmpty => true // There is no IAM
          case Full(consentJWT) if consentJWT.views.map(_.bank_id).contains(bankId.value) => true
          // Roles
          case Full(consentJWT) if consentJWT.entitlements.exists(_.bank_id.isEmpty()) => true // System roles
          case Full(consentJWT) if consentJWT.entitlements.map(_.bank_id).contains(bankId.value) => true // Bank level roles
          case _ => false
        }
      }
    consentsOfBank
  }

  def filterStrictlyByBank(consents: List[MappedConsent], bankId: String): List[MappedConsent] = {
    implicit val formats = CustomJsonFormats.formats
    val consentsOfBank =
      consents.filter { consent =>
        val jsonWebTokenAsCaseClass: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken)
          .map(parse(_).extract[ConsentJWT])
        jsonWebTokenAsCaseClass match {
          // There is a View related to Bank ID
          case Full(consentJWT) if consentJWT.views.map(_.bank_id).contains(bankId) => true
          // There is a Role related to Bank ID
          case Full(consentJWT) if consentJWT.entitlements.map(_.bank_id).contains(bankId) => true
          // Filter out Consent because there is no a View or a Role related to Bank ID
          case _ => false
        }
      }
    consentsOfBank
  }

  def expireAllPreviousValidBerlinGroupConsents(consent: MappedConsent, updateToStatus: ConsentStatus): Boolean = {
    if(updateToStatus == ConsentStatus.valid &&
      consent.apiStandard == ConstantsBG.berlinGroupVersion1.apiStandard) {
      MappedConsent.findAll( // Find all
          By(MappedConsent.mApiStandard, ConstantsBG.berlinGroupVersion1.apiStandard), // Berlin Group
          By(MappedConsent.mRecurringIndicator, true), // recurring
          By(MappedConsent.mStatus, ConsentStatus.valid.toString), // and valid consents
          By(MappedConsent.mUserId, consent.userId), // for the same PSU
          By(MappedConsent.mConsumerId, consent.consumerId), // from the same TPP
        ).filterNot(_.consentId == consent.consentId) // Exclude current consent
        .map{ c => // Set to terminatedByTpp
          val message = s"|---> Changed status from ${c.status} to ${ConsentStatus.terminatedByTpp.toString} for consent ID: ${c.id}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("") // Prepend to existing note if any
          val changedStatus =
            c.mStatus(ConsentStatus.terminatedByTpp.toString)
              .mNote(newNote)
              .mLastActionDate(new Date())
              .save
          if(changedStatus) logger.warn(message)
          changedStatus
        }.forall(_ == true)
    } else {
      true
    }
  }

  /*
    // Example Usage
    val box1: Box[String] = Full("Hello, World!")
    val box2: Box[String] = Failure("Something went wrong")
    val box3: Box[String] = ParamFailure("Invalid parameter", Empty, Empty, "UserID=123")

    println(handleBox(box1)) // Output: "Success: Hello, World!"
    println(handleBox(box2)) // Output: "Error: Something went wrong"
    println(handleBox(box3)) // Output: "Error: Invalid parameter (Parameter: UserID=123)"
 */
  def handleBox[T](box: Box[T]): String = box match {
    case Full(value) =>
      s"Success: $value"
    case Empty =>
      "Error: Box is empty"
    case ParamFailure(msg, _, _, param) =>
      s"Error: $msg (Parameter: $param)"
    case Failure(msg, _, _) =>
      s"Error: $msg"
  }




}
