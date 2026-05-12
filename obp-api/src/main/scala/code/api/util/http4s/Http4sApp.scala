package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.http4s._
import org.typelevel.ci.CIString

/**
 * Shared HTTP4S Application Builder
 * 
 * This object provides the httpApp configuration used by both:
 * - Production server (Http4sServer)
 * - Test server (Http4sTestServer)
 * 
 * This ensures tests run against the exact same routing configuration as production,
 * eliminating code duplication and ensuring we test the real server.
 * 
 * Priority-based routing:
 * 1. v5.0.0 native HTTP4S routes (checked first)
 * 2. v7.0.0 native HTTP4S routes (checked second)
 * 3. Http4sLiftWebBridge (fallback for all other API versions)
 * 4. 404 Not Found (if no handler matches)
 */
object Http4sApp {
  
  type HttpF[A] = OptionT[IO, A]

  // Handles all OPTIONS (CORS preflight) requests before they reach the Lift bridge.
  //
  // Without this, OPTIONS falls through the Kleisli chain to Http4sLiftWebBridge →
  // OBPAPI6_0_0's `this.serve({case OPTIONS => corsResponse})`, which pays full Lift
  // overhead (body buffering, S.init) for every preflight. More critically, when the
  // Lift bridge is eventually removed, CORS would break silently. Headers match the
  // corsResponse defined in v4/v5/v6 Lift endpoints.
  private val corsHandler: HttpRoutes[IO] = HttpRoutes[IO] { req =>
    if (req.method == Method.OPTIONS) {
      OptionT.liftF(
        IO.pure(
          Response[IO](Status.NoContent)
            .putHeaders(
              Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
              Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS, PUT, PATCH, DELETE"),
              Header.Raw(CIString("Access-Control-Allow-Headers"), "*"),
              Header.Raw(CIString("Access-Control-Allow-Credentials"), "true")
            )
        )
      )
    } else {
      OptionT.none
    }
  }

  // Whole-version gates: short-circuit to empty when api_disabled_versions / api_enabled_versions
  // exclude a version, so the entire vN.N.N http4s chain is bypassed without per-request cost.
  // Evaluated once at object init, matching Lift's startup-only evaluation in enableVersionIfAllowed.
  // The per-endpoint disable check still runs inside ResourceDocMiddleware for finer-grained Props
  // (api_disabled_endpoints / api_enabled_endpoints).
  private def gate(version: ScannedApiVersion, routes: HttpRoutes[IO]): HttpRoutes[IO] =
    if (APIUtil.versionIsAllowed(version)) routes else HttpRoutes.empty[IO]

  private val v121Routes: HttpRoutes[IO] = gate(ApiVersion.v1_2_1, code.api.v1_2_1.Http4s121.wrappedRoutesV121Services)
  private val v130Routes: HttpRoutes[IO] = gate(ApiVersion.v1_3_0, code.api.v1_3_0.Http4s130.wrappedRoutesV130Services)
  private val v140Routes: HttpRoutes[IO] = gate(ApiVersion.v1_4_0, code.api.v1_4_0.Http4s140.wrappedRoutesV140Services)
  private val v200Routes: HttpRoutes[IO] = gate(ApiVersion.v2_0_0, code.api.v2_0_0.Http4s200.wrappedRoutesV200Services)
  private val v210Routes: HttpRoutes[IO] = gate(ApiVersion.v2_1_0, code.api.v2_1_0.Http4s210.wrappedRoutesV210Services)
  private val v220Routes: HttpRoutes[IO] = gate(ApiVersion.v2_2_0, code.api.v2_2_0.Http4s220.wrappedRoutesV220Services)
  private val v300Routes: HttpRoutes[IO] = gate(ApiVersion.v3_0_0, code.api.v3_0_0.Http4s300.wrappedRoutesV300Services)
  private val v310Routes: HttpRoutes[IO] = gate(ApiVersion.v3_1_0, code.api.v3_1_0.Http4s310.wrappedRoutesV310Services)
  private val v400Routes: HttpRoutes[IO] = gate(ApiVersion.v4_0_0, code.api.v4_0_0.Http4s400.wrappedRoutesV400Services)
  private val v500Routes: HttpRoutes[IO] = gate(ApiVersion.v5_0_0, code.api.v5_0_0.Http4s500.wrappedRoutesV500Services)
  private val v700Routes: HttpRoutes[IO] = gate(ApiVersion.v7_0_0, code.api.v7_0_0.Http4s700.wrappedRoutesV700Services)

  /**
   * Build the base HTTP4S routes with priority-based routing
   */
  private def baseServices: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
    corsHandler.run(req)
      .orElse(AppsPage.routes.run(req))
      .orElse(StatusPage.routes.run(req))
      .orElse(v500Routes.run(req))
      .orElse(v700Routes.run(req))
      .orElse(code.api.berlin.group.v2.Http4sBGv2.wrappedRoutes.run(req))
      .orElse(v400Routes.run(req))
      .orElse(v310Routes.run(req))
      .orElse(v300Routes.run(req))
      .orElse(v220Routes.run(req))
      .orElse(v210Routes.run(req))
      .orElse(v200Routes.run(req))
      .orElse(v140Routes.run(req))
      .orElse(v130Routes.run(req))
      .orElse(v121Routes.run(req))
      .orElse(Http4sLiftWebBridge.routes.run(req))
  }

  /**
   * Build the complete HTTP4S application with standard headers
   */
  def httpApp: HttpApp[IO] = {
    val services: HttpRoutes[IO] = Http4sLiftWebBridge.withStandardHeaders(baseServices)
    val app = services.orNotFound
    Kleisli { req: Request[IO] =>
      app.run(req).map(resp => Http4sLiftWebBridge.ensureStandardHeaders(req, resp))
    }
  }
}
