package code.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SecureLoggingTest extends AnyFlatSpec with Matchers {

  "maskSensitive" should "mask a password normally, outside the sensitivePatterns bootstrap window" in {
    val masked = SecureLogging.maskSensitive("password=hunter2")
    masked should not include "hunter2"
  }

  /**
   * Regression for the sensitivePatterns bootstrap-window leak: computingSensitivePatterns is
   * set only while sensitivePatterns (a props-driven lazy val) is computing itself, to avoid a
   * reentrant-lazy-val deadlock (see the comment on computingSensitivePatterns). The fallback
   * used to be "return the message completely unmasked" for any log call that happens to run on
   * that same thread during that window - which is not limited to SecureLogging's own bootstrap
   * traffic, since it is the whole APIUtil$ class-init cascade. This drives the same guard a real
   * log call would hit mid-cascade, and asserts the fallback still redacts a credential rather
   * than emitting it in cleartext.
   */
  it should "still mask a password when called during the sensitivePatterns bootstrap window" in {
    SecureLogging.computingSensitivePatterns.set(true)
    try {
      val masked = SecureLogging.maskSensitive("System environment property value found for OBP_DB_PASSWORD : hunter2")
      masked should not include "hunter2"
    } finally {
      SecureLogging.computingSensitivePatterns.set(false)
    }
  }

  it should "still mask a jdbc URL password during the sensitivePatterns bootstrap window" in {
    SecureLogging.computingSensitivePatterns.set(true)
    try {
      val masked = SecureLogging.maskSensitive("jdbc:postgresql://user:hunter2@dbhost:5432/obp")
      masked should not include "hunter2"
    } finally {
      SecureLogging.computingSensitivePatterns.set(false)
    }
  }

  /**
   * bootstrapPatterns' first cut covered password/secret/token/jdbc only - a live Authorization
   * header or API key logged during the same window (the guard is not scoped to any particular
   * message source, see the comment above) passed through unmasked, since none of those four
   * categories match "Authorization: Bearer ..." or "api_key=...".
   */
  it should "still mask an Authorization bearer token during the sensitivePatterns bootstrap window" in {
    SecureLogging.computingSensitivePatterns.set(true)
    try {
      val masked = SecureLogging.maskSensitive("Authorization: Bearer eyJhbGciOiSECRETVALUE")
      masked should not include "eyJhbGciOiSECRETVALUE"
    } finally {
      SecureLogging.computingSensitivePatterns.set(false)
    }
  }

  /**
   * Each prefix bootstrapPatterns' key pattern actually alternates on, checked individually -
   * a future edit that drops or typos one of the five would otherwise compile and pass the full
   * suite silently, since no single prefix's coverage depended on any of the others.
   */
  for (prefix <- List("api", "private", "secret", "access", "encryption", "consumer")) {
    it should s"still mask a ${prefix}_key during the sensitivePatterns bootstrap window" in {
      SecureLogging.computingSensitivePatterns.set(true)
      try {
        val masked = SecureLogging.maskSensitive(s"${prefix}_key=sk_live_hunter2")
        masked should not include "sk_live_hunter2"
      } finally {
        SecureLogging.computingSensitivePatterns.set(false)
      }
    }
  }

  /**
   * A bare "key" pattern (rather than one requiring an api_/private_/secret_/access_/
   * encryption_ prefix) also matches non-credential debug lines like
   * MappedMetrics.getAllAggregateMetricsBox's "cache key: ...". bootstrapPatterns is neither
   * configurable nor scoped the way sensitivePatterns' toggles let an operator turn a specific
   * category off, so a false-positive match here silently destroys debug output during the
   * bootstrap window with no way to recover it - regression for the value surviving intact.
   */
  it should "not mask a non-credential 'cache key' debug line during the sensitivePatterns bootstrap window" in {
    SecureLogging.computingSensitivePatterns.set(true)
    try {
      val masked = SecureLogging.maskSensitive("getAllAggregateMetricsBox cache key: (foo,bar), TTL: 60 seconds")
      masked should include("(foo,bar)")
    } finally {
      SecureLogging.computingSensitivePatterns.set(false)
    }
  }
}
