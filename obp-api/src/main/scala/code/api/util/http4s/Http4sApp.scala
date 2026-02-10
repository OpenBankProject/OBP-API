package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s._

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

  /**
   * Build the base HTTP4S routes with priority-based routing
   */
  private def baseServices: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
    code.api.v5_0_0.Http4s500.wrappedRoutesV500Services.run(req)
      .orElse(code.api.v7_0_0.Http4s700.wrappedRoutesV700Services.run(req))
      .orElse(code.api.berlin.group.v2.Http4sBGv2.wrappedRoutes.run(req))
      .orElse(Http4sLiftWebBridge.routes.run(req))
  }

  /**
   * Build the complete HTTP4S application with standard headers
   */
  def httpApp: HttpApp[IO] = {
    val services: HttpRoutes[IO] = Http4sLiftWebBridge.withStandardHeaders(baseServices)
    services.orNotFound
  }
}
