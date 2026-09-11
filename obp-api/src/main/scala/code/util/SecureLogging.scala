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

  // sensitivePatterns' own initializer calls APIUtil.getPropsAsBoolValue below, which is the
  // first touch of APIUtil$ on this thread and so triggers APIUtil$'s class init - which
  // eagerly evaluates every APIUtil val, not just publicAppUrlDefaults (e.g. `vendor = new
  // CustomDBVendor(..., getPropsValue("db.password"))`), and some of those calls getPropsValue,
  // which logs a debug message when a prop is sourced from a sys-env var - a normal deployment
  // pattern for db.password. Every log call in MdcLoggable routes through maskSensitive, which
  // needs sensitivePatterns to mask anything, so this calls back into
  // maskSensitive -> sensitivePatterns, on the very same thread, before the first call has
  // returned. Scala 2's lazy val used a reentrant `synchronized` block, so the recursive call
  // silently passed through; Scala 3's LazyVals uses a CountDownLatch, which is not reentrant,
  // so the same thread deadlocks waiting on a latch only it could count down. This flag detects
  // that specific bootstrap window and applies bootstrapPatterns instead of recursing - not
  // "return unmasked", because the window is not limited to SecureLogging/APIUtil's own
  // messages: it is the whole APIUtil$ class-init cascade, on whatever thread first happens to
  // touch it, which can be a request thread just as easily as a startup thread, and can carry a
  // credential (db.password, db.url) through a log line that would otherwise be masked.
  private[util] val computingSensitivePatterns = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

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
    computingSensitivePatterns.set(true)
    try {
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
    } finally {
      computingSensitivePatterns.set(false)
    }
  }

  // Used only inside the computingSensitivePatterns window (see above): plain vals, no props
  // lookup, so applying them can't recurse back into APIUtil/sensitivePatterns and deadlock.
  // Not the full configurable pattern set - just the categories most likely to appear in a live
  // credential during this window (password, secret, token, a handful of "*_key" prefixes - see
  // the key pattern's own comment below for exactly which - Authorization header, jdbc URL) - so
  // the bootstrap window degrades to a narrower mask instead of no mask at all. Regex find()
  // matches anywhere in the string, not just at a word boundary, so "token" also catches
  // access_token/refresh_token/id_token without a separate pattern per variant.
  //
  // The key pattern requires an api_/private_/secret_/access_/encryption_/consumer_ prefix
  // rather than a bare "key", unlike sensitivePatterns' own (props-gated, opt-outable) "key"
  // pattern above: that bare form also matches "cache key: ..."/"primary key: ..." debug lines
  // that carry no credential (MappedMetrics.getAllAggregateMetricsBox logs exactly this shape),
  // and this list is neither configurable nor limited to messages that are actually
  // credential-shaped the way the full sensitivePatterns list's toggles let an operator scope
  // it - a false-positive redaction here silently destroys debug output with no way to turn it
  // back on.
  //
  // This prefix list is a known-common-case enumeration, not a closed/exhaustive one - "*_key"
  // credential vocabulary in this codebase is open-ended (grep turned up public_key/session_key
  // too, but neither has a confirmed log call site the way consumer_key does at
  // ConsentUtil.scala's "consumer_key='$consentConsumerKey'" debug line, so they were left out
  // rather than added speculatively). If a future log statement logs another "*_key"-shaped
  // credential during this window, add its prefix here rather than assuming the list already
  // covers it.
  private val bootstrapPatterns: List[(Pattern, Matcher => String)] = List(
    (Pattern.compile("(?i)(password[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***")),
    (Pattern.compile("(?i)(secret[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***")),
    (Pattern.compile("(?i)(token[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***")),
    (Pattern.compile("(?i)((?:api|private|secret|access|encryption|consumer)_key[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s&]+)"), staticReplacement("$1***")),
    (Pattern.compile("(?i)(Authorization:\\s*Bearer\\s+)([^\\s,&]+)"), staticReplacement("$1***")),
    (Pattern.compile("(?i)(jdbc:[^\\s]+://[^:]+:)([^@\\s]+)(@)"), staticReplacement("$1***$3"))
  )

  // ===== Pattern cache for custom usage =====
  // Thread-safe: maskWithCustomPattern is called concurrently from many request threads. A plain
  // mutable.Map.getOrElseUpdate is not atomic and can corrupt the map during a concurrent resize.
  private val customPatternCache: scala.collection.concurrent.Map[String, Pattern] =
    scala.collection.concurrent.TrieMap.empty
  private def getOrCompileCustomPattern(regex: String): Pattern =
    customPatternCache.getOrElseUpdate(regex, Pattern.compile(regex, Pattern.CASE_INSENSITIVE))

  // ===== Masking Logic =====
  private def applyPatterns(msgString: String, patterns: List[(Pattern, Matcher => String)]): String = {
    patterns.foldLeft(msgString) { case (acc, (pattern, replaceFn)) =>
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

  def maskSensitive(msg: AnyRef): String = {
    val msgString = Option(msg).map(_.toString).getOrElse("")
    if (msgString.isEmpty) return msgString
    if (computingSensitivePatterns.get()) return applyPatterns(msgString, bootstrapPatterns)

    applyPatterns(msgString, sensitivePatterns)
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
