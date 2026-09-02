package code.api.berlin.group.v1_3

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.util.Helper.MdcLoggable
import org.http4s._

import scala.collection.mutable.ArrayBuffer

/**
 * Native http4s aggregator for Berlin Group v1.3, replacing the Lift
 * `OBP_BERLIN_GROUP_1_3` statelessDispatch registration. Mirrors `Http4sBGv2`.
 *
 * Groups: AIS / PIS / SigningBaskets / PIIS. Added incrementally.
 */
object Http4sBGv13 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion1

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sBGv13AIS.resourceDocs ++
    Http4sBGv13PIS.resourceDocs ++
    Http4sBGv13PIIS.resourceDocs ++
    Http4sBGv13SigningBaskets.resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sBGv13AIS.routes(req)
      .orElse(Http4sBGv13PIS.routes(req))
      .orElse(Http4sBGv13PIIS.routes(req))
      .orElse(Http4sBGv13SigningBaskets.routes(req))
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
}
