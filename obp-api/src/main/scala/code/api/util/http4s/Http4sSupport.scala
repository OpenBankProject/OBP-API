package code.api.util.http4s

import cats.effect._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.CallContext
import com.openbankproject.commons.model.{Bank, User}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.provider.HTTPParam
import org.http4s._
import org.http4s.dsl.io._
import org.typelevel.ci.CIString
import org.typelevel.vault.Key

import java.util.{Date, UUID}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.higherKinds

/**
 * Http4s support utilities for OBP API.
 * 
 * This file contains three main components:
 * 
 * 1. Http4sRequestAttributes: Request attribute management and endpoint helpers
 *    - Stores CallContext in http4s request Vault
 *    - Provides helper methods to simplify endpoint implementations
 *    - Validated entities are stored in CallContext fields
 * 
 * 2. Http4sCallContextBuilder: Builds CallContext from http4s Request[IO]
 *    - Extracts headers, auth params, and request metadata
 *    - Supports DirectLogin, OAuth, and Gateway authentication
 * 
 * 3. ResourceDocMatcher: Matches requests to ResourceDoc entries
 *    - Finds ResourceDoc by HTTP verb and URL pattern
 *    - Extracts path parameters (BANK_ID, ACCOUNT_ID, etc.)
 *    - Attaches ResourceDoc to CallContext for metrics/rate limiting
 */

/**
 * Request attributes and helper methods for http4s endpoints.
 * 
 * CallContext is stored in request attributes using http4s Vault (type-safe key-value store).
 * Validated entities (user, bank, bankAccount, view, counterparty) are stored within CallContext.
 */
object Http4sRequestAttributes {
  
  /**
   * Vault key for storing CallContext in http4s request attributes.
   * CallContext contains request data and validated entities (user, bank, account, view, counterparty).
   */
  val callContextKey: Key[CallContext] = 
    Key.newKey[IO, CallContext].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  /**
   * Implicit class that adds .callContext accessor to Request[IO].
   * 
   * Usage:
   * {{{
   * import Http4sRequestAttributes.RequestOps
   * 
   * case req @ GET -> Root / "banks" =>
   *   implicit val cc: CallContext = req.callContext
   *   // Use cc for business logic
   * }}}
   */
  implicit class RequestOps(val req: Request[IO]) extends AnyVal {
    /**
     * Extract CallContext from request attributes.
     * Throws RuntimeException if not found (should never happen with ResourceDocMiddleware).
     */
    def callContext: CallContext = {
      req.attributes.lookup(callContextKey).getOrElse(
        throw new RuntimeException("CallContext not found in request attributes")
      )
    }
  }
  
  /**
   * Helper methods to eliminate boilerplate in endpoint implementations.
   * 
   * These methods handle:
   * - CallContext extraction from request
   * - User/Bank extraction from CallContext
   * - Future execution with IO.fromFuture
   * - JSON serialization with Lift JSON
   * - Ok response creation
   */
  object EndpointHelpers {
    import net.liftweb.json.JsonAST.prettyRender
    import net.liftweb.json.{Extraction, Formats}

    private def toJsonOk[A](result: A)(implicit formats: Formats): IO[Response[IO]] = {
      val jsonString = prettyRender(Extraction.decompose(result))
      Ok(jsonString)
    }

    /**
     * Execute Future-based business logic and return JSON response.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def executeAndRespond[A](req: Request[IO])(f: CallContext => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      IO.fromFuture(IO(f(cc))).attempt.flatMap {
        case Right(result) => toJsonOk(result)
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
      }
    }
    
    /**
     * Execute business logic requiring validated User.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withUser[A](req: Request[IO])(f: (User, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user <- IO.fromOption(cc.user.toOption)(new RuntimeException("User not found in CallContext"))
        result <- IO.fromFuture(IO(f(user, cc)))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result)
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
      }
    }
    
    /**
     * Execute business logic requiring validated Bank.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withBank[A](req: Request[IO])(f: (Bank, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        bank <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
        result <- IO.fromFuture(IO(f(bank, cc)))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result)
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
      }
    }
    
    /**
     * Execute business logic requiring both User and Bank.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withUserAndBank[A](req: Request[IO])(f: (User, Bank, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user <- IO.fromOption(cc.user.toOption)(new RuntimeException("User not found in CallContext"))
        bank <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
        result <- IO.fromFuture(IO(f(user, bank, cc)))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result)
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
      }
    }

    /**
     * Execute Future-based business logic with error handling.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     * Takes a by-name Future (caller manages CallContext themselves).
     */
    def executeFuture[A](req: Request[IO])(f: => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      IO.fromFuture(IO(f)).attempt.flatMap {
        case Right(result) => toJsonOk(result)
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
      }
    }

