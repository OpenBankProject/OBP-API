package code.util

import code.util.Helper.MdcLoggable
import net.liftweb.common.Loggable

/**
 * SecureLoggingDemo - Demonstration of secure logging functionality in OBP API
 * 
 * This file demonstrates how to use the secure logging features to automatically
 * mask sensitive data like secrets, tokens, passwords, and API keys from log output.
 * 
 * The secure logging functionality is implemented in two ways:
 * 1. Automatically in MdcLoggable trait - all logging is automatically masked
 * 2. Manually using SecureLogging utility methods for fine-grained control
 */
object SecureLoggingDemo extends Loggable {

  /**
   * Example class that extends MdcLoggable to get automatic secure logging
   */
  class OAuthService extends MdcLoggable {
    
    def authenticateUser(clientSecret: String, accessToken: String): Unit = {
      // This will automatically mask sensitive data before logging
      logger.info(s"Authenticating user with client_secret=$clientSecret and access_token=$accessToken")
      
      // Simulate an OAuth error that might contain sensitive data
      val errorMsg = s"OBP-50014: OAuth authentication failed. client_secret=$clientSecret, token=$accessToken"
      logger.error(errorMsg)
      
      // Even complex JSON-like strings are masked
      val jsonResponse = s"""{"client_secret": "$clientSecret", "access_token": "$accessToken", "status": "failed"}"""
      logger.warn(s"OAuth response: $jsonResponse")
    }
    
    def processPayment(cardNumber: String, apiKey: String): Unit = {
      // Credit cards and API keys are also masked
      logger.info(s"Processing payment for card $cardNumber with api_key=$apiKey")
      
      // Database connection strings with passwords are masked too
      val dbUrl = "jdbc:mysql://localhost:3306/obp?user=admin:supersecret123@dbhost"
      logger.debug(s"Connecting to database: $dbUrl")
    }
  }

  /**
   * Example of using SecureLogging utility methods directly
   */
  class PaymentService extends Loggable {
    
    def processTransaction(authHeader: String, clientId: String): Unit = {
      // Using SecureLogging utility methods for manual control
      SecureLogging.safeInfo(logger, s"Processing transaction with $authHeader and client_id=$clientId")
      
      // You can also mask messages before using them elsewhere
      val sensitiveMessage = s"Transaction failed: Authorization: Bearer abc123xyz, client_secret=mySecret"
      val maskedMessage = SecureLogging.maskSensitive(sensitiveMessage)
      
      // Now you can safely use maskedMessage anywhere
      println(s"Safe message: $maskedMessage")
      
      // Or log it normally since it's already masked
      logger.error(maskedMessage)
    }
  }

  /**
   * Demonstration of various sensitive data patterns that are automatically masked
   */
  def demonstratePatterns(): Unit = {
    println("\n=== Secure Logging Pattern Demonstration ===\n")
    
    val testCases = List(
      // OAuth2 and API secrets
      ("OAuth Secret", "client_secret=abc123def456"),
      ("API Key", "api_key=sk_live_1234567890abcdef"),
      ("Generic Secret", "secret=V6knYTLivzqHeTjBKf0X1DTCa8q4rzyJOq3AiLHsCDM"),
      
      // Tokens and authorization
      ("Bearer Token", "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"),
      ("Access Token", "access_token=ya29.a0ARrdaM9example"),
      ("Refresh Token", "refresh_token=1//04example"),
      
      // JSON format
      ("JSON Secrets", """{"client_secret": "mySecret", "access_token": "token123"}"""),
      
      // Passwords
      ("Password", "password=supersecret123&username=john"),
      ("Database URL", "jdbc:mysql://user:password123@localhost:3306/db"),
      
      // Credit cards
      ("Credit Card", "Payment with card 4532-1234-5678-9012 was processed"),
      
      // Email in sensitive context
      ("Email", "email=admin@example.com in auth context")
    )
    
    testCases.foreach { case (label, original) =>
      val masked = SecureLogging.maskSensitive(original)
      println(s"$label:")
      println(s"  Original: $original")
      println(s"  Masked:   $masked")
      println()
    }
  }

  /**
   * Example of how to create custom masking patterns for specific use cases
   */
  def demonstrateCustomMasking(): Unit = {
    println("=== Custom Masking Patterns ===\n")
    
    // Example: Mask custom internal identifiers
    val message = "Processing user internal_id=USER_12345_SECRET and session_key=SESS_ABCD_XYZ"
    
    // Create custom pattern for internal IDs
    val customPattern = "(?i)(internal_id=)([^\\s,&]+)"
    val customReplacement = "$1***"
    
    val maskedWithCustom = SecureLogging.maskWithCustomPattern(customPattern, customReplacement, message)
    
    println(s"Original: $message")
    println(s"Custom Masked: $maskedWithCustom")
    println()
  }

