package code.api.UKOpenBanking.v2_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.ResourceDocMiddleware
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion
import org.http4s._

import scala.collection.mutable.ArrayBuffer

/**
 * UK Open Banking v2.0 — http4s aggregator (mirror of Berlin Group's Http4sBGv2).
 *
 * Wraps the migrated account-information routes with ResourceDocMiddleware and
 * exposes `wrappedRoutes` for Http4sApp. The two account-scoped endpoints
 * (/accounts/ID/balances, /accounts/ID/transactions) are not migrated here and
 * fall through to the Lift bridge unchanged.
 */
object Http4sUKOBv200 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV20

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sUKOBv200AIS.resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sUKOBv200AIS.routes(req)
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
}
