package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
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

  /**
   * Build the base HTTP4S routes with priority-based routing
   */
  private def baseServices: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
    corsHandler.run(req)
      .orElse(AppsPage.routes.run(req))
      .orElse(StatusPage.routes.run(req))
      .orElse(code.api.v5_0_0.Http4s500.wrappedRoutesV500Services.run(req))
      .orElse(code.api.v7_0_0.Http4s700.wrappedRoutesV700Services.run(req))
      .orElse(code.api.berlin.group.v2.Http4sBGv2.wrappedRoutes.run(req))
      .orElse(code.api.v1_4_0.Http4s140.wrappedRoutesV140Services.run(req))
      .orElse(code.api.v1_3_0.Http4s130.wrappedRoutesV130Services.run(req))
      .orElse(code.api.v1_2_1.Http4s121.wrappedRoutesV121Services.run(req))
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
