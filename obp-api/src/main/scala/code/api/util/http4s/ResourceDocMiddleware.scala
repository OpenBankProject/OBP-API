package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.APIFailureNewStyle
import code.util.Helper.MdcLoggable
import code.api.util.APIUtil
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util.NewStyle
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{CallContext => SharedCallContext}
import com.openbankproject.commons.model.{Bank, BankAccount, BankId, AccountId, ViewId, BankIdAccountId, CounterpartyTrait, User, View}
import net.liftweb.common.{Box, Empty, Full, Failure => LiftFailure}
import org.http4s._
import org.http4s.headers.`Content-Type`

import scala.collection.mutable.ArrayBuffer
import scala.language.higherKinds

/**
 * ResourceDoc-driven validation middleware for http4s.
 * 
 * This middleware wraps http4s routes with automatic validation based on ResourceDoc metadata.
 * 
 * VALIDATION ORDER:
 * 1. Authentication first
 * 2. BANK_ID validation (if present in path)
 * 3. ACCOUNT_ID validation (if present in path)
 * 4. VIEW_ID validation (if present in path)
 * 5. Role authorization (if roles specified in ResourceDoc)
 * 6. COUNTERPARTY_ID validation (if present in path)
 */
object ResourceDocMiddleware extends MdcLoggable{
  
