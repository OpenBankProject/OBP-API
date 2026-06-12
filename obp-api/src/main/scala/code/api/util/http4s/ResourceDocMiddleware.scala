package code.api.util.http4s

import org.json4s._
import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
import code.api.Constant
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, ApiRole, CallContext, NewStyle}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiShortVersions
import net.liftweb.common.{Box, Empty, Failure, Full}
import org.http4s._
import org.http4s.headers.`Content-Type`

import java.sql.Connection
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * ResourceDoc-driven validation middleware for http4s.
 *
 * This middleware wraps http4s routes with automatic validation based on ResourceDoc metadata.
 * Validation is performed in a specific order to ensure security and proper error responses.
 *
 * VALIDATION ORDER:
 * 1. Authentication - Check if user is authenticated (if required by ResourceDoc)
 * 2. Authorization - Verify user has required roles/entitlements
 * 3. Bank validation - Validate BANK_ID path parameter (if present)
 * 4. Account validation - Validate ACCOUNT_ID path parameter (if present)
 * 5. View validation - Validate VIEW_ID and check user access (if present)
 * 6. Counterparty validation - Validate COUNTERPARTY_ID (if present)
 *
 * Validated entities are stored in CallContext fields for use in endpoint handlers.
 */
object ResourceDocMiddleware extends MdcLoggable {

  /** Type alias for http4s OptionT route effect */
  type HttpF[A] = OptionT[IO, A]

  // Same prop as FutureUtil.defaultTimeout — one setting controls both v6 and v7.
  // Default 55 s.  Override with long_endpoint_timeout in props.
  private def endpointTimeoutMs: Long = Constant.longEndpointTimeoutInMillis

  /** Type alias for validation effect using EitherT */
  type Validation[A] = EitherT[IO, Response[IO], A]

