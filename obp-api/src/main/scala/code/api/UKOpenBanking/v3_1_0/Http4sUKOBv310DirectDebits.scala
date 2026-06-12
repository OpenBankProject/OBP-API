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

/** UK Open Banking v3.1 — DirectDebitsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310DirectDebits extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Direct Debits") :: apiTagMockedData :: Nil

  lazy val getAccountsAccountIdDirectDebits: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "direct-debits" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdDirectDebits),
    "GET",
    "/accounts/ACCOUNTID/direct-debits",
    "Get Direct Debits",
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
    "DirectDebit" : [ {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    }, {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdDirectDebits)
  )

  lazy val getDirectDebits: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "direct-debits" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDirectDebits),
    "GET",
    "/direct-debits",
    "Get Direct Debits",
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
    "DirectDebit" : [ {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    }, {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getDirectDebits)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdDirectDebits(req).orElse(getDirectDebits(req))
  }
}
