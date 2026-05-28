/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.api.dynamic.endpoint

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil
import code.api.util.ErrorMessages
import code.api.util.http4s.Http4sLiftWebBridge
import code.api.{APIFailure, JsonResponseException}
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.http.{LiftResponse, LiftRules, S}
import org.http4s._

/**
 * Phase-3a Lift→http4s adapter for the dynamic-endpoint data plane.
 *
 * The runtime dispatch for `/obp/dynamic-endpoint/...` is built around a runtime Scala
 * compiler (`DynamicEndpoints.compileScalaCode[OBPEndpoint]`) that emits Lift-typed
 * `OBPEndpoint`s; the compiled user-supplied bodies read directly from `request.body`,
 * `request.json`, `request.path.partPath`. Phase 3a keeps that codegen unchanged and
 * stands a thin http4s shim in front: build a synthetic Lift `Req` from the http4s
 * request, run the standard Lift dispatch ceremony (`S.init` over a stateless session),
 * call the already-fully-wrapped `OBPAPIDynamicEndpoint` (apiPrefix + oauthServe +
 * wrappedWithAuthCheck — URL matching, auth, role and ResourceDoc bookkeeping all live
 * in those wrappings), and convert the resulting `Box[LiftResponse]` back to http4s.
 *
 * Functionally this is a scoped version of `Http4sLiftWebBridge`. Same Lift-Req
 * construction, same response conversion, same async-continuation handling — only the
 * dispatcher is narrowed from `LiftRules.statelessDispatch.toList ++ LiftRules.dispatch.toList`
 * to one specific `RestHelper` object. The win:
 *
 *  - `/obp/dynamic-endpoint/...` is **authoritative** here: requests never fall through
 *    to the generic Lift bridge, so the bridge-traffic audit's `real_work` bucket loses
 *    this prefix entirely (only the open-banking standards remain on the bridge).
 *  - `OBPAPIDynamicEndpoint` stays registered on `LiftRules` (in `APIUtil.scala:2878`)
 *    as a dormant fallback — this http4s service wins by ordering. The Lift registration
 *    is removed in the bridge-removal PR.
 *
 * Phase 3b (rewriting the codegen to emit `HttpRoutes[IO]` and drop the synthetic-`Req`
 * coupling) is a separate, larger workstream; intentionally not done here.
 *
 * The three bridge helpers reused — `Http4sLiftWebBridge.buildLiftReq`,
 * `Http4sLiftWebBridge.liftResponseToHttp4s`, `Http4sLiftWebBridge.resolveContinuation`
 * — had their `private` visibility relaxed for this purpose; their bodies are unchanged.
 */
object Http4sDynamicEndpoint extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  /**
   * Run the dispatch under a Lift `S.init` over a stateless session, then map the
   * result `Box[LiftResponse]` into a `LiftResponse` (mirroring `runLiftDispatch` in
   * `Http4sLiftWebBridge`). `OBPAPIDynamicEndpoint` extends `RestHelper`, which is a
   * `PartialFunction[Req, () => Box[LiftResponse]]` — the already-fully-wrapped shape
   * Lift's REST machinery itself uses, so URL matching, auth, role checks and the
   * `ResourceDoc`/`operationId` plumbing are all already wired up by the same code
   * paths that production has always used.
   *
   * `S.init` is defensive: the compiled `OBPEndpoint` bodies are user-supplied via the
   * create-dynamic-resource-doc admin endpoint, so we can't audit them all for `S.*`
   * usage. The bridge does the same; we just scope it.
   *
   * `ContinuationException` can escape both the handler thunk and (rarely) the dispatch
   * setup itself — Future-based for-comprehensions inside an `OBPEndpoint` body convert
   * to `Box[JsonResponse]` via a continuation-throwing implicit. Mirror the bridge's
   * defensive layering and catch at both depths.
   */
  private def dispatchUnderLift(liftReq: net.liftweb.http.Req): LiftResponse = {
    val session = LiftRules.statelessSession.vend.apply(liftReq)
    S.init(Full(liftReq), session) {
      try {
        if (OBPAPIDynamicEndpoint.isDefinedAt(liftReq)) {
          val thunk: () => Box[LiftResponse] = OBPAPIDynamicEndpoint(liftReq)
          try {
            thunk() match {
              case Full(resp) => resp
              case ParamFailure(_, _, _, apiFailure: APIFailure) =>
                APIUtil.errorJsonResponse(apiFailure.msg, apiFailure.responseCode)
              case Failure(msg, _, _) =>
                APIUtil.errorJsonResponse(msg)
              case Empty =>
                APIUtil.errorJsonResponse(
                  s"${ErrorMessages.InvalidUri}Current Url is (${liftReq.request.uri})", 404)
            }
          } catch {
            case JsonResponseException(jsonResponse) => jsonResponse
            case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
              Http4sLiftWebBridge.resolveContinuation(e)
          }
        } else {
          // /obp/dynamic-endpoint/... path but no swagger-defined or compiled endpoint
          // matches → 404 with the same `InvalidUri` framing the bridge produces, so
          // callers see identical wire behaviour.
          APIUtil.errorJsonResponse(
            s"${ErrorMessages.InvalidUri}Current Url is (${liftReq.request.uri})", 404)
        }
      } catch {
        case JsonResponseException(jsonResponse) => jsonResponse
        case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
          Http4sLiftWebBridge.resolveContinuation(e)
      }
    }
  }

  private def handle(req: Request[IO]): IO[Response[IO]] =
    for {
      bodyBytes  <- req.body.compile.to(Array)
      liftReq     = Http4sLiftWebBridge.buildLiftReq(req, bodyBytes)
      liftResp   <- IO { dispatchUnderLift(liftReq) }
      http4sResp <- Http4sLiftWebBridge.liftResponseToHttp4s(liftResp)
    } yield http4sResp

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
    // Drop empty segments to mirror Lift's `Req.path.partPath` (e.g. trailing slash).
    val segments = req.uri.path.segments.map(_.decoded()).filter(_.nonEmpty).toList
    segments match {
      // Authoritative for the whole prefix — unmatched paths get a 404 *here* and never
      // fall through to `Http4sLiftWebBridge`. That's the whole point: the bridge's
      // audit `real_work` bucket loses `/obp/dynamic-endpoint/...` after this PR.
      case "obp" :: "dynamic-endpoint" :: _ => OptionT.liftF(handle(req))
      case _                                => OptionT.none[IO, Response[IO]]
    }
  }
}
