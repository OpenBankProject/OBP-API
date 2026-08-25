package code.api.UKOpenBanking.v3_1_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion
import org.http4s._

import scala.collection.mutable.ArrayBuffer

/**
 * UK Open Banking v3.1 — http4s aggregator (mirror of Berlin Group's Http4sBGv2).
 *
 * Collects resource docs and routes from every per-category endpoint object,
 * wraps the combined routes once with ResourceDocMiddleware (which builds the
 * CallContext via anonymousAccess for these non-/obp paths), and exposes
 * `wrappedRoutes` for wiring into Http4sApp.baseServices.
 *
 * Coverage: all 20 v3.1 categories (~67 endpoints) are migrated to http4s and
 * composed into allRoutes below. The Lift ScannedApis aggregator
 * (OBP_UKOpenBanking_310) registers `routes = Nil`, so no UK v3.1 path is served
 * by Lift — nothing falls through to the Lift bridge.
 */
object Http4sUKOBv310 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV31

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sUKOBv310AccountAccess.resourceDocs ++
    Http4sUKOBv310Accounts.resourceDocs ++
    Http4sUKOBv310Products.resourceDocs ++
    Http4sUKOBv310Beneficiaries.resourceDocs ++
    Http4sUKOBv310DirectDebits.resourceDocs ++
    Http4sUKOBv310Offers.resourceDocs ++
    Http4sUKOBv310Partys.resourceDocs ++
    Http4sUKOBv310ScheduledPayments.resourceDocs ++
    Http4sUKOBv310StandingOrders.resourceDocs ++
    Http4sUKOBv310Statements.resourceDocs ++
    Http4sUKOBv310DomesticPayments.resourceDocs ++
    Http4sUKOBv310DomesticScheduledPayments.resourceDocs ++
    Http4sUKOBv310DomesticStandingOrders.resourceDocs ++
    Http4sUKOBv310FilePayments.resourceDocs ++
    Http4sUKOBv310FundsConfirmations.resourceDocs ++
    Http4sUKOBv310InternationalPayments.resourceDocs ++
    Http4sUKOBv310InternationalScheduledPayments.resourceDocs ++
    Http4sUKOBv310InternationalStandingOrders.resourceDocs ++
    Http4sUKOBv310Balances.resourceDocs ++
    Http4sUKOBv310Transactions.resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sUKOBv310AccountAccess.routes(req)
      .orElse(Http4sUKOBv310Accounts.routes(req))
      .orElse(Http4sUKOBv310Balances.routes(req))
      .orElse(Http4sUKOBv310Transactions.routes(req))
      .orElse(Http4sUKOBv310Products.routes(req))
      .orElse(Http4sUKOBv310Beneficiaries.routes(req))
      .orElse(Http4sUKOBv310DirectDebits.routes(req))
      .orElse(Http4sUKOBv310Offers.routes(req))
      .orElse(Http4sUKOBv310Partys.routes(req))
      .orElse(Http4sUKOBv310ScheduledPayments.routes(req))
      .orElse(Http4sUKOBv310StandingOrders.routes(req))
      .orElse(Http4sUKOBv310Statements.routes(req))
      .orElse(Http4sUKOBv310DomesticPayments.routes(req))
      .orElse(Http4sUKOBv310DomesticScheduledPayments.routes(req))
      .orElse(Http4sUKOBv310DomesticStandingOrders.routes(req))
      .orElse(Http4sUKOBv310FilePayments.routes(req))
      .orElse(Http4sUKOBv310FundsConfirmations.routes(req))
      .orElse(Http4sUKOBv310InternationalPayments.routes(req))
      .orElse(Http4sUKOBv310InternationalScheduledPayments.routes(req))
      .orElse(Http4sUKOBv310InternationalStandingOrders.routes(req))
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
}
