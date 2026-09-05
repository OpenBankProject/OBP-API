package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil
import code.api.util.http4s.Http4sRequestAttributes
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.http4s._
import org.typelevel.ci.CIString

/**
 * Shared HTTP4S Application Builder
 *
 * Provides the httpApp used by both production (Http4sServer) and test (Http4sTestServer).
 * All API versions (v1.2.1–v7.0.0, BG, UK OB) are served by native http4s handlers.
 * Unmatched requests receive a JSON 404.
 */
object Http4sApp extends MdcLoggable {
  
  type HttpF[A] = OptionT[IO, A]

  // Handles all OPTIONS (CORS preflight) requests by short-circuiting at the head of the
  // Kleisli chain, before any version routes run. Returns 204 with the standard CORS headers
  // so a browser preflight never pays the cost of entering the per-version handlers.
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
  private val v510Routes: HttpRoutes[IO] = gate(ApiVersion.v5_1_0, code.api.v5_1_0.Http4s510.wrappedRoutesV510Services)
  private val v600Routes: HttpRoutes[IO] = gate(ApiVersion.v6_0_0, code.api.v6_0_0.Http4s600.wrappedRoutesV600Services)
  private val v700Routes: HttpRoutes[IO] = gate(ApiVersion.v7_0_0, code.api.v7_0_0.Http4s700.wrappedRoutesV700Services)
  // DynamicEntity runtime CRUD (/obp/dynamic-entity/*) — native http4s, replaces the Lift
  // OBPAPIDynamicEntity dispatch.
  private val dynamicEntityRoutes: HttpRoutes[IO] = gate(ApiVersion.`dynamic-entity`, code.api.dynamic.entity.Http4sDynamicEntity.wrappedRoutesDynamicEntity)
  // DynamicEndpoint dispatch (/obp/dynamic-endpoint/*) — fully-native http4s: proxy (DynamicReq)
  // + runtime-compiled resource docs, no Lift dispatch. Replaces the LiftRules.statelessDispatch
  // registration. Must sit AHEAD of the Lift bridge (the bridge no longer carries dynamic-endpoint).
  // DynamicEndpoint dispatch (/obp/dynamic-endpoint/*) — proxy (DynamicReq) + runtime-compiled
  // resource docs / practise. Runs the OBPAPIDynamicEndpoint.routes in-process via an adapter,
  // replacing the former LiftRules.statelessDispatch registration.
  private val dynamicEndpointRoutes: HttpRoutes[IO] = gate(ApiVersion.`dynamic-endpoint`, code.api.dynamic.endpoint.Http4sDynamicEndpoint.wrappedRoutesDynamicEndpoint)
  // UK Open Banking (non-/obp prefixes /open-banking/v2.0 and /open-banking/v3.1) — native
  // http4s, replaces the classpath-scanned Lift ScannedApis. All endpoints (v2.0: 5, v3.1: ~67)
  // are migrated to http4s.
  private val ukV20Routes: HttpRoutes[IO] = gate(ApiVersion.ukOpenBankingV20, code.api.UKOpenBanking.v2_0_0.Http4sUKOBv200.wrappedRoutes)
  private val ukV31Routes: HttpRoutes[IO] = gate(ApiVersion.ukOpenBankingV31, code.api.UKOpenBanking.v3_1_0.Http4sUKOBv310.wrappedRoutes)
  private val ukV401Routes: HttpRoutes[IO] = gate(ApiVersion.ukOpenBankingV401, code.api.UKOpenBanking.v4_0_1.Http4sUKOBv401.wrappedRoutes)

  // JSON 404 for all unmatched paths — terminal entry in baseServices.
  private val notFoundCatchAll: HttpRoutes[IO] = HttpRoutes[IO] { req =>
    val contentType = req.headers.get(CIString("Content-Type")).map(_.head.value).getOrElse("")
    val msg = s"${code.api.util.ErrorMessages.InvalidUri}Current Url is (${req.uri}), Current Content-Type Header is ($contentType)"
    val escaped = msg.replace("\\", "\\\\").replace("\"", "\\\"")
    val body = s"""{"code":404,"message":"$escaped"}"""
    OptionT.liftF(IO.pure(
      Response[IO](status = Status.NotFound)
        .withEntity(body.getBytes("UTF-8"))
        .withHeaders(Headers(Header.Raw(CIString("Content-Type"), "application/json; charset=utf-8")))
    ))
  }

  /**
   * Build the base HTTP4S routes with priority-based routing.
   *
   * Body caching: http4s request bodies are single-shot streams. The first version's
   * `ResourceDocMiddleware.fromRequest` consumes the body to build CallContext; any later
   * bridge hop (v400→v310→v300→…→v210) that re-reads `req.bodyText` gets an empty stream
   * and the eventual handler returns 500 because JSON parsing fails. We pre-read the body
   * here and stash it in `cachedBodyKey`, so every downstream `fromRequest` reads from the
   * attribute instead of the (now-drained) stream. GETs/DELETEs/HEADs/OPTIONS skip this.
   */
  private val noBodyMethods: Set[Method] = Set(Method.GET, Method.DELETE, Method.HEAD, Method.OPTIONS)

