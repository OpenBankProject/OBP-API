package code.api

import org.json4s._
import cats.effect.IO
import code.api.util.{CallContext, CustomJsonFormats}
import code.api.util.http4s.{Http4sCallContextBuilder, Http4sRequestAttributes}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.v6_0_0.JSONFactory600
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Empty
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.concurrent.Future

/**
 * Native http4s route for the non-versioned DirectLogin endpoint.
 *
 * Handles `POST /my/logins/direct` (no `/obp/vX.Y.Z` prefix). Version-prefixed
 * paths are served by per-version files (e.g. `Http4s600.directLoginEndpoint`).
 *
 * Adding this route lets us retire `LiftRules.statelessDispatch.append(DirectLogin)`
 * in `Boot.scala` — it was the last consumer of the bare path on the Lift bridge.
 */
object DirectLoginRoutes {

  private implicit val formats: Formats = CustomJsonFormats.formats

  /**
   * DirectLogin header parser — mirrors `DirectLogin.getAllParameters` but reads
   * from `CallContext.requestHeaders` (populated by `Http4sCallContextBuilder.fromRequest`)
   * instead of Lift's thread-local `S.request`.
   */
  private def parseDirectLoginParams(cc: CallContext): Map[String, String] = {
    def find(name: String): Option[String] = cc.requestHeaders
      .find(_.name.equalsIgnoreCase(name))
      .flatMap(_.values.headOption)
    val directLoginHeader = find("DirectLogin")
    val authHeader = find("Authorization")
    val raw = directLoginHeader
      .orElse(authHeader.filter(h => h.startsWith("DirectLogin") || h.contains("DirectLogin")))
      .getOrElse("")
    val cleaned = raw.stripPrefix("DirectLogin").split(",").map(_.trim).toList
    val keys = Set("consumer_key", "token", "username", "password")
    cleaned.flatMap { entry =>
      if (entry.contains("=")) {
        val s = entry.split("=", 2)
        val v = s(1).replaceAll("^\"|\"$", "")
        if (keys.contains(s(0)) && v.nonEmpty) Some(s(0) -> v) else None
      } else None
    }.toMap
  }

  private val directLoginAllowed: Boolean =
    code.api.util.APIUtil.getPropsAsBoolValue("allow_direct_login", true)

  val routes: HttpRoutes[IO] = if (!directLoginAllowed) HttpRoutes.empty[IO] else HttpRoutes.of[IO] {
    case req @ POST -> Root / "my" / "logins" / "direct" =>
      Http4sCallContextBuilder.fromRequest(req, apiVersion = "").flatMap { cc =>
        val reqWithCC = req.withAttribute(Http4sRequestAttributes.callContextKey, cc)
        EndpointHelpers.executeFutureCreated(reqWithCC) {
          val parsed = parseDirectLoginParams(cc)
          val params =
            if (parsed.isEmpty) Map("error" -> code.api.util.ErrorMessages.MissingDirectLoginHeader)
            else parsed
          for {
            triple <- DirectLogin.validatorFutureWithParams("authorizationToken", "POST", params)
            (httpCode, message, dlParams) = triple
            tokenTriple = DirectLogin.createTokenCommonPart(httpCode, message, dlParams)
            _ <- Future(DirectLogin.grantEntitlementsToUseDynamicEndpointsInSpacesInDirectLogin(tokenTriple._3))
          } yield {
            if (tokenTriple._1 == 200) JSONFactory600.createTokenJSON(tokenTriple._2)
            else code.api.util.APIUtil.unboxFullOrFail(Empty, None, tokenTriple._2, tokenTriple._1)
          }
        }
      }
  }
}
