package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, mockedDataText}
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v3.1 — FundsConfirmationsApi stubs migrated to http4s.
 * GET/POST stubs return the NotImplemented marker (200); DELETE returns 204
 * (matching the Lift handler's HttpCode.`204`).
 */
object Http4sUKOBv310FundsConfirmations extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Funds Confirmations") :: apiTagMockedData :: Nil

  lazy val createFundsConfirmationConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "funds-confirmation-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFundsConfirmationConsents),
    "POST",
    "/funds-confirmation-consents",
    "Create Funds Confirmation Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DebtorAccount" : {
      "SecondaryIdentification" : "SecondaryIdentification",
      "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
      "Identification" : "Identification",
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ConsentId" : "ConsentId"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createFundsConfirmationConsents)
  )

  lazy val createFundsConfirmations: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "funds-confirmations" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFundsConfirmations),
    "POST",
    "/funds-confirmations",
    "Create Funds Confirmations",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "FundsConfirmationId" : "FundsConfirmationId",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Reference" : "Reference",
    "FundsAvailable" : true,
    "ConsentId" : "ConsentId",
    "InstructedAmount" : {
      "Amount" : { },
      "Currency" : "Currency"
    }
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createFundsConfirmations)
  )

  // DELETE returns 204 — matching Lift's HttpCode.`204` response
  lazy val deleteFundsConfirmationConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `ukV31Prefix` / "funds-confirmation-consents" / _ =>
      EndpointHelpers.withUserDelete(req) { (_, _) => Future.successful(()) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteFundsConfirmationConsentsConsentId),
    "DELETE",
    "/funds-confirmation-consents/CONSENTID",
    "Delete Funds Confirmation Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(deleteFundsConfirmationConsentsConsentId)
  )

  lazy val getFundsConfirmationConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "funds-confirmation-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFundsConfirmationConsentsConsentId),
    "GET",
    "/funds-confirmation-consents/CONSENTID",
    "Get Funds Confirmation Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DebtorAccount" : {
      "SecondaryIdentification" : "SecondaryIdentification",
      "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
      "Identification" : "Identification",
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ConsentId" : "ConsentId"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFundsConfirmationConsentsConsentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createFundsConfirmationConsents(req)
      .orElse(createFundsConfirmations(req))
      .orElse(deleteFundsConfirmationConsentsConsentId(req))
      .orElse(getFundsConfirmationConsentsConsentId(req))
  }
}
