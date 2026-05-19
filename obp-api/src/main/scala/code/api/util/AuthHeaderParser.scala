package code.api.util

import net.liftweb.common.{Box, Empty, Full}

/**
 * Transport-independent parsing of the HTTP `Authorization` header value
 * into the subset of CallContext fields used by the authentication chain.
 *
 * The auth chain in [[APIUtil.getUserAndSessionContextFuture]] identifies which
 * scheme to use (OAuth 2 / OIDC, DirectLogin, Gateway Login, DAuth) by reading
 * CallContext.authReqHeaderField / directLoginParams — *not* requestHeaders.
 * Every transport that supports authentication (REST via http4s, gRPC, etc.)
 * must populate these fields identically, otherwise schemes will silently fail
 * to match and the chain will fall through to "OBP-20080 Authorization Header
 * format is not supported".
 *
 * This helper is the single source of truth for that parsing so that all
 * transports stay in sync.
 */
object AuthHeaderParser {

  /** Result of parsing an Authorization header value. */
  final case class ParsedAuthHeader(
    authReqHeaderField: Box[String],
    directLoginParams: Map[String, String]
  )

  private val EmptyParsed: ParsedAuthHeader =
    ParsedAuthHeader(Empty, Map.empty)

  private val DirectLoginAllowedParameters: List[String] =
    List("consumer_key", "token", "username", "password")

  /**
   * Parse an Authorization header value (e.g. "Bearer eyJ...", "DirectLogin token=...")
   * into the auth-related CallContext fields.
   *
   * Returns empty fields when no header value is present.
   */
  def parseAuthorizationHeader(authHeaderValue: Option[String]): ParsedAuthHeader =
    authHeaderValue match {
      case None => EmptyParsed
      case Some(value) =>
        ParsedAuthHeader(
          authReqHeaderField = Full(value),
          directLoginParams = if (value.contains("DirectLogin")) parseDirectLoginHeader(value) else Map.empty
        )
    }

  /**
   * Parse a DirectLogin header value into its named parameters.
   * Accepts both:
   *   - `DirectLogin token="xxx", username="yyy"` (old Authorization header format, with prefix)
   *   - `token="xxx", username="yyy"`            (new dedicated `DirectLogin:` header, no prefix)
   *
   * Only the whitelisted parameters (`consumer_key`, `token`, `username`, `password`)
   * are kept. Mirrors Lift's getAllParameters in directlogin.scala.
   */
  def parseDirectLoginHeader(headerValue: String): Map[String, String] = {
    val cleanedParameterList = headerValue.stripPrefix("DirectLogin").split(",").map(_.trim).toList
    cleanedParameterList.flatMap { input =>
      if (input.contains("=")) {
        val split = input.split("=", 2)
        val paramName = split(0).trim
        val paramValue = split(1).replaceAll("^\"|\"$", "").trim
        if (DirectLoginAllowedParameters.contains(paramName) && paramValue.nonEmpty)
          Some(paramName -> paramValue)
        else
          None
      } else {
        None
      }
    }.toMap
  }
}
