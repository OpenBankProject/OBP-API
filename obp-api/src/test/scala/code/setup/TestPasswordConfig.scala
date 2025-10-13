package code.setup

import code.api.util.APIUtil
import net.liftweb.util.Helpers

/**
 * Test configuration utility for handling passwords securely in test environments.
 * This addresses SonarCloud warnings about hard-coded credentials by using properties configuration
 * with secure random fallbacks.
 */
object TestPasswordConfig {

  /**
   * Invalid password used for negative test cases.
   * Can be overridden via TEST_INVALID_PASSWORD property.
   */
  val INVALID_PASSWORD: String = APIUtil.getPropsValue("TEST_INVALID_PASSWORD", generateSecureTestPassword())

  /**
   * Valid password used for positive test cases.
   * Can be overridden via TEST_VALID_PASSWORD property.
   */
  val VALID_PASSWORD: String = APIUtil.getPropsValue("TEST_VALID_PASSWORD", generateSecureTestPassword())

  /**
   * Berlin Group API test password for PSU authentication.
   * Can be overridden via TEST_BERLIN_GROUP_PASSWORD property.
   */
  val BERLIN_GROUP_PASSWORD: String = APIUtil.getPropsValue("TEST_BERLIN_GROUP_PASSWORD", "start12")

  /**
   * Certificate/keystore password for test certificates.
   * Can be overridden via TEST_CERTIFICATE_PASSWORD property.
   */
  val CERTIFICATE_PASSWORD: String = APIUtil.getPropsValue("TEST_CERTIFICATE_PASSWORD", generateSecureTestPassword())

  /**
   * TPP signature password for Berlin Group tests.
   * Can be overridden via TEST_TPP_SIGNATURE_PASSWORD property.
   */
  val TPP_SIGNATURE_PASSWORD: String = APIUtil.getPropsValue("TEST_TPP_SIGNATURE_PASSWORD", "testpassword123")

  /**
   * Wrong/invalid password used for failed login attempt tests.
   * Can be overridden via TEST_WRONG_PASSWORD property.
   */
  val WRONG_PASSWORD: String = APIUtil.getPropsValue("TEST_WRONG_PASSWORD", "wrongpassword")

  /**
   * Generates a secure test password that meets common password complexity requirements.
   * @return A randomly generated password with uppercase, lowercase, numbers, and special characters
   */
  private def generateSecureTestPassword(): String = {
    val randomBase = Helpers.randomString(12)
    val specialChars = "!@#$%"
    val numbers = "123456789"
    val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    
    // Ensure password has mixed case, numbers, and special characters
    randomBase + uppercase.charAt(scala.util.Random.nextInt(uppercase.length)) + 
    numbers.charAt(scala.util.Random.nextInt(numbers.length)) +
    specialChars.charAt(scala.util.Random.nextInt(specialChars.length))
  }

  /**
   * Documentation for properties configuration:
   * 
   * TEST_INVALID_PASSWORD: Password used for authentication failure tests
   * TEST_VALID_PASSWORD: Password used for successful authentication tests
   * TEST_BERLIN_GROUP_PASSWORD: Password for Berlin Group PSU authentication (default: "start12")
   * TEST_CERTIFICATE_PASSWORD: Password for test certificates and keystores
   * TEST_TPP_SIGNATURE_PASSWORD: Password for TPP signature certificates (default: "testpassword123")
   * TEST_WRONG_PASSWORD: Password for testing failed login attempts (default: "wrongpassword")
   * 
   * Configuration can be set via:
   * 1. Environment variables: export TEST_INVALID_PASSWORD="invalidTestPass123!"
   * 2. System properties: -DTEST_VALID_PASSWORD="validTestPass456@"
   * 3. Props file entries: TEST_BERLIN_GROUP_PASSWORD="berlinGroupTest789#"
   * 
   * Example usage:
   * mvn test -DTEST_INVALID_PASSWORD="customInvalid" -DTEST_VALID_PASSWORD="customValid"
   * 
   * If not set, secure random passwords will be generated automatically (except for API-specific defaults).
   */
}