  type HttpF[A] = OptionT[IO, A]
  type Middleware[F[_]] = HttpRoutes[F] => HttpRoutes[F]
  private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)
  
  /**
   * Check if ResourceDoc requires authentication based on errorResponseBodies
   */
  private def needsAuthentication(resourceDoc: ResourceDoc): Boolean = {
    // Roles always require an authenticated user to validate entitlements
    resourceDoc.errorResponseBodies.contains($AuthenticatedUserIsRequired) || resourceDoc.roles.exists(_.nonEmpty)
  }
  
  /**
   * Create middleware that applies ResourceDoc-driven validation
   */
  def apply(resourceDocs: ArrayBuffer[ResourceDoc]): Middleware[IO] = { routes =>
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      OptionT(validateAndRoute(req, routes, resourceDocs).map(Option(_)))
    }
  }
  
  /**
   * Validate request and route to handler if validation passes
   */
  private def validateAndRoute(
    req: Request[IO],
    routes: HttpRoutes[IO],
    resourceDocs: ArrayBuffer[ResourceDoc]
  ): IO[Response[IO]] = {
    for {
      cc <- Http4sCallContextBuilder.fromRequest(req, "v7.0.0")
      resourceDocOpt = ResourceDocMatcher.findResourceDoc(req.method.name, req.uri.path, resourceDocs)
      response <- resourceDocOpt match {
        case Some(resourceDoc) =>
          val ccWithDoc = ResourceDocMatcher.attachToCallContext(cc, resourceDoc)
          val pathParams = ResourceDocMatcher.extractPathParams(req.uri.path, resourceDoc)
          runValidationChain(req, resourceDoc, ccWithDoc, pathParams, routes)
            .map(ensureJsonContentType)
        case None =>
          routes.run(req).getOrElseF(IO.pure(Response[IO](org.http4s.Status.NotFound)))
      }
    } yield response
  }

  private def ensureJsonContentType(response: Response[IO]): Response[IO] = {
    response.contentType match {
      case Some(contentType) if contentType.mediaType == MediaType.application.json => response
      case _ => response.withContentType(jsonContentType)
    }
  }
  
  /**
   * Run the validation chain in order: auth → bank → account → view → roles → counterparty
   */
  private def runValidationChain(
    req: Request[IO],
    resourceDoc: ResourceDoc,
    cc: SharedCallContext,
    pathParams: Map[String, String],
    routes: HttpRoutes[IO]
  ): IO[Response[IO]] = {
    import com.openbankproject.commons.ExecutionContext.Implicits.global
    
    // Step 1: Authentication
    val needsAuth = needsAuthentication(resourceDoc)
    logger.debug(s"[ResourceDocMiddleware] needsAuthentication for ${resourceDoc.partialFunctionName}: $needsAuth")
    
    val authResult: IO[Either[Response[IO], (Box[User], SharedCallContext)]] = 
      if (needsAuth) {
        IO.fromFuture(IO(APIUtil.authenticatedAccess(cc))).attempt.flatMap {
          case Right((boxUser, optCC)) => 
            val updatedCC = optCC.getOrElse(cc)
            boxUser match {
              case Full(user) => 
                IO.pure(Right((boxUser, updatedCC)))
              case Empty => 
                ErrorResponseConverter.createErrorResponse(401, $AuthenticatedUserIsRequired, updatedCC).map(Left(_))
              case LiftFailure(msg, _, _) => 
                ErrorResponseConverter.createErrorResponse(401, msg, updatedCC).map(Left(_))
            }
          case Left(e: APIFailureNewStyle) => 
            ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, cc).map(Left(_))
          case Left(e) => 
            val (code, msg) = try {
              import net.liftweb.json._
              implicit val formats = net.liftweb.json.DefaultFormats
              val json = parse(e.getMessage)
              val failCode = (json \ "failCode").extractOpt[Int].getOrElse(401)
              val failMsg = (json \ "failMsg").extractOpt[String].getOrElse($AuthenticatedUserIsRequired)
              (failCode, failMsg)
            } catch {
              case _: Exception => (401, $AuthenticatedUserIsRequired)
            }
            ErrorResponseConverter.createErrorResponse(code, msg, cc).map(Left(_))
        }
      } else {
        IO.fromFuture(IO(APIUtil.anonymousAccess(cc))).attempt.flatMap {
          case Right((boxUser, Some(updatedCC))) => 
            logger.debug(s"[ResourceDocMiddleware] anonymousAccess succeeded with user: $boxUser")
            IO.pure(Right((boxUser, updatedCC)))
          case Right((boxUser, None)) => 
            logger.debug(s"[ResourceDocMiddleware] anonymousAccess succeeded with user: $boxUser (no updated CC)")
            IO.pure(Right((boxUser, cc)))
          case Left(e) => 
            // For anonymous access, we don't fail on auth errors - just continue with Empty user
            // This allows endpoints without $AuthenticatedUserIsRequired to work without authentication
            logger.debug(s"[ResourceDocMiddleware] anonymousAccess threw exception (ignoring for anonymous): ${e.getClass.getName}: ${e.getMessage.take(100)}")
            IO.pure(Right((Empty, cc)))
        }
      }

    
    authResult.flatMap {
      case Left(errorResponse) => IO.pure(errorResponse)
      case Right((boxUser, cc1)) =>
        // Step 2: Role authorization - BEFORE business logic validation
        val rolesResult: IO[Either[Response[IO], SharedCallContext]] = 
          resourceDoc.roles match {
            case Some(roles) if roles.nonEmpty =>
              boxUser match {
                case Full(user) =>
                  val userId = user.userId
                  val bankId = pathParams.get("BANK_ID").getOrElse("")
                  val hasRole = roles.exists { role =>
                    val checkBankId = if (role.requiresBankId) bankId else ""
                    APIUtil.hasEntitlement(checkBankId, userId, role)
                  }
                  if (hasRole) IO.pure(Right(cc1)) 
                  else ErrorResponseConverter.createErrorResponse(403, UserHasMissingRoles + roles.mkString(", "), cc1).map(Left(_))
                case _ =>
                  ErrorResponseConverter.createErrorResponse(401, $AuthenticatedUserIsRequired, cc1).map(Left(_))
              }
            case _ => IO.pure(Right(cc1))
          }
        
        rolesResult.flatMap {
          case Left(errorResponse) => IO.pure(errorResponse)
          case Right(cc2) =>
            // Step 3: Bank validation
            val bankResult: IO[Either[Response[IO], (Option[Bank], SharedCallContext)]] = 
              pathParams.get("BANK_ID") match {
                case Some(bankIdStr) =>
                  IO.fromFuture(IO(NewStyle.function.getBank(BankId(bankIdStr), Some(cc2)))).attempt.flatMap {
                    case Right((bank, Some(updatedCC))) =>
                      IO.pure(Right((Some(bank), updatedCC)))
                    case Right((bank, None)) =>
                      IO.pure(Right((Some(bank), cc2)))
                    case Left(e: APIFailureNewStyle) =>
                      ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, cc2).map(Left(_))
                    case Left(e) =>
                      ErrorResponseConverter.createErrorResponse(404, BankNotFound + ": " + bankIdStr, cc2).map(Left(_))
                  }
                case None => IO.pure(Right((None, cc2)))
              }
            
            bankResult.flatMap {
              case Left(errorResponse) => IO.pure(errorResponse)
              case Right((bankOpt, cc3)) =>
                // Step 4: Account validation (if ACCOUNT_ID in path)
                val accountResult: IO[Either[Response[IO], (Option[BankAccount], SharedCallContext)]] = 
                  (pathParams.get("BANK_ID"), pathParams.get("ACCOUNT_ID")) match {
                    case (Some(bankIdStr), Some(accountIdStr)) =>
                      IO.fromFuture(IO(NewStyle.function.getBankAccount(BankId(bankIdStr), AccountId(accountIdStr), Some(cc3)))).attempt.flatMap {
                        case Right((account, Some(updatedCC))) => IO.pure(Right((Some(account), updatedCC)))
                        case Right((account, None)) => IO.pure(Right((Some(account), cc3)))
                        case Left(e: APIFailureNewStyle) => 
                          ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, cc3).map(Left(_))
                        case Left(e) => 
                          ErrorResponseConverter.createErrorResponse(404, BankAccountNotFound + s": bankId=$bankIdStr, accountId=$accountIdStr", cc3).map(Left(_))
                      }
                    case _ => IO.pure(Right((None, cc3)))
                  }

                
                accountResult.flatMap {
                  case Left(errorResponse) => IO.pure(errorResponse)
                  case Right((accountOpt, cc4)) =>
                    // Step 5: View validation (if VIEW_ID in path)
                    val viewResult: IO[Either[Response[IO], (Option[View], SharedCallContext)]] = 
                      (pathParams.get("BANK_ID"), pathParams.get("ACCOUNT_ID"), pathParams.get("VIEW_ID")) match {
                        case (Some(bankIdStr), Some(accountIdStr), Some(viewIdStr)) =>
                          val bankIdAccountId = BankIdAccountId(BankId(bankIdStr), AccountId(accountIdStr))
                          IO.fromFuture(IO(ViewNewStyle.checkViewAccessAndReturnView(ViewId(viewIdStr), bankIdAccountId, boxUser.toOption, Some(cc4)))).attempt.flatMap {
                            case Right(view) => IO.pure(Right((Some(view), cc4)))
                            case Left(e: APIFailureNewStyle) => 
                              ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, cc4).map(Left(_))
                            case Left(e) => 
                              ErrorResponseConverter.createErrorResponse(403, UserNoPermissionAccessView + s": viewId=$viewIdStr", cc4).map(Left(_))
                          }
                        case _ => IO.pure(Right((None, cc4)))
                      }
                    
                    viewResult.flatMap {
                      case Left(errorResponse) => IO.pure(errorResponse)
                      case Right((viewOpt, cc5)) =>
                        // Step 6: Counterparty validation (if COUNTERPARTY_ID in path)
                        val counterpartyResult: IO[Either[Response[IO], (Option[CounterpartyTrait], SharedCallContext)]] = 
                          (pathParams.get("BANK_ID"), pathParams.get("ACCOUNT_ID"), pathParams.get("COUNTERPARTY_ID")) match {
                            case (Some(bankIdStr), Some(accountIdStr), Some(counterpartyIdStr)) =>
                              IO.fromFuture(IO(NewStyle.function.getCounterpartyTrait(BankId(bankIdStr), AccountId(accountIdStr), counterpartyIdStr, Some(cc5)))).attempt.flatMap {
                                case Right((counterparty, Some(updatedCC))) => IO.pure(Right((Some(counterparty), updatedCC)))
                                case Right((counterparty, None)) => IO.pure(Right((Some(counterparty), cc5)))
                                case Left(e: APIFailureNewStyle) => 
                                  ErrorResponseConverter.createErrorResponse(e.failCode, e.failMsg, cc5).map(Left(_))
                                case Left(e) => 
                                  ErrorResponseConverter.createErrorResponse(404, CounterpartyNotFound + s": counterpartyId=$counterpartyIdStr", cc5).map(Left(_))
                              }
                            case _ => IO.pure(Right((None, cc5)))
                          }
                        
                        counterpartyResult.flatMap {
                          case Left(errorResponse) => IO.pure(errorResponse)
                          case Right((counterpartyOpt, finalCC)) =>
                            // All validations passed - store validated context and invoke route
                            var updatedReq = req.withAttribute(Http4sVaultKeys.callContextKey, finalCC)
                            boxUser.toOption.foreach { user => updatedReq = updatedReq.withAttribute(Http4sVaultKeys.userKey, user) }
                            bankOpt.foreach { bank => updatedReq = updatedReq.withAttribute(Http4sVaultKeys.bankKey, bank) }
                            accountOpt.foreach { account => updatedReq = updatedReq.withAttribute(Http4sVaultKeys.bankAccountKey, account) }
                            viewOpt.foreach { view => updatedReq = updatedReq.withAttribute(Http4sVaultKeys.viewKey, view) }
                            counterpartyOpt.foreach { counterparty => updatedReq = updatedReq.withAttribute(Http4sVaultKeys.counterpartyKey, counterparty) }
                            routes.run(updatedReq).getOrElseF(IO.pure(Response[IO](org.http4s.Status.NotFound)))
                        }
                    }
                }
            }
        }
    }
  }
}
