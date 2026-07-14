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

/** UK Open Banking v3.1 — StandingOrdersApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310StandingOrders extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): code.api.berlin.group.v1_3.JvalueCaseClass = code.api.berlin.group.v1_3.JvalueCaseClass(com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject])
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Standing Orders") :: apiTagMockedData :: Nil

  lazy val getAccountsAccountIdStandingOrders: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "standing-orders" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStandingOrders),
    "GET",
    "/accounts/ACCOUNTID/standing-orders",
    "Get Standing Orders",
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
    "StandingOrder" : [ {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdStandingOrders)
  )

  lazy val getStandingOrders: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "standing-orders" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getStandingOrders),
    "GET",
    "/standing-orders",
    "Get Standing Orders",
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
    "StandingOrder" : [ {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getStandingOrders)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdStandingOrders(req).orElse(getStandingOrders(req))
  }
}
