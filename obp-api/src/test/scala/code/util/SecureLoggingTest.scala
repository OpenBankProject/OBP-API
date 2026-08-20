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
}
