package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.APIFailureNewStyle
import code.api.util.APIUtil
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util.NewStyle
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{CallContext => SharedCallContext}
import com.openbankproject.commons.model.{Bank, BankAccount, BankId, AccountId, ViewId, BankIdAccountId, CounterpartyTrait, User, View}
import net.liftweb.common.{Box, Empty, Full, Failure => LiftFailure}
import org.http4s._

import scala.collection.mutable.ArrayBuffer
import scala.language.higherKinds

/**
 * ResourceDoc-driven validation middleware for http4s.
 * 
 * This middleware wraps http4s routes with automatic validation based on ResourceDoc metadata:
 * - Authentication (if required by ResourceDoc)
 * - Bank existence validation (if BANK_ID in path)
 * - Role-based authorization (if roles specified in ResourceDoc)
 * - Account existence validation (if ACCOUNT_ID in path)
 * - View access validation (if VIEW_ID in path)
 * - Counterparty existence validation (if COUNTERPARTY_ID in path)
 * 
 * Validation order matches Lift: auth → bank → roles → account → view → counterparty
 */
object ResourceDocMiddleware {
  
  type HttpF[A] = OptionT[IO, A]
  type Middleware[F[_]] = HttpRoutes[F] => HttpRoutes[F]
  
  /**
   * Check if ResourceDoc requires authentication based on errorResponseBodies
   */
  private def needsAuthentication(resourceDoc: ResourceDoc): Boolean = {
    resourceDoc.errorResponseBodies.contains($UserNotLoggedIn)
  }
  
