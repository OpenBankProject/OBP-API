/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
 * in `Boot.scala` — it was the last Lift registration serving the bare path.
 */
object DirectLoginRoutes {

  private implicit val formats: Formats = CustomJsonFormats.formats

  /**
   * DirectLogin header parser — mirrors `DirectLogin.getAllParameters` but reads
   * from `CallContext.requestHeaders` (populated by `Http4sCallContextBuilder.fromRequest`)
   * instead of Lift's thread-local `S.request`.
   */
  private def parseDirectLoginParams(cc: CallContext): Map[String, String] = {
    def find(name: String): Option[String] = code.api.util.RequestHeadersUtil.find(cc.requestHeaders, name)
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

  /**
   * This checks DirectLogin credentials and issues a token. It is the one implementation behind both the
   * unversioned POST /my/logins/direct and the versioned POST /obp/v6.0.0/my/logins/direct, so the two cannot drift.
   *
   * When allow_direct_login is false it refuses before reading any credentials, so the endpoint neither checks
   * passwords nor stores tokens on an instance where DirectLogin is turned off. The prop is read on each request.
   */
  def issueToken(cc: CallContext): Future[code.api.v6_0_0.TokenJSON] = {
    val directLoginAllowed = code.api.util.APIUtil.getPropsAsBoolValue("allow_direct_login", true)
    for {
      _ <- code.util.Helper.booleanToFuture(code.api.util.ErrorMessages.DirectLoginIsDisabled, failCode = 403, cc = Some(cc)) {
        directLoginAllowed
      }
      // If the parser found nothing usable, fall back to a single "error" key so that
      // validatorFutureWithParams returns the MissingDirectLoginHeader message.
      parsed = parseDirectLoginParams(cc)
      params = if (parsed.isEmpty) Map("error" -> code.api.util.ErrorMessages.MissingDirectLoginHeader) else parsed
      triple <- DirectLogin.validatorFutureWithParams("authorizationToken", "POST", params)
      (httpCode, message, dlParams) = triple
      tokenTriple = DirectLogin.createTokenCommonPart(httpCode, message, dlParams)
      _ <- Future(DirectLogin.grantEntitlementsToUseDynamicEndpointsInSpacesInDirectLogin(tokenTriple._3))
    } yield {
      if (tokenTriple._1 == 200) JSONFactory600.createTokenJSON(tokenTriple._2)
      else code.api.util.APIUtil.unboxFullOrFail(Empty, None, tokenTriple._2, tokenTriple._1)
    }
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "my" / "logins" / "direct" =>
      Http4sCallContextBuilder.fromRequest(req, apiVersion = "").flatMap { cc =>
        val reqWithCC = req.withAttribute(Http4sRequestAttributes.callContextKey, cc)
        EndpointHelpers.executeFutureCreated(reqWithCC) {
          issueToken(cc)
        }
      }
  }
}
