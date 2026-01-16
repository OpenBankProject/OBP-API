package code.api.util.http4s

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