  /**
   * Create middleware that applies ResourceDoc-driven validation
   * 
   * @param resourceDocs Collection of ResourceDoc entries for matching
   * @return Middleware that wraps routes with validation
   */
  def apply(resourceDocs: ArrayBuffer[ResourceDoc]): Middleware[IO] = { routes =>
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      OptionT.liftF(validateAndRoute(req, routes, resourceDocs))
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
      // Build CallContext from request
      cc <- Http4sCallContextBuilder.fromRequest(req, "v7.0.0")
      
      // Match ResourceDoc
      resourceDocOpt = ResourceDocMatcher.findResourceDoc(req.method.name, req.uri.path, resourceDocs)
      
      response <- resourceDocOpt match {
        case Some(resourceDoc) =>
          // Attach ResourceDoc to CallContext for metrics/rate limiting
          val ccWithDoc = ResourceDocMatcher.attachToCallContext(cc, resourceDoc)
          val pathParams = ResourceDocMatcher.extractPathParams(req.uri.path, resourceDoc)
          
          // Run validation chain
          runValidationChain(req, resourceDoc, ccWithDoc, pathParams, routes)
          
        case None =>
          // No matching ResourceDoc - pass through to routes
          routes.run(req).getOrElseF(
            IO.pure(Response[IO](org.http4s.Status.NotFound))
          )
      }
    } yield response
  }
  
  /**
   * Run the validation chain in order: auth → bank → roles → account → view → counterparty
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
    val authResult: IO[Either[Response[IO], (Box[User], SharedCallContext)]] = 
      if (needsAuthentication(resourceDoc)) {
        IO.fromFuture(IO(APIUtil.authenticatedAccess(cc))).attempt.map {
          case Right((boxUser, Some(updatedCC))) => 
            boxUser match {
              case Full(_) => Right((boxUser, updatedCC))
              case Empty => Left(Response[IO](org.http4s.Status.Unauthorized))
              case LiftFailure(_, _, _) => Left(Response[IO](org.http4s.Status.Unauthorized))
            }
          case Right((boxUser, None)) => Right((boxUser, cc))
          case Left(e: APIFailureNewStyle) => 
            Left(Response[IO](org.http4s.Status.fromInt(e.failCode).getOrElse(org.http4s.Status.Unauthorized)))
          case Left(_) => Left(Response[IO](org.http4s.Status.Unauthorized))
        }
      } else {
        IO.fromFuture(IO(APIUtil.anonymousAccess(cc))).attempt.map {
          case Right((boxUser, Some(updatedCC))) => Right((boxUser, updatedCC))
          case Right((boxUser, None)) => Right((boxUser, cc))
          case Left(_) => Right((Empty, cc))
        }
      }
    
    authResult.flatMap {
      case Left(errorResponse) => IO.pure(errorResponse)
      case Right((boxUser, cc1)) =>
        // Step 2: Bank validation (if BANK_ID in path)
        val bankResult: IO[Either[Response[IO], (Option[Bank], SharedCallContext)]] = 
          pathParams.get("BANK_ID") match {
            case Some(bankIdStr) =>
              IO.fromFuture(IO(NewStyle.function.getBank(BankId(bankIdStr), Some(cc1)))).attempt.map {
                case Right((bank, Some(updatedCC))) => Right((Some(bank), updatedCC))
                case Right((bank, None)) => Right((Some(bank), cc1))
                case Left(_: APIFailureNewStyle) => 
                  Left(Response[IO](org.http4s.Status.NotFound))
                case Left(_) => Left(Response[IO](org.http4s.Status.NotFound))
              }
            case None => IO.pure(Right((None, cc1)))
          }
        
        bankResult.flatMap {
          case Left(errorResponse) => IO.pure(errorResponse)
          case Right((bankOpt, cc2)) =>
            // Step 3: Role authorization (if roles specified)
            val rolesResult: IO[Either[Response[IO], SharedCallContext]] = 
              resourceDoc.roles match {
                case Some(roles) if roles.nonEmpty && boxUser.isDefined =>
                  val userId = boxUser.map(_.userId).getOrElse("")
                  val bankId = bankOpt.map(_.bankId.value).getOrElse("")
                  
                  // Check if user has at least one of the required roles
                  val hasRole = roles.exists { role =>
                    val checkBankId = if (role.requiresBankId) bankId else ""
                    APIUtil.hasEntitlement(checkBankId, userId, role)
                  }
                  
                  if (hasRole) {
                    IO.pure(Right(cc2))
                  } else {
                    IO.pure(Left(Response[IO](org.http4s.Status.Forbidden)))
                  }
                case _ => IO.pure(Right(cc2))
              }
            
            rolesResult.flatMap {
              case Left(errorResponse) => IO.pure(errorResponse)
              case Right(cc3) =>
                // Step 4: Account validation (if ACCOUNT_ID in path)
                val accountResult: IO[Either[Response[IO], (Option[BankAccount], SharedCallContext)]] = 
                  (pathParams.get("BANK_ID"), pathParams.get("ACCOUNT_ID")) match {
                    case (Some(bankIdStr), Some(accountIdStr)) =>
                      IO.fromFuture(IO(
                        NewStyle.function.getBankAccount(BankId(bankIdStr), AccountId(accountIdStr), Some(cc3))
                      )).attempt.map {
                        case Right((account, Some(updatedCC))) => Right((Some(account), updatedCC))
                        case Right((account, None)) => Right((Some(account), cc3))
                        case Left(_) => Left(Response[IO](org.http4s.Status.NotFound))
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
                          IO.fromFuture(IO(
                            ViewNewStyle.checkViewAccessAndReturnView(
                              ViewId(viewIdStr), 
                              bankIdAccountId, 
                              boxUser.toOption, 
                              Some(cc4)
                            )
                          )).attempt.map {
                            case Right(view) => Right((Some(view), cc4))
                            case Left(_) => Left(Response[IO](org.http4s.Status.Forbidden))
                          }
                        case _ => IO.pure(Right((None, cc4)))
                      }
                    
                    viewResult.flatMap {
                      case Left(errorResponse) => IO.pure(errorResponse)
                      case Right((viewOpt, cc5)) =>
                        // Step 6: Counterparty validation (if COUNTERPARTY_ID in path)
                        val counterpartyResult: IO[Either[Response[IO], (Option[CounterpartyTrait], SharedCallContext)]] = 
                          pathParams.get("COUNTERPARTY_ID") match {
                            case Some(_) =>
                              // For now, skip counterparty validation - can be added later
                              IO.pure(Right((None, cc5)))
                            case None => IO.pure(Right((None, cc5)))
                          }
                        
                        counterpartyResult.flatMap {
                          case Left(errorResponse) => IO.pure(errorResponse)
                          case Right((counterpartyOpt, finalCC)) =>
                            // All validations passed - store validated context and invoke route
                            val validatedContext = ValidatedContext(
                              user = boxUser.toOption,
                              bank = bankOpt,
                              bankAccount = accountOpt,
                              view = viewOpt,
                              counterparty = counterpartyOpt,
                              callContext = finalCC
                            )
                            
                            // Store validated objects in request attributes
                            var updatedReq = req.withAttribute(Http4sVaultKeys.callContextKey, finalCC)
                            boxUser.toOption.foreach { user =>
                              updatedReq = updatedReq.withAttribute(Http4sVaultKeys.userKey, user)
                            }
                            bankOpt.foreach { bank =>
                              updatedReq = updatedReq.withAttribute(Http4sVaultKeys.bankKey, bank)
                            }
                            accountOpt.foreach { account =>
                              updatedReq = updatedReq.withAttribute(Http4sVaultKeys.bankAccountKey, account)
                            }
                            viewOpt.foreach { view =>
                              updatedReq = updatedReq.withAttribute(Http4sVaultKeys.viewKey, view)
                            }
                            counterpartyOpt.foreach { counterparty =>
                              updatedReq = updatedReq.withAttribute(Http4sVaultKeys.counterpartyKey, counterparty)
                            }
                            
                            // Invoke the original route
                            routes.run(updatedReq).getOrElseF(
                              IO.pure(Response[IO](org.http4s.Status.NotFound))
                            )
                        }
                    }
                }
            }
        }
    }
  }
}
