package code.api.v6_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.ResourceDocMiddleware
import code.api.v5_1_0.Http4s510
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import org.http4s.{HttpRoutes, Request, Response, Uri}
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer

/**
 * v6.0.0 http4s endpoints — Phase 0 skeleton.
 *
 * Inert: `Http4sApp.baseServices` does NOT yet route through this object.
 * Wait until all 35 v6 overrides (see LIFT_HTTP4S_MIGRATION_V6_AUDIT.md) are
 * migrated and added to `Implementations6_0_0.allRoutes` — wiring this in
 * earlier would let the bridge cascade hijack v6 override requests down to
 * older handlers (CLAUDE.md → "Bridge-cascade hijack").
 *
 * To wire in once overrides are complete:
 *   1. Add `private val v600Routes = gate(ApiVersion.v6_0_0, Http4s600.wrappedRoutesV600Services)`
 *      to Http4sApp.scala.
 *   2. Insert `.orElse(v600Routes.run(req))` into `baseServices` between
 *      `v510Routes` and `v500Routes` (highest non-v7 first).
 *   3. Append `.orElse(Implementations6_0_0.v600ToV510Bridge.run(req))` to
 *      `allRoutes` below so unmigrated v6 endpoints fall through to v5.1.
 */
object Http4s600 {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0
  val versionStatus: String = ApiVersionStatus.BLEEDING_EDGE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations6_0_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // No endpoints migrated yet — Phase 1 will populate this with the 35
    // override endpoints, then the originals by domain bucket.
    val allRoutes: HttpRoutes[IO] = HttpRoutes.empty[IO]

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)

    // ─── path-rewriting bridge: /obp/v6.0.0/… → /obp/v5.1.0/… ─────────────
    // Mirrors the v510ToV500Bridge pattern in Http4s510.scala. Not appended to
    // allRoutes yet — see object-level comment for the wire-in checklist.
    val v600ToV510Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v6.0.0/")) {
        val rewritten = rawPath.replaceFirst("/obp/v6\\.0\\.0/", "/obp/v5.1.0/")
        val newUri = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        Http4s510.wrappedRoutesV510Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  val wrappedRoutesV600Services: HttpRoutes[IO] =
    Implementations6_0_0.allRoutesWithMiddleware
}
