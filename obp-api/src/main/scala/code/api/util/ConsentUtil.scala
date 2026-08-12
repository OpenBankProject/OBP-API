package code.api.util

import org.json4s._
import code.accountholders.AccountHolders
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{ConsentAccessJson, PostConsentJson}
import code.api.util.APIUtil.{HTTPParam, fullBoxOrException}
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
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.counterpartylimit.CounterpartyLimitProvider
import code.metadata.counterparties.Counterparties
import code.views.Views
import com.nimbusds.jwt.JWTClaimsSet
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiStandards
import net.liftweb.common._
import org.json4s.ParserUtil.ParseException
import org.json4s.{Extraction, MappingException}
import com.openbankproject.commons.util.JsonAliases.{compactRender, parse}
import net.liftweb.mapper.By
import net.liftweb.util.Props

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable.{List, Nil}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
// Not scala.util.Failure: net.liftweb.common._ above already binds that name to Box's failure case.
import scala.util.{Success, Try}

// Design boundary (not enforced by the compiler — keep it that way by convention): consent-layer
// attributes belong on the consent record, never as a View can_* permission. BG's
// frequencyPerDay/recurringIndicator/validUntil and UK's TransactionFromDateTime/ToDateTime/
// ExpirationDateTime (exp/nbf above) already live here for that reason. Do not add a can_* string
// like "can_see_transactions_last_90_days" to a system view's permission set to express a
// consent's time-boxing or access-frequency limit — that's a property of *this* consent, not of
// the account's view. See MapperViews.applyDefaultsForSystemView / Constant.SYSTEM_READ_*_VIEW_PERMISSION
// for where view-layer can_* sets are defined.
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
            // Normalise BOTH sides. PSD2-CERT is canonicalised on ingress (Psd2CertIngress), but the
            // stored certificate is whatever was pasted when the Consumer was registered, so only
            // the request side is under our control. The removeBreakLines comparison is kept as an
            // alternative rather than replaced: it strips line breaks only, where
            // comparePemX509Certificates rewrites whitespace between the PEM markers and leaves a
            // marker-less value untouched. Neither subsumes the other for every stored form, so
            // accepting either keeps this strictly more permissive than before — no Consumer that
            // matched previously can stop matching.
            val certificateMatches =
              CertificateUtil.comparePemX509Certificates(clientCert, consumerFromConsent.clientCertificate.get) ||
                removeBreakLines(clientCert) == removeBreakLines(consumerFromConsent.clientCertificate.get)
            if (certificateMatches) {
              logger.debug(s"| Consent.checkConsumerIsActiveAndMatched | certificate matches | true |")
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
          val consentConsumerId = c.mConsumerId.get
          val requestConsumerId = callContext.consumer.map(_.consumerId.get).getOrElse("NONE")
          val consumerValidationMethodForConsent = APIUtil.getPropsValue("consumer_validation_method_for_consent").openOr("")
          if(requestConsumerId == "NONE" || consumerValidationMethodForConsent.isEmpty) {
            logger.warn(s"consumer_validation_method_for_consent is empty while request consumer_id=NONE - consent_id=${consent.jti}, aud=${consent.aud}")
          }
          // Get consumer keys for debugging
          val consentConsumerKey = Consumers.consumers.vend.getConsumerByConsumerId(consentConsumerId).map(_.key.get).getOrElse("Unknown")
          val requestConsumerKey = callContext.consumer.map(_.key.get).getOrElse("None")
          val detailedErrorMsg = s"${ErrorMessages.ConsentNotFound} Consumer mismatch: consent has consumer_id='$consentConsumerId' (consumer_key='$consentConsumerKey'), but current request has consumer_id='$requestConsumerId' (consumer_key='$requestConsumerKey')"
          logger.debug(s"ConsentNotFound: TPP/Consumer mismatch. Consent holder consumer_id=$consentConsumerId, Request consumer_id=$requestConsumerId, consent_id=${consent.jti}")
          logger.debug(s"ConsentNotFound: $detailedErrorMsg")
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
      case Empty =>
        logger.info(s"ConsentNotFound: Consent does not exist in database. consent_id=${consent.jti}")
        Failure(ErrorMessages.ConsentNotFound)
      case Failure(msg, _, _) =>
        logger.info(s"ConsentNotFound: Failed to retrieve consent from database. consent_id=${consent.jti}, error=$msg")
        Failure(ErrorMessages.ConsentNotFound)
      case _ =>
        logger.info(s"ConsentNotFound: Unexpected error retrieving consent. consent_id=${consent.jti}")
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

  /**
   * Materialise a consent's views as AccountAccess rows, always under ALL_CONSUMERS.
   *
   * Every standard wants this and none wants anything else: each consent resolves to its own shadow
   * user (createXxxConsentJWT gives every consent a random `sub`, which applyConsentRules and
   * resolveUKConsentPrincipal turn into a distinct user), so a consent's rows are isolated by
   * construction and there is nothing for a consumer to disambiguate. UK used to be the exception --
   * it granted to the real PSU and passed the consent's own consumerId -- but it moved to the shadow
   * user like the others, which left the parameter with no caller and the scaladoc describing an
   * arrangement that no longer existed.
   *
   * ==Do not reintroduce consumer scoping here without changing revokeConsentAccountAccess==
   *
   * That is why the parameter is gone rather than merely unused. AccountAccess.consumer_id holds the
   * literal string ALL_CONSUMERS, not a wildcard, and every lookup matches it by equality
   * (MapperViews.accessGrantedToUserForConsumer, User.hasAccountAccess). revokeConsentAccountAccess
   * sweeps a revoked consent's rows by asking for exactly ALL_CONSUMERS, so a row written under a
   * real consumer id would have been invisible to it and would have outlived the consent that
   * created it -- access that no longer has a consent behind it, left in the table with nothing to
   * remove it. Keeping the branch as dead code kept that trap armed for whoever used it next.
   */
  private def grantAccessToViews(user: User, consent: ConsentJWT): Box[User] = {
    val consumerId = Constant.ALL_CONSUMERS
    val wanted: List[BankIdAccountIdViewId] = consent.views.map { view =>
      BankIdAccountIdViewId(BankId(view.bank_id), AccountId(view.account_id), ViewId(view.view_id))
    }.distinct

    // Reconcile rather than revoke-then-regrant. This runs on every request that presents the
    // consent, so deleting a row and putting it back left a window in which the row did not exist:
    // a second concurrent request for the same consent would delete the row the first had just
    // re-granted, and the first would then fail its own access check with a 403 it could do nothing
    // about. Two requests could also insert the same row at once and collide on the unique index.
    // Touching only the difference means a steady-state request writes nothing at all.
    val held: List[BankIdAccountIdViewId] = Views.views.vend.accessGrantedToUserForConsumer(user, consumerId)
    val wantedSet = wanted.toSet
    // A failed revoke leaves the consent holding a view it no longer declares, which is the one
    // direction of this reconciliation that matters: a grant that fails denies access the consent
    // asked for, and the caller sees that immediately, but a revoke that fails keeps access the
    // consent gave up, and nothing downstream can tell. The result used to be discarded outright,
    // so the only symptom was data the consent no longer covered still being served.
    //
    // Logged rather than returned. Failing the request would make a consent stuck in this state
    // unusable altogether -- its remaining, legitimate views included -- and the operator would have
    // no way back short of deleting rows by hand. The narrower reading is that the consent still
    // holds everything it declares, plus one view it should have lost; refusing everything is a
    // worse answer to that than serving it and saying so loudly.
    //
    // canRevokeOwnerAccess is the realistic trigger (MapperViews): it refuses to drop an `owner`
    // row when no other principal holds one on that account, which an OBP-native consent can reach
    // because createConsentJWT takes its views from whatever the PSU already holds, `owner` included.
    for {
      staleAccess <- held.filterNot(wantedSet.contains)
    } yield {
      Views.views.vend.revokeAccess(staleAccess, user) match {
        case Full(true) => // gone
        case other =>
          logger.warn(
            s"grantAccessToViews: could not revoke ${staleAccess.viewId.value} on " +
            s"${staleAccess.bankId.value}/${staleAccess.accountId.value} from user ${user.userId}, " +
            s"which consent ${consent.jti} no longer declares. The access is still held: $other")
      }
    }

    val heldSet = held.toSet
    val result: List[Box[View]] = {
      for {
        // A view that is already granted and still wanted needs nothing done to it, and nothing
        // looked up either: on a consent that has been used before -- the common case, since this
        // runs on every request -- this loop does no work at all.
        view <- consent.views.filterNot { view =>
          heldSet.contains(BankIdAccountIdViewId(BankId(view.bank_id), AccountId(view.account_id), ViewId(view.view_id)))
        }
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
          logger.debug(s"applyConsentRulesCommonOldStyle.getSignedPayloadAsJson.Start of com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]: $jsonAsString")
          val consent = com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]
          logger.debug(s"applyConsentRulesCommonOldStyle.getSignedPayloadAsJson.End of com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]: $consent")
          checkConsent(consent, consentIdAsJwt, calContext) match { // Check is it Consent-JWT expired
            case (Full(true)) => // OK
              // A Consent-JWT / Consent-Id is an OBP-native gate: reject a consent created by
              // another standard. A JWT with no backing MappedConsent row is grandfathered.
              Consents.consentProvider.vend.getConsentByConsentId(consent.jti) match {
                case Full(mc) => assertConsentStandard(mc, ConsentStandardOBP) match {
                  case Some(failure) => failure
                  case None => applyConsentRules(consent)
                }
                case _ => applyConsentRules(consent)
              }
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
      val ccWithOnBehalf = if (consent.createdByUserId.nonEmpty) {
        val onBehalfOfUser = Users.users.vend.getUserByUserId(consent.createdByUserId)
        temp.copy(onBehalfOfUser = onBehalfOfUser.toOption)
      } else {
        temp
      }
      // Stamp the consent_reference_id on the CallContext so the metric writer can record it.
      val cc = Consents.consentProvider.vend.getConsentByConsentId(consent.jti) match {
        case Full(mc) => ccWithOnBehalf.copy(consentReferenceId = Some(mc.consentReferenceId))
        case _        => ccWithOnBehalf
      }
      if (cc.onBehalfOfUser.nonEmpty &&
        APIUtil.getPropsAsBoolValue(nameOfProperty = "experimental_become_user_that_created_consent", defaultValue = false)) {
        logger.warn("WARNING: experimental_become_user_that_created_consent is DEPRECATED and will be removed soon. Please unset this property.")
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
          logger.debug(s"applyConsentRulesCommon.Start of com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]: $jsonAsString")
          val consent = com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]
          logger.debug(s"applyConsentRulesCommon.End of com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]: $consent")
          checkConsent(consent, consentAsJwt, callContext) match { // Check is it Consent-JWT expired
            case (Full(true)) => // OK
              // A Consent-JWT / Consent-Id is an OBP-native gate: reject a consent created by
              // another standard. A JWT with no backing MappedConsent row is grandfathered.
              Consents.consentProvider.vend.getConsentByConsentId(consent.jti) match {
                case Full(mc) => assertConsentStandard(mc, ConsentStandardOBP) match {
                  case Some(failure) => Future(failure, Some(callContext))
                  case None => applyConsentRules(consent)
                }
                case _ => applyConsentRules(consent)
              }
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

  /**
   * Resolve the stored consent behind either spelling of the OBP consent header: a bare consent id
   * in Consent-Id, or the consent JWT itself in Consent-JWT (whose jti is the consent id).
   *
   * Returns the row whatever standard created it -- the caller decides which gate to apply. Nothing
   * here is authentication: the value is unverified caller input until one of the applyXxxRules
   * functions has checked it.
   */
  def getConsentByHeaderValue(headerValue: String): Option[MappedConsent] = {
    getConsentJwtValueByConsentId(headerValue) // Consent-Id: a bare consent id
      .orElse { // Consent-JWT: the consent JWT itself
        JwtUtil.getOptionalClaim("jti", headerValue)
          .flatMap(jti => Consents.consentProvider.vend.getConsentByConsentId(jti).toOption)
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
      // Stamp the consent_reference_id on the CallContext so the metric writer can record it.
      val cc = Consents.consentProvider.vend.getConsentByConsentId(consent.jti) match {
        case Full(mc) => callContext.copy(consentReferenceId = Some(mc.consentReferenceId))
        case _        => callContext
      }
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

    /**
     * Whether this pass through the authentication pipeline is the one that will actually serve the
     * request, and so the one that should spend a frequencyPerDay access.
     *
     * The pipeline runs many times per HTTP request: each API version wraps its own routes in its
     * own ResourceDocMiddleware, and a middleware whose index holds no matching doc still runs
     * best-effort authentication before falling through to the next one
     * (ResourceDocMiddleware.scala, the `case None` branch). Only the middleware that matched a
     * ResourceDoc attaches it to the CallContext, so its presence is exactly the distinction.
     *
     * Without this, one request spent an access per middleware in the chain, which drove the
     * counter to frequencyPerDay before the request was served: a consent asking for four accesses
     * a day got none, answering 429 to its very first call.
     */
    def isTheServingPass: Boolean = callContext.resourceDocument.isDefined

    def checkFrequencyPerDay(storedConsent: consent.ConsentTrait) = {
      if(isTheServingPass && BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(callContext.requestHeaders)) {
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
      // A consent may only be exercised by its own standard: reject a non-BG consent here.
      case Full(storedConsent) if assertConsentStandard(storedConsent, ConsentStandardBG).isDefined =>
        Future(assertConsentStandard(storedConsent, ConsentStandardBG).get, Some(callContext))
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
                logger.debug(s"applyBerlinGroupConsentRulesCommon.Start of com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]: $jsonAsString")
                val consent = com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]
                logger.debug(s"applyBerlinGroupConsentRulesCommon.End of com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT]: $consent")
                val consentBox = checkConsent(consent, storedConsent.jsonWebToken, updatedCallContext)
                logger.debug(s"End of com.openbankproject.commons.util.JsonAliases.parse(jsonAsString).extract[ConsentJWT].checkConsent.consentBox: $consent")
                consentBox match { // Check is it Consent-JWT expired
                  case (Full(true)) => // OK
                    if(isTheServingPass && BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(callContext.requestHeaders)) {
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
  /**
   * Authenticate a request that presents a UK Open Banking consent as its sole credential, in a
   * Consent-Id / Consent-JWT request header, with no Authorization header at all.
   *
   * UK Open Banking itself binds a consent to an OAuth2 access token (the consent_id claim that
   * checkUKConsent reads). This is an OBP extension alongside that: it lets a client exercise a UK
   * consent the way Berlin Group and OBP-native clients already exercise theirs. The token path is
   * unaffected.
   *
   * Every gate checkUKConsent applies is applied here too -- standard, AUTHORISED status, not-before
   * / expiry, and the consumer match -- so the header path is never the weaker of the two. The
   * user-binding check that checkUKConsent performs (consent.mUserId == the token's user) has no
   * analogue here and needs none: there is no independently-authenticated user to disagree with,
   * because the PSU *is* resolved from the consent's own mUserId.
   *
   * Signature verification is stricter than the token path needs. On the token path OAuth2Login has
   * already verified the access token's signature before checkUKConsent runs; here the caller may
   * hand us the consent JWT itself, so it is verified against the consent's stored secret.
   *
   * The principal is the consent's own shadow user (see resolveUKConsentShadowUser); the PSU the
   * consent belongs to is carried alongside it on the CallContext as `consenter`.
   */
  def applyUKRules(storedConsent: MappedConsent,
                   consentHeaderValue: String,
                   callContext: CallContext): Future[(Box[User], Option[CallContext])] = Future {
    val allowed = APIUtil.getPropsAsBoolValue(nameOfProperty = "consents.allowed", defaultValue = false)
    if (!allowed) {
      (Failure(ErrorMessages.ConsentDisabled), Some(callContext))
    } else {
      val result: Box[(User, CallContext)] = for {
        // A consent may only be exercised by its own standard.
        _ <- assertConsentStandard(storedConsent, ConsentStandardUK) match {
          case Some(failure) => failure
          case None => Full(true)
        }
        _ <- if (storedConsent.status.toUpperCase == ConsentStatus.AUTHORISED.toString) Full(true)
             else Failure(s"${ErrorMessages.ConsentStatusIssue}${ConsentStatus.AUTHORISED.toString}.")
        currentTimeMillis = System.currentTimeMillis
        _ <- if (currentTimeMillis >= storedConsent.creationDateTime.getTime) Full(true)
             else Failure(ErrorMessages.ConsentNotBeforeIssue)
        // A null expirationDateTime means the consent never expires (0..1 per spec, open-ended if
        // absent -- see createUKConsentJWT).
        _ <- if (!Option(storedConsent.expirationDateTime).exists(_.getTime < currentTimeMillis)) Full(true)
             else Failure(ErrorMessages.ConsentExpiredIssue)
        // Only meaningful when the caller supplied the JWT itself; a bare consent id carries
        // nothing to forge, since the JWT is then read straight from our own row.
        _ <- if (!JwtUtil.checkIfStringIsJWTValue(consentHeaderValue).isDefined) Full(true)
             else if (verifyHmacSignedJwt(consentHeaderValue, storedConsent)) Full(true)
             else Failure(ErrorMessages.ConsentVerificationIssue)
        consentJwt <- {
          implicit val dateFormats = CustomJsonFormats.formats
          JwtUtil.getSignedPayloadAsJson(storedConsent.jsonWebToken).map(parse(_).extract[ConsentJWT])
        } ?~! ErrorMessages.ConsentNotFound
        _ <- checkConsumerIsActiveAndMatchedUK(
          consentJwt,
          callContext.consumer.map(_.consumerId.get)
        )
        // The PSU bound to the consent by updateConsentUser during the authorise ceremony. A
        // consent that was never authorised has no user, and the status gate above already
        // rejected it -- this is the belt to that braces.
        psu <- Users.users.vend.getUserByUserId(storedConsent.userId) ?~! ErrorMessages.ConsentNotFound
        principal <- resolveUKConsentPrincipal(storedConsent, consentJwt, psu)
      } yield {
        (principal, callContext.copy(
          // The PSU stays reachable for everything that needs a human: the CBS adapter, metric
          // attribution, and CallContext.effectiveHumanUserId.
          consenter = Full(psu),
          ukConsentId = Some(storedConsent.consentId),
          consentReferenceId = Some(storedConsent.consentReferenceId)
        ))
      }

      result match {
        case Full((user, updatedCallContext)) => (Full(user), Some(updatedCallContext))
        case failure@Failure(_, _, _) => (failure, Some(callContext))
        case _ => (Failure(ErrorMessages.ConsentNotFound), Some(callContext))
      }
    }
  }

  /**
   * Drop the account access a consent's shadow user holds.
   *
   * Revoking or expiring a consent only ever flipped a status column, which was enough while the
   * status gate was the only thing standing between the caller and the data. It leaves the granted
   * AccountAccess rows in the table forever, so anything that reads them without going through the
   * consent gate -- the account-permissions listing, a future endpoint, an operator looking at the
   * database -- still sees a revoked consent's access as live. A shadow user's rows belong to
   * exactly one consent, so for the first time they can be cleaned up without guessing.
   *
   * Safe for a consent whose shadow user was never minted (one that predates account binding and
   * still runs as the PSU): the lookup simply finds nothing. It must never fall back to the PSU --
   * that would delete access the PSU holds in their own right.
   *
   * The ALL_CONSUMERS below is an exact match on the column, not a wildcard, so this is complete
   * only because grantAccessToViews writes nothing else. The two have to stay in step: see the
   * warning on grantAccessToViews before giving a consent's rows a real consumer id.
   */
  def revokeConsentAccountAccess(consent: code.consent.ConsentTrait): Unit = {
    implicit val dateFormats = CustomJsonFormats.formats
    val consentJwtBox = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken).map(parse(_).extract[ConsentJWT])

    val revoked = for {
      consentJwt <- consentJwtBox
      shadowUser <- Users.users.vend.getUserByProviderId(provider = consentJwt.iss, idGivenByProvider = consentJwt.sub)
    } yield {
      val (dropped, stuck) = Views.views.vend.accessGrantedToUserForConsumer(shadowUser, Constant.ALL_CONSUMERS)
        .map(access => access -> Views.views.vend.revokeAccessToViewForUserAndConsumer(access, shadowUser, Constant.ALL_CONSUMERS))
        .partition(_._2 == Full(true))
      (dropped.size, stuck)
    }
    revoked match {
      case Full((count, stuck)) =>
        if (count > 0) {
          logger.info(s"revokeConsentAccountAccess: dropped $count account access rows for consent ${consent.consentId}")
        }
        // revokeAccessToViewForUserAndConsumer can refuse -- canRevokeOwnerAccess will not drop the
        // last `owner` row on an account -- so counting the attempts rather than the successes
        // reported rows as gone while they were still there. On a revoked consent that is the worst
        // version of this to get wrong: the access outlives the consent entirely, and nothing else
        // ever comes back for it.
        for ((access, outcome) <- stuck) {
          logger.warn(
            s"revokeConsentAccountAccess: could not revoke ${access.viewId.value} on " +
            s"${access.bankId.value}/${access.accountId.value} for revoked consent " +
            s"${consent.consentId}. The access outlives the consent: $outcome")
        }
      case _ =>
    }

    // Deliberately outside the comprehension above, and after it. Outside, because a VRP consent
    // that never reached SCA has no shadow user, and its mandate still has to be released -- an
    // abandoned mandate is exactly the case that used to accumulate. After, because the view can
    // only be removed once every access row pointing at it is gone, the shadow user's included.
    consentJwtBox.foreach(releaseVrpMandateArtefacts(consent, _))
  }

  /**
   * Release the artefacts a VRP mandate created, when the consent that owns them is revoked.
   *
   * Converting a VRP consent-request builds a private custom view named `_vrp-<uuid>`, grants it to
   * the PSU, hangs a counterparty off it and gives that counterparty a limit. Together they are the
   * mandate: the view carries CAN_ADD_TRANSACTION_REQUEST_TO_BENEFICIARY, and the limit is how much
   * may be paid under it. Revoking the consent used to drop only the shadow user's access, so the
   * PSU kept a live standing payment authority for a mandate they had just cancelled -- and one set
   * of these accumulated on the account for every mandate ever requested, revoked or abandoned.
   *
   * Each artefact is named after the view, and the view belongs to exactly one consent, so this can
   * be undone without guessing. What gets released, and what deliberately does not:
   *
   *  - the PSU's grant on the view, which is the authority itself;
   *  - the counterparty's limit, which is the amount that authority was good for;
   *  - the view, but only once no access row is left pointing at it -- removeCustomView refuses
   *    otherwise, so a view some other principal still holds is left alone rather than orphaning it.
   *
   * The counterparty row stays. It is a payee record that settled transactions refer to, and
   * deleting it would take history with it; with the view and the limit gone it grants nothing.
   */
  private def releaseVrpMandateArtefacts(consent: code.consent.ConsentTrait, consentJwt: ConsentJWT): Unit = {
    val vrpViews = consentJwt.views.filter(_.view_id.startsWith(Constant.VRP_VIEW_ID_PREFIX))
    if (vrpViews.nonEmpty) {
      Users.users.vend.getUserByUserId(consent.userId) match {
        case Full(psu) =>
          vrpViews.foreach { consentView =>
            val bankId = BankId(consentView.bank_id)
            val accountId = AccountId(consentView.account_id)
            val viewId = ViewId(consentView.view_id)

            Views.views.vend.revokeAccessToViewForUserAndConsumer(
              BankIdAccountIdViewId(bankId, accountId, viewId), psu, Constant.ALL_CONSUMERS)

            // A Failure here is not "this view has no counterparties" -- treating it as such would
            // skip the deletions and still report success below.
            Counterparties.counterparties.vend.getCounterparties(bankId, accountId, viewId) match {
              case Full(counterparties) =>
                counterparties.foreach { counterparty =>
                  // Awaited so the deletion's outcome is observed rather than left in an unwatched
                  // Future -- but caught, because the caller has already committed the revoke. The
                  // status flip and the shadow user's access are gone by the time this runs, and a
                  // second attempt is refused with ConsentAlreadyRevoked, so letting a timeout or a
                  // connector failure escape would turn a completed revoke into a 500 and abandon
                  // the views still queued behind this one.
                  Try(Await.result(
                    CounterpartyLimitProvider.counterpartyLimit.vend.deleteCounterpartyLimit(
                      bankId.value, accountId.value, viewId.value, counterparty.counterpartyId),
                    10.seconds)) match {
                    case Success(deleted) if deleted.isDefined => // released
                    case other => logger.warn(
                      s"releaseVrpMandateArtefacts: could not delete the limit on ${viewId.value} " +
                      s"for counterparty ${counterparty.counterpartyId}: $other")
                  }
                }
              case other =>
                logger.warn(s"releaseVrpMandateArtefacts: could not list counterparties on " +
                  s"${viewId.value} for consent ${consent.consentId}, limits left in place: $other")
            }

            Views.views.vend.removeCustomView(viewId, BankIdAccountId(bankId, accountId)) match {
              case Full(_) =>
                logger.info(s"releaseVrpMandateArtefacts: released ${viewId.value} for consent ${consent.consentId}")
              case other =>
                // Something still holds the view. Its authority is gone either way; say so and stop.
                logger.info(s"releaseVrpMandateArtefacts: kept ${viewId.value} for consent ${consent.consentId}: $other")
            }
          }
        case _ =>
          logger.warn(s"releaseVrpMandateArtefacts: no PSU on consent ${consent.consentId}, mandate views left in place")
      }
    }
  }


  /**
   * The Bearer-token half of the shadow-user resolution.
   *
   * A UK consent normally travels as a `consent_id` claim inside an OAuth2 access token. On that
   * path the request is authenticated as the PSU long before any UK code runs, and checkUKConsent --
   * which validates the consent -- executes inside the endpoint, by which point the principal is
   * already fixed. So the swap has to happen here, at the end of authentication, where the
   * CallContext the endpoint will see is still being assembled.
   *
   * There are three outcomes, and the third is the one that matters.
   *
   *  1. The request has nothing to do with a UK consent -- no token, no `consent_id` claim, no such
   *     consent, another standard. Left exactly as it was.
   *  2. The consent resolves. The principal narrows to its shadow user and the PSU moves to
   *     `consenter`.
   *  3. The consent is named but cannot be resolved. The principal CANNOT stay the PSU: the PSU's
   *     own AccountAccess rows govern, so the caller would see everything the PSU can see and the
   *     consent's Permissions would constrain none of it -- the widest possible reading of a consent
   *     we just failed to understand. `ukConsentUnresolved` records why, and checkUKConsent refuses
   *     on it.
   *
   * Case 3 used to be folded into case 1 by a `.toOption.getOrElse`, which is what made the token
   * path fail open while the Consent-Id header path (applyUKRules) refused the same consent.
   *
   * The refusal is deferred to checkUKConsent rather than raised here because this runs for every
   * request while only the data-read endpoints run on the principal -- see CallContext.ukConsentUnresolved.
   * checkUKConsent still applies everything it did before as well: wrong standard, revoked, expired,
   * bound to a different PSU, held by a different consumer.
   */
  def applyUKConsentPrincipalFromToken(user: Box[User],
                                       callContext: Option[CallContext]): (Box[User], Option[CallContext]) = {
    // Whether this request is exercising a UK consent at all. None at any step means it is not --
    // no CallContext, the header path already resolved one, no Bearer token, no consent_id claim, no
    // such consent row, or a consent belonging to another standard. All of those leave the request
    // exactly as it was, which is the behaviour every non-UK caller depends on.
    def namedConsent: Option[(CallContext, User, MappedConsent)] = for {
      cc <- callContext
      // The Consent-Id / Consent-JWT header path resolved the principal already, in applyUKRules.
      if cc.ukConsentId.isEmpty
      psu <- user.toOption
      accessToken <- cc.authReqHeaderField.toOption.map(_.replaceFirst("Bearer\\s+", "").trim)
      if JwtUtil.checkIfStringIsJWTValue(accessToken).isDefined
      consentId <- JwtUtil.getOptionalClaim("consent_id", accessToken)
      storedConsent <- Consents.consentProvider.vend.getConsentByConsentId(consentId).toOption
      if storedConsent.apiStandard == ConsentStandardUK
    } yield (cc, psu, storedConsent)

    // Past that point the request IS exercising a UK consent, so a failure here can no longer mean
    // "carry on as the PSU": that serves everything the PSU can see, which is the whole of what the
    // consent exists to narrow. ConsentNotFound for an unreadable JWT matches what applyUKRules
    // answers on the header path for the same row.
    def resolve(psu: User, storedConsent: MappedConsent): Box[User] = for {
      consentJwt <- {
        implicit val dateFormats = CustomJsonFormats.formats
        JwtUtil.getSignedPayloadAsJson(storedConsent.jsonWebToken).map(parse(_).extract[ConsentJWT])
      } ?~! ErrorMessages.ConsentNotFound
      principal <- resolveUKConsentPrincipal(storedConsent, consentJwt, psu)
    } yield principal

    namedConsent match {
      case None => (user, callContext)
      case Some((cc, psu, storedConsent)) =>
        // A throw is as unresolved as a Failure, and for the same reason it must not fall through:
        // the fallback is the PSU. Box.map does not catch, so an unextractable ConsentJWT arrives
        // here as a MappingException rather than as a Failure.
        val outcome = Try(resolve(psu, storedConsent))
        outcome match {
          case Success(Full(principal)) =>
            (Full(principal), Some(cc.copy(
              user = Full(principal),
              consenter = Full(psu),
              consentReferenceId = Some(storedConsent.consentReferenceId)
            )))
          case _ =>
            val reason = outcome match {
              case Success(Failure(msg, _, _)) => msg
              case _ => ErrorMessages.ConsentNotFound
            }
            // Warn rather than fail authentication. The refusal lands in checkUKConsent, which only
            // the data-read endpoints call -- the consent-management endpoints stay reachable so the
            // TPP can still inspect and revoke the consent this is about. See CallContext.
            logger.warn(
              s"applyUKConsentPrincipalFromToken: UK consent ${storedConsent.consentId} could not be " +
              s"resolved to its shadow user, so this request is NOT scoped to it. Data access is " +
              s"refused by checkUKConsent. Reason: $reason" +
              outcome.failed.map(e => s" (threw ${e.getClass.getSimpleName}: ${e.getMessage})").getOrElse(""))
            // consentReferenceId as well as the refusal: a refused call must still be attributable
            // to the consent that caused it from the metrics table, which is where consent traffic
            // is searchable. Without it the 403s are visible only in the application log.
            (user, Some(cc.copy(
              ukConsentUnresolved = Some(reason),
              consentReferenceId = Some(storedConsent.consentReferenceId)
            )))
        }
    }
  }

  /**
   * The user a UK consent's data access runs as: the consent's own shadow user.
   *
   * Every consent JWT carries a random UUID in `sub` (createUKConsentJWT, same as Berlin Group and
   * OBP-native). Berlin Group and OBP-native resolve that UUID to a user of its own and grant it the
   * consent's views, so one consent's AccountAccess rows can never be another's. UK used to skip
   * that and grant to the real PSU instead, which is why two consents held by the same TPP for the
   * same PSU wrote to one set of rows and rewrote each other -- and why a consent could read
   * accounts it never named. Resolving to the shadow user makes the isolation structural: the rows
   * belong to an identity that exists only for this consent.
   *
   * It also closes the bypasses for free. The shadow user holds no entitlements (a UK consent JWT
   * carries `entitlements = Nil`), so account firehose and the ABAC fallback -- both of which are
   * consulted before the AccountAccess check in APIUtil.hasAccountAccess -- can never answer for it.
   * And it holds no `owner` view, so the account-listing query (a bare WHERE user_id = ?) returns
   * exactly the consent's accounts without the query having to learn about consents at all.
   *
   * The views are re-granted from the JWT on every request, as Berlin Group does: the JWT is the
   * source of truth, and the rows are only its materialisation. Narrowing a consent therefore takes
   * effect immediately, with no sweep of anything.
   *
   * ==A consent that names no account grants no access==
   *
   * A consent authorised before grantUKConsentAccountAccess existed still carries the
   * `(null, null, permission)` placeholder views createUKConsentJWT writes at creation time. Nothing
   * created today can look like that: the authorise endpoint rejects an empty account_ids with 400,
   * so every consent it accepts names real accounts. These are old rows and nothing else.
   *
   * Such a consent used to fall back to running as the PSU. That is the widest possible reading of a
   * consent that selected nothing -- the PSU's own AccountAccess rows govern, so the TPP saw
   * everything the PSU can see, and the consent's declared Permissions constrained none of it. The
   * fallback was chosen to avoid breaking rows that predated the feature, but "we cannot tell which
   * accounts this consent covers" is a reason to serve nothing, not a reason to serve everything.
   *
   * So it is refused. Re-authorising binds the consent to accounts and it works again; the error
   * says so. `uk_consent_allow_unbound_legacy` restores the old behaviour for an operator who needs
   * a migration window and accepts what it means -- default false, and it warns on every use.
   */
  private def resolveUKConsentPrincipal(storedConsent: MappedConsent, consentJwt: ConsentJWT, psu: User): Box[User] = {
    val namesRealAccounts = consentJwt.views.exists { view =>
      Option(view.bank_id).exists(_.trim.nonEmpty) && Option(view.account_id).exists(_.trim.nonEmpty)
    }
    if (!namesRealAccounts) {
      if (APIUtil.getPropsAsBoolValue(nameOfProperty = "uk_consent_allow_unbound_legacy", defaultValue = false)) {
        logger.warn(
          s"UK consent ${storedConsent.consentId} names no real account in its JWT views -- it predates " +
          s"account binding. uk_consent_allow_unbound_legacy is set, so it runs as the PSU: this consent's " +
          s"declared Permissions place no limit on what it can read. Re-authorise it to bind it to accounts, " +
          s"then unset the property.")
        Full(psu)
      } else {
        logger.warn(
          s"UK consent ${storedConsent.consentId} names no real account in its JWT views -- it predates " +
          s"account binding, so there is nothing it can be scoped to and it is refused. Re-authorise it to " +
          s"bind it to accounts. Set uk_consent_allow_unbound_legacy=true to serve it as the PSU meanwhile, " +
          s"which places no limit on what it can read.")
        Failure(ErrorMessages.ConsentNamesNoAccount)
      }
    } else {
      for {
        shadowUser <- getOrCreateUKConsentShadowUser(storedConsent, consentJwt)
        _ <- grantAccessToViews(shadowUser, consentJwt)
      } yield shadowUser
    }
  }

  /**
   * Get (or first mint) the shadow user for a UK consent, copying the consent's snapshot of the
   * PSU's auth context onto it the first time -- the connectors read those key/value pairs to
   * identify the customer, so a shadow user without them would be a different caller to the CBS.
   */
  private def getOrCreateUKConsentShadowUser(storedConsent: MappedConsent, consentJwt: ConsentJWT): Box[User] = {
    // Reuses the shared get-or-create rather than doing its own find-then-insert: this runs on
    // every request, so the very first two requests for a new consent race, and only the shared one
    // recovers from the resulting unique-index violation by re-reading.
    val (user, isNew) = Users.users.vend.getOrCreateUserByProviderId(
      provider = consentJwt.iss,
      idGivenByProvider = consentJwt.sub,
      consentId = Some(storedConsent.consentId),
      name = None,
      email = None)
    for {
      shadowUser <- user ?~! ErrorMessages.CannotGetOrCreateUser
      _ = if (isNew) copyAuthContextOfConsentToUser(storedConsent.consentId, shadowUser.userId, newUser = true)
    } yield shadowUser
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
                       helperInfo: Option[HelperInfoJson], //this is only used for VRP consent, all the others are NONE.
                       preComputedViews: Option[List[ConsentView]] = None // bypass Doobie view lookup (e.g. for VRP consent where the view was just created in the same transaction)
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
    val viewsToAdd: Seq[ConsentView] = preComputedViews.getOrElse {
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
      .map(com.openbankproject.commons.util.JsonAliases.parse(_).extract[ConsentJWT]) // Extract case class


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
      .map(com.openbankproject.commons.util.JsonAliases.parse(_).extract[ConsentJWT]) // Extract case class

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
      .map(com.openbankproject.commons.util.JsonAliases.parse(_).extract[ConsentJWT]) // Extract case class

    val updatedPayload = payloadToUpdate.map(i => i.copy(createdByUserId = createdByUserId)) // Update only the field "createdByUserId"
    val jwtPayloadAsJson = compactRender(Extraction.decompose(updatedPayload))
    val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
    Full(CertificateUtil.jwtWithHmacProtection(jwtClaims, consent.secret))
  }
  
  /**
   * The permission codes UK Open Banking defines for an account-access-consent.
   *
   * Taken from the Account and Transaction API profile's permission table. Codes outside this set
   * are not UK permission codes at all, which is a different thing from a code this ASPSP happens
   * not to implement -- see validateUKConsentPermissions.
   */
  private val ukPermissionCodes: Set[String] = Set(
    "ReadAccountsBasic", "ReadAccountsDetail",
    "ReadBalances",
    "ReadBeneficiariesBasic", "ReadBeneficiariesDetail",
    "ReadDirectDebits",
    "ReadOffers",
    "ReadPAN",
    "ReadParty", "ReadPartyPSU",
    "ReadProducts",
    "ReadScheduledPaymentsBasic", "ReadScheduledPaymentsDetail",
    "ReadStandingOrdersBasic", "ReadStandingOrdersDetail",
    "ReadStatementsBasic", "ReadStatementsDetail",
    "ReadTransactionsBasic", "ReadTransactionsCredits",
    "ReadTransactionsDebits", "ReadTransactionsDetail"
  )

  /**
   * Check a UK account-access-consent's Permissions array against the combinations the standard
   * forbids, returning the reason it must be refused or None when it is well formed.
   *
   * The Account and Transaction API profile requires the ASPSP to reject these with 400, and the
   * rules are not arbitrary: every AIS endpoint other than /accounts is /accounts/{AccountId}/...,
   * so a consent without an account-read permission can never discover the ids it would need and
   * is a dead end -- it authorises data the AISP has no way to reach. Accepting one produces a
   * consent that looks authorised and returns an empty account list forever.
   *
   * Deliberately not enforced: "a permission code that is not supported by the ASPSP". That rule
   * is about the endpoint subset an ASPSP publishes, and OBP publishes no such list, so rejecting
   * on it would be guesswork. A code that is not a UK permission code at all is still refused.
   *
   * Requesting both a Basic and its corresponding Detail code is explicitly allowed: the profile
   * calls it duplication but forbids rejecting on that basis alone.
   */
  def validateUKConsentPermissions(permissions: List[String]): Option[String] = {
    val granted = permissions.toSet
    val transactionDepth = Set("ReadTransactionsBasic", "ReadTransactionsDetail")
    val transactionDirection = Set("ReadTransactionsCredits", "ReadTransactionsDebits")

    if (permissions.isEmpty) {
      Some("The Permissions array must not be empty.")
    } else {
      val unknown = granted.diff(ukPermissionCodes)
      if (unknown.nonEmpty) {
        Some(s"Unknown permission code(s): ${unknown.toList.sorted.mkString(", ")}.")
      } else if (granted.intersect(Set("ReadAccountsBasic", "ReadAccountsDetail")).isEmpty) {
        Some("The Permissions array must contain at least one of ReadAccountsBasic and ReadAccountsDetail.")
      } else if (granted.intersect(transactionDepth).nonEmpty &&
                 granted.intersect(transactionDirection).isEmpty) {
        Some("A Permissions array containing ReadTransactionsBasic or ReadTransactionsDetail must also " +
          "contain at least one of ReadTransactionsCredits and ReadTransactionsDebits.")
      } else if (granted.intersect(transactionDirection).nonEmpty &&
                 granted.intersect(transactionDepth).isEmpty) {
        Some("A Permissions array containing ReadTransactionsCredits or ReadTransactionsDebits must also " +
          "contain at least one of ReadTransactionsBasic and ReadTransactionsDetail.")
      } else {
        None
      }
    }
  }

  /**
   * Decide whether a caller may read or revoke a UK account-access-consent, returning the reason to
   * refuse with 403 or None when it is allowed.
   *
   * The standard names one caller, and it is not the PSU. In the Endpoints table of
   * account-access-consents, in both v3.1 and v4.0.1, GET and DELETE carry Grant Type "Client
   * Credentials", and the prose repeats it: "Prior to calling the API, the AISP must have an access
   * token issued by the ASPSP using a client credentials grant." GET retrieves a consent "that they
   * have created"; DELETE is what the AISP does after the PSU has revoked consent with the AISP.
   * So for a standard caller there is no PSU in the session at all, and the Consumer the consent was
   * lodged under is the whole of the authorisation: that AISP may read and revoke it whatever its
   * status, and no other Consumer may.
   *
   * The user check is kept for a caller that does present a PSU. OBP allows credentials the standard
   * does not describe here, and for those the stricter rule applies: a session acting as one PSU
   * cannot reach another PSU's consent, and the lodging TPP cannot use a PSU session to do it
   * either. That path is a superset of the standard, never a way around it -- the PSU comparison
   * only ever narrows, and a caller with no PSU never reaches it.
   *
   * Blank ids are treated as absent: a consent that was never authorised stores no user id, and one
   * lodged before consumer binding existed stores no consumer id.
   *
   * Kept here rather than inline so the four endpoints that need it (v3.1 and v4.0.1, GET and
   * DELETE) share one definition, and so the rule can be tested without standing up a request --
   * same reasoning as validateUKConsentPermissions above.
   */
  def checkUKConsentAccess(
    consentUserId: String,
    consentConsumerId: String,
    callerUserId: Option[String],
    callerConsumerId: Option[String],
    callerIsScaFrontEnd: Boolean
  ): Option[String] = {
    val stillUnclaimed = Option(consentUserId).map(_.trim).forall(_.isEmpty)
    // The ASPSP's own approval screen has to read the consent to show the PSU what they are being
    // asked to grant, and it arrives under its own Consumer rather than the TPP's -- so the lodging
    // Consumer comparison refuses precisely the caller whose whole job is to inform the PSU, and the
    // screen renders with no permissions, no status and no expiry.
    //
    // Narrow on purpose. It applies only while the consent is still unclaimed, which is the only
    // window the approval screen exists for; once a PSU is bound, the PSU half below governs and a
    // declared front end gets no further than anyone else. And it is inert unless an ASPSP has
    // declared a front end at all.
    if (callerIsScaFrontEnd && stillUnclaimed) None
    else psuOrLodgingTppRefusal(consentUserId, consentConsumerId, callerUserId, callerConsumerId)
  }

  /**
   * Decide whether a caller may drive a Berlin Group consent's authorisation sub-resources --
   * starting one, and answering it, the two steps that bind the consent to a PSU -- returning the
   * reason to refuse with 403 or None when it is allowed.
   *
   * The Consumer half is the standard's own blanket rule, not a UK import. The Implementation
   * Guidelines state it once for the whole API, in section 4.11 "API Access Methods" (p.24): "all
   * methods submitted by a TPP, which are addressing dynamically created resources in this API, may
   * only apply to resources which have been created by the same TPP before." A consent and its
   * authorisation sub-resources are exactly such dynamically created resources, so the Consumer the
   * consent was lodged under is what identifies a legitimate caller. Two endpoints in this family
   * already enforce that inline -- deleteConsent and getConsentInformation -- and only the
   * authorisation pair was left without it.
   *
   * Note who that caller is. In Berlin Group the PSU does not call the API: under the Redirect
   * approach it authenticates at the ASPSP, and under Embedded it hands its authentication factors
   * to the TPP, which relays them. So on this pair the session is the TPP's, and a Consumer check is
   * the substantive one -- which is why callerUserId must be a genuine PSU (see genuinePsu) rather
   * than whatever principal the token resolved to.
   *
   * The PSU half covers re-binding. updateConsentUser overwrites mUserId unconditionally, so without
   * it a second party could take over a consent another PSU has already authorised. The standard
   * leaves PSU identity to the ASPSP to enforce and says so where it defines the PSU-ID header: "the
   * ASPSP might check whether PSU-ID and token match, according to ASPSP documentation". Where the
   * caller does present a genuine PSU and the consent already names one, they must be the same.
   *
   * This lands on the same rule as checkUKConsentAccess, and the agreement is worth stating because
   * the two derivations are not the same. UK's rests on a per-endpoint claim -- its Endpoints table
   * marks these calls Client Credentials, so no PSU is party to them. Berlin Group's rests on the
   * blanket same-TPP rule above plus PSU binding happening at SCA time. Different premises, same
   * conclusion, so they share one implementation rather than one being copied onto the other.
   *
   * With one exception, and it is in the wording of the rule itself: it binds "methods submitted by
   * a TPP". Under the Redirect approach the PSU authenticates at the ASPSP, so these calls arrive
   * from the ASPSP's own front end -- not a TPP, and never the Consumer that lodged the consent.
   * Applying the rule there refuses the only caller Redirect has, and the scaRedirect ceremony
   * cannot complete at all.
   *
   * Nothing in the request tells that front end apart from a second TPP holding a PSU session --
   * both are an authenticated person arriving under a Consumer that did not lodge the consent -- so
   * the ASPSP declares its own, and callerIsScaFrontEnd is that declaration reaching this rule. See
   * APIUtil.scaFrontEndConsumerIds. It is not a way past the PSU half: a consent already
   * bound to someone still only re-binds to them.
   *
   * What it is emphatically not is a substitute for checking that the claiming PSU has anything to
   * do with the accounts the consent names. A Consumer comparison could never have provided that --
   * the TPP that lodged the consent passes it by definition, and is the party the access accrues to
   * -- so that check lives on its own in assertBerlinGroupConsentAccountsHeld, which the
   * authorisation pair applies before anything is written.
   */
  def checkBerlinGroupConsentAccess(
    consentUserId: String,
    consentConsumerId: String,
    callerUserId: Option[String],
    callerConsumerId: Option[String],
    callerIsScaFrontEnd: Boolean
  ): Option[String] = {
    def present(s: String): Option[String] = Option(s).map(_.trim).filter(_.nonEmpty)

    (present(consentUserId), callerUserId.flatMap(present)) match {
      case (Some(psu), Some(caller)) if psu != caller => Some(ErrorMessages.ConsentDoesNotMatchUser)
      case (Some(_), Some(_)) => None
      // Only while the consent is still unclaimed. A caller with no PSU used to reach this too, so a
      // declared front end presenting client credentials could drive the authorisation of a consent
      // already bound to somebody else -- the opposite of what the paragraph above promises.
      case (None, _) if callerIsScaFrontEnd => None
      case _ => psuOrLodgingTppRefusal(consentUserId, consentConsumerId, callerUserId, callerConsumerId)
    }
  }

  /**
   * Whether the Consumer making this call is one the ASPSP declared as its own SCA front end.
   *
   * Not Berlin-Group-specific: the same front end drives the UK approval screen, and the same
   * difficulty applies there -- nothing in the request distinguishes the ASPSP's own screen from a
   * second TPP holding a PSU session, so the ASPSP has to say which Consumer is its own.
   */
  def isScaFrontEnd(callerConsumerId: Option[String]): Boolean =
    callerConsumerId.map(_.trim).filter(_.nonEmpty)
      .exists(APIUtil.scaFrontEndConsumerIds.contains)

  /**
   * The rule shared by checkUKConsentAccess and checkBerlinGroupConsentAccess: a consent bound to a
   * PSU belongs to that PSU, and otherwise belongs to the Consumer that lodged it.
   *
   * Blank ids are treated as absent: a consent that was never authorised stores no user id, and one
   * lodged before consumer binding existed stores no consumer id.
   *
   * Private on purpose. Each standard states its own case in its own scaladoc above; this is only
   * the mechanism they turned out to share, and it carries no argument of its own.
   */
  private def psuOrLodgingTppRefusal(
    consentUserId: String,
    consentConsumerId: String,
    callerUserId: Option[String],
    callerConsumerId: Option[String]
  ): Option[String] = {
    def present(s: String): Option[String] = Option(s).map(_.trim).filter(_.nonEmpty)

    (present(consentUserId), callerUserId.flatMap(present)) match {
      case (Some(psu), Some(caller)) =>
        // The consent belongs to a PSU and the caller is acting as one: they must be the same PSU.
        if (psu == caller) None else Some(ErrorMessages.ConsentDoesNotMatchUser)
      case _ =>
        // Either the consent has no PSU yet, or the caller is not acting as one. Either way the
        // Consumer that lodged it is what identifies a legitimate caller.
        val owner = present(consentConsumerId)
        if (owner.forall(id => callerConsumerId.flatMap(present).contains(id))) None
        else Some(ErrorMessages.ConsentDoesNotMatchConsumer)
    }
  }

  /**
   * Decide whether a caller may read an OBP-native consent through GET /user/current/consents/CONSENT_ID,
   * returning the reason to refuse or None when it is allowed.
   *
   * OBP-native answers to no external standard, so the contract is OBP's own API surface, and that
   * surface is explicit about the subject: this endpoint is /user/current/..., while its sibling
   * /consumer/current/consents/CONSENT_ID is the Consumer-scoped read. Two endpoints, two subjects.
   * So the comparison here is against the human the request is on behalf of -- CallContext.humanUser,
   * not CallContext.userId, which returns the authenticated principal and under consent
   * authentication is the per-consent shadow user rather than the PSU. checkUKConsent already
   * resolves the human this way for the same comparison.
   *
   * A consent with no PSU yet stays readable, and that is deliberate rather than an oversight
   * inherited from the previous guard. This endpoint is where the PSU inspects a consent before
   * deciding to authorise it, in both the Berlin Group and UK journeys, and the app doing the
   * inspecting is the PSU's own -- a different Consumer from the TPP that lodged the consent. Adding
   * the Consumer fallback checkUKConsentAccess uses would therefore break the journey, not tighten
   * it; that is where OBP-native's rule genuinely parts company with the standards', and why this is
   * its own function rather than a second caller of theirs.
   *
   * What that leaves open, stated plainly: an unbound consent's metadata can be read by any
   * authenticated caller who knows its consent id. Claiming one is a separate matter and is gated
   * where the binding happens.
   *
   * Refuses with ConsentNotFound rather than a distinct message, preserving the endpoint's existing
   * 404 so it does not tell a stranger that a consent id exists.
   */
  /**
   * The URL segment shared by the UK account-access-consent endpoints, and by nothing else.
   * Named here rather than repeated at the one place it is matched, so the exemption below and any
   * future reader of those endpoints see the same string.
   */
  val UK_CONSENT_MANAGEMENT_URL_SEGMENT = "account-access-consents"

  /**
   * Whether a request must be refused because the Bearer token named a UK consent that could not be
   * resolved to its shadow user. Returns the reason to refuse with 403, or None when it may proceed.
   *
   * The principal in that state is still the PSU, whose own AccountAccess rows are wider than any
   * consent. checkUKConsent refuses it, but only the UK data-read endpoints call checkUKConsent, so
   * this is what ResourceDocMiddleware applies to every other endpoint family. Without it a consent
   * we failed to understand outranked one we understood: the successful swap narrows the principal
   * to the shadow user everywhere, the failed one left the PSU in place everywhere the UK gate does
   * not run.
   *
   * The account-access-consent endpoints are exempt. They are how a TPP inspects and revokes the
   * very consent this is about, they answer from the consent row rather than from the principal, and
   * they carry their own guard (assertUKConsentAccess). Refusing them would leave a TPP holding a
   * consent it can neither use nor clean up.
   *
   * Extracted from the middleware for the same reason checkUKConsentAccess is extracted from its
   * four endpoints: the rule -- and in particular the exemption, which is the part that can go
   * quietly wrong -- can then be tested without standing up a request. The token path itself cannot
   * be driven over HTTP from the test suite, since authenticating a Bearer token needs a JWKS the
   * suite has no key for; that half is covered by the out-of-repo probe matrix.
   */
  def unresolvedUKConsentRefusal(ukConsentUnresolved: Option[String], requestUrl: String): Option[String] =
    ukConsentUnresolved.filterNot(_ => requestUrl.contains(UK_CONSENT_MANAGEMENT_URL_SEGMENT))

  def checkObpConsentUserAccess(consentUserId: String, callerHumanUserId: Option[String]): Option[String] = {
    def present(s: String): Option[String] = Option(s).map(_.trim).filter(_.nonEmpty)

    present(consentUserId) match {
      case Some(psu) if !callerHumanUserId.flatMap(present).contains(psu) =>
        Some(ErrorMessages.ConsentNotFound)
      case _ => None
    }
  }

  /**
   * The PSU behind a request, where the session really carries one.
   *
   * A pure client-credentials token still resolves to a user: OAuth2.getOrCreateResourceUser maps
   * the JWT sub onto idGivenByProvider, and in that grant the sub is the caller's own client id, so
   * an auto-vivified pseudo-user appears where the code expects a person. Ownership checks must not
   * mistake it for a PSU -- doing so would refuse a legitimate TPP poll by comparing the TPP's own
   * pseudo-identity against the consent's real owner.
   *
   * Berlin Group consent lodging filters the same way inline (see createConsent); that site was left
   * duplicated deliberately while ConsentUtil was being edited on another branch. This is that
   * extraction, used by the checks added here.
   */
  def genuinePsu(callContext: CallContext): Option[User] =
    callContext.user.toOption.filterNot(u => callContext.consumer.map(_.key.get).contains(u.idGivenByProvider))

  /**
   * Refuse a Berlin Group consent authorisation unless the PSU claiming it holds every account the
   * consent names, returning the reason to refuse or None.
   *
   * A Berlin Group consent carries its accounts from the moment it is created: the TPP lists IBANs
   * in the access object and createBerlinGroupConsentJWT resolves each one to a (bank_id,
   * account_id) view before any PSU is involved. Nothing until now checked that the PSU who
   * eventually authorises it has anything to do with those accounts, and the consequence was
   * reachable rather than theoretical -- a consent naming another customer's IBAN, authorised by a
   * PSU who does not hold it, bound and then served that account's details and balances to the TPP.
   * It is the same gap UK closed at its own authorise step, and the same error answers it.
   *
   * Read off the JWT rather than the access object because the JWT is what the read path will
   * actually grant: applyConsentRules materialises exactly these views for the consent's shadow
   * user. Checking anything else would leave the two able to disagree.
   *
   * A consent whose JWT names no account yet is not refused here. That is the availableAccounts
   * ("allAccounts") shape, whose views are materialised later and against the PSU's own holdings,
   * so there is nothing for this check to compare and nothing it could wrongly let through.
   */
  def assertBerlinGroupConsentAccountsHeld(
    psu: User,
    storedConsent: consent.ConsentTrait,
    callContext: Option[CallContext]
  ): Future[Box[Unit]] = Future {
    implicit val dateFormats: Formats = CustomJsonFormats.formats
    val consentAccounts: List[(String, String)] = JwtUtil.getSignedPayloadAsJson(storedConsent.jsonWebToken)
      .map(com.openbankproject.commons.util.JsonAliases.parse(_).extract[ConsentJWT])
      .map(_.views.map(v => (v.bank_id, v.account_id)).distinct)
      .getOrElse(Nil)
      .filter { case (bankId, accountId) => bankId != null && accountId != null }

    val notHeld: List[String] = consentAccounts
      .groupBy(_._1)
      .toList
      .flatMap { case (bankId, pairs) =>
        val held = AccountHolders.accountHolders.vend
          .getAccountsHeld(BankId(bankId), psu)
          .map(_.accountId.value)
        pairs.map(_._2).filterNot(held.contains)
      }

    (notHeld.isEmpty, notHeld): (Boolean, List[String])
  } flatMap { case (allHeld: Boolean, notHeld: List[String]) =>
    Helper.booleanToFuture(
      s"${ErrorMessages.ConsentAccountNotHeldByUser} Account(s): ${notHeld.mkString(", ")}",
      403, callContext)(allHeld)
  }

  /**
   * The PSU a caller is acting as, or None when it is acting only as itself.
   *
   * genuinePsu answers this for every credential except one: a request authenticated by the consent
   * itself. applyUKRules swaps callContext.user to the consent's shadow user and sets aside the real
   * PSU on consenter, and a shadow user's idGivenByProvider is a random UUID rather than the
   * consumer key, so genuinePsu waves it through as if it were a person. An ownership check handed
   * that principal compares the shadow user against the consent's owner and can never match --
   * checkUKConsent already reads consenter for exactly this reason, and says so at its own PSU
   * comparison.
   *
   * So: the PSU the swap set aside if there is one, otherwise whatever genuine PSU the session
   * carries. Both absent is the standard's own AISP call -- a client-credentials token with no PSU
   * anywhere -- and None is the answer that lets checkUKConsentAccess fall through to the Consumer
   * rule, which is the whole of the authorisation there.
   */
  def actingPsu(callContext: CallContext): Option[User] =
    callContext.consenter.toOption.orElse(genuinePsu(callContext))

  /**
   * The whole guard the four UK consent-by-id endpoints apply: resolve who the caller is acting as,
   * put that to checkUKConsentAccess, and refuse with 403 when it says so.
   *
   * The rule and the identity it is asked about belong together. Keeping them apart is what let the
   * four sites settle on callContext.user -- a value that is never absent on a request reaching
   * them, so the rule's own "caller with no PSU" branch was unreachable from every one of them,
   * however well that branch was tested in isolation.
   */
  def assertUKConsentAccess(
    consentUserId: String,
    consentConsumerId: String,
    callContext: CallContext
  ): Future[Box[Unit]] = {
    val refusal = checkUKConsentAccess(
      consentUserId, consentConsumerId,
      actingPsu(callContext).map(_.userId), callContext.consumer.map(_.consumerId.get),
      isScaFrontEnd(callContext.consumer.map(_.consumerId.get)))
    // booleanToFuture only reads failMsg when the statement is false, so the empty default is never
    // the message anyone sees.
    Helper.booleanToFuture(refusal.getOrElse(""), 403, Some(callContext))(refusal.isEmpty)
  }

  /**
   * Resolve the PSU a Berlin Group consent authorisation is for, returning that PSU's user id or the
   * reason to refuse.
   *
   * The session cannot answer this, and the reason is not a formality. Berlin Group has the TPP make
   * these calls, not the PSU: under Redirect the PSU authenticates at the ASPSP, under Embedded it
   * hands its factors to the TPP, which relays them (Implementation Guidelines V1.3.12, section
   * 6.1.1.4, p.123: "the TPP is transmitting the authentication data of the customer, e.g. an OTP").
   * So on an Embedded call the token is the TPP's, and a client-credentials token still resolves to
   * a user -- the caller's own auto-vivified pseudo-identity, see genuinePsu. Minting the challenge
   * against that principal names the TPP, and the challenge answer is delivered to whoever the
   * challenge names: createChallengeInternal sends it to getEmailsByUserId / getPhoneNumbersByUserId
   * of the minted user. The OTP would go to the TPP and never reach the PSU.
   *
   * Where the standard does put the PSU's identity is the PSU-ID header. It is not in the body:
   * psuData carries password, encryptedPassword, additionalPassword and additionalEncryptedPassword,
   * and no identifier at all. On the start-authorisation call PSU-ID "shall be transmitted if this
   * Request is indicated by 'startAuthorisationWithPsuIdentification' or
   * 'startAuthorisationWithPsuAuthentication' ... and this field has not yet been transmitted
   * before" (section 7.1, p.195); on the update call it is "contained if not yet contained in a
   * pre-ceeding request" (section 7.2.1, p.206). Both make it conditional on the ASPSP not already
   * knowing, which is exactly the order used here:
   *
   *  1. the consent's own PSU, once SCA has bound one -- the strongest form of "already transmitted";
   *  2. a genuine PSU in the session, which is the Redirect approach, where the PSU really is the
   *     caller;
   *  3. the PSU-ID header, which is Embedded, the TPP naming the PSU on the PSU's behalf.
   *
   * With none of the three there is no one to mint the challenge for and nowhere to send the OTP, so
   * the call is refused. That is an ordinary outcome rather than an attack being repelled: a
   * conforming client-credentials call that omitted the header lands here.
   *
   * A header that disagrees with 1 or 2 is refused rather than resolved by precedence. The standard
   * sanctions the check where it defines the header -- "the ASPSP might check whether PSU-ID and
   * token match, according to ASPSP documentation" (section 6.3.1, p.134) -- and what it closes is
   * specific: without it the lodging TPP could name a third party on a consent already bound to
   * someone else, and have that person's OTP mailed to them.
   *
   * What this deliberately does not do is verify a first factor. psuData.password is still not
   * checked anywhere in the Berlin Group path, so PSU-ID is an assertion by the TPP and not proof of
   * anything. It is the OTP, delivered out of band to the PSU this resolves to, that actually binds
   * the consent -- which is why resolving it correctly is what makes the unverified assertion safe,
   * and why getting it wrong was the defect rather than a tidiness problem.
   */
  def resolveBerlinGroupPsu(
    consentUserId: String,
    sessionPsuId: Option[String],
    headerPsuId: Option[String]
  ): Either[String, String] = {
    def present(s: String): Option[String] = Option(s).map(_.trim).filter(_.nonEmpty)

    val alreadyKnown = present(consentUserId).orElse(sessionPsuId.flatMap(present))

    (alreadyKnown, headerPsuId.flatMap(present)) match {
      case (Some(known), Some(named)) if known != named => Left(ErrorMessages.ConsentDoesNotMatchUser)
      case (Some(known), _)                             => Right(known)
      case (None, Some(named))                          => Right(named)
      case (None, None)                                 => Left(ErrorMessages.BerlinGroupPsuNotIdentified)
    }
  }

  /**
   * Resolve a Berlin Group PSU-ID header value -- "Client ID of the PSU in the ASPSP client
   * interface" -- to the user it names.
   *
   * Local users first, since that is what the header means at an ASPSP running its own identity
   * store. A federated PSU carries its issuer as provider rather than the local one, so a username
   * that is not local is looked up across providers and accepted only when exactly one user answers
   * to it. Two would make the header ambiguous, and choosing between them is not the ASPSP's to do
   * on the PSU's behalf.
   */
  def findPsuByPsuId(psuId: String): Box[User] =
    Users.users.vend.getUserByProviderAndUsername(Constant.localIdentityProvider, psuId) or {
      Users.users.vend.getUsersByUsername(psuId) match {
        case theOnlyOne :: Nil => Full(theOnlyOne)
        case _                 => Empty
      }
    }

  def createUKConsentJWT(
    user: Option[User],
    bankId: Option[String],
    accountIds: Option[List[String]],
    permissions: List[String],
    expirationDateTime: Option[Date],
    transactionFromDateTime: Option[Date],
    transactionToDateTime: Option[Date],
    secret: String,
    consentId: String,
    consumerId: Option[String]
  ): String = {

    val createdByUserId = user.map(_.userId).getOrElse("None")
    val currentConsumerId = Consumer.findAll(By(Consumer.createdByUserId, createdByUserId)).map(_.consumerId.get).headOption.getOrElse("")
    val currentTimeInSeconds = System.currentTimeMillis / 1000
    // No ExpirationDateTime means the consent never expires (UK spec: 0..1, open-ended if absent).
    // Use Long.MaxValue rather than e.g. "now" (the convention createBerlinGroupConsentJWT falls
    // back to for its own optional validUntil) since that would make the JWT read as already
    // expired -- wrong for "no limit". Nothing currently reads this exp claim for UK consents
    // (checkUKConsent doesn't check expiry at all yet), so this only matters once that's added.
    val validUntilTimeInSeconds = expirationDateTime.map(_.getTime / 1000).getOrElse(Long.MaxValue)
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

  /**
   * Binds a UK Open Banking account-access consent to the PSU-selected accounts.
   *
   * createUKConsentJWT (called from POST /account-access-consents, before any account is known)
   * writes every granted permission as ConsentView(bank_id=null, account_id=null, view_id=permission)
   * — a row that can never match a real account (see User.hasAccountAccess: plain equality on
   * bank_id/account_id, no wildcard). Call this once the PSU has selected which accounts the
   * consent applies to (currently: the UK authorise step, since OBP has no separate
   * ASPSP-hosted account-selection UI) to replace those dead rows with real per-account
   * ConsentViews — mirroring how updateViewsOfBerlinGroupConsentJWT resolves BG's IBAN-keyed access
   * into real accounts.
   *
   * The JWT it writes is the consent's whole scope: resolveUKConsentPrincipal re-derives the
   * consent's AccountAccess from it on every request. So this grants nothing itself, and must keep
   * running as the real PSU -- the account-holdership check below is the security control that stops
   * a consent being bound to somebody else's accounts, and only the PSU holds accounts.
   */
  def grantUKConsentAccountAccess(user: User,
                                   bankId: BankId,
                                   accountIds: List[String],
                                   consent: MappedConsent,
                                   callContext: Option[CallContext]): Future[Box[MappedConsent]] = {
    implicit val dateFormats = CustomJsonFormats.formats
    val payloadToUpdate: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken)
      .map(com.openbankproject.commons.util.JsonAliases.parse(_).extract[ConsentJWT])

    val permissions: List[String] = payloadToUpdate match {
      case Full(consentJwt) => consentJwt.views.map(_.view_id).distinct
      case _ => Nil
    }

    val accountChecks: List[Future[Box[BankAccount]]] = accountIds.distinct.map { accountId =>
      Connector.connector.vend.checkBankAccountExists(bankId, AccountId(accountId), callContext).map(_._1)
    }

    Future.sequence(accountChecks).map { boxes =>
      val error = s"$BankAccountNotFound BankId(${bankId.value})"
      val validatedAccountIds: List[String] = boxes.map(_.openOrThrowException(error)).map(_.accountId.value)

      // Existence alone is not enough: only bind the consent to accounts the authorising PSU
      // actually holds -- otherwise any authenticated user could authorise a consent naming an
      // arbitrary existing account_id and be granted every consented view on it.
      val heldAccountIds: Set[String] = AccountHolders.accountHolders.vend
        .getAccountsHeld(bankId, user)
        .map(_.accountId.value)
      val notHeld: List[String] = validatedAccountIds.filterNot(heldAccountIds.contains)

      val newViews: List[ConsentView] = for {
        accountId <- validatedAccountIds
        permission <- permissions
      } yield ConsentView(bank_id = bankId.value, account_id = accountId, view_id = permission, None)

      if (notHeld.nonEmpty) {
        Failure(s"$ConsentAccountNotHeldByUser Account(s): ${notHeld.mkString(", ")}")
      } else if (newViews.isEmpty) {
        Empty
      } else {
        val updatedPayload = payloadToUpdate.map(_.copy(views = newViews))
        val jwtPayloadAsJson = compactRender(Extraction.decompose(updatedPayload))
        val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)
        val jwt = CertificateUtil.jwtWithHmacProtection(jwtClaims, consent.secret)
        // Writing the JWT is the whole job. Nothing is granted here.
        //
        // This used to eagerly write AccountAccess rows for the PSU, and then sweep away the rows
        // an earlier, wider consent had left behind -- necessary only because those rows were keyed
        // to the PSU and so were shared by every consent that PSU had granted. Data access now runs
        // as the consent's own shadow user (resolveUKConsentPrincipal), which re-derives its views
        // from this JWT on every request, so a consent's rows are its own and narrowing one takes
        // effect the moment this JWT is written. There is nothing left to sweep, and an eager grant
        // to the PSU would only put back the rows that caused the problem.
        Consents.consentProvider.vend.setJsonWebToken(consent.consentId, jwt)
      }
    }
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


  // Canonical apiStandard values stamped on a MappedConsent at creation time. A consent may
  // only be *exercised* (used for data access) through endpoints of the standard that created
  // it — enforced by assertConsentStandard at each exercise gate. Reduction (revoke/expire) and
  // read/audit deliberately stay cross-standard; the OBP-hosted authorise ceremony acts on a
  // consent as its own standard, so it is not blocked here.
  val ConsentStandardOBP: String = ApiStandards.obp.toString                  // "obp"
  val ConsentStandardBG: String = ConstantsBG.berlinGroupVersion1.apiStandard // "BG"
  val ConsentStandardUK: String = "UKOpenBanking"

  /**
   * Check whether a stored consent may be exercised by the standard now using it.
   * Returns None when allowed, Some(Failure) on a known mismatch. Returning the Failure (a
   * `Box[Nothing]`) lets callers propagate it into any `Box[User]` / `Box[Boolean]` result.
   *
   * Option A (grandfathering): a legacy consent whose apiStandard is blank/null is allowed
   * (with a warning) so pre-existing consents keep working; only a KNOWN mismatch is rejected.
   * Every live creator now stamps a standard, so blank means "old row" — backfill mApiStandard
   * to 'obp' to remove the grandfathering path and tighten to strict equality later.
   */
  // Test seam: assert the standard by consentId (used by ConsentStandardBoundaryTest).
  def assertConsentStandardById(consentId: String, expectedStandard: String): Option[Failure] =
    Consents.consentProvider.vend.getConsentByConsentId(consentId).toOption
      .flatMap(assertConsentStandard(_, expectedStandard))

  def assertConsentStandard(storedConsent: MappedConsent, expectedStandard: String): Option[Failure] = {
    val actualStandard = Option(storedConsent.apiStandard).map(_.trim).getOrElse("")
    if (actualStandard.isEmpty) {
      logger.warn(s"assertConsentStandard: consent ${storedConsent.consentId} has no apiStandard; " +
        s"grandfathering it for '$expectedStandard' access. Consider backfilling mApiStandard.")
      None
    } else if (actualStandard == expectedStandard) {
      None
    } else {
      Some(Failure(s"${ErrorMessages.ConsentDoesNotMatchStandard}Consent standard: $actualStandard, required: $expectedStandard."))
    }
  }

  /**
   * Validates the UK Open Banking AIS consent bound to the presented OAuth2 access token.
   *
   * Provider contract (OBP-OIDC, Keycloak, or any other OIDC server): the Bearer access token
   * is a JWT carrying a `consent_id` claim, planted by the identity provider during the consent
   * authorisation flow. Signature, issuer and expiry of the token were already verified by the
   * authentication layer (OAuth2Login issuer dispatch) before any endpoint reaches this check,
   * so only the claim is extracted here. The OBP database remains authoritative for consent
   * state: the status/user/consumer checks below run on every request, so revoking a consent
   * takes effect immediately even though an already-issued JWT cannot itself be revoked.
   *
   * The user-binding check matters because the consent_id claim is NOT validated by the IdP:
   * it must only pass for the PSU who authorised the consent (bound via updateConsentUser in
   * the authorise step), otherwise any user of the same consumer could present a foreign
   * consent_id and exercise a consent they never authorised.
   */
  def checkUKConsent(user: User, calContext: Option[CallContext]): Box[Boolean] = {
    // The request may instead have been authenticated by the consent itself, presented in a
    // Consent-Id / Consent-JWT header with no Authorization header at all. applyUKRules has then
    // already run every check below -- and resolved this very user from the consent -- so there is
    // nothing left to re-derive from a token that does not exist.
    if (calContext.flatMap(_.ukConsentId).isDefined) return Full(true)

    // The token named a UK consent that could not be resolved to its shadow user
    // (applyUKConsentPrincipalFromToken). The principal is therefore still the PSU, which is wider
    // than the consent rather than narrower -- serving it would hand the caller every account the
    // PSU holds under a consent that named other accounts, or none. Refused here rather than at
    // authentication so the consent-management endpoints, which never reach this check, stay usable
    // for inspecting and revoking that consent.
    //
    // 403 rather than 401: the caller authenticated fine, it is the consent's scope that cannot be
    // honoured. apiFailureToBox is what carries the code -- a bare Failure would take
    // fullBoxOrException's default (see NewStyle.function.checkUKConsent, which unboxes this).
    calContext.flatMap(_.ukConsentUnresolved) match {
      case Some(reason) => return ErrorUtil.apiFailureToBox(reason, 403)(calContext)
      case None => // resolved, or not a UK consent request at all
    }

    // No Authorization header, and applyUKRules did not run either. The usual way to arrive here
    // is a consent of another standard: the dispatcher routes it into that standard's branch,
    // which authenticates the request and leaves ukConsentId unset, and then a UK endpoint asks
    // this question. That is an ordinary refusal and the mirror of what the Berlin Group side
    // answers for a UK consent, so it gets the same code. Throwing instead surfaced it as
    // OBP-50000 Unknown Error at 500 -- a server fault for a request that was merely not
    // entitled.
    val accessToken = calContext.flatMap(_.authReqHeaderField)
      .map(_.replaceFirst("Bearer\\s+", "")) match {
      case Some(token) => token
      case None =>
        return Failure(s"$ConsentDoesNotMatchStandard Required: $ConsentStandardUK.")
    }

    val boxedConsent: Box[MappedConsent] = JwtUtil.getOptionalClaim("consent_id", accessToken) match {
      case Some(consentId) => Consents.consentProvider.vend.getConsentByConsentId(consentId)
      case None => return Failure(ErrorMessages.ConsentIdClaimMissing)
    }

    boxedConsent match {
      case Full(c) => assertConsentStandard(c, ConsentStandardUK) match {
        case Some(failure) => failure // Wrong standard — reject before status/user checks
        case None => c.mStatus.toString().toUpperCase() match {
          case status if status == ConsentStatus.AUTHORISED.toString =>
            System.currentTimeMillis match {
              case currentTimeMillis if currentTimeMillis < c.creationDateTime.getTime =>
                Failure(ErrorMessages.ConsentNotBeforeIssue)
              // A null expirationDateTime means the consent never expires (0..1 per spec,
              // open-ended if absent -- see createUKConsentJWT). ConsentScheduler.expiredUKConsents
              // proactively flips long-past-expiry consents to EXPIRED, but that runs on an
              // interval; this reactive check closes the gap immediately regardless of timing.
              case currentTimeMillis if Option(c.expirationDateTime).exists(_.getTime < currentTimeMillis) =>
                Failure(ErrorMessages.ConsentExpiredIssue)
              // The consent must belong to the PSU the access token authenticated. Data access runs
              // as the consent's shadow user (applyUKConsentPrincipalFromToken), so compare against
              // the PSU that swap set aside rather than against the principal -- a shadow user's id
              // can never equal mUserId. `user` is the fallback for a request the swap left alone.
              case _ if c.mUserId.get != calContext.flatMap(_.consenter.toOption).getOrElse(user).userId =>
                Failure(ErrorMessages.ConsentDoesNotMatchUser)
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
          case _ =>
            Failure(s"${ErrorMessages.ConsentStatusIssue}${ConsentStatus.AUTHORISED.toString}.")
        }
      }
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
