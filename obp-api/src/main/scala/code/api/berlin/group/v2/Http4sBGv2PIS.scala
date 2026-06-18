package code.api.berlin.group.v2

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

object Http4sBGv2PIS extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion2
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV2Prefix = Root / ConstantsBG.berlinGroupVersion2.urlPrefix / ConstantsBG.berlinGroupVersion2.apiShortVersion

  // ── POST /v2/payments/{payment-product} ───────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(initiatePayment),
    "POST",
    "/payments/PAYMENT_PRODUCT",
    "Payment initiation request",
    "Creates a payment initiation request at the ASPSP.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockPaymentInitiation("sepa-credit-transfers"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(initiatePayment)
  )

  val initiatePayment: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV2Prefix` / "payments" / paymentProduct =>
      Created(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockPaymentInitiation(paymentProduct)))
  }

  // ── POST /v2/bulk-payments/{payment-product} ──────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(initiateBulkPayment),
    "POST",
    "/bulk-payments/PAYMENT_PRODUCT",
    "Payment initiation request (bulk)",
    "Creates a bulk payment initiation request at the ASPSP.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockPaymentInitiation("sepa-credit-transfers"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(initiateBulkPayment)
  )

  val initiateBulkPayment: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV2Prefix` / "bulk-payments" / paymentProduct =>
      Created(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockPaymentInitiation(paymentProduct)))
  }

  // ── POST /v2/periodic-payments/{payment-product} ──────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(initiatePeriodicPayment),
    "POST",
    "/periodic-payments/PAYMENT_PRODUCT",
    "Payment initiation request (periodic)",
    "Creates a periodic payment initiation request at the ASPSP.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockPaymentInitiation("sepa-credit-transfers"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(initiatePeriodicPayment)
  )

  val initiatePeriodicPayment: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV2Prefix` / "periodic-payments" / paymentProduct =>
      Created(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockPaymentInitiation(paymentProduct)))
  }

  // ── GET /v2/bulk-payments/{pp}/{paymentId}/extended-status ────────
  // Must be before generic 4-segment patterns

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getBulkPaymentExtendedStatus),
    "GET",
    "/bulk-payments/PAYMENT_PRODUCT/PAYMENT_ID/extended-status",
    "Get Bulk Payment Extended Status",
    "Returns the extended status of a bulk payment.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockBulkPaymentExtendedStatus("sepa-credit-transfers", "PAYMENT_ID"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getBulkPaymentExtendedStatus)
  )

  val getBulkPaymentExtendedStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "bulk-payments" / paymentProduct / paymentId / "extended-status" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockBulkPaymentExtendedStatus(paymentProduct, paymentId)))
  }

  // ── GET /v2/{payment-service}/{payment-product}/{paymentId}/status ─

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getPaymentStatus),
    "GET",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/status",
    "Payment initiation status request",
    "Returns the transaction status of a payment initiation.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockPaymentStatus,
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getPaymentStatus)
  )

  val getPaymentStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / paymentService / paymentProduct / paymentId / "status"
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockPaymentStatus))
  }

  // ── GET /v2/{payment-service}/{payment-product}/{paymentId} ───────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getPayment),
    "GET",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID",
    "Get Payment Information",
    "Returns the content of a payment object.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockPaymentDetails("payments", "sepa-credit-transfers", "PAYMENT_ID"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getPayment)
  )

  val getPayment: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / paymentService / paymentProduct / paymentId
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockPaymentDetails(paymentService, paymentProduct, paymentId)))
  }

  // ── DELETE /v2/{payment-service}/{payment-product}/{paymentId} ────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deletePayment),
    "DELETE",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID",
    "Payment Cancellation Request",
    "Cancels a payment initiation.",
    EmptyBody,
    EmptyBody,
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(deletePayment)
  )

  val deletePayment: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `bgV2Prefix` / paymentService / paymentProduct / paymentId
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) =>
      NoContent()
  }

  // ── POST /v2/{resource-path}/{resourceId}/{authorisation-category} ─

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(startAuthorisation),
    "POST",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations",
    "Start the authorisation process",
    "Creates an authorisation sub-resource.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockAuthorisationStart("payments/sepa-credit-transfers", "PAYMENT_ID"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(startAuthorisation)
  )

  val startAuthorisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV2Prefix` / paymentService / paymentProduct / resourceId / authorisationCategory
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) &&
         Set("authorisations", "cancellation-authorisations").contains(authorisationCategory) =>
      val resourcePath = s"$paymentService/$paymentProduct"
      Created(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockAuthorisationStart(resourcePath, resourceId)))
  }

  // ── GET /v2/{resource-path}/{resourceId}/{authorisation-category} ──

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAuthorisationSubResources),
    "GET",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations",
    "Get authorisation sub-resources",
    "Returns a list of all authorisation sub-resource IDs.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockAuthorisationSubResources("payments/sepa-credit-transfers", "PAYMENT_ID"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getAuthorisationSubResources)
  )

  val getAuthorisationSubResources: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / paymentService / paymentProduct / resourceId / authorisationCategory
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) &&
         Set("authorisations", "cancellation-authorisations").contains(authorisationCategory) =>
      val resourcePath = s"$paymentService/$paymentProduct"
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockAuthorisationSubResources(resourcePath, resourceId)))
  }

  // ── GET /v2/{resource-path}/{resourceId}/{auth-category}/{authId} ──

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAuthorisationStatus),
    "GET",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
    "Read the SCA status of the authorisation",
    "Returns the SCA status of a corresponding authorisation sub-resource.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockAuthorisationStatus("AUTHORISATION_ID"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getAuthorisationStatus)
  )

  val getAuthorisationStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / paymentService / paymentProduct / resourceId / authorisationCategory / authorisationId
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) &&
         Set("authorisations", "cancellation-authorisations").contains(authorisationCategory) =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockAuthorisationStatus(authorisationId)))
  }

  // ── PUT /v2/{resource-path}/{resourceId}/{auth-category}/{authId} ──

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updatePsuData),
    "PUT",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
    "Update PSU Data for payment initiation",
    "Updates PSU data for the corresponding authorisation sub-resource.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockUpdatePsuData("AUTHORISATION_ID"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(updatePsuData)
  )

  val updatePsuData: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV2Prefix` / paymentService / paymentProduct / resourceId / authorisationCategory / authorisationId
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) &&
         Set("authorisations", "cancellation-authorisations").contains(authorisationCategory) =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockUpdatePsuData(authorisationId)))
  }

  // ── PUT /v2/{resource-path}/{resourceId} ──────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateResourceWithDebtorAccount),
    "PUT",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID",
    "Update resource with debtor account",
    "Updates the payment resource with the debtor account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockUpdateDebtorAccount("PAYMENT_ID"),
    List(UnknownError),
    apiTagPSD2PIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(updateResourceWithDebtorAccount)
  )

  val updateResourceWithDebtorAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV2Prefix` / paymentService / paymentProduct / resourceId
      if Set("payments", "bulk-payments", "periodic-payments").contains(paymentService) =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockUpdateDebtorAccount(resourceId)))
  }

  // ── Combined routes (ordering matters!) ───────────────────────────
  // More specific paths first, then generic patterns

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    // POST routes (2-segment after prefix)
    initiatePayment(req)
      .orElse(initiateBulkPayment(req))
      .orElse(initiatePeriodicPayment(req))
      // GET specific 4-segment: bulk extended status
      .orElse(getBulkPaymentExtendedStatus(req))
      // GET/DELETE generic 4-segment: status
      .orElse(getPaymentStatus(req))
      // 5-segment: authorisation with ID
      .orElse(getAuthorisationStatus(req))
      .orElse(updatePsuData(req))
      // 4-segment: authorisation list / start
      .orElse(startAuthorisation(req))
      .orElse(getAuthorisationSubResources(req))
      // DELETE 3-segment
      .orElse(deletePayment(req))
      // GET 3-segment: payment details
      .orElse(getPayment(req))
      // PUT 3-segment: debtor account update
      .orElse(updateResourceWithDebtorAccount(req))
  }
}
