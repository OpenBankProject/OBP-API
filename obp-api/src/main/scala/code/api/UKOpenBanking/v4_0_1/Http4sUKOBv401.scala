package code.api.UKOpenBanking.v4_0_1

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.ResourceDocMiddleware
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion
import org.http4s._

import scala.collection.mutable.ArrayBuffer

/**
 * UK Open Banking Read/Write v4.0.1 — http4s aggregator (mirror of Http4sUKOBv310).
 *
 * Collects resource docs and routes from every per-sub-API endpoint object,
 * wraps the combined routes once with ResourceDocMiddleware (which builds the
 * CallContext via anonymousAccess for these non-/obp paths), and exposes
 * `wrappedRoutes` for wiring into Http4sApp.baseServices.
 *
 * Coverage: all 6 v4.0.1 sub-APIs (account-info / payment-initiation /
 * confirmation-funds / event-notifications / events / vrp), ~89 endpoints.
 * Spec-faithful scaffold — routes return synthesized OpenAPI example JSON; deepen
 * to real OBP connector logic per endpoint later.
 */
object Http4sUKOBv401 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV401

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sUKOBv401AccountInfo.resourceDocs ++
    Http4sUKOBv401PaymentInitiation.resourceDocs ++
    Http4sUKOBv401ConfirmationFunds.resourceDocs ++
    Http4sUKOBv401EventNotifications.resourceDocs ++
    Http4sUKOBv401Events.resourceDocs ++
    Http4sUKOBv401Vrp.resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sUKOBv401AccountInfo.routes(req)
      .orElse(Http4sUKOBv401PaymentInitiation.routes(req))
      .orElse(Http4sUKOBv401ConfirmationFunds.routes(req))
      .orElse(Http4sUKOBv401EventNotifications.routes(req))
      .orElse(Http4sUKOBv401Events.routes(req))
      .orElse(Http4sUKOBv401Vrp.routes(req))
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
}
