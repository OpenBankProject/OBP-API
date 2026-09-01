package code.api.berlin.group.v2

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ScannedApis
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ScannedApiVersion
import org.http4s._

import scala.collection.mutable.ArrayBuffer

object Http4sBGv2 extends MdcLoggable with ScannedApis {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion2

  // ScannedApis discovery marker: makes BGv2 convention-driven like the other Berlin Group /
  // UK Open Banking standards, so ResourceDocRegistry picks it up without a hand-maintained entry.
  override val apiVersion: ScannedApiVersion = implementedInApiVersion

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sBGv2AIS.resourceDocs ++
    Http4sBGv2PIS.resourceDocs ++
    Http4sBGv2PIIS.resourceDocs

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sBGv2AIS.routes(req)
      .orElse(Http4sBGv2PIS.routes(req))
      .orElse(Http4sBGv2PIIS.routes(req))
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
}
