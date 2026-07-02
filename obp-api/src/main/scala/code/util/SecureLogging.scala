package code.util

import code.api.util.APIUtil

import java.util.regex.{Matcher, Pattern}
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
   * Conditional inclusion helper using APIUtil.getPropsAsBoolValue
   */
  private def conditionalPattern(
                                  prop: String,
                                  defaultValue: Boolean = true
                                )(pattern: => (Pattern, Matcher => String)): Option[(Pattern, Matcher => String)] = {
    if (APIUtil.getPropsAsBoolValue(prop, defaultValue)) Some(pattern) else None
  }

  /** Helper to create a static replacement function from a replacement string */
  private def staticReplacement(replacement: String): Matcher => String = _ => replacement

  /** Helper to create a partial-mask replacement that shows first 3 and last 3 chars of group 2 */
  private def partialMaskReplacement: Matcher => String = m => {
    val prefix = m.group(1)
    val value = m.group(2)
    if (value.length > 6) s"${prefix}${value.take(3)}...${value.takeRight(3)}"
    else s"${prefix}***"
  }

  /**
   * Toggleable sensitive patterns.
   * Note: The sensitive keywords are defined in APIUtil.sensitiveKeywords.
   * When adding new categories here, also update that shared list.
   */
  private lazy val sensitivePatterns: List[(Pattern, Matcher => String)] = {
    val patterns = Seq(
      // OAuth2 / API secrets
      conditionalPattern("securelogging_mask_secret") {
        (Pattern.compile("(?i)(secret=)([^,\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_client_secret") {
        (Pattern.compile("(?i)(client_secret[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_client_secret") {
        (Pattern.compile("(?i)(client_secret\\s*->\\s*)([^,\\s&\\)]+)"), staticReplacement("$1***"))
      },

      // Authorization / Tokens
      conditionalPattern("securelogging_mask_authorization") {
        (Pattern.compile("(?i)(Authorization:\\s*Bearer\\s+)([^\\s,&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_access_token") {
        (Pattern.compile("(?i)(access_token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_access_token") {
        (Pattern.compile("(?i)(access_token\\s*->\\s*)([^,\\s&\\)]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_refresh_token") {
        (Pattern.compile("(?i)(refresh_token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_refresh_token") {
        (Pattern.compile("(?i)(refresh_token\\s*->\\s*)([^,\\s&\\)]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_id_token") {
        (Pattern.compile("(?i)(id_token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_id_token") {
        (Pattern.compile("(?i)(id_token\\s*->\\s*)([^,\\s&\\)]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_token") {
        (Pattern.compile("(?i)(token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_token") {
        (Pattern.compile("(?i)(token\\s*->\\s*)([^,\\s&\\)]+)"), staticReplacement("$1***"))
      },

      // Passwords
      conditionalPattern("securelogging_mask_password") {
        (Pattern.compile("(?i)(password[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_password") {
        (Pattern.compile("(?i)(password\\s*->\\s*)([^,\\s&\\)]+)"), staticReplacement("$1***"))
      },

      // API keys - use partial masking to show first 3 and last 3 characters
      conditionalPattern("securelogging_mask_api_key") {
        (Pattern.compile("(?i)(api_key[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), partialMaskReplacement)
      },
      conditionalPattern("securelogging_mask_api_key") {
        (Pattern.compile("(?i)(api_key\\s*->\\s*)([^,\\s&\\)]+)"), partialMaskReplacement)
      },
      conditionalPattern("securelogging_mask_key") {
        (Pattern.compile("(?i)(key[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), partialMaskReplacement)
      },
      conditionalPattern("securelogging_mask_key") {
        (Pattern.compile("(?i)(key\\s*->\\s*)([^,\\s&\\)]+)"), partialMaskReplacement)
      },
      conditionalPattern("securelogging_mask_private_key") {
        (Pattern.compile("(?i)(private_key[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***"))
      },
      conditionalPattern("securelogging_mask_private_key") {
        (Pattern.compile("(?i)(private_key\\s*->\\s*)([^,\\s&\\)]+)"), staticReplacement("$1***"))
      },

      // Database
      conditionalPattern("securelogging_mask_jdbc") {
        (Pattern.compile("(?i)(jdbc:[^\\s]+://[^:]+:)([^@\\s]+)(@)"), staticReplacement("$1***$3"))
      },

      // Credit card
      conditionalPattern("securelogging_mask_credit_card") {
        (Pattern.compile("\\b([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{3,7})\\b"), staticReplacement("$1-****-****-$4"))
      },

      // Email addresses
      conditionalPattern("securelogging_mask_email") {
        (Pattern.compile("(?i)(email[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+@[^\"',\\s&]+)"), staticReplacement("$1***@***.***"))
      }
    )

    patterns.flatten.toList
  }

  // ===== Pattern cache for custom usage =====
  // Thread-safe: maskWithCustomPattern is called concurrently from many request threads. A plain
  // mutable.Map.getOrElseUpdate is not atomic and can corrupt the map during a concurrent resize.
  private val customPatternCache: scala.collection.concurrent.Map[String, Pattern] =
    scala.collection.concurrent.TrieMap.empty
  private def getOrCompileCustomPattern(regex: String): Pattern =
    customPatternCache.getOrElseUpdate(regex, Pattern.compile(regex, Pattern.CASE_INSENSITIVE))

  // ===== Masking Logic =====
  def maskSensitive(msg: AnyRef): String = {
    val msgString = Option(msg).map(_.toString).getOrElse("")
    if (msgString.isEmpty) return msgString

    sensitivePatterns.foldLeft(msgString) { case (acc, (pattern, replaceFn)) =>
      val matcher = pattern.matcher(acc)
      val sb = new StringBuffer()
      while (matcher.find()) {
        val replacement = replaceFn(matcher)
        // If the function returns a string with $ references (static replacements),
        // use appendReplacement which handles group references.
        // Otherwise, quote the replacement to avoid $ interpretation.
        if (replacement.contains("$1") || replacement.contains("$2") || replacement.contains("$3") || replacement.contains("$4")) {
          matcher.appendReplacement(sb, replacement)
        } else {
          matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
        }
      }
      matcher.appendTail(sb)
      sb.toString
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
   * Test method to demonstrate the masking functionality.
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
      "User email: sensitive@example.com in auth context",
      "Map(client_secret -> my_client_secret, token -> secret_token)",
      "Map(client_secret->my_client_secret, access_token->oauth_token_123)",
      "directLoginParams=Map(password -> secret123, api_key -> sk_live_key)",
      "client_secret -> my_client_secret",
      "client_secret->my_client_secret",
      "Map(token->private_token, password -> supersecret, api_key->sk_live_123)"
    )
    testMessages.map(msg => (msg, maskSensitive(msg)))
  }

  /**
   * Print test results to console for manual verification.
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