  /** JSON content type for responses */
  private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)

  /**
   * Context that accumulates all validated entities during request processing.
   * This context is passed along the validation chain.
   */
  final case class ValidationContext(
                                      user: Box[User] = Empty,
                                      callContext: CallContext,
                                      bank: Option[Bank] = None,
                                      account: Option[BankAccount] = None,
                                      view: Option[View] = None,
                                      counterparty: Option[CounterpartyTrait] = None
                                    )

  /** Simple DSL for success/failure in the validation chain */
  object DSL {
    def success[A](a: A): Validation[A] = EitherT.rightT(a)
    def failure(resp: Response[IO]): Validation[Nothing] = EitherT.leftT(resp)
  }

  /**
   * Check if ResourceDoc requires authentication.
   *
   * Authentication is required if:
   * - ResourceDoc errorResponseBodies contains $AuthenticatedUserIsRequired
   * - ResourceDoc has roles (roles always require authenticated user)
   */
  private def needsAuthentication(resourceDoc: ResourceDoc): Boolean = {
    resourceDoc.errorResponseBodies.contains($AuthenticatedUserIsRequired) || resourceDoc.roles.exists(_.nonEmpty)
  }

  /**
   * Pure decision: is this ResourceDoc enabled given the endpoint-level Props?
   *
   * Semantics:
   * - if operationId is in disabledOperationIds          → disabled
   * - if enabledOperationIds non-empty and op not in it  → disabled
   * - otherwise                                          → enabled
   *
   * Version-level enable/disable (`api_disabled_versions` / `api_enabled_versions`)
   * is deliberately NOT enforced here. It is applied once at startup by
   * `Http4sApp.gate`, which makes a disabled version's top-level routes empty so
   * direct `/obp/vX.Y.Z/...` traffic falls through to a 404. The middleware does
   * not re-check `implementedInApiVersion` per request because doing so blocks
   * the documented OBP-API behaviour: disabling, say, v2.0.0 retires the
   * `/obp/v2.0.0/...` prefix but the v2.0.0-origin endpoints stay reachable via
   * any newer-version prefix (v3.0.0, v4.0.0, ...) the operator has kept enabled.
   * That cascading surface is intentional — it lets newer versions act as the
   * stable, supported entry point for older endpoints' functionality.
   *
   * Extracted from `apply` so the decision can be unit-tested without standing up
   * a middleware instance or mutating global Props.
   */
  def isEndpointEnabled(
    rd: ResourceDoc,
    disabledOperationIds: Set[String],
    enabledOperationIds: Set[String]
  ): Boolean =
    !disabledOperationIds.contains(rd.operationId) &&
      (enabledOperationIds.isEmpty || enabledOperationIds.contains(rd.operationId))

  /**
   * Middleware factory: wraps HttpRoutes with ResourceDoc validation.
   * Finds the matching ResourceDoc, validates the request, and enriches CallContext.
   */
  def apply(resourceDocs: ArrayBuffer[ResourceDoc]): HttpRoutes[IO] => HttpRoutes[IO] = { routes =>
    // Build the lookup index once per middleware instance (at startup), not per request.
    val resourceDocIndex = ResourceDocMatcher.buildIndex(resourceDocs)
    Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      // Read enable/disable Props per request so runtime changes (e.g. `setPropsValues` in
      // tests or live config reloads) take effect immediately. Cost is a few Lift Props
      // lookups — negligible per request, but lets disabled endpoints be toggled without
      // restarting the server. A disabled endpoint yields OptionT.none so the request
      // falls through to the next handler in the chain (typically the Lift bridge).
      //
      // Version-level enable/disable is NOT re-checked here — that's enforced once at
      // startup by `Http4sApp.gate` for the URL prefix the request arrives at, so that
      // disabling vX.Y.Z retires `/obp/vX.Y.Z/...` but leaves the same endpoints
      // reachable via newer enabled prefixes through the cascade. See
      // `isEndpointEnabled`'s docstring for the rationale.
      val disabledOperationIds = APIUtil.getDisabledEndpointOperationIds().toSet
      val enabledOperationIds = APIUtil.getEnabledEndpointOperationIds().toSet
      def endpointIsEnabled(rd: ResourceDoc): Boolean =
        isEndpointEnabled(rd, disabledOperationIds, enabledOperationIds)
      val apiVersionFromPath = req.uri.path.segments.map(_.encoded).toList match {
        case apiPathZero :: version :: _ if apiPathZero == APIUtil.getPropsValue("apiPathZero", "obp") => version
        case _ => ApiShortVersions.`v7.0.0`.toString
      }
      // Build initial CallContext from request
      OptionT.liftF(Http4sCallContextBuilder.fromRequest(req, apiVersionFromPath)).flatMap { cc =>
        // Cache the body so bridge-cascade hops (v400→v310→v300→…) don't re-read the now-empty stream.
        // First read won the body in fromRequest; we replay it from cc.httpBody onwards.
        val reqWithCachedBody = req.withAttribute(Http4sRequestAttributes.cachedBodyKey, cc.httpBody)
        ResourceDocMatcher.findResourceDoc(req.method.name, req.uri.path, resourceDocIndex) match {
          case Some(resourceDoc) if !endpointIsEnabled(resourceDoc) =>
            // Disabled by api_disabled_endpoints / api_enabled_endpoints / api_disabled_versions /
            // api_enabled_versions. Fall through so the Lift bridge can serve or 404.
            OptionT.none[IO, Response[IO]]
          case Some(resourceDoc) =>
            val ccWithDoc = ResourceDocMatcher.attachToCallContext(cc, resourceDoc)
            val pathParams = ResourceDocMatcher.extractPathParams(req.uri.path, resourceDoc)
            // Validate first (read-only, outside any transaction), then run business logic.
            // GET/HEAD are safe methods — no writes, no transaction needed; they run on
            // auto-commit vendor connections (same as validation).  All other methods
            // (POST/PUT/DELETE/PATCH) wrap routes.run in withBusinessDBTransaction.
            val work: IO[Option[Response[IO]]] =
              validateOnly(reqWithCachedBody, resourceDoc, pathParams, ccWithDoc).flatMap {
                case Left(errorResponse) =>
                  IO.pure(Option(errorResponse))
                case Right(enrichedReq) =>
                  val routeIO =
                    routes.run(enrichedReq)
                      .map(ensureJsonContentType)
                      .getOrElseF(IO.pure(ensureJsonContentType(Response[IO](org.http4s.Status.NotFound))))
                  val executed =
                    if (req.method == Method.GET || req.method == Method.HEAD) routeIO
                    else RequestScopeConnection.withBusinessDBTransaction(routeIO)
                  executed.map(Option(_))
              }
            OptionT(work.timeoutTo(endpointTimeoutMs.millis, endpointTimeoutResponse(req)))

          case None =>
            // No matching ResourceDoc: fallback to original route (NO transaction scope opened).
            // Attach the basic CC so req.callContext works in the inner route even without a doc match.
            // Carry the cached body forward so the bridge cascade can still read it.
            // Best-effort authentication: populate cc.user from request credentials so that
            // withUser/withUserAndBank handlers return 401/403 correctly (e.g. empty path segments
            // that bypass ResourceDocMatcher but still match a route pattern).
            OptionT.liftF(
              IO.fromFuture(IO(APIUtil.anonymousAccess(cc))).map {
                case (Full(user), Some(updatedCC)) => reqWithCachedBody.withAttribute(Http4sRequestAttributes.callContextKey, updatedCC.copy(user = Full(user)))
                case (Full(user), None)            => reqWithCachedBody.withAttribute(Http4sRequestAttributes.callContextKey, cc.copy(user = Full(user)))
                case (_, Some(updatedCC))          => reqWithCachedBody.withAttribute(Http4sRequestAttributes.callContextKey, updatedCC)
                case _                             => reqWithCachedBody.withAttribute(Http4sRequestAttributes.callContextKey, cc)
              }.recover { case _ => reqWithCachedBody.withAttribute(Http4sRequestAttributes.callContextKey, cc) }
            ).flatMap(routes.run)
        }
      }
    }
  }

  /** 504 response emitted when endpointTimeoutMs elapses before the handler completes. */
  private def endpointTimeoutResponse(req: Request[IO]): IO[Option[Response[IO]]] = IO {
    logger.warn(
      s"[ResourceDocMiddleware] Endpoint timeout after ${endpointTimeoutMs}ms: " +
      s"${req.method.name} ${req.uri.renderString}"
    )
    val body = s"""{"message":"Request timeout: backend service did not respond within ${endpointTimeoutMs}ms."}"""
    Some(ensureJsonContentType(
      Response[IO](org.http4s.Status.GatewayTimeout).withEntity(body.getBytes("UTF-8"))
    ))
  }

  // withBusinessDBTransaction moved to RequestScopeConnection.withBusinessDBTransaction
  // so that services which build their own request scope without this middleware
  // (e.g. Http4sDynamicEntity) can reuse the same commit/rollback/close logic.
  // The call site above now delegates to RequestScopeConnection.withBusinessDBTransaction.

  /**
   * Runs the full validation chain (auth → roles → bank → account → view → counterparty)
   * and returns either an error Response or an enriched Request ready for the handler.
   *
   * All steps are read-only and execute outside any DB transaction, so no locks are
   * held during validation.  The caller opens a transaction only after this returns Right.
   */
  private def validateOnly(
                            req: Request[IO],
                            resourceDoc: ResourceDoc,
                            pathParams: Map[String, String],
                            cc: CallContext
                          ): IO[Either[Response[IO], Request[IO]]] = {

    val initialContext = ValidationContext(callContext = cc)

    // Validation order MUST match Lift's wrappedWithAuthCheck (APIUtil.scala:1934-1969):
    //   beforeAuthenticateInterceptors (= validateQueryParams / validateAuthType) first,
    //   then auth → bank → roles → account → view → counterparty
    //     → afterAuthenticateInterceptors (= Force-Error / AuthType / JsonSchema)
    // Per Lift's own comment: "A Bank MUST be checked before Roles. In opposite case
    // we get next paradox: We set non existing bank → We get error that we don't
    // have a proper role → We cannot assign the role to non existing bank."
    // Force-Error / AuthType / JsonSchema interceptors must run LAST so the
    // natural role/bank/account checks short-circuit first when they fail —
    // ForceErrorValidationTest expects the role-check error message (with the
    // doc's role names) when Force-Error: OBP-20006 is sent and the natural
    // role check would also fail.
    val result: Validation[ValidationContext] = for {
      context <- validateDuplicateQueryParams(cc, initialContext)
      context <- authenticate(req, resourceDoc, context)
      context <- validateBank(pathParams, context)
      context <- authorizeRoles(resourceDoc, pathParams, context)
      context <- validateAccount(pathParams, context)
      context <- validateView(pathParams, context)
      context <- validateCounterparty(pathParams, context)
      context <- processForceError(req, resourceDoc, context)
      context <- validateAuthType(resourceDoc, context)
      context <- validateJsonSchema(resourceDoc, context)
    } yield context

    result.value.map {
      case Left(errorResponse) =>
        Left(ensureJsonContentType(errorResponse))
      case Right(validCtx) =>
        Right(req.withAttribute(
          Http4sRequestAttributes.callContextKey,
          validCtx.callContext.copy(
            bank = validCtx.bank,
            bankAccount = validCtx.account,
            view = validCtx.view,
            counterparty = validCtx.counterparty
          )
        ))
    }
  }

  /** Authentication step: verifies user and updates ValidationContext */
  private def authenticate(req: Request[IO], resourceDoc: ResourceDoc, ctx: ValidationContext): Validation[ValidationContext] = {
    val needsAuth = ResourceDocMiddleware.needsAuthentication(resourceDoc)
    logger.debug(s"[ResourceDocMiddleware] needsAuthentication for ${resourceDoc.partialFunctionName}: $needsAuth")

    // Dispatch on authMode the same way Lift's wrappedWithAuthCheck (APIUtil.scala:1783-1788) does:
    //   ApplicationOnly | UserOrApplication → applicationAccess (returns ApplicationNotIdentified
    //     when neither user nor consumer credentials are valid; also accepts consumer-only).
    //   UserOnly | UserAndApplication       → anonymousAccess  (returns AuthenticatedUserIsRequired
    //     when needsAuth is true and user is missing).
    // Without this dispatch, every endpoint behaved as UserOnly — breaking
    // ApplicationNotIdentified semantics for v5.1.0 createConsumer / getConsumers.
    val isAppMode = resourceDoc.authMode match {
      case APIUtil.ApplicationOnly | APIUtil.UserOrApplication => true
      case _ => false
    }
    val io = IO.fromFuture(IO(
      if (isAppMode) APIUtil.applicationAccess(ctx.callContext)
      else APIUtil.anonymousAccess(ctx.callContext)
    ))

    EitherT(
      io.attempt.flatMap {
        // Fully authenticated — happy path.
        case Right((Full(user), Some(updatedCC))) =>
          IO.pure(Right(ctx.copy(user = Full(user), callContext = updatedCC)))
        case Right((Full(user), None)) =>
          IO.pure(Right(ctx.copy(user = Full(user))))
        // Empty box — no valid credentials provided, and auth is required.
        // For UserOrApplication / ApplicationOnly: applicationAccess already returned
        // successfully because the consumer is valid (just no user). Pass through.
        case Right((_, optCC)) if needsAuth && !isAppMode =>
          val cc2 = optCC.getOrElse(ctx.callContext)
          ErrorResponseConverter.createErrorResponse(401, $AuthenticatedUserIsRequired, cc2).map(Left(_))
        // Anonymous endpoint — pass any box user through unchanged.
        case Right((boxUser, Some(updatedCC))) =>
          IO.pure(Right(ctx.copy(user = boxUser, callContext = updatedCC)))
        case Right((boxUser, None)) =>
          IO.pure(Right(ctx.copy(user = boxUser)))
        case Left(e: APIFailureNewStyle) =>
          ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, ctx.callContext).map(Left(_))
        case Left(e) =>
          // anonymousAccess threw a plain Exception(json_of_APIFailureNewStyle).
          // Parse the JSON to recover the original message and failCode (typically 401).
          // Old Style endpoints (v1.x, v2.0.0) keep 400 to match Lift Old Style behavior.
          // New Style endpoints (v2.1.0+) use the original failCode from the exception.
          val (failMsg, parsedCode) = scala.util.Try {
            implicit val formats = org.json4s.DefaultFormats
            val parsed = com.openbankproject.commons.util.JsonAliases.parse(e.getMessage).extract[APIFailureNewStyle]
            (parsed.failMsg, parsed.failCode)
          }.getOrElse(($AuthenticatedUserIsRequired, 401))
          val oldStyleShortVersions = Set("v1.2.1", "v1.3.0", "v1.4.0", "v2.0.0")
          val versionStr = resourceDoc.implementedInApiVersion.apiShortVersion
          val isOldStyle = oldStyleShortVersions.contains(versionStr)
          val effectiveCode = if (isOldStyle) 400 else parsedCode
          logger.debug(s"[ResourceDocMiddleware.authenticate] version=$versionStr isOldStyle=$isOldStyle parsedCode=$parsedCode effectiveCode=$effectiveCode")
          ErrorResponseConverter.createErrorResponse(effectiveCode, failMsg, ctx.callContext).map(Left(_))
      }
    )
  }

  /** Role authorization step: ensures user has required roles */
  private def authorizeRoles(resourceDoc: ResourceDoc, pathParams: Map[String, String], ctx: ValidationContext): Validation[ValidationContext] = {
    import DSL._

    resourceDoc.roles match {
      case Some(roles) if roles.nonEmpty =>
        ctx.user match {
          case Full(user) =>
            val bankId = pathParams.getOrElse("BANK_ID", "")
            val consumerId = APIUtil.getConsumerPrimaryKey(Some(ctx.callContext))
            // Use handleAccessControlWithAuthMode so authMode = UserOrApplication
            // accepts consumer-scope-only requests (the auth-only handleAccess...
            // function checks user-entitlements + scopes ANDed and rejects pure
            // consumer-scope requests with 403 even under UserOrApplication).
            val ok = APIUtil.handleAccessControlWithAuthMode(bankId, user.userId, consumerId, roles, resourceDoc.authMode)
            if (ok) success(ctx)
            else EitherT[IO, Response[IO], ValidationContext](
              ErrorResponseConverter.createErrorResponse(403, UserHasMissingRoles + roles.mkString(" or "), ctx.callContext)
                .map[Either[Response[IO], ValidationContext]](Left(_))
            )
          case _ =>
            EitherT[IO, Response[IO], ValidationContext](
              ErrorResponseConverter
                .createErrorResponse(401, $AuthenticatedUserIsRequired, ctx.callContext)
                .map[Either[Response[IO], ValidationContext]](resp => Left(resp))
            )
        }
      case _ => success(ctx)
    }
  }

  /**
   * Force-Error / Response-Code header processing.
   *
   * Port of `APIUtil.afterAuthenticateInterceptors`'s force-error case. Lets a
   * caller short-circuit the endpoint and synthesize a specific error response,
   * for testing / contract validation. Off by default; opt-in via the
   * `enable.force_error` prop. When enabled and a `Force-Error` header is present:
   *
   *   - Invalid OBP-error name format → 400 "Force-Error value not correct"
   *   - Non-numeric `Response-Code` header → 400 "Response-Code value not correct"
   *   - Error name not in this ResourceDoc's `errorResponseBodies` → 400
   *     "Invalid Force Error Code"
   *   - Otherwise → look up the matching error message, return it with the
   *     ResourceDoc-implied status (or override from Response-Code).
   *
   * Without this, migrated endpoints quietly ignore the header and the test that
   * asserts on the synthesized response sees a 200/201 (success) or a 500
   * (endpoint side-effect) instead.
   */
  private def processForceError(req: Request[IO], resourceDoc: ResourceDoc, ctx: ValidationContext): Validation[ValidationContext] = {
    import DSL._
    if (!APIUtil.getPropsAsBoolValue("enable.force_error", false)) success(ctx)
    else {
      val headers = req.headers
      val forceError = headers.get(org.typelevel.ci.CIString("Force-Error")).map(_.head.value)
      val responseCodeHeader = headers.get(org.typelevel.ci.CIString("Response-Code")).map(_.head.value)
      forceError match {
        case None => success(ctx)
        case Some(errorName) =>
          val errorNamePrefix = if (errorName.endsWith(":")) errorName else errorName + ":"
          val correlationId = ctx.callContext.correlationId
          val cc = ctx.callContext
          val responseIO: IO[Response[IO]] = {
            if (!code.api.util.ErrorMessages.isValidName(errorName)) {
              ErrorResponseConverter.createErrorResponse(
                400, s"${code.api.util.ErrorMessages.ForceErrorInvalid} Force-Error value not correct: $errorName", cc)
            } else if (responseCodeHeader.exists(it => !org.apache.commons.lang3.StringUtils.isNumeric(it))) {
              ErrorResponseConverter.createErrorResponse(
                400, s"${code.api.util.ErrorMessages.ForceErrorInvalid} Response-Code value not correct: ${responseCodeHeader.orNull}", cc)
            } else if (!resourceDoc.errorResponseBodies.exists(_.startsWith(errorNamePrefix))) {
              ErrorResponseConverter.createErrorResponse(
                400, s"${code.api.util.ErrorMessages.ForceErrorInvalid} Invalid Force Error Code: $errorName", cc)
            } else {
              val errorValue = code.api.util.ErrorMessages.getValueMatches(_.startsWith(errorNamePrefix))
                .getOrElse(throw new RuntimeException(s"force-error code $errorName matched but lookup failed"))
              val statusCode = responseCodeHeader.map(_.toInt).getOrElse(code.api.util.ErrorMessages.getCode(errorValue))
              ErrorResponseConverter.createErrorResponse(statusCode, errorValue, cc)
            }
          }
          EitherT[IO, Response[IO], ValidationContext](responseIO.map[Either[Response[IO], ValidationContext]](Left(_)))
      }
    }
  }

  /**
   * Authentication-type validation. Port of `APIUtil.validateAuthType`. If an
   * operator has registered allowed auth types for this endpoint via
   * `AuthenticationTypeValidationProvider`, reject any request whose authType
   * isn't on the allow-list (anonymous requests skip — they already failed auth
   * if the endpoint required it).
   */
  private def validateAuthType(resourceDoc: ResourceDoc, ctx: ValidationContext): Validation[ValidationContext] = {
    import DSL._
    val cc = ctx.callContext
    val authType = cc.authType
    if (authType == code.api.util.AuthenticationType.Anonymous) success(ctx)
    else {
      val operationId = APIUtil.buildOperationId(resourceDoc.implementedInApiVersion, resourceDoc.partialFunctionName)
      code.authtypevalidation.AuthenticationTypeValidationProvider.validationProvider.vend.getByOperationId(operationId) match {
        case Full(v) if !v.authTypes.contains(authType) =>
          val errorMsg = s"""${code.api.util.ErrorMessages.AuthenticationTypeIllegal} allowed authentication types: ${v.authTypes.mkString("[", ", ", "]")}, current request auth type: $authType"""
          EitherT[IO, Response[IO], ValidationContext](
            ErrorResponseConverter.createErrorResponse(400, errorMsg, cc)
              .map[Either[Response[IO], ValidationContext]](Left(_))
          )
        case _ => success(ctx)
      }
    }
  }

  /**
   * JSON-schema body validation. Port of the json-schema interceptor in
   * `APIUtil.afterAuthenticateInterceptors`. Only fires when an operator has
   * registered a schema for this endpoint via `JsonSchemaValidationProvider`. If
   * the body fails validation, returns 400 with the concatenated schema errors;
   * otherwise the request continues.
   */
  private def validateJsonSchema(resourceDoc: ResourceDoc, ctx: ValidationContext): Validation[ValidationContext] = {
    import DSL._
    val operationId = APIUtil.buildOperationId(resourceDoc.implementedInApiVersion, resourceDoc.partialFunctionName)
    code.util.JsonSchemaUtil.validateRequest(Some(ctx.callContext))(operationId) match {
      case Some(errorMsg) =>
        // Mirror Lift's afterAuthenticateInterceptors prefix so tests asserting on
        // `$InvalidRequestPayload` still pass.
        val message = s"${code.api.util.ErrorMessages.InvalidRequestPayload} $errorMsg"
        EitherT[IO, Response[IO], ValidationContext](
          ErrorResponseConverter.createErrorResponse(400, message, ctx.callContext)
            .map[Either[Response[IO], ValidationContext]](Left(_))
        )
      case None => success(ctx)
    }
  }

  /**
   * Port of `APIUtil.validateQueryParams` (a `beforeAuthenticateInterceptor` in Lift).
   * Rejects requests with duplicate query-parameter names with 400
   * `DuplicateQueryParameters`. Returns a plain OBP `{"message":"..."}` body (not BG
   * format) to match Lift's `createErrorJsonResponse` output — the test asserts on
   * `ErrorMessage.message`, not `ErrorMessagesBG.tppMessages`.
   */
  private def validateDuplicateQueryParams(cc: CallContext, ctx: ValidationContext): Validation[ValidationContext] = {
    import DSL._
    val queryString = if (cc.url.contains("?")) cc.url.split("\\?", 2)(1) else ""
    val paramNames = queryString.split("&").map(s => s.split("=", 2)(0)).filter(_.nonEmpty)
    val hasDuplicates = paramNames.groupBy(identity).exists(_._2.length > 1)
    if (hasDuplicates) {
      import org.json4s.JsonDSL._
      import com.openbankproject.commons.util.JsonAliases.compactRender
      // Match Lift's createErrorJsonResponse: {"code": 400, "message": "OBP-XXXXX: ..."}
      // The test asserts extract[ErrorMessage].message where ErrorMessage(code: Int, message: String).
      val body = compactRender(("code" -> 400) ~ ("message" -> code.api.util.ErrorMessages.DuplicateQueryParameters))
      val resp = Response[IO](org.http4s.Status.BadRequest)
        .withEntity(body.getBytes("UTF-8"))
        .withContentType(jsonContentType)
      EitherT.leftT[IO, ValidationContext](resp)
    } else
      success(ctx)
  }

  /** Bank validation: checks BANK_ID and fetches bank */
  private def validateBank(pathParams: Map[String, String], ctx: ValidationContext): Validation[ValidationContext] = {

    pathParams.get("BANK_ID") match {
      case Some(bankId) =>
        EitherT(
          IO.fromFuture(IO(NewStyle.function.getBank(BankId(bankId), Some(ctx.callContext))))
            .attempt.flatMap {
              case Right((bank, Some(updatedCC))) => IO.pure(Right(ctx.copy(bank = Some(bank), callContext = updatedCC)))
              case Right((bank, None))             => IO.pure(Right(ctx.copy(bank = Some(bank))))
              case Left(e: APIFailureNewStyle)     => ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, ctx.callContext).map(Left(_))
              case Left(_)                          => ErrorResponseConverter.createErrorResponse(404, BankNotFound + s": $bankId", ctx.callContext).map(Left(_))
            }
        )
      case None => DSL.success(ctx)
    }
  }

  /** Account validation: checks ACCOUNT_ID and fetches bank account */
  private def validateAccount(pathParams: Map[String, String], ctx: ValidationContext): Validation[ValidationContext] = {

    (pathParams.get("BANK_ID"), pathParams.get("ACCOUNT_ID")) match {
      case (Some(bankId), Some(accountId)) =>
        EitherT(
          IO.fromFuture(IO(NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(ctx.callContext))))
            .attempt.flatMap {
              case Right((acc, Some(updatedCC))) => IO.pure(Right(ctx.copy(account = Some(acc), callContext = updatedCC)))
              case Right((acc, None))            => IO.pure(Right(ctx.copy(account = Some(acc))))
              case Left(e: APIFailureNewStyle)   => ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, ctx.callContext).map(Left(_))
              case Left(_)                        => ErrorResponseConverter.createErrorResponse(404, BankAccountNotFound + s": bankId=$bankId, accountId=$accountId", ctx.callContext).map(Left(_))
            }
        )
      case _ => DSL.success(ctx)
    }
  }

  /** View validation: checks VIEW_ID and user access */
  private def validateView(pathParams: Map[String, String], ctx: ValidationContext): Validation[ValidationContext] = {

    (pathParams.get("BANK_ID"), pathParams.get("ACCOUNT_ID"), pathParams.get("VIEW_ID")) match {
      case (Some(bankId), Some(accountId), Some(viewId)) =>
        EitherT(
          IO.fromFuture(IO(ViewNewStyle.checkViewAccessAndReturnView(ViewId(viewId), BankIdAccountId(BankId(bankId), AccountId(accountId)), ctx.user.toOption, Some(ctx.callContext))))
            .attempt.flatMap {
              case Right(view) => IO.pure(Right(ctx.copy(view = Some(view))))
              case Left(e: APIFailureNewStyle) => ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, ctx.callContext).map(Left(_))
              case Left(_) => ErrorResponseConverter.createErrorResponse(403, UserNoPermissionAccessView + s": viewId=$viewId", ctx.callContext).map(Left(_))
            }
        )
      case _ => DSL.success(ctx)
    }
  }

  /** Counterparty validation: checks COUNTERPARTY_ID and fetches counterparty */
  private def validateCounterparty(pathParams: Map[String, String], ctx: ValidationContext): Validation[ValidationContext] = {

    (pathParams.get("BANK_ID"), pathParams.get("ACCOUNT_ID"), pathParams.get("COUNTERPARTY_ID")) match {
      case (Some(bankId), Some(accountId), Some(counterpartyId)) =>
        EitherT(
          IO.fromFuture(IO(NewStyle.function.getCounterpartyTrait(BankId(bankId), AccountId(accountId), counterpartyId, Some(ctx.callContext))))
            .attempt.flatMap {
              case Right((cp, Some(updatedCC))) => IO.pure(Right(ctx.copy(counterparty = Some(cp), callContext = updatedCC)))
              case Right((cp, None))            => IO.pure(Right(ctx.copy(counterparty = Some(cp))))
              case Left(e: APIFailureNewStyle)  => ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, ctx.callContext).map(Left(_))
              case Left(_)                       => ErrorResponseConverter.createErrorResponse(404, CounterpartyNotFound + s": counterpartyId=$counterpartyId", ctx.callContext).map(Left(_))
            }
        )
      case _ => DSL.success(ctx)
    }
  }

  /** Ensure the response has JSON content type */
  private def ensureJsonContentType(response: Response[IO]): Response[IO] = {
    response.contentType match {
      case Some(contentType) if contentType.mediaType == MediaType.application.json => response
      case _ => response.withContentType(jsonContentType)
    }
  }
}
