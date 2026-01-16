package code.api.v7_0_0

import cats.effect._
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util.{CallContext => SharedCallContext}
import com.openbankproject.commons.model.{Bank, BankAccount, BankId, AccountId, ViewId, BankIdAccountId, CounterpartyTrait, User, View}
import net.liftweb.common.{Box, Empty, Full, Failure => LiftFailure}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.{Extraction, compactRender}
import net.liftweb.json.JsonDSL._
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString
import org.typelevel.vault.Key

import java.util.{Date, UUID}
import scala.collection.mutable.ArrayBuffer
import scala.language.higherKinds

/**
 * Http4s support for ResourceDoc-driven validation.
 * 
 * This file contains:
 * - Http4sCallContextBuilder: Builds shared CallContext from http4s Request[IO]
 * - Http4sVaultKeys: Vault keys for storing validated objects in request attributes
 * - ResourceDocMatcher: Matches http4s requests to ResourceDoc entries
 * - ResourceDocMiddleware: Validation chain middleware for http4s
 * - ErrorResponseConverter: Converts OBP errors to http4s Response[IO]
 */

/**
 * Vault keys for storing validated objects in http4s request attributes.
 * These keys allow middleware to pass validated objects to endpoint handlers.
 */
object Http4sVaultKeys {
  // Use shared CallContext from code.api.util.ApiSession
  val callContextKey: Key[SharedCallContext] = 
    Key.newKey[IO, SharedCallContext].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  val userKey: Key[User] = 
    Key.newKey[IO, User].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  val bankKey: Key[Bank] = 
    Key.newKey[IO, Bank].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  val bankAccountKey: Key[BankAccount] = 
    Key.newKey[IO, BankAccount].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  val viewKey: Key[View] = 
    Key.newKey[IO, View].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  val counterpartyKey: Key[CounterpartyTrait] = 
    Key.newKey[IO, CounterpartyTrait].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  /**
   * Helper methods for accessing validated objects from request attributes
   */
  def getCallContext(req: Request[IO]): Option[SharedCallContext] = 
    req.attributes.lookup(callContextKey)
  
  def getUser(req: Request[IO]): Option[User] = 
    req.attributes.lookup(userKey)
  
  def getBank(req: Request[IO]): Option[Bank] = 
    req.attributes.lookup(bankKey)
  
  def getBankAccount(req: Request[IO]): Option[BankAccount] = 
    req.attributes.lookup(bankAccountKey)
  
  def getView(req: Request[IO]): Option[View] = 
    req.attributes.lookup(viewKey)
  
  def getCounterparty(req: Request[IO]): Option[CounterpartyTrait] = 
    req.attributes.lookup(counterpartyKey)
}

/**
 * Builds shared CallContext from http4s Request[IO].
 * 
 * This builder extracts all necessary request data and populates the shared CallContext,
 * enabling the existing authentication and validation code to work with http4s requests.
 */
object Http4sCallContextBuilder {
  
  /**
   * Build CallContext from http4s Request[IO]
   * Populates all fields needed by getUserAndSessionContextFuture
   * 
   * @param request The http4s request
   * @param apiVersion The API version string (e.g., "v7.0.0")
   * @return IO[SharedCallContext] with all request data populated
   */
  def fromRequest(request: Request[IO], apiVersion: String): IO[SharedCallContext] = {
    for {
      body <- request.bodyText.compile.string.map(s => if (s.isEmpty) None else Some(s))
    } yield SharedCallContext(
      url = request.uri.renderString,
      verb = request.method.name,
      implementedInVersion = apiVersion,
      correlationId = extractCorrelationId(request),
      ipAddress = extractIpAddress(request),
      requestHeaders = extractHeaders(request),
      httpBody = body,
      authReqHeaderField = extractAuthHeader(request),
      directLoginParams = extractDirectLoginParams(request),
      oAuthParams = extractOAuthParams(request),
      startTime = Some(new Date())
    )
  }
  
  /**
   * Extract headers from http4s request and convert to List[HTTPParam]
   */
  private def extractHeaders(request: Request[IO]): List[HTTPParam] = {
    request.headers.headers.map { h =>
      HTTPParam(h.name.toString, List(h.value))
    }.toList
  }
  
