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

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.dynamic.endpoint.helper.{DynamicEndpointHelper, DynamicEndpoints}
import code.api.util.CustomJsonFormats
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.http4s.{ErrorResponseConverter, Http4sCallContextBuilder, Http4sRequestAttributes}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiShortVersions, ApiStandards}
import net.liftweb.common.{Box, Empty, Full}
import org.json4s.Formats
import org.json4s.JsonAST.{JNothing, JValue}
import org.http4s.{HttpRoutes, Request, Response}

/**
 * Native http4s entry point for the OBP dynamic-endpoint dispatch (under /obp/dynamic-endpoint/).
 *
 * Fully native — no Lift `Req`, `S.init`, `buildLiftReq` or `liftResponseToHttp4s`. Covers BOTH
 * runtime pieces that the former Lift `OBPAPIDynamicEndpoint` dispatch carried:
 *
 *   - Piece B (proxy): requests matched by `DynamicEndpointHelper.DynamicReq.resolveProxyTarget`
 *     (the framework-neutral core of the Lift DynamicReq extractor) and run through the shared
 *     `APIMethodsDynamicEndpoint.proxyHandle` (auth / entitlement / before+after interceptors /
 *     mock-or-connector proxy). The dynamic status code from the connector / obp_mock result is
 *     rendered via `EndpointHelpers.executeFutureWithStatus`. Proxy writes run on auto-commit
 *     (no withBusinessDBTransaction), matching the prior bridge/adapter behaviour.
 *
 *   - Piece C (runtime-compiled): requests matched by `DynamicEndpoints.findEndpoint` to a dynamic
 *     ResourceDoc whose compiled native handler is carried in `ResourceDoc.dynamicHttp4sFunction`
 *     (an `OBPEndpointIO` produced by the native code-generation template — see
 *     `code.api.dynamic.endpoint.helper.DynamicEndpoints.CompiledObjects` / `DynamicCompileEndpoint`).
 *     The doc's auth/validation chain (`ResourceDoc.authCheckIO`, the native mirror of
 *     `wrappedWithAuthCheck`) runs first, then the handler runs inside the dynamic-code security
 *     sandbox (`Sandbox.runInSandboxIO`, applied inside the compiled handler).
 *
 * Piece B is tried first; a non-match falls through to Piece C; a non-match there returns
 * `OptionT.none`, so the request falls through the Http4sApp chain (the Lift bridge produces the
 * final 404, as before).
 */
object Http4sDynamicEndpoint extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  private implicit val formats: Formats = CustomJsonFormats.formats

  private val apiStandard = ApiStandards.obp.toString
  private val apiVersionString = ApiShortVersions.`dynamic-endpoint`.toString // "dynamic-endpoint"

  private def queryParams(req: Request[IO]): Map[String, List[String]] =
    req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }

  /**
   * Native Piece B (proxy) handler. Matches via `DynamicEndpointHelper.DynamicReq.resolveProxyTarget`
   * and runs the shared, framework-neutral `APIMethodsDynamicEndpoint.proxyHandle`. The CallContext
   * is built by `Http4sCallContextBuilder` and attached so `EndpointHelpers.executeFutureWithStatus`
   * can reuse the error conversion + metric; auth / entitlement run inside `proxyHandle`. No match ->
   * `OptionT.none` (fall through to [[pieceC]]).
   *
   * Note: no JSON content-type gate. The Lift `DynamicReq` extractor gated on `testResponse_?`, but
   * that treated a wildcard Accept (and absent Accept) as JSON-acceptable, i.e. it matched the OBP
   * test client's GET requests (wildcard Accept, text/plain Content-Type). Re-implementing the gate
   * as a literal "contains json" check wrongly rejected those GET proxy calls (404). Since the native
   * dispatch has no XML alternative and `resolveProxyTarget` already returns None for any path that
   * is not a registered dynamic-endpoint, the gate is unnecessary, so we just try to resolve.
   */
  private def proxy(req: Request[IO]): OptionT[IO, Response[IO]] =
    OptionT {
      val partPath = req.uri.path.segments.drop(2).map(_.encoded).toList // segments after obp/dynamic-endpoint
      Http4sCallContextBuilder.fromRequest(req, apiVersionString).flatMap { cc0 =>
        val bodyJValue: JValue = cc0.httpBody.filter(_.nonEmpty).map(com.openbankproject.commons.util.JsonAliases.parse).getOrElse(JNothing)
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
   * Native Piece C (runtime-compiled) handler. Locates the matching dynamic ResourceDoc via
   * `DynamicEndpoints.findEndpoint`, builds + enriches the CallContext, runs the doc's native
   * auth/validation chain (`ResourceDoc.authCheckIO`), then runs the compiled native handler
   * (`ResourceDoc.dynamicHttp4sFunction`, which wraps itself in the security sandbox). Auth / role /
   * lookup failures are converted to a response via `ErrorResponseConverter`. No match ->
   * `OptionT.none` (fall through the Http4sApp chain).
   */
  private def pieceC(req: Request[IO]): OptionT[IO, Response[IO]] =
    DynamicEndpoints.findEndpoint(req) match {
      case None => OptionT.none[IO, Response[IO]]
      case Some(doc) =>
        OptionT.liftF {
          Http4sCallContextBuilder.fromRequest(req, apiVersionString).flatMap { cc0 =>
            val cc = cc0.copy(resourceDocument = Some(doc), operationId = Some(doc.operationId))
            val partPath = req.uri.path.segments.drop(2).map(_.encoded).toList
            val bodyJValue: Box[JValue] = cc.httpBody.filter(_.nonEmpty).map(com.openbankproject.commons.util.JsonAliases.parse) match {
              case Some(jv) => Full(jv)
              case None     => Empty
            }
            val io: IO[Response[IO]] = for {
              authedCcOpt <- IO.fromFuture(IO(doc.authCheckIO(partPath, bodyJValue, cc)))
              authedCc    = authedCcOpt.getOrElse(cc)
              resp        <- doc.dynamicHttp4sFunction.get.apply(req)(authedCc)
            } yield resp
            io.handleErrorWith(err => ErrorResponseConverter.toHttp4sResponse(err, cc))
          }
        }
    }

  /** Entry point wired into Http4sApp.baseServices (before the Lift bridge). */
  lazy val wrappedRoutesDynamicEndpoint: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { (req: Request[IO]) =>
      req.uri.path.segments.map(_.encoded).toList match {
        case standard :: version :: _ if standard == apiStandard && version == apiVersionString =>
          // Native Piece B (proxy) first; a non-match falls through to native Piece C (runtime-compiled).
          proxy(req).orElse(pieceC(req))
        case _ =>
          OptionT.none[IO, Response[IO]]
      }
    }
}
