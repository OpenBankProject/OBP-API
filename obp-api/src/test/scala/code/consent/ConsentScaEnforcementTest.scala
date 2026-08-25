package code.consent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * `consents.sca.enabled=false` must not disable answer verification in production.
 *
 * The switch exists so local development can confirm a consent without an OTP anyone can receive,
 * and off that path it should keep behaving exactly as it always has. In production it means
 * something else entirely: any caller who reaches the confirmation endpoint with a consent id in
 * INITIATED state moves it to ACCEPTED with an arbitrary string, and the only thing that said so
 * was a boot-time warning.
 *
 * Run mode cannot be changed from inside a test, so the decision is asserted through the pure
 * function `checkAnswer` delegates to rather than through the endpoint.
 */
class ConsentScaEnforcementTest extends AnyFlatSpec with Matchers {

  "SCA verification" should "be required in production even when the prop disables it" in {
    withClue("production ignores the switch - otherwise any answer confirms a consent: ") {
      MappedConsentProvider.scaVerificationRequired(scaEnabledProp = false, isProduction = true) should equal(true)
    }
  }

  it should "stay off outside production when the prop disables it" in {
    withClue("the switch must keep working where it is meant to - development without an OTP: ") {
      MappedConsentProvider.scaVerificationRequired(scaEnabledProp = false, isProduction = false) should equal(false)
    }
  }

  it should "be required whenever the prop enables it, in any mode" in {
    MappedConsentProvider.scaVerificationRequired(scaEnabledProp = true, isProduction = false) should equal(true)
    MappedConsentProvider.scaVerificationRequired(scaEnabledProp = true, isProduction = true) should equal(true)
  }
}
