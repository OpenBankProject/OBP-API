/**
Open Bank Project - API
Copyright (C) 2011-2025, TESOBE GmbH

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
TESOBE GmbH
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.dynamic.endpoint

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil
import code.api.util.ErrorMessages.{InvalidUri, UnknownError}
import code.api.util.http4s.Http4sLiftWebBridge
import code.api.{APIFailure, JsonResponseException}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiShortVersions, ApiStandards}
import net.liftweb.common.{Empty, Failure, Full, ParamFailure}
import net.liftweb.http.{LiftResponse, LiftRules, Req, S}
import org.http4s.{HttpRoutes, Request, Response}

/**
 * Native http4s entry point for the OBP dynamic-endpoint dispatch (under /obp/dynamic-endpoint/).
 *
 * Replaces the Lift `LiftRules.statelessDispatch` registration of [[OBPAPIDynamicEndpoint]]
 * (see APIUtil.enableVersionIfAllowed, now commented for `dynamic-endpoint`). It covers BOTH
 * runtime pieces that OBPAPIDynamicEndpoint.routes carries:
 *
 *   - Piece B (proxy): `ImplementationsDynamicEndpoint.dynamicEndpoint`, matched by
 *     `DynamicEndpointHelper.DynamicReq` and proxied to a backend connector / obp_mock.
 *   - Piece C (runtime-compiled): `DynamicEndpoints.dynamicEndpoint`, serving the
 *     practise / dynamic-resource-doc endpoints compiled from user Scala via
 *     `DynamicUtil.compileScalaCode[OBPEndpoint]`.
 *
 * Why an in-process Lift adapter (not a native rewrite):
 *   - Piece C's compiled artifact has its type hard-wired to Lift
 *     (`PartialFunction[Req, CallContext => Box[JsonResponse]]`, generated code imports
 *     `net.liftweb.http.{JsonResponse, Req}`), so it can only be RUN, never natively rewritten.
 *   - dynamic-endpoint already ran through the http4s -> Lift bridge today (it was on
 *     statelessDispatch, which the bridge iterates), i.e. the Req was already built by
 *     `Http4sLiftWebBridge.buildLiftReq`. This migration does NOT change Req construction,
 *     body buffering, or the (auto-commit) transaction behaviour — it only relocates the
 *     `collectFirst` from the bridge's global statelessDispatch list into this dedicated,
 *     dynamic-endpoint-only service positioned ahead of the bridge.
 *
 * Mechanics (a faithful, narrowed copy of `Http4sLiftWebBridge.runLiftDispatch`):
 *   1. Buffer the body and build a Lift `Req` with `buildLiftReq` (full uri `/obp/dynamic-endpoint/...`
 *      so `DynamicReq`'s prefix gate passes).
 *   2. Inside `S.init` (required: failIfBadAuthorizationHeader reads `S.request`, and Lift
 *      `Req.body`/`json`/`testResponse_?` resolve only in that scope), `collectFirst` over the
 *      SAME wrapped form Lift registered — `routes.map(apiPrefix andThen buildOAuthHandler)` — and run it.
 *   3. Reduce the `Box[LiftResponse]` exactly as runLiftDispatch does (Full / ParamFailure /
 *      Failure / Empty), catching `JsonResponseException` (force-error / json-schema / auth
 *      interceptors) and Lift `ContinuationException` (async).
 *   4. No match -> `OptionT.none` so the request falls through the Http4sApp chain (eventually
 *      the bridge produces the final 404, just as before).
 *
 * No `withBusinessDBTransaction` wrap: the bridge path that served dynamic-endpoint until now did
 * not wrap either (writes ran on auto-commit connections), so omitting it preserves behaviour.
 */
