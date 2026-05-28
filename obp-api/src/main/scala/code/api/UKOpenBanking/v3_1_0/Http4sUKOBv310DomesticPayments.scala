package code.api.UKOpenBanking.v3_1_0

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
import net.liftweb.json.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/** UK Open Banking v3.1 — DomesticPaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310DomesticPayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Domestic Payments") :: apiTagMockedData :: Nil

  lazy val createDomesticPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "domestic-payment-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createDomesticPaymentConsents),
    "POST",
    "/domestic-payment-consents",
    "Create Domestic Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createDomesticPaymentConsents)
  )

  lazy val createDomesticPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "domestic-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createDomesticPayments),
    "POST",
    "/domestic-payments",
    "Create Domestic Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createDomesticPayments)
  )

  // Deeper path must come before single-wildcard path in `routes`
  lazy val getDomesticPaymentConsentsConsentIdFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "domestic-payment-consents" / _ / "funds-confirmation" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getDomesticPaymentConsentsConsentIdFundsConfirmation),
    "GET",
    "/domestic-payment-consents/CONSENTID/funds-confirmation",
    "Get Domestic Payment Consents Funds Confirmation",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getDomesticPaymentConsentsConsentIdFundsConfirmation)
  )

  lazy val getDomesticPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "domestic-payment-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getDomesticPaymentConsentsConsentId),
    "GET",
    "/domestic-payment-consents/CONSENTID",
    "Get Domestic Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getDomesticPaymentConsentsConsentId)
  )

  lazy val getDomesticPaymentsDomesticPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "domestic-payments" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getDomesticPaymentsDomesticPaymentId),
    "GET",
    "/domestic-payments/DOMESTICPAYMENTID",
    "Get Domestic Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getDomesticPaymentsDomesticPaymentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createDomesticPaymentConsents(req)
      .orElse(createDomesticPayments(req))
      .orElse(getDomesticPaymentConsentsConsentIdFundsConfirmation(req))
      .orElse(getDomesticPaymentConsentsConsentId(req))
      .orElse(getDomesticPaymentsDomesticPaymentId(req))
  }
}
