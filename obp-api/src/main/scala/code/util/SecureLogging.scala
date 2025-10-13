package code.util

import code.api.util.APIUtil

import java.util.regex.Pattern
import scala.collection.mutable

/**
 * SecureLogging utility for masking sensitive data in logs.
 *
 * Each pattern can be toggled via props:
 *   securelogging_mask_client_secret=true|false
 *
 * Default: all patterns enabled (true)
 */
object SecureLogging {

  /**
   * ✅ Conditional inclusion helper using APIUtil.getPropsAsBoolValue
   */
  private def conditionalPattern(
                                  prop: String,
                                  defaultValue: Boolean = true
                                )(pattern: => (Pattern, String)): Option[(Pattern, String)] = {
    if (APIUtil.getPropsAsBoolValue(prop, defaultValue)) Some(pattern) else None
  }

  /**
   * ✅ Toggleable sensitive patterns
   */
  private lazy val sensitivePatterns: List[(Pattern, String)] = {
    val patterns = Seq(
      // OAuth2 / API secrets
      conditionalPattern("securelogging_mask_secret") {
        (Pattern.compile("(?i)(secret=)([^,\\s&]+)"), "$1***")
      },
      conditionalPattern("securelogging_mask_client_secret") {
        (Pattern.compile("(?i)(client_secret[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },

      // Authorization / Tokens
      conditionalPattern("securelogging_mask_authorization") {
        (Pattern.compile("(?i)(Authorization:\\s*Bearer\\s+)([^\\s,&]+)"), "$1***")
      },
      conditionalPattern("securelogging_mask_access_token") {
        (Pattern.compile("(?i)(access_token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },
      conditionalPattern("securelogging_mask_refresh_token") {
        (Pattern.compile("(?i)(refresh_token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },
      conditionalPattern("securelogging_mask_id_token") {
        (Pattern.compile("(?i)(id_token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },
      conditionalPattern("securelogging_mask_token") {
        (Pattern.compile("(?i)(token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },

      // Passwords
      conditionalPattern("securelogging_mask_password") {
        (Pattern.compile("(?i)(password[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },

      // API keys
      conditionalPattern("securelogging_mask_api_key") {
        (Pattern.compile("(?i)(api_key[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },
      conditionalPattern("securelogging_mask_key") {
        (Pattern.compile("(?i)(key[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },
      conditionalPattern("securelogging_mask_private_key") {
        (Pattern.compile("(?i)(private_key[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), "$1***")
      },

      // Database
      conditionalPattern("securelogging_mask_jdbc") {
        (Pattern.compile("(?i)(jdbc:[^\\s]+://[^:]+:)([^@\\s]+)(@)"), "$1***$3")
      },

      // Credit card
      conditionalPattern("securelogging_mask_credit_card") {
        (Pattern.compile("\\b([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{3,7})\\b"), "$1-****-****-$4")
      },

      // Email addresses
      conditionalPattern("securelogging_mask_email") {
        (Pattern.compile("(?i)(email[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+@[^\"',\\s&]+)"), "$1***@***.***")
      }
    )

    patterns.flatten.toList
  }

  // ===== Pattern cache for custom usage =====
  private val customPatternCache: mutable.Map[String, Pattern] = mutable.Map.empty
  private def getOrCompileCustomPattern(regex: String): Pattern =
    customPatternCache.getOrElseUpdate(regex, Pattern.compile(regex, Pattern.CASE_INSENSITIVE))

  // ===== Masking Logic =====
  def maskSensitive(msg: AnyRef): String = {
    val msgString = Option(msg).map(_.toString).getOrElse("")
    if (msgString.isEmpty) return msgString

    sensitivePatterns.foldLeft(msgString) { case (acc, (pattern, replacement)) =>
      pattern.matcher(acc).replaceAll(replacement)
    }
  }

  def maskSensitive(msg: String): String = maskSensitive(msg.asInstanceOf[AnyRef])

  // ===== Safe Logging =====
  def safeInfo(logger: net.liftweb.common.Logger, msg: => AnyRef): Unit =
    logger.info(maskSensitive(msg))

  def safeInfo(logger: net.liftweb.common.Logger, msg: => AnyRef, t: => Throwable): Unit =
    logger.info(maskSensitive(msg), t)

  def safeWarn(logger: net.liftweb.common.Logger, msg: => AnyRef): Unit =
    logger.warn(maskSensitive(msg))

  def safeWarn(logger: net.liftweb.common.Logger, msg: => AnyRef, t: Throwable): Unit =
    logger.warn(maskSensitive(msg), t)

  def safeError(logger: net.liftweb.common.Logger, msg: => AnyRef): Unit =
    logger.error(maskSensitive(msg))

  def safeError(logger: net.liftweb.common.Logger, msg: => AnyRef, t: Throwable): Unit =
    logger.error(maskSensitive(msg), t)

  def safeDebug(logger: net.liftweb.common.Logger, msg: => AnyRef): Unit =
    logger.debug(maskSensitive(msg))

  def safeDebug(logger: net.liftweb.common.Logger, msg: => AnyRef, t: Throwable): Unit =
    logger.debug(maskSensitive(msg), t)

  def safeTrace(logger: net.liftweb.common.Logger, msg: => AnyRef): Unit =
    logger.trace(maskSensitive(msg))

  def safeTrace(logger: net.liftweb.common.Logger, msg: => AnyRef, t: Throwable): Unit =
    logger.trace(maskSensitive(msg).asInstanceOf[AnyRef], t)


  // ===== Custom Masking =====
  def maskWithCustomPattern(pattern: String, replacement: String, msg: String): String = {
    val compiledPattern = getOrCompileCustomPattern(pattern)
    val masked = maskSensitive(msg)
    compiledPattern.matcher(masked).replaceAll(replacement)
  }

  /**
   * ✅ Test method to demonstrate the masking functionality.
   */
  def testMasking(): List[(String, String)] = {
    val testMessages = List(
      "OBP-50014: Can not refresh User. secret=V6knYTLivzqHeTjBKf0X1DTCa8q4rzyJOq3AiLHsCDM",
      """{"client_secret": "mySecretKey123", "access_token": "tokenABC"}""",
      "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",
      "password=supersecret123&username=testuser",
      "api_key=sk_test_1234567890abcdef",
      "Error connecting to jdbc:mysql://localhost:3306/obp?user=admin:secretpassword@dbhost",
      "Credit card: 4532-1234-5678-9012 was processed",
      "User email: sensitive@example.com in auth context"
    )
    testMessages.map(msg => (msg, maskSensitive(msg)))
  }

  /**
   * ✅ Print test results to console for manual verification.
   */
  def printTestResults(): Unit = {
    println("\n=== SecureLogging Test Results ===")
    testMasking().foreach { case (original, masked) =>
      println(s"Original: $original")
      println(s"Masked:   $masked")
      println("---")
    }
    println("=== End Test Results ===\n")
  }
}
