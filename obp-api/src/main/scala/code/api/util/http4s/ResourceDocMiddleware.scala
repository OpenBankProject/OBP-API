package code.api.util.http4s

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, CallContext, NewStyle}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Empty, Full}
import org.http4s._
import org.http4s.headers.`Content-Type`

import scala.collection.mutable.ArrayBuffer

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
    if (resourceDoc.partialFunctionName == "getResourceDocsObpV700") {
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
    Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      // Build initial CallContext from request
      OptionT.liftF(Http4sCallContextBuilder.fromRequest(req, "v7.0.0")).flatMap { cc =>
        ResourceDocMatcher.findResourceDoc(req.method.name, req.uri.path, resourceDocs) match {
          case Some(resourceDoc) =>
            val ccWithDoc = ResourceDocMatcher.attachToCallContext(cc, resourceDoc)
            val pathParams = ResourceDocMatcher.extractPathParams(req.uri.path, resourceDoc)
            // Run full validation chain
            OptionT(validateRequest(req, resourceDoc, pathParams, ccWithDoc, routes).map(Option(_)))

          case None =>
            // No matching ResourceDoc: fallback to original route
            routes.run(req)
        }
      }
    }
  }

  /**
   * Executes the full validation chain for the request.
   * Returns either an error Response or enriched request routed to the handler.
   */
  private def validateRequest(
                               req: Request[IO],
                               resourceDoc: ResourceDoc,
                               pathParams: Map[String, String],
                               cc: CallContext,
                               routes: HttpRoutes[IO]
                             ): IO[Response[IO]] = {

    // Initial context with just CallContext
    val initialContext = ValidationContext(callContext = cc)

    // Compose all validation steps using EitherT
    val result: Validation[ValidationContext] = for {
      context <- authenticate(req, resourceDoc, initialContext)
      context <- authorizeRoles(resourceDoc, pathParams, context)
      context <- validateBank(pathParams, context)
      context <- validateAccount(pathParams, context)
      context <- validateView(pathParams, context)
      context <- validateCounterparty(pathParams, context)
    } yield context

    // Convert Validation result to Response
    result.value.flatMap {
      case Left(errorResponse) => IO.pure(ensureJsonContentType(errorResponse)) // Ensure all error responses are JSON
      case Right(validCtx) =>
        // Enrich request with validated CallContext
        val enrichedReq = req.withAttribute(
          Http4sRequestAttributes.callContextKey,
          validCtx.callContext.copy(
            bank = validCtx.bank,
            bankAccount = validCtx.account,
            view = validCtx.view,
            counterparty = validCtx.counterparty
          )
        )
        routes.run(enrichedReq)
          .map(ensureJsonContentType) // Ensure routed response has JSON content type
          .getOrElseF(IO.pure(ensureJsonContentType(Response[IO](org.http4s.Status.NotFound))))
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

    resourceDoc.roles match {
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
