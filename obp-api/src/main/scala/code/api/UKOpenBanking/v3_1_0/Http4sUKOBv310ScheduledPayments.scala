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

/** UK Open Banking v3.1 — ScheduledPaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310ScheduledPayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil

  lazy val getAccountsAccountIdScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "scheduled-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdScheduledPayments),
    "GET",
    "/accounts/ACCOUNTID/scheduled-payments",
    "Get Scheduled Payments",
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
    "ScheduledPayment" : [ {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    }, {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdScheduledPayments)
  )

  lazy val getScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "scheduled-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getScheduledPayments),
    "GET",
    "/scheduled-payments",
    "Get Scheduled Payments",
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
    "ScheduledPayment" : [ {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    }, {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getScheduledPayments)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdScheduledPayments(req).orElse(getScheduledPayments(req))
  }
}