    /**
     * Execute Future-based business logic with error handling.
     * Returns 201 Created on success, converts errors via ErrorResponseConverter.
     */
    def executeFutureCreated[A](req: Request[IO])(f: => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      IO.fromFuture(IO(f)).attempt.flatMap {
        case Right(result) =>
          val jsonString = prettyRender(Extraction.decompose(result))
          Created(jsonString)
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
      }
    }
  }
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
   * @return IO[CallContext] with all request data populated
   */
  def fromRequest(request: Request[IO], apiVersion: String): IO[CallContext] = {
    for {
      body <- request.bodyText.compile.string.map(s => if (s.isEmpty) None else Some(s))
    } yield CallContext(
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
   * Supports two formats:
   * 1. New format (2021): DirectLogin: token=xxx
   * 2. Old format (deprecated): Authorization: DirectLogin token=xxx
   */
  private def extractDirectLoginParams(request: Request[IO]): Map[String, String] = {
    // Try new format first: DirectLogin header
    request.headers.get(CIString("DirectLogin"))
      .map(h => parseDirectLoginHeader(h.head.value))
      .getOrElse {
        // Fall back to old format: Authorization: DirectLogin token=xxx
        request.headers.get(CIString("Authorization"))
          .filter(_.head.value.contains("DirectLogin"))
          .map(h => parseDirectLoginHeader(h.head.value))
          .getOrElse(Map.empty)
      }
  }
  
  /**
   * Parse DirectLogin header value into parameter map
   * Matches Lift's parsing logic in directlogin.scala getAllParameters
   * Supports formats:
   * - DirectLogin token="xxx"
   * - DirectLogin token=xxx
   * - token="xxx", username="yyy"
   */
  private def parseDirectLoginHeader(headerValue: String): Map[String, String] = {
    val directLoginPossibleParameters = List("consumer_key", "token", "username", "password")
    
    // Strip "DirectLogin" prefix and split by comma, then trim each part (matches Lift logic)
    val cleanedParameterList = headerValue.stripPrefix("DirectLogin").split(",").map(_.trim).toList
    
    cleanedParameterList.flatMap { input =>
      if (input.contains("=")) {
        val split = input.split("=", 2)
        val paramName = split(0).trim
        // Remove surrounding quotes if present
        val paramValue = split(1).replaceAll("^\"|\"$", "").trim
        if (directLoginPossibleParameters.contains(paramName) && paramValue.nonEmpty)
          Some(paramName -> paramValue)
        else
          None
      } else {
        None
      }
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
  
  // API prefix pattern: /obp/vX.X.X
  private val apiPrefixPattern = """^/obp/v\d+\.\d+\.\d+""".r
  
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
    // Extract API version from path (e.g., "v5.0.0" from "/obp/v5.0.0/banks")
    val apiVersion = pathString.split("/").filter(_.nonEmpty).drop(1).headOption.getOrElse("")
    // Strip the API prefix (/obp/vX.X.X) from the path for matching
    val strippedPath = apiPrefixPattern.replaceFirstIn(pathString, "")
    resourceDocs.find { doc =>
      doc.requestVerb.equalsIgnoreCase(verb) && 
      doc.implementedInApiVersion.toString == apiVersion &&
      matchesUrlTemplate(strippedPath, doc.requestUrl)
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
    // Strip the API prefix (/obp/vX.X.X) from the path for matching
    val strippedPath = apiPrefixPattern.replaceFirstIn(pathString, "")
    val pathSegments = strippedPath.split("/").filter(_.nonEmpty)
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
    callContext: CallContext,
    resourceDoc: ResourceDoc
  ): CallContext = {
    callContext.copy(
      resourceDocument = Some(resourceDoc),
      operationId = Some(resourceDoc.operationId)
    )
  }
}