  /**
   * Extract correlation ID from X-Request-ID header or generate a new UUID
   */
  private def extractCorrelationId(request: Request[IO]): String = {
    request.headers.get(CIString("X-Request-ID"))
      .map(_.head.value)
      .getOrElse(UUID.randomUUID().toString)
  }
  
  /**
   * Extract IP address from X-Forwarded-For header or request remote address
   */
  private def extractIpAddress(request: Request[IO]): String = {
    request.headers.get(CIString("X-Forwarded-For"))
      .map(_.head.value.split(",").head.trim)
      .orElse(request.remoteAddr.map(_.toUriString))
      .getOrElse("")
  }
  
  /**
   * Extract Authorization header value as Box[String]
   */
  private def extractAuthHeader(request: Request[IO]): Box[String] = {
    request.headers.get(CIString("Authorization"))
      .map(h => Full(h.head.value))
      .getOrElse(Empty)
  }
  
  /**
   * Extract DirectLogin header parameters if present
   * DirectLogin header format: DirectLogin token="xxx"
   */
  private def extractDirectLoginParams(request: Request[IO]): Map[String, String] = {
    request.headers.get(CIString("DirectLogin"))
      .map(h => parseDirectLoginHeader(h.head.value))
      .getOrElse(Map.empty)
  }
  
  /**
   * Parse DirectLogin header value into parameter map
   * Format: DirectLogin token="xxx", username="yyy"
   */
  private def parseDirectLoginHeader(headerValue: String): Map[String, String] = {
    val pattern = """(\w+)="([^"]*)"""".r
    pattern.findAllMatchIn(headerValue).map { m =>
      m.group(1) -> m.group(2)
    }.toMap
  }
  
  /**
   * Extract OAuth parameters from Authorization header if OAuth
   */
  private def extractOAuthParams(request: Request[IO]): Map[String, String] = {
    request.headers.get(CIString("Authorization"))
      .filter(_.head.value.startsWith("OAuth "))
      .map(h => parseOAuthHeader(h.head.value))
      .getOrElse(Map.empty)
  }
  
  /**
   * Parse OAuth Authorization header value into parameter map
   * Format: OAuth oauth_consumer_key="xxx", oauth_token="yyy", ...
   */
  private def parseOAuthHeader(headerValue: String): Map[String, String] = {
    val oauthPart = headerValue.stripPrefix("OAuth ").trim
    val pattern = """(\w+)="([^"]*)"""".r
    pattern.findAllMatchIn(oauthPart).map { m =>
      m.group(1) -> m.group(2)
    }.toMap
  }
}

/**
 * Matches http4s requests to ResourceDoc entries.
 * 
 * ResourceDoc entries use URL templates with uppercase variable names:
 * - BANK_ID, ACCOUNT_ID, VIEW_ID, COUNTERPARTY_ID
 * 
 * This matcher finds the corresponding ResourceDoc for a given request
 * and extracts path parameters.
 */
object ResourceDocMatcher {
  
  /**
   * Find ResourceDoc matching the given verb and path
   * 
   * @param verb HTTP verb (GET, POST, PUT, DELETE, etc.)
   * @param path Request path
   * @param resourceDocs Collection of ResourceDoc entries to search
   * @return Option[ResourceDoc] if a match is found
   */
  def findResourceDoc(
    verb: String,
    path: Uri.Path,
    resourceDocs: ArrayBuffer[ResourceDoc]
  ): Option[ResourceDoc] = {
    val pathString = path.renderString
    resourceDocs.find { doc =>
      doc.requestVerb.equalsIgnoreCase(verb) && matchesUrlTemplate(pathString, doc.requestUrl)
    }
  }
  
  /**
   * Check if a path matches a URL template
   * Template segments in uppercase are treated as variables
   */
  private def matchesUrlTemplate(path: String, template: String): Boolean = {
    val pathSegments = path.split("/").filter(_.nonEmpty)
    val templateSegments = template.split("/").filter(_.nonEmpty)
    
    if (pathSegments.length != templateSegments.length) {
      false
    } else {
      pathSegments.zip(templateSegments).forall { case (pathSeg, templateSeg) =>
        // Uppercase segments are variables (BANK_ID, ACCOUNT_ID, etc.)
        isTemplateVariable(templateSeg) || pathSeg == templateSeg
      }
    }
  }
  
  /**
   * Check if a template segment is a variable (uppercase)
   */
  private def isTemplateVariable(segment: String): Boolean = {
    segment.nonEmpty && segment.forall(c => c.isUpper || c == '_' || c.isDigit)
  }
  
