package code.api.v6_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages._
import code.api.util.http4s.ResourceDocMiddleware
import code.api.v5_1_0.{Http4s510, JSONFactory510}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.json.{Extraction, Formats}
import net.liftweb.json.JsonAST.prettyRender
import org.http4s.{HttpRoutes, Request, Response, Uri}
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer

/**
 * v6.0.0 http4s endpoints — Phase 1 in progress.
 *
 * Wire-in into `Http4sApp.baseServices` is performed alongside this object.
 * The v600→v510 bridge (`v600ToV510Bridge`) is intentionally NOT appended to
 * `allRoutes`: unmigrated v6 paths must fall through the http4s chain to the
 * Lift fallback, which still serves the v6 Lift handlers. Adding the bridge
 * would let v6 *overrides* be hijacked into v5.1 handlers (CLAUDE.md →
 * "Bridge-cascade hijack"). The bridge val is kept here so it can be enabled
 * later if the team decides to short-circuit Lift for v6 originals.
 */
object Http4s600 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0
  val versionStatus: String = ApiVersionStatus.BLEEDING_EDGE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations6_0_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // Route: GET /obp/v6.0.0/ and GET /obp/v6.0.0/root
    // Mirrors v6 Lift root — both bare prefix and /root return the same
    // info JSON. Reuses JSONFactory510.getApiInfoJSON because v6's API-info
    // shape is unchanged from v5.1.
    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> `prefixPath` =>
        Ok(convertAnyToJsonString(
          JSONFactory510.getApiInfoJSON(implementedInApiVersion, versionStatus)
        ))
      case GET -> `prefixPath` / "root" =>
        Ok(convertAnyToJsonString(
          JSONFactory510.getApiInfoJSON(implementedInApiVersion, versionStatus)
        ))
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Hosted at information
        |* Energy source information
        |* Git Commit""".stripMargin,
      EmptyBody,
      apiInfoJson400,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(root)
    )

    val allRoutes: HttpRoutes[IO] =
      root

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)

    // ─── path-rewriting bridge: /obp/v6.0.0/… → /obp/v5.1.0/… ─────────────
    // NOT appended to allRoutes — see object-level scaladoc.
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
