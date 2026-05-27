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
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.util.APIUtil
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{InvalidUri, UnknownError}
import code.api.util.http4s.Http4sLiftWebBridge
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.http4s.{Http4sCallContextBuilder, Http4sRequestAttributes}
import code.api.{APIFailure, JsonResponseException}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiShortVersions, ApiStandards}
import net.liftweb.common.{Empty, Failure, Full, ParamFailure}
import net.liftweb.http.{LiftResponse, LiftRules, Req, S}
import net.liftweb.json.Formats
import net.liftweb.json.JsonAST.{JNothing, JValue}
import org.http4s.{HttpRoutes, Request, Response}
import org.typelevel.ci.CIString

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
 * Stage 1 — Piece B is served NATIVELY by [[proxy]]: the request is matched by
 * `DynamicEndpointHelper.DynamicReq.resolveProxyTarget` (the framework-neutral core of the Lift
 * `DynamicReq` extractor) and run through the shared `APIMethodsDynamicEndpoint.proxyHandle`
 * (auth / entitlement / before+after interceptors / mock-or-connector proxy). No Lift `Req`,
 * `S.init`, `buildLiftReq` or `liftResponseToHttp4s` on this path. The dynamic status code carried
 * by the connector / obp_mock result is rendered via `EndpointHelpers.executeFutureWithStatus`.
 * Proxy writes run on auto-commit (no `withBusinessDBTransaction`), matching the prior bridge/adapter
 * behaviour.
 *
 * Piece C is STILL served by the in-process Lift adapter ([[dispatch]]) because its compiled
 * artifact is hard-wired to Lift (`PartialFunction[Req, CallContext => Box[JsonResponse]]`,
 * generated code imports `net.liftweb.http.{JsonResponse, Req}`) and can only be RUN, not natively
 * rewritten. The native [[proxy]] is tried first; a non-match falls through to [[dispatch]], whose
 * `collectFirst` over `OBPAPIDynamicEndpoint.routes` then serves Piece C. (Stage 2 will redefine the
 * Piece C contract to native http4s and remove the adapter entirely.)
 */
object Http4sDynamicEndpoint extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  private implicit val formats: Formats = CustomJsonFormats.formats

  private val apiStandard = ApiStandards.obp.toString
  private val apiVersionString = ApiShortVersions.`dynamic-endpoint`.toString // "dynamic-endpoint"

  private def queryParams(req: Request[IO]): Map[String, List[String]] =
    req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }

  // Mirror of the Lift DynamicReq gate `testResponse_?`: only treat the request as a dynamic-endpoint
  // proxy candidate when it is JSON (Content-Type or Accept carries json). A non-JSON request returns
  // OptionT.none so it falls through to the Piece C adapter / Http4sApp chain, exactly as before.
  private def isJsonRequest(req: Request[IO]): Boolean = {
    def header(name: String): String = req.headers.get(CIString(name)).map(_.head.value).getOrElse("")
    header("Content-Type").toLowerCase.contains("json") || header("Accept").toLowerCase.contains("json")
  }

  /**
   * Native Piece B (proxy) handler. Matches via `DynamicEndpointHelper.DynamicReq.resolveProxyTarget`
   * (same DB lookup the Lift `DynamicReq.unapply` used) and runs the shared, framework-neutral
   * `APIMethodsDynamicEndpoint.proxyHandle`. The CallContext is built by `Http4sCallContextBuilder`
   * and attached so `EndpointHelpers.executeFutureWithStatus` can reuse the error conversion + metric;
   * auth / entitlement run inside `proxyHandle`. No match -> `OptionT.none` (fall through to [[dispatch]]).
   */
  private def proxy(req: Request[IO]): OptionT[IO, Response[IO]] =
    if (!isJsonRequest(req)) OptionT.none[IO, Response[IO]]
    else OptionT {
      val partPath = req.uri.path.segments.drop(2).map(_.encoded).toList // segments after obp/dynamic-endpoint
      Http4sCallContextBuilder.fromRequest(req, apiVersionString).flatMap { cc0 =>
        val bodyJValue: JValue = cc0.httpBody.filter(_.nonEmpty).map(net.liftweb.json.parse).getOrElse(JNothing)
        DynamicEndpointHelper.DynamicReq.resolveProxyTarget(req.method.name, partPath, queryParams(req), bodyJValue) match {
          case None => IO.pure(Option.empty[Response[IO]])
          case Some((url, json, method, params, pathParams, role, operationId, mockResponse, bankId)) =>
            val reqWithCc = req.withAttribute(Http4sRequestAttributes.callContextKey, cc0)
            EndpointHelpers.executeFutureWithStatus(reqWithCc) {
              APIMethodsDynamicEndpoint.ImplementationsDynamicEndpoint.proxyHandle(
                url, json, method, params, pathParams, role, operationId, mockResponse, bankId, cc0)
            }.map(Some(_))
        }
      }
    }

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
          // Native Piece B (proxy) first; a non-match falls through to the Lift adapter for Piece C.
          proxy(req).orElse(dispatch(req))
        case _ =>
          OptionT.none[IO, Response[IO]]
      }
    }
}
