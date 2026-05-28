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

/** UK Open Banking v3.1 — InternationalScheduledPaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310InternationalScheduledPayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("International Scheduled Payments") :: apiTagMockedData :: Nil

  lazy val createInternationalScheduledPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "international-scheduled-payment-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createInternationalScheduledPaymentConsents),
    "POST",
    "/international-scheduled-payment-consents",
    "Create International Scheduled Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createInternationalScheduledPaymentConsents)
  )

  lazy val createInternationalScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "international-scheduled-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createInternationalScheduledPayments),
    "POST",
    "/international-scheduled-payments",
    "Create International Scheduled Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createInternationalScheduledPayments)
  )

  // Deeper path must come before single-wildcard path in `routes`
  lazy val getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-scheduled-payment-consents" / _ / "funds-confirmation" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation),
    "GET",
    "/international-scheduled-payment-consents/CONSENTID/funds-confirmation",
    "Get International Scheduled Payment Consents Funds Confirmation",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation)
  )

  lazy val getInternationalScheduledPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-scheduled-payment-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getInternationalScheduledPaymentConsentsConsentId),
    "GET",
    "/international-scheduled-payment-consents/CONSENTID",
    "Get International Scheduled Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalScheduledPaymentConsentsConsentId)
  )

  lazy val getInternationalScheduledPaymentsInternationalScheduledPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-scheduled-payments" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getInternationalScheduledPaymentsInternationalScheduledPaymentId),
    "GET",
    "/international-scheduled-payments/INTERNATIONALSCHEDULEDPAYMENTID",
    "Get International Scheduled Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalScheduledPaymentsInternationalScheduledPaymentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createInternationalScheduledPaymentConsents(req)
      .orElse(createInternationalScheduledPayments(req))
      .orElse(getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation(req))
      .orElse(getInternationalScheduledPaymentConsentsConsentId(req))
      .orElse(getInternationalScheduledPaymentsInternationalScheduledPaymentId(req))
  }
}
