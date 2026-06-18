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

/** UK Open Banking v3.1 — OffersApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310Offers extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Offers") :: apiTagMockedData :: Nil

  lazy val getAccountsAccountIdOffers: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "offers" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdOffers),
    "GET",
    "/accounts/ACCOUNTID/offers",
    "Get Offers",
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdOffers)
  )

  lazy val getOffers: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "offers" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOffers),
    "GET",
    "/offers",
    "Get Offers",
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getOffers)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdOffers(req).orElse(getOffers(req))
  }
}
