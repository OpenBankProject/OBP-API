package code.api.util.http4s

import cats.effect._
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util.CallContext
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
 * - Http4sRequestAttributes: Request attribute key for storing CallContext
 * - ResourceDocMatcher: Matches http4s requests to ResourceDoc entries
 * 
 * Validated entities (User, Bank, BankAccount, View, Counterparty) are stored
 * directly in CallContext fields, making them available throughout the call chain.
 */

/**
 * Request attribute keys for storing CallContext in http4s requests.
 * 
 */
object Http4sRequestAttributes {
  // CallContext contains all request data and validated entities
  val callContextKey: Key[CallContext] = 
    Key.newKey[IO, CallContext].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  /**
   * Get CallContext from request attributes.
   * CallContext contains validated entities: bank, bankAccount, view, counterparty
   */
  def getCallContext(req: Request[IO]): Option[CallContext] = 
    req.attributes.lookup(callContextKey)
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
    // Strip the API prefix (/obp/vX.X.X) from the path for matching
    val strippedPath = apiPrefixPattern.replaceFirstIn(pathString, "")
    resourceDocs.find { doc =>
      doc.requestVerb.equalsIgnoreCase(verb) && matchesUrlTemplate(strippedPath, doc.requestUrl)
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
