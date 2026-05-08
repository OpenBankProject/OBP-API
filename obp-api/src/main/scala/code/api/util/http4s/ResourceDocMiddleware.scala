package code.api.util.http4s

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
import code.api.Constant
import code.api.v7_0_0.Http4s700
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, ApiRole, CallContext, NewStyle}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiShortVersions
import com.github.dwickern.macros.NameOf.nameOf
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
   * - Special case: resource-docs endpoint checks resource_docs_requires_role property
   */
  private def needsAuthentication(resourceDoc: ResourceDoc): Boolean = {
    if (resourceDoc.partialFunctionName == nameOf(Http4s700.Implementations7_0_0.getResourceDocsObpV700)) {
      APIUtil.getPropsAsBoolValue("resource_docs_requires_role", false)
    } else {
      resourceDoc.errorResponseBodies.contains($AuthenticatedUserIsRequired) || resourceDoc.roles.exists(_.nonEmpty)
    }
  }

  /**
   * Middleware factory: wraps HttpRoutes with ResourceDoc validation.
   * Finds the matching ResourceDoc, validates the request, and enriches CallContext.
   */
  def apply(resourceDocs: ArrayBuffer[ResourceDoc]): HttpRoutes[IO] => HttpRoutes[IO] = { routes =>
    // Build the lookup index once per middleware instance (at startup), not per request.
    val resourceDocIndex = ResourceDocMatcher.buildIndex(resourceDocs)
    Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      val apiVersionFromPath = req.uri.path.segments.map(_.encoded).toList match {
        case apiPathZero :: version :: _ if apiPathZero == APIUtil.getPropsValue("apiPathZero", "obp") => version
        case _ => ApiShortVersions.`v7.0.0`.toString
      }
      // Build initial CallContext from request
      OptionT.liftF(Http4sCallContextBuilder.fromRequest(req, apiVersionFromPath)).flatMap { cc =>
        ResourceDocMatcher.findResourceDoc(req.method.name, req.uri.path, resourceDocIndex) match {
          case Some(resourceDoc) =>
            val ccWithDoc = ResourceDocMatcher.attachToCallContext(cc, resourceDoc)
            val pathParams = ResourceDocMatcher.extractPathParams(req.uri.path, resourceDoc)
            // Validate first (read-only, outside any transaction), then run business logic.
            // GET/HEAD are safe methods — no writes, no transaction needed; they run on
            // auto-commit vendor connections (same as validation).  All other methods
            // (POST/PUT/DELETE/PATCH) wrap routes.run in withBusinessDBTransaction.
            val work: IO[Option[Response[IO]]] =
              validateOnly(req, resourceDoc, pathParams, ccWithDoc).flatMap {
                case Left(errorResponse) =>
                  IO.pure(Option(errorResponse))
                case Right(enrichedReq) =>
                  val routeIO =
                    routes.run(enrichedReq)
                      .map(ensureJsonContentType)
                      .getOrElseF(IO.pure(ensureJsonContentType(Response[IO](org.http4s.Status.NotFound))))
                  val executed =
                    if (req.method == Method.GET || req.method == Method.HEAD) routeIO
                    else withBusinessDBTransaction(routeIO)
                  executed.map(Option(_))
              }
            OptionT(work.timeoutTo(endpointTimeoutMs.millis, endpointTimeoutResponse(req)))

          case None =>
            // No matching ResourceDoc: fallback to original route (NO transaction scope opened).
            // ResourceDocMatcher.findResourceDoc already logged a WARN with full key/index detail.
            // Any background DB calls triggered by the Lift bridge for this request will use
            // RequestAwareConnectionManager, which now falls back to a fresh vendor connection
            // when the TTL-stale proxy is detected as closed.
            routes.run(req)
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

  /**
   * Activates a lazy request-scoped DB transaction for mutating methods
   * (POST/PUT/DELETE/PATCH).  GET/HEAD bypass this entirely.
   *
   * NO connection is borrowed upfront.  Instead, a once-only acquisition IO is
   * installed in requestLazyAcquire.  The first fromFuture call that actually needs
   * a DB connection triggers the acquisition; endpoints that only call external REST
   * or SOAP connectors never touch the pool at all.
   *
   * Concurrent acquisition (rare — most handlers are sequential for-comprehensions):
   * the inner Deferred serialises callers.  The first fiber to complete it wins;
   * any concurrent loser closes its own connection immediately and shares the winner's
   * proxy.  All fibers use one underlying Connection and one transaction.
   *
   * currentProxy (TTL) is NOT set here.  Every DB call goes through
   * RequestScopeConnection.fromFuture, which atomically sets + submits + clears the
   * TTL within a single IO.defer block on the compute thread.
   *
   * On success (connection was acquired): commit, then close.
   * On error/cancel (connection was acquired): rollback (errors swallowed), then close.
   * If no DB call was made: deferred is never completed → nothing to commit or close.
   */
  private def withBusinessDBTransaction(io: IO[Response[IO]]): IO[Response[IO]] =
    Deferred[IO, (Connection, Connection)].flatMap { deferred =>
      // acquireOnce: idempotent across concurrent callers via the Deferred.
      // The loser of the complete() race discards its own connection and awaits
      // the winner's proxy so all fibers share one transaction.
      val acquireOnce: IO[Connection] = for {
        realConn <- IO.blocking(APIUtil.vendor.HikariDatasource.ds.getConnection())
        _        <- IO.blocking { realConn.setAutoCommit(false) }
        proxy    =  RequestScopeConnection.makeProxy(realConn)
        ok       <- deferred.complete((realConn, proxy))
        _        <- if (!ok) IO.blocking { try { realConn.close() } catch { case _: Exception => () } }
                    else IO.unit
        p        <- deferred.get.map(_._2)
      } yield p

      RequestScopeConnection.requestLazyAcquire.set(Some(acquireOnce)).bracket(_ =>
        io.guaranteeCase { outcome =>
          deferred.tryGet.flatMap {
            case None => IO.unit   // no DB calls — pool unaffected
            case Some((realConn, _)) =>
              RequestScopeConnection.requestProxyLocal.set(None) *>
                (outcome match {
                  case Outcome.Succeeded(_) =>
                    IO.blocking { realConn.commit() }
                  case _ =>
                    IO.blocking { try { realConn.rollback() } catch { case _: Exception => () } }
                }) *>
                IO.blocking { try { realConn.close() } catch { case _: Exception => () } }
          }
        }
      )(_ => RequestScopeConnection.requestLazyAcquire.set(None))
    }

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

    val result: Validation[ValidationContext] = for {
      context <- authenticate(req, resourceDoc, initialContext)
      context <- authorizeRoles(resourceDoc, pathParams, context)
      context <- validateBank(pathParams, context)
      context <- validateAccount(pathParams, context)
      context <- validateView(pathParams, context)
      context <- validateCounterparty(pathParams, context)
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

    // Always call anonymousAccess to get the raw Box[User].
    // authenticatedAccess internally calls fullBoxOrException which converts any
    // non-Full box into a thrown plain Exception(json) with failCode=401, losing
    // the original error (e.g. UsernameHasBeenLocked should produce 400, not 401).
    // anonymousAccess already runs all post-auth checks (locked/deleted user,
    // consumer-disabled, rate-limiting) and returns the Failure box untouched.
    val io = IO.fromFuture(IO(APIUtil.anonymousAccess(ctx.callContext)))

    EitherT(
      io.attempt.flatMap {
        // Fully authenticated — happy path.
        case Right((Full(user), Some(updatedCC))) =>
          IO.pure(Right(ctx.copy(user = Full(user), callContext = updatedCC)))
        case Right((Full(user), None)) =>
          IO.pure(Right(ctx.copy(user = Full(user))))
        // Auth returned a Failure box (e.g. UsernameHasBeenLocked, UserIsDeleted,
        // ConsumerIsDisabled). Old Lift returned 400 for all unadorned Failure boxes.
        case Right((Failure(msg, _, _), optCC)) if needsAuth =>
          val cc2 = optCC.getOrElse(ctx.callContext)
          ErrorResponseConverter.createErrorResponse(400, msg, cc2).map(Left(_))
        // Empty box — no valid credentials provided, and auth is required.
        case Right((_, optCC)) if needsAuth =>
          val cc2 = optCC.getOrElse(ctx.callContext)
          ErrorResponseConverter.createErrorResponse(401, $AuthenticatedUserIsRequired, cc2).map(Left(_))
        // Anonymous endpoint — pass any box user through unchanged.
        case Right((boxUser, Some(updatedCC))) =>
          IO.pure(Right(ctx.copy(user = boxUser, callContext = updatedCC)))
        case Right((boxUser, None)) =>
          IO.pure(Right(ctx.copy(user = boxUser)))
        case Left(e: APIFailureNewStyle) =>
          ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, ctx.callContext).map(Left(_))
        case Left(_) =>
          ErrorResponseConverter.createErrorResponse(401, $AuthenticatedUserIsRequired, ctx.callContext).map(Left(_))
      }
    )
  }

  /** Role authorization step: ensures user has required roles */
  private def authorizeRoles(resourceDoc: ResourceDoc, pathParams: Map[String, String], ctx: ValidationContext): Validation[ValidationContext] = {
    import DSL._

    val rolesToCheck: Option[List[ApiRole]] =
      if (resourceDoc.partialFunctionName == nameOf(Http4s700.Implementations7_0_0.getResourceDocsObpV700) && APIUtil.getPropsAsBoolValue("resource_docs_requires_role", false)) {
        Some(List(ApiRole.canReadResourceDoc))
      } else {
        resourceDoc.roles
      }

    rolesToCheck match {
      case Some(roles) if roles.nonEmpty =>
        ctx.user match {
          case Full(user) =>
            val bankId = pathParams.getOrElse("BANK_ID", "")
            val ok = roles.exists { role =>
              val checkBankId = if (role.requiresBankId) bankId else ""
              APIUtil.hasEntitlement(checkBankId, user.userId, role)
            }
            if (ok) success(ctx)
            else EitherT[IO, Response[IO], ValidationContext](
              ErrorResponseConverter.createErrorResponse(403, UserHasMissingRoles + roles.mkString(", "), ctx.callContext)
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
