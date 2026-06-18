package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.Constant
import code.api.UKOpenBanking.v3_1_0.JSONFactory_UKOpenBanking_310.ConsentPostBodyUKV310
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, connectorEmptyResponse, mockedDataText, passesPsd2Aisp, unboxFullOrFail, DateWithDayFormat}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, ConsentNotFound, ConsentViewNotFund, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.CallContext
import code.api.util.{ConsentJWT, JwtUtil}
import code.consent.Consents
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
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion

  lazy val createAccountAccessConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "account-access-consents" =>
      // Check auth FIRST (before body parsing) to mirror Lift's wrappedWithAuthCheck behaviour:
      // unauthenticated → 401, invalid body → 400. withUserAndBodyCreated parses body first
      // (→ 400) before checking auth — wrong order. Use executeFutureCreated + manual auth check.
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        for {
          u <- cc.user.toOption match {
            case Some(user) => Future.successful(user)
            case None       => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
          }
          consentJson <- Future.fromTry(scala.util.Try(
            com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("{}")).extract[ConsentPostBodyUKV310]
          ))
          consumerId = cc.consumer.map(_.consumerId.get)
          _ <- passesPsd2Aisp(Some(cc))
          createdConsent <- Future(Consents.consentProvider.vend.saveUKConsent(
            Some(u),
            bankId = None,
            accountIds = None,
            consumerId = consumerId,
            permissions = consentJson.Data.Permissions,
            expirationDateTime = DateWithDayFormat.parse(consentJson.Data.ExpirationDateTime),
            transactionFromDateTime = DateWithDayFormat.parse(consentJson.Data.TransactionFromDateTime),
            transactionToDateTime = DateWithDayFormat.parse(consentJson.Data.TransactionToDateTime),
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
              "TransactionToDateTime" : "${consentJson.Data.TransactionToDateTime}",
              "ExpirationDateTime" : "${consentJson.Data.ExpirationDateTime}",
              "Permissions" : ${consentJson.Data.Permissions.mkString("[\"", "\",\"", "\"]")},
              "ConsentId" : "${createdConsent.consentId}",
              "TransactionFromDateTime" : "${consentJson.Data.TransactionFromDateTime}"
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
    http4sPartialFunction = Some(createAccountAccessConsents)
  )

  lazy val deleteAccountAccessConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `ukV31Prefix` / "account-access-consents" / consentId =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        for {
          _ <- passesPsd2Aisp(Some(cc))
          _ <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, Some(cc), ConsentNotFound)
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
    http4sPartialFunction = Some(deleteAccountAccessConsentsConsentId)
  )

  lazy val getAccountAccessConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "account-access-consents" / consentId =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        for {
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)")
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
              "TransactionToDateTime" : "${consent.transactionToDateTime}",
              "ExpirationDateTime" : "${consent.expirationDateTime}",
              "Permissions" : ${consentViews.mkString("[\"", "\",\"", "\"]")},
              "ConsentId" : "${consent.consentId}",
              "TransactionFromDateTime" : "${consent.transactionFromDateTime}"
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
    http4sPartialFunction = Some(getAccountAccessConsentsConsentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createAccountAccessConsents(req)
      .orElse(deleteAccountAccessConsentsConsentId(req))
      .orElse(getAccountAccessConsentsConsentId(req))
  }
}