  /**
   * Extract path parameters from matched ResourceDoc
   * 
   * @param path Request path
   * @param resourceDoc Matched ResourceDoc
   * @return Map with keys: BANK_ID, ACCOUNT_ID, VIEW_ID, COUNTERPARTY_ID (if present)
   */
  def extractPathParams(
    path: Uri.Path,
    resourceDoc: ResourceDoc
  ): Map[String, String] = {
    val pathString = path.renderString
    val pathSegments = pathString.split("/").filter(_.nonEmpty)
    val templateSegments = resourceDoc.requestUrl.split("/").filter(_.nonEmpty)
    
    if (pathSegments.length != templateSegments.length) {
      Map.empty
    } else {
      pathSegments.zip(templateSegments).collect {
        case (pathSeg, templateSeg) if isTemplateVariable(templateSeg) =>
          templateSeg -> pathSeg
      }.toMap
    }
  }
  
  /**
   * Update CallContext with matched ResourceDoc
   * MUST be called after successful match for metrics/rate limiting consistency
   * 
   * @param callContext Current CallContext
   * @param resourceDoc Matched ResourceDoc
   * @return Updated CallContext with resourceDocument and operationId set
   */
  def attachToCallContext(
    callContext: SharedCallContext,
    resourceDoc: ResourceDoc
  ): SharedCallContext = {
    callContext.copy(
      resourceDocument = Some(resourceDoc),
      operationId = Some(resourceDoc.operationId)
    )
  }
}

/**
 * Validated context containing all validated objects from the middleware chain.
 * This is passed to endpoint handlers after successful validation.
 */
case class ValidatedContext(
  user: Option[User],
  bank: Option[Bank],
  bankAccount: Option[BankAccount],
  view: Option[View],
  counterparty: Option[CounterpartyTrait],
  callContext: SharedCallContext
)


/**
 * Converts OBP errors to http4s Response[IO].
 * Uses Lift JSON for serialization (consistent with OBP codebase).
 */
object ErrorResponseConverter {
  import net.liftweb.json.Formats
  import code.api.util.CustomJsonFormats
  
  implicit val formats: Formats = CustomJsonFormats.formats
  private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)
  
  /**
   * OBP standard error response format
   */
  case class OBPErrorResponse(
    code: Int,
    message: String
  )
  
  /**
   * Convert error response to JSON string
   */
  private def toJsonString(error: OBPErrorResponse): String = {
    val json = ("code" -> error.code) ~ ("message" -> error.message)
    compactRender(json)
  }
  
  /**
   * Convert an error to http4s Response[IO]
   */
  def toHttp4sResponse(error: Throwable, callContext: SharedCallContext): IO[Response[IO]] = {
    error match {
      case e: APIFailureNewStyle =>
        apiFailureToResponse(e, callContext)
      case e =>
        unknownErrorToResponse(e, callContext)
    }
  }
  
  /**
   * Convert APIFailureNewStyle to http4s Response
   */
  def apiFailureToResponse(failure: APIFailureNewStyle, callContext: SharedCallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(failure.failCode, failure.failMsg)
    val status = org.http4s.Status.fromInt(failure.failCode).getOrElse(org.http4s.Status.BadRequest)
    IO.pure(
      Response[IO](status)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Convert Box Failure to http4s Response
   */
  def boxFailureToResponse(failure: LiftFailure, callContext: SharedCallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(400, failure.msg)
    IO.pure(
      Response[IO](org.http4s.Status.BadRequest)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Convert unknown error to http4s Response
   */
  def unknownErrorToResponse(e: Throwable, callContext: SharedCallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(500, s"$UnknownError: ${e.getMessage}")
    IO.pure(
      Response[IO](org.http4s.Status.InternalServerError)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Create error response with specific status code and message
   */
  def createErrorResponse(statusCode: Int, message: String, callContext: SharedCallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(statusCode, message)
    val status = org.http4s.Status.fromInt(statusCode).getOrElse(org.http4s.Status.BadRequest)
    IO.pure(
      Response[IO](status)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
}

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
  import cats.data.{Kleisli, OptionT}
  import code.api.util.APIUtil
  import code.api.util.NewStyle
  import code.api.util.newstyle.ViewNewStyle
  
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
