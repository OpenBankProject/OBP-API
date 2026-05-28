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

/** UK Open Banking v3.1 — FilePaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310FilePayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("File Payments") :: apiTagMockedData :: Nil

  // Deeper path (/{id}/file) must come before single-wildcard (/{id}) in `routes`
  lazy val createFilePaymentConsentsConsentIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "file-payment-consents" / _ / "file" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createFilePaymentConsentsConsentIdFile),
    "POST",
    "/file-payment-consents/CONSENTID/file",
    "Create File Payment Consents File",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createFilePaymentConsentsConsentIdFile)
  )

  lazy val createFilePaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "file-payment-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createFilePaymentConsents),
    "POST",
    "/file-payment-consents",
    "Create File Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createFilePaymentConsents)
  )

  lazy val createFilePayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "file-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(createFilePayments),
    "POST",
    "/file-payments",
    "Create File Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createFilePayments)
  )

  lazy val getFilePaymentConsentsConsentIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payment-consents" / _ / "file" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getFilePaymentConsentsConsentIdFile),
    "GET",
    "/file-payment-consents/CONSENTID/file",
    "Get File Payment Consents File",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFilePaymentConsentsConsentIdFile)
  )

  lazy val getFilePaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payment-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getFilePaymentConsentsConsentId),
    "GET",
    "/file-payment-consents/CONSENTID",
    "Get File Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFilePaymentConsentsConsentId)
  )

  lazy val getFilePaymentsFilePaymentIdReportFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payments" / _ / "report-file" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getFilePaymentsFilePaymentIdReportFile),
    "GET",
    "/file-payments/FILEPAYMENTID/report-file",
    "Get File Payments Report File",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFilePaymentsFilePaymentIdReportFile)
  )

  lazy val getFilePaymentsFilePaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payments" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    null,
    implementedInApiVersion,
    nameOf(getFilePaymentsFilePaymentId),
    "GET",
    "/file-payments/FILEPAYMENTID",
    "Get File Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFilePaymentsFilePaymentId)
  )

  // Routes ordered deep-first: /{id}/file and /{id}/report-file before /{id}
  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createFilePaymentConsentsConsentIdFile(req)
      .orElse(createFilePaymentConsents(req))
      .orElse(createFilePayments(req))
      .orElse(getFilePaymentConsentsConsentIdFile(req))
      .orElse(getFilePaymentConsentsConsentId(req))
      .orElse(getFilePaymentsFilePaymentIdReportFile(req))
      .orElse(getFilePaymentsFilePaymentId(req))
  }
}
