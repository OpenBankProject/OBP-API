package code.api.berlin.group.v2

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.ResourceDocMiddleware
import code.util.Helper.MdcLoggable
import org.http4s._

import scala.collection.mutable.ArrayBuffer

object Http4sBGv2 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion2

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sBGv2AIS.resourceDocs ++
    Http4sBGv2PIS.resourceDocs ++
    Http4sBGv2PIIS.resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sBGv2AIS.routes(req)
      .orElse(Http4sBGv2PIS.routes(req))
      .orElse(Http4sBGv2PIIS.routes(req))
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
}
