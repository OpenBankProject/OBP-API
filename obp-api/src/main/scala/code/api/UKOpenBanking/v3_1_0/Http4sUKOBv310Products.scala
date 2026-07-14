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
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v3.1 — ProductsApi, migrated from Lift to http4s.
 *
 * Both endpoints are stubs: the Lift handler authenticated the user and returned
 * the NotImplemented marker (HTTP 200). We mirror that with `withUser` (which
 * yields 401 when unauthenticated) returning the same marker.
 */
object Http4sUKOBv310Products extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): code.api.berlin.group.v1_3.JvalueCaseClass = code.api.berlin.group.v1_3.JvalueCaseClass(com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject])

  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion

  private val tag = ApiTag("Products") :: apiTagMockedData :: Nil

  // ── GET /accounts/ACCOUNTID/product ────────────────────────────────
  lazy val getAccountsAccountIdProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "product" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdProduct),
    "GET",
    "/accounts/ACCOUNTID/product",
    "Get Products",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdProduct)
  )

  // ── GET /products ──────────────────────────────────────────────────
  lazy val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "products" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getProducts),
    "GET",
    "/products",
    "Get Products",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getProducts)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdProduct(req)
      .orElse(getProducts(req))
  }
}
