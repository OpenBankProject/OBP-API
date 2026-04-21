package code.api.util.http4s

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
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
import net.liftweb.common.{Box, Empty, Full}
import org.http4s._
import org.http4s.headers.`Content-Type`

import scala.collection.mutable.ArrayBuffer
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
            // (POST/PUT/DELETE/PATCH) wrap routes.run in withRequestTransaction.
            OptionT(
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
                    else withRequestTransaction(routeIO)
                  executed.map(Option(_))
              }
            )

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

  /**
   * Wraps the business-logic IO in a request-scoped DB transaction.
   *
   * Called only for mutating methods (POST/PUT/DELETE/PATCH) after validateOnly succeeds.
   * GET/HEAD bypass this entirely and run on auto-commit vendor connections, avoiding
   * a pool borrow + empty-commit overhead on every read request.
   *
   * Borrows a Connection from HikariCP via Resource.make so close() is guaranteed
   * even if commit/rollback throws or the fiber is cancelled.  The proxy prevents
   * Lift's internal DB.use lifecycle from committing or returning the connection
   * prematurely.
   *
   * currentProxy (TTL) is NOT set here.  Every DB call goes through
   * RequestScopeConnection.fromFuture, which atomically sets + submits + clears the
   * TTL within a single IO.defer block on the compute thread, so the thread is never
   * left dirty after the fromFuture call returns.
   *
   * On success: commits, then Resource finalizer closes.
   * On error/cancellation: rolls back (errors swallowed to preserve original cause),
   *   then Resource finalizer closes.
   *
   * Metric writes (IO.blocking in recordMetric) run on the blocking pool where
   * currentProxy is not set — they get their own pool connection and commit
   * independently, matching v6 behaviour.
   */
  private def withRequestTransaction(io: IO[Response[IO]]): IO[Response[IO]] =
    Resource.make(
      IO.blocking(APIUtil.vendor.HikariDatasource.ds.getConnection())
    )(conn =>
      IO.blocking { try { conn.close() } catch { case _: Exception => () } }
    ).use { realConn =>
      val proxy = RequestScopeConnection.makeProxy(realConn)
      for {
        _ <- RequestScopeConnection.requestProxyLocal.set(Some(proxy))
        // Note: currentProxy (TTL) is NOT set here.  Every DB call goes through
        // RequestScopeConnection.fromFuture, which atomically sets + submits + clears
        // the TTL within a single IO.defer block on the compute thread.
        result <- io.guaranteeCase {
                    case Outcome.Succeeded(_) =>
                      RequestScopeConnection.requestProxyLocal.set(None) *>
                        IO.blocking { realConn.commit() }
                    case _ =>
                      RequestScopeConnection.requestProxyLocal.set(None) *>
                        IO.blocking { try { realConn.rollback() } catch { case _: Exception => () } }
                  }
      } yield result
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

    val io =
      if (needsAuth) IO.fromFuture(IO(APIUtil.authenticatedAccess(ctx.callContext)))
      else IO.fromFuture(IO(APIUtil.anonymousAccess(ctx.callContext)))

    EitherT(
      io.attempt.flatMap {
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
