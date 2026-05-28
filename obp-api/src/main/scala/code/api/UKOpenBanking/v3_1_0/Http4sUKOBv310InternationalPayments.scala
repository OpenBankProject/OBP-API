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

/** UK Open Banking v3.1 — InternationalPaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310InternationalPayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("International Payments") :: apiTagMockedData :: Nil

  lazy val createInternationalPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "international-payment-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createInternationalPaymentConsents),
    "POST",
    "/international-payment-consents",
    "Create International Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createInternationalPaymentConsents)
  )

  lazy val createInternationalPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "international-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createInternationalPayments),
    "POST",
    "/international-payments",
    "Create International Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createInternationalPayments)
  )

  // Deeper path must come before single-wildcard path in `routes`
  lazy val getInternationalPaymentConsentsConsentIdFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-payment-consents" / _ / "funds-confirmation" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getInternationalPaymentConsentsConsentIdFundsConfirmation),
    "GET",
    "/international-payment-consents/CONSENTID/funds-confirmation",
    "Get International Payment Consents Funds Confirmation",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalPaymentConsentsConsentIdFundsConfirmation)
  )

  lazy val getInternationalPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-payment-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getInternationalPaymentConsentsConsentId),
    "GET",
    "/international-payment-consents/CONSENTID",
    "Get International Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalPaymentConsentsConsentId)
  )

  lazy val getInternationalPaymentsInternationalPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-payments" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getInternationalPaymentsInternationalPaymentId),
    "GET",
    "/international-payments/INTERNATIONALPAYMENTID",
    "Get International Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalPaymentsInternationalPaymentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createInternationalPaymentConsents(req)
      .orElse(createInternationalPayments(req))
      .orElse(getInternationalPaymentConsentsConsentIdFundsConfirmation(req))
      .orElse(getInternationalPaymentConsentsConsentId(req))
      .orElse(getInternationalPaymentsInternationalPaymentId(req))
  }
}
