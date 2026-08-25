package code.api.UKOpenBanking.v2_0_0

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
 * UK Open Banking v2.0 — http4s aggregator (mirror of Berlin Group's Http4sBGv2).
 *
 * Wraps the migrated account-information routes with ResourceDocMiddleware and
 * exposes `wrappedRoutes` for Http4sApp. All 5 v2.0 endpoints — including the two
 * account-scoped ones (/accounts/ID/balances, /accounts/ID/transactions) — are
 * migrated in Http4sUKOBv200AIS. The Lift ScannedApis aggregator
 * (OBP_UKOpenBanking_200) registers `routes = Nil`, so no UK v2.0 path is served
 * by Lift — nothing falls through to the Lift bridge.
 */
object Http4sUKOBv200 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV20

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sUKOBv200AIS.resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sUKOBv200AIS.routes(req)
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
}