  private def cacheBodyOnce(req: Request[IO]): IO[Request[IO]] = {
    if (req.attributes.lookup(Http4sRequestAttributes.cachedBodyKey).isDefined) IO.pure(req)
    else if (noBodyMethods.contains(req.method)) IO.pure(req.withAttribute(Http4sRequestAttributes.cachedBodyKey, Option.empty[String]))
    else req.body.compile.to(Array).map { bytes =>
      val cached: Option[String] = if (bytes.isEmpty) None else Some(new String(bytes, "UTF-8"))
      // Replay the bytes on every subsequent stream read so any downstream cascade-bridge
      // hop and any handler that still reads req.body sees the same payload. fs2.Stream.emits
      // is pure — re-evaluating it yields a fresh stream of the same bytes.
      req
        .withBodyStream(fs2.Stream.emits(bytes).covary[IO])
        .withAttribute(Http4sRequestAttributes.cachedBodyKey, cached)
    }
  }

  private def baseServices: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { (req: Request[IO]) =>
    OptionT.liftF(cacheBodyOnce(req)).flatMap { req =>
      corsHandler.run(req)
        .orElse(AppsPage.routes.run(req))
        .orElse(StatusPage.routes.run(req))
        .orElse(Http4sResourceDocs.routes.run(req))
        .orElse(v700Routes.run(req))
        .orElse(v600Routes.run(req))
        .orElse(v510Routes.run(req))
        .orElse(v500Routes.run(req))
        .orElse(code.api.berlin.group.v2.Http4sBGv2.wrappedRoutes.run(req))
        .orElse(ukV20Routes.run(req))
        .orElse(ukV31Routes.run(req))
        .orElse(ukV401Routes.run(req))
        .orElse(code.api.berlin.group.v1_3.Http4sBGv13.wrappedRoutes.run(req))
        .orElse(code.api.berlin.group.v1_3.Http4sBGv13Alias.wrappedRoutes.run(req))
        .orElse(v400Routes.run(req))
        .orElse(v310Routes.run(req))
        .orElse(v300Routes.run(req))
        .orElse(v220Routes.run(req))
        .orElse(v210Routes.run(req))
        .orElse(v200Routes.run(req))
        .orElse(v140Routes.run(req))
        .orElse(v130Routes.run(req))
        .orElse(v121Routes.run(req))
        .orElse(dynamicEntityRoutes.run(req))
        .orElse(dynamicEndpointRoutes.run(req))
        .orElse(code.api.DirectLoginRoutes.routes.run(req))
        .orElse(code.api.SIWERoutes.routes.run(req))
        .orElse(code.api.AliveCheckRoutes.routes.run(req))
        .orElse(notFoundCatchAll.run(req))
    }
  }

  // RFC 7230 §3.3: a server MUST NOT send a message body in a response to a HEAD request.
  // Ember does not automatically strip bodies for explicitly-defined HEAD routes, so we strip
  // here at the outermost layer to keep TCP connections clean for the OkHttp3 test client.
  private def stripBodyForHead(req: Request[IO], resp: Response[IO]): Response[IO] =
    if (req.method == Method.HEAD)
      Response[IO](status = resp.status, httpVersion = resp.httpVersion, headers = resp.headers)
    else resp

  def httpApp: HttpApp[IO] = {
    val app = baseServices.orNotFound
    Kleisli { (rawReq: Request[IO]) =>
      // Establish who is calling before anything reads PSD2-CERT: canonicalise the header into the
      // one form the rest of OBP compares, then decide whether the TLS peer is that caller or a
      // trusted forwarder naming it. Unconditional on purpose — the deployment where the answer is
      // least obvious, a proxy forwarding the header over a hop with no client certificate, enables
      // no TLS middleware at all, so neither step can live in Http4sServer's mtls.enabled branch.
      val req = CallerCertificate.resolveCaller(Psd2CertIngress.canonicalize(rawReq))
      app.run(req)
        .map(resp => stripBodyForHead(req, Http4sStandardHeaders(req, resp)))
        .handleErrorWith { e =>
          logger.error(s"[Http4sApp] Uncaught exception: ${req.method} ${req.uri} - ${e.getMessage}", e)
          val errMsg = Option(e.getMessage).getOrElse("Internal Server Error")
            .replace("\\", "\\\\").replace("\"", "\\\"")
          val body = s"""{"code":500,"message":"$errMsg"}"""
          IO.pure(stripBodyForHead(req, Http4sStandardHeaders(req,
            Response[IO](status = Status.InternalServerError)
              .withEntity(body.getBytes("UTF-8"))
              .withHeaders(Headers(Header.Raw(CIString("Content-Type"), "application/json; charset=utf-8"))))))
        }
    }
  }
}
