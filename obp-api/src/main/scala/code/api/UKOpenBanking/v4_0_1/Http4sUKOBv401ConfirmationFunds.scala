package code.api.UKOpenBanking.v4_0_1

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.JsonAliases
import org.json4s.{Formats, JObject}
import org.http4s._
import org.http4s.dsl.io._
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

// AUTO-GENERATED from UK Open Banking read-write-api-specs v4.0.1 (ConfirmationFunds).
// Spec-faithful scaffold: routes return synthesized example JSON from the
// OpenAPI schemas (the specs carry no examples). Deepen to real OBP
// connector logic per endpoint later, mirroring v3_1_0.
object Http4sUKOBv401ConfirmationFunds extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV401
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): JObject = JsonAliases.parse(s).asInstanceOf[JObject]
  val ukV401Prefix = Root / ApiVersion.ukOpenBankingV401.urlPrefix / ApiVersion.ukOpenBankingV401.apiShortVersion

  private val EXREQ_createFundsConfirmationConsents: String = """{
  "Data": {
    "ExpirationDateTime": "2020-01-01T00:00:00+00:00",
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  }
}"""
  private val EX_createFundsConfirmationConsents: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ExpirationDateTime": "2020-01-01T00:00:00+00:00",
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createFundsConfirmationConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "cbpii" / "funds-confirmation-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createFundsConfirmationConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFundsConfirmationConsents),
    "POST",
    "/cbpii/funds-confirmation-consents",
    "Create a Funds Confirmation Consent",
    """Enables a CBPII to ask an ASPSP to create a new funds-confirmation-consent resource, by sending a copy of the consent to the ASPSP.""",
    parseBody(EXREQ_createFundsConfirmationConsents),
    parseBody(EX_createFundsConfirmationConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Funds Confirmation Consents") :: Nil,
    http4sPartialFunction = Some(createFundsConfirmationConsents)
  )

  private val EX_getFundsConfirmationConsentsConsentId: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ExpirationDateTime": "2020-01-01T00:00:00+00:00",
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getFundsConfirmationConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "cbpii" / "funds-confirmation-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getFundsConfirmationConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFundsConfirmationConsentsConsentId),
    "GET",
    "/cbpii/funds-confirmation-consents/CONSENT_ID",
    "Get a Funds Confirmation Consent",
    """Enables a CBPII to retrieve the status of a Funds Confirmation Consent resource.""",
    EmptyBody,
    parseBody(EX_getFundsConfirmationConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Funds Confirmation Consents") :: Nil,
    http4sPartialFunction = Some(getFundsConfirmationConsentsConsentId)
  )

  private val EX_deleteFundsConfirmationConsentsConsentId: String = """{}"""
  lazy val deleteFundsConfirmationConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `ukV401Prefix` / "cbpii" / "funds-confirmation-consents" / consentId =>
      EndpointHelpers.executeDelete(req) { cc => Future.successful(()) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteFundsConfirmationConsentsConsentId),
    "DELETE",
    "/cbpii/funds-confirmation-consents/CONSENT_ID",
    "Delete a Funds Confirmation Consent",
    """Enables a CBPII to inform the PSU’s ASPSP that the PSU has revoked their consent to provide funds confirmations.""",
    EmptyBody,
    parseBody(EX_deleteFundsConfirmationConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Funds Confirmation Consents") :: Nil,
    http4sPartialFunction = Some(deleteFundsConfirmationConsentsConsentId)
  )

  private val EXREQ_createFundsConfirmations: String = """{
  "Data": {
    "ConsentId": "string",
    "Reference": "string",
    "InstructedAmount": {
      "Amount": "string",
      "Currency": "string"
    }
  }
}"""
  private val EX_createFundsConfirmations: String = """{
  "Data": {
    "FundsConfirmationId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "FundsAvailable": true,
    "Reference": "string",
    "InstructedAmount": {
      "Amount": "string",
      "Currency": "string"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createFundsConfirmations: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "cbpii" / "funds-confirmations" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createFundsConfirmations)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFundsConfirmations),
    "POST",
    "/cbpii/funds-confirmations",
    "Create a Funds Confirmation Request",
    """Enables a CBPII to check whether a PSU has sufficient available funds for a CBPII transaction.""",
    parseBody(EXREQ_createFundsConfirmations),
    parseBody(EX_createFundsConfirmations),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Funds Confirmations") :: Nil,
    http4sPartialFunction = Some(createFundsConfirmations)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createFundsConfirmationConsents(req)
      .orElse(getFundsConfirmationConsentsConsentId(req)
      .orElse(deleteFundsConfirmationConsentsConsentId(req)
      .orElse(createFundsConfirmations(req))))
  }
}