object Http4sDynamicEndpoint extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  private val apiStandard = ApiStandards.obp.toString
  private val apiVersionString = ApiShortVersions.`dynamic-endpoint`.toString // "dynamic-endpoint"

  /**
   * The exact wrapped form Lift held in statelessDispatch for dynamic-endpoint:
   * `routes.map(endpoint => oauthServe(apiPrefix{endpoint}, None))`. `oauthServe` registers,
   * `buildOAuthHandler` returns the identical wrapped PF (failIfBadAuthorizationHeader { failIfBadJSON }
   * + endpoint metric) without registering, so we can apply it in-process. Built once; the
   * per-request DB lookups happen inside each route's `isDefinedAt`/`apply`
   * (`DynamicReq.unapply` / `DynamicEndpoints.findEndpoint`), exactly as before.
   */
  private lazy val wrappedRoutes: List[PartialFunction[Req, () => net.liftweb.common.Box[LiftResponse]]] =
    OBPAPIDynamicEndpoint.routes.map(route =>
      OBPAPIDynamicEndpoint.buildOAuthHandler(OBPAPIDynamicEndpoint.apiPrefix(route), None))

  /** Reduce a handler's `Box[LiftResponse]` to a `LiftResponse`, mirroring runLiftDispatch. */
  private def boxToLiftResponse(box: net.liftweb.common.Box[LiftResponse], liftReq: Req): LiftResponse =
    box match {
      case Full(resp) => resp
      case ParamFailure(_, _, _, apiFailure: APIFailure) =>
        APIUtil.errorJsonResponse(apiFailure.msg, apiFailure.responseCode)
      case Failure(msg, _, _) =>
        APIUtil.errorJsonResponse(msg)
      case Empty =>
        val contentType = liftReq.request.headers("Content-Type").headOption.getOrElse("")
        APIUtil.errorJsonResponse(
          s"${InvalidUri}Current Url is (${liftReq.request.uri}), Current Content-Type Header is ($contentType)", 404)
    }

  private def dispatch(req: Request[IO]): OptionT[IO, Response[IO]] = OptionT {
    val io: IO[Option[LiftResponse]] = for {
      bodyBytes <- req.body.compile.to(Array)
      liftReq = Http4sLiftWebBridge.buildLiftReq(req, bodyBytes)
      liftRespOpt <- IO {
        val session = LiftRules.statelessSession.vend.apply(liftReq)
        S.init(Full(liftReq), session) {
          try {
            // collectFirst's guard runs each route's isDefinedAt (per-request DB lookup);
            // pf(liftReq) eagerly runs failIfBadAuthorizationHeader/failIfBadJSON, so a
            // JsonResponseException (auth / interceptor) can surface here — hence the try wraps both.
            wrappedRoutes.collectFirst { case pf if pf.isDefinedAt(liftReq) => pf(liftReq) } match {
              case None      => Option.empty[LiftResponse]
              case Some(run) => Some(boxToLiftResponse(run(), liftReq))
            }
          } catch {
            case JsonResponseException(jsonResponse) => Some(jsonResponse)
            case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
              Some(Http4sLiftWebBridge.resolveContinuation(e))
          }
        }
      }
    } yield liftRespOpt

    io.flatMap {
      case None     => IO.pure(Option.empty[Response[IO]])
      case Some(lr) => Http4sLiftWebBridge.liftResponseToHttp4s(lr).map(Some(_))
    }.handleErrorWith { e =>
      // A matched dynamic-endpoint handler threw an unexpected (non-JsonResponse) exception.
      // The Lift bridge converted such cases to a 500; do the same here so it does not escape
      // as an unhandled IO failure. (No fall-through: a handler had claimed the request.)
      logger.error(s"[Http4sDynamicEndpoint] uncaught exception dispatching ${req.method} ${req.uri.renderString}: ${e.getMessage}", e)
      Http4sLiftWebBridge.liftResponseToHttp4s(APIUtil.errorJsonResponse(s"$UnknownError ${e.getMessage}", 500)).map(Some(_))
    }
  }

  /** Entry point wired into Http4sApp.baseServices (before the Lift bridge). */
  lazy val wrappedRoutesDynamicEndpoint: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { (req: Request[IO]) =>
      req.uri.path.segments.map(_.encoded).toList match {
        case standard :: version :: _ if standard == apiStandard && version == apiVersionString =>
          dispatch(req)
        case _ =>
          OptionT.none[IO, Response[IO]]
      }
    }
}
