package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.Constant
import code.api.UKOpenBanking.v3_1_0.JSONFactory_UKOpenBanking_310.ConsentPostBodyUKV310
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, UserOrApplication, connectorEmptyResponse, mockedDataText, passesPsd2Aisp, unboxFullOrFail, parseIso8601OrDayDate}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, ConsentNotFound, ConsentViewNotFund, InvalidJsonFormat, InvalidUKConsentPermissions, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.CallContext
import code.api.util.{Consent, ConsentJWT, JwtUtil, NewStyle}
import code.consent.Consents
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v3.1 — AccountAccessApi, migrated from Lift to http4s.
 * Genuine data-backed endpoints: createAccountAccessConsents (POST, 201),
 * deleteAccountAccessConsentsConsentId (DELETE, 204),
 * getAccountAccessConsentsConsentId (GET, 200).
 */
object Http4sUKOBv310AccountAccess extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): code.api.berlin.group.v1_3.JvalueCaseClass = code.api.berlin.group.v1_3.JvalueCaseClass(com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject])
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion

  lazy val createAccountAccessConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "account-access-consents" =>
      // Check auth FIRST (before body parsing) to mirror Lift's wrappedWithAuthCheck behaviour:
      // unauthenticated → 401, invalid body → 400. withUserAndBodyCreated parses body first
      // (→ 400) before checking auth — wrong order. Use executeFutureCreated + manual auth check.
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        for {
          // Client-credentials lodging: require some authentication (consumer or user) but not a
          // PSU specifically; reject only a fully anonymous request. The PSU is bound later at
          // authorise time. Mirrors the Berlin Group native consent flow and the v4.0.1 handler.
          _ <- if (cc.user.isEmpty && cc.consumer.isEmpty)
                 Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
               else Future.successful(())
          // A pure client-credentials token still resolves cc.user to an auto-vivified
          // pseudo-user (idGivenByProvider == the calling consumer's own client key) rather than
          // leaving it Empty -- that pseudo-user is not a PSU, so it must not become the
          // consent's owner (it would permanently block the real PSU's authorise-time
          // ConsentDoesNotMatchUser check). Only carry a genuine PSU session through.
          createdByUser = cc.user.toOption
            .filterNot(u => cc.consumer.map(_.key.get).contains(u.idGivenByProvider))
          consentJson <- Future.fromTry(scala.util.Try(
            com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("{}")).extract[ConsentPostBodyUKV310]
          ))
          // Separate step (not inlined into the saveUKConsent call below) so a bad date string
          // fails here -> 400. NewStyle.function.tryons (not Future.fromTry) is required for
          // that: ErrorResponseConverter only special-cases APIFailureNewStyle to preserve a set
          // HTTP code -- tryons wraps failures that way, a bare Future.fromTry(Try(...)) doesn't
          // and falls through to unknownErrorToResponse, i.e. 500.
          (expirationDateTime, transactionFromDateTime, transactionToDateTime) <- NewStyle.function.tryons(
            s"$InvalidJsonFormat The Json body should have valid ISO-8601 ExpirationDateTime/TransactionFromDateTime/TransactionToDateTime values ", 400, Some(cc)) {
            (
              consentJson.Data.ExpirationDateTime.map(parseIso8601OrDayDate),
              consentJson.Data.TransactionFromDateTime.map(parseIso8601OrDayDate),
              consentJson.Data.TransactionToDateTime.map(parseIso8601OrDayDate)
            )
          }
          // The standard requires the ASPSP to refuse malformed permission combinations with 400
          // rather than create a consent that can never be exercised -- see
          // Consent.validateUKConsentPermissions for the rules and why they matter.
          _ <- Consent.validateUKConsentPermissions(consentJson.Data.Permissions) match {
            case Some(reason) =>
              Helper.booleanToFuture(s"$InvalidUKConsentPermissions$reason", 400, Some(cc))(false)
            case None => Future.successful(true)
          }
          consumerId = cc.consumer.map(_.consumerId.get)
          _ <- passesPsd2Aisp(Some(cc))
          createdConsent <- Future(Consents.consentProvider.vend.saveUKConsent(
            createdByUser,
            bankId = None,
            accountIds = None,
            consumerId = consumerId,
            permissions = consentJson.Data.Permissions,
            expirationDateTime = expirationDateTime,
            transactionFromDateTime = transactionFromDateTime,
            transactionToDateTime = transactionToDateTime,
            apiStandard = Some("UKOpenBanking"),
            apiVersion = Some("3.1.0")
          )) map { i => connectorEmptyResponse(i, Some(cc)) }
        } yield {
          com.openbankproject.commons.util.JsonAliases.parse(s"""{
            "Meta" : {
              "LastAvailableDateTime" : "2000-01-23T06:44:05.618Z",
              "FirstAvailableDateTime" : "2000-01-23T06:44:05.618Z",
              "TotalPages" : 0
            },
            "Links" : {
              "Self" : "${Constant.HostName}/open-banking/v3.1/account-access-consents"
            },
            "Risk" : "",
            "Data" : {
              "Status" : "${createdConsent.status}",
              "StatusUpdateDateTime" : "${createdConsent.statusUpdateDateTime}",
              "CreationDateTime" : "${createdConsent.creationDateTime}",
              "TransactionToDateTime" : "${consentJson.Data.TransactionToDateTime.getOrElse("")}",
              "ExpirationDateTime" : "${consentJson.Data.ExpirationDateTime.getOrElse("")}",
              "Permissions" : ${consentJson.Data.Permissions.mkString("[\"", "\",\"", "\"]")},
              "ConsentId" : "${createdConsent.consentId}",
              "TransactionFromDateTime" : "${consentJson.Data.TransactionFromDateTime.getOrElse("")}"
            }
          }""")
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createAccountAccessConsents),
    "POST",
    "/account-access-consents",
    "Create Account Access Consents",
    s"""${mockedDataText(false)}
       |Create Account Access Consents
       |""".stripMargin,
    parseBody("""{
  "Data": {
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-10-20T08:40:47.285Z",
    "TransactionFromDateTime": "2020-10-20T08:40:47.285Z",
    "TransactionToDateTime": "2020-10-20T08:40:47.285Z"
  },
  "Risk": ""
}"""),
    parseBody("""{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-10-20T08:40:47.375Z",
    "Status": "Authorised",
    "StatusUpdateDateTime": "2020-10-20T08:40:47.375Z",
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-10-20T08:40:47.375Z",
    "TransactionFromDateTime": "2020-10-20T08:40:47.375Z",
    "TransactionToDateTime": "2020-10-20T08:40:47.375Z"
  },
  "Risk": {},
  "Links": {
    "Self": "https://obp.example.com/open-banking/v3.1/account-access-consents/CONSENT_ID"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-10-20T08:40:47.375Z",
    "LastAvailableDateTime": "2020-10-20T08:40:47.375Z"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Account Access") :: Nil,
    // Consent lodging is a client-credentials call: the TPP is authenticated as an app, and there
    // is no PSU yet. The handler above already says so; this makes the ResourceDoc say it too.
    // Without it the doc defaults to UserOnly, which sends the middleware down anonymousAccess and
    // 401s any request that carries no user -- so the endpoint only works today because OAuth2
    // token parsing auto-vivifies a user for a client-credentials token. Matches the Berlin Group
    // twin (Http4sBGv13AIS.createConsent), which has always been UserOrApplication.
    authMode = UserOrApplication,
    http4sPartialFunction = Some(createAccountAccessConsents)
  )

  lazy val deleteAccountAccessConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `ukV31Prefix` / "account-access-consents" / consentId =>
      // Not withUserDelete: the standard has the AISP revoke its own consent with a
      // client-credentials token, which carries no PSU. Consent.checkUKConsentAccess decides who
      // may revoke it from whichever identity the session does carry.
      EndpointHelpers.executeDelete(req) { cc =>
        for {
          _ <- passesPsd2Aisp(Some(cc))
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, Some(cc), ConsentNotFound)
          }
          _ <- Consent.checkUKConsentAccess(
            consent.userId, consent.consumerId,
            cc.user.toOption.map(_.userId), cc.consumer.map(_.consumerId.get)) match {
            case Some(reason) => Helper.booleanToFuture(reason, 403, Some(cc))(false)
            case None => Future.successful(true)
          }
          _ <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
            i => connectorEmptyResponse(i, Some(cc))
          }
        } yield ()
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteAccountAccessConsentsConsentId),
    "DELETE",
    "/account-access-consents/CONSENT_ID",
    "Delete Account Access Consents",
    s"""${mockedDataText(false)}
       |Delete Account Access Consents
       |""".stripMargin,
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Account Access") :: Nil,
    // As with the POST that lodges the consent: revoking is a client-credentials call in the
    // standard's AISP flow, so a PSU cannot be required. See Consent.checkUKConsentAccess.
    authMode = UserOrApplication,
    http4sPartialFunction = Some(deleteAccountAccessConsentsConsentId)
  )

  lazy val getAccountAccessConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "account-access-consents" / consentId =>
      // Not withUser -- see the DELETE twin above.
      EndpointHelpers.executeAndRespond(req) { cc =>
        for {
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)")
          }
          _ <- Consent.checkUKConsentAccess(
            consent.userId, consent.consumerId,
            cc.user.toOption.map(_.userId), cc.consumer.map(_.consumerId.get)) match {
            case Some(reason) => Helper.booleanToFuture(reason, 403, Some(cc))(false)
            case None => Future.successful(true)
          }
          consentViews <- Future(JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken).map(
            com.openbankproject.commons.util.JsonAliases.parse(_).extract[ConsentJWT].views.map(_.view_id)
          )) map { unboxFullOrFail(_, Some(cc), s"$ConsentViewNotFund ($consentId)") }
        } yield {
          com.openbankproject.commons.util.JsonAliases.parse(s"""{
            "Meta" : {
              "LastAvailableDateTime" : "2000-01-23T06:44:05.618Z",
              "FirstAvailableDateTime" : "2000-01-23T06:44:05.618Z",
              "TotalPages" : 0
            },
            "Risk": "",
            "Links" : {
              "Self" : "${Constant.HostName}/open-banking/v3.1/account-access-consents/CONSENT_ID"
            },
            "Data" : {
              "Status" : "${consent.status}",
              "StatusUpdateDateTime" : "${consent.statusUpdateDateTime}",
              "CreationDateTime" : "${consent.creationDateTime}",
              "TransactionToDateTime" : "${Option(consent.transactionToDateTime).getOrElse("")}",
              "ExpirationDateTime" : "${Option(consent.expirationDateTime).getOrElse("")}",
              "Permissions" : ${consentViews.mkString("[\"", "\",\"", "\"]")},
              "ConsentId" : "${consent.consentId}",
              "TransactionFromDateTime" : "${Option(consent.transactionFromDateTime).getOrElse("")}"
            }
          }""")
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountAccessConsentsConsentId),
    "GET",
    "/account-access-consents/CONSENT_ID",
    "Get Account Access Consents",
    s"""
       |${mockedDataText(false)}
       |Get Account Access Consents
       |""".stripMargin,
    EmptyBody,
    parseBody("""{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-10-20T10:28:39.801Z",
    "Status": "Authorised",
    "StatusUpdateDateTime": "2020-10-20T10:28:39.801Z",
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-10-20T10:28:39.801Z",
    "TransactionFromDateTime": "2020-10-20T10:28:39.801Z",
    "TransactionToDateTime": "2020-10-20T10:28:39.801Z"
  },
  "Risk": "",
  "Links": {
    "Self": "https://obp.example.com/open-banking/v3.1/account-access-consents/CONSENT_ID"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-10-20T10:28:39.801Z",
    "LastAvailableDateTime": "2020-10-20T10:28:39.801Z"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Account Access") :: Nil,
    // As above: the AISP polls its own consent with a client-credentials token.
    authMode = UserOrApplication,
    http4sPartialFunction = Some(getAccountAccessConsentsConsentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createAccountAccessConsents(req)
      .orElse(deleteAccountAccessConsentsConsentId(req))
      .orElse(getAccountAccessConsentsConsentId(req))
  }
}