  /**
   * Performance demonstration - showing that masking has minimal overhead
   */
  def demonstratePerformance(): Unit = {
    println("=== Performance Test ===\n")
    
    val testMessage = "client_secret=abc123 password=secret token=xyz789"
    val iterations = 100000
    
    // Test without masking
    val startTime1 = System.nanoTime()
    for (_ <- 1 to iterations) {
      val _ = testMessage.toString // Simulate normal logging
    }
    val time1 = System.nanoTime() - startTime1
    
    // Test with masking
    val startTime2 = System.nanoTime()
    for (_ <- 1 to iterations) {
      val _ = SecureLogging.maskSensitive(testMessage)
    }
    val time2 = System.nanoTime() - startTime2
    
    println(s"Normal logging (${iterations} iterations): ${time1/1000000} ms")
    println(s"Secure logging (${iterations} iterations): ${time2/1000000} ms")
    println(s"Overhead: ${((time2.toDouble/time1.toDouble - 1) * 100).round}%")
    println()
  }

  /**
   * Main demonstration method
   */
  def main(args: Array[String]): Unit = {
    println("OBP API Secure Logging Demonstration")
    println("====================================\n")
    
    // 1. Demonstrate automatic masking with MdcLoggable
    println("1. Automatic Masking with MdcLoggable:")
    val oauthService = new OAuthService()
    oauthService.authenticateUser("myClientSecret123", "accessToken456")
    oauthService.processPayment("4532-1234-5678-9012", "sk_test_abcd1234")
    println()
    
    // 2. Demonstrate manual masking with SecureLogging utility
    println("2. Manual Masking with SecureLogging Utility:")
    val paymentService = new PaymentService()
    paymentService.processTransaction("Authorization: Bearer xyz789", "client_12345")
    println()
    
    // 3. Show all supported patterns
    demonstratePatterns()
    
    // 4. Custom masking patterns
    demonstrateCustomMasking()
    
    // 5. Performance test
    demonstratePerformance()
    
    // 6. Run built-in tests
    println("=== Built-in Test Results ===")
    SecureLogging.printTestResults()
    
    println("Demonstration complete!")
  }

  /**
   * Integration example for existing OBP API code
   */
  object IntegrationExamples {
    
    /**
     * Example: How to retrofit existing logging in API endpoints
     */
    class AccountsEndpoint extends MdcLoggable {
      
      def getAccount(authHeader: String, accountId: String): Unit = {
        // Old way (potentially unsafe):
        // logger.info(s"Getting account $accountId with auth $authHeader")
        
        // New way (automatically safe with MdcLoggable):
        logger.info(s"Getting account $accountId with auth $authHeader")
        
        // For existing loggers that don't use MdcLoggable:
        // SecureLogging.safeInfo(someExistingLogger, s"Message with $authHeader")
      }
      
      // Make this method accessible for demonstration
      def demonstrateLogging(authHeader: String, accountId: String): Unit = {
        getAccount(authHeader, accountId)
      }
    }
    
    /**
     * Example: How to handle exceptions that might contain sensitive data
     */
    def handleOAuthException(exception: Exception): Unit = {
      // Exception messages might contain sensitive data
      val errorMessage = s"OAuth error: ${exception.getMessage}"
      
      // Safe way to log exceptions using SecureLogging utility
      val logger = net.liftweb.common.Logger("OAuthHandler")
      SecureLogging.safeError(logger, errorMessage, exception)
      
      // Or manually mask if needed
      val maskedError = SecureLogging.maskSensitive(errorMessage)
      println(s"Safe error message: $maskedError")
    }
    
    /**
     * Demonstrate the AccountsEndpoint functionality
     */
    def demonstrateAccountEndpoint(): Unit = {
      val endpoint = new AccountsEndpoint()
      endpoint.demonstrateLogging("Authorization: Bearer secret123", "account456")
    }
    
    /**
     * Example: Configuration and connection string logging
     */
    def logConfiguration(): Unit = {
      val configs = Map(
        "database.url" -> "jdbc:postgresql://user:password123@localhost:5432/obp",
        "redis.url" -> "redis://admin:secret456@redis-server:6379",
        "oauth.client_secret" -> "oauth_secret_xyz789",
        "api.key" -> "api_key_abc123"
      )
      
      val logger = net.liftweb.common.Logger("ConfigLogger")
      configs.foreach { case (key, value) =>
        // This will automatically mask sensitive values using SecureLogging
        SecureLogging.safeDebug(logger, s"Config $key = $value")
      }
    }
  }
}