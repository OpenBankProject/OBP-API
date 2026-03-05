package code.api

import code.api.Constant.localIdentityProvider
import code.api.util.ErrorMessages
import code.loginattempts.LoginAttempt
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.setup.{ServerSetup, TestPasswordConfig}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

/**
 * Property-based tests for authentication refactoring
 * Feature: centralize-authentication-logic
 * 
 * These tests verify universal properties that should hold across all authentication scenarios.
 * Note: This file provides test infrastructure. Property tests are optional and can be implemented later.
 */
class AuthenticationPropertyTest extends FlatSpec 
  with Matchers 
  with ServerSetup 
  with BeforeAndAfter {

  // ============================================================================
  // Test Data Generators (Simplified - no ScalaCheck)
  // ============================================================================

  /**
   * Generate a random valid username
   */
  def generateUsername(): String = {
    s"user_${randomString(8)}"
  }

  /**
   * Generate a random password
   */
  def generatePassword(): String = {
    randomString(12)
  }

  /**
   * Generate a random provider
   */
  def generateProvider(): String = {
    val providers = List(
      localIdentityProvider,
      "https://auth.example.com",
      "https://external-idp.com",
      "https://sso.company.com"
    )
    providers(scala.util.Random.nextInt(providers.length))
  }

  // ============================================================================
  // Test Data Setup Utilities
  // ============================================================================

  /**
   * Creates a test user with specified properties
   * @param username The username for the test user
   * @param password The password for the test user
   * @param provider The authentication provider
   * @param validated Whether the email is validated
   * @return The created AuthUser
   */
  def createTestUser(
    username: String, 
    password: String, 
    provider: String = localIdentityProvider,
    validated: Boolean = true
  ): AuthUser = {
    // Clean up any existing user
    AuthUser.findAll(By(AuthUser.username, username), By(AuthUser.provider, provider)).foreach(_.delete_!)
    
    // Create new user
    val user = AuthUser.create
      .email(s"${randomString(10)}@example.com")
      .username(username)
      .password(password)
      .provider(provider)
      .validated(validated)
      .firstName(randomString(10))
      .lastName(randomString(10))
      .saveMe()
    
    user
  }

  /**
   * Creates a locked test user
   * @param username The username for the locked user
   * @param password The password for the locked user
   * @param provider The authentication provider
   * @return The created AuthUser
   */
  def createLockedUser(
    username: String, 
    password: String, 
    provider: String = localIdentityProvider
  ): AuthUser = {
    val user = createTestUser(username, password, provider, validated = true)
    
    // Lock the user by incrementing bad login attempts beyond threshold
    for (_ <- 1 to 6) {
      LoginAttempt.incrementBadLoginAttempts(provider, username)
    }
    
    user
  }

  /**
   * Creates an unvalidated test user (email not validated)
   * @param username The username for the unvalidated user
   * @param password The password for the unvalidated user
   * @return The created AuthUser
   */
  def createUnvalidatedUser(username: String, password: String): AuthUser = {
    createTestUser(username, password, localIdentityProvider, validated = false)
  }

  /**
   * Cleans up test user and associated login attempts
   * @param username The username to clean up
   * @param provider The authentication provider
   */
  def cleanupTestUser(username: String, provider: String = localIdentityProvider): Unit = {
    AuthUser.findAll(By(AuthUser.username, username), By(AuthUser.provider, provider)).foreach(_.delete_!)
    LoginAttempt.resetBadLoginAttempts(provider, username)
  }

  /**
   * Gets the current bad login attempt count for a user
   * @param username The username to check
   * @param provider The authentication provider
   * @return The number of bad login attempts
   */
  def getBadLoginAttemptCount(username: String, provider: String = localIdentityProvider): Int = {
    LoginAttempt.getBadLoginAttempts(provider, username)
  }

  // ============================================================================
  // Basic Infrastructure Tests
  // ============================================================================

  "Test infrastructure" should "be set up correctly" in {
    val username = generateUsername()
    val password = generatePassword()
    
    username should not be empty
    password.length should be >= 8
  }

  "Test user creation and cleanup" should "work correctly" in {
    val testUsername = s"test_${randomString(10)}"
    val password = generatePassword()
    
    try {
      // Create test user
      val user = createTestUser(testUsername, password)
      user.username.get shouldBe testUsername
      user.validated.get shouldBe true
      
      // Verify user exists
      val foundUser = AuthUser.find(By(AuthUser.username, testUsername), By(AuthUser.provider, localIdentityProvider))
      foundUser.isDefined shouldBe true
    } finally {
      // Cleanup
      cleanupTestUser(testUsername)
    }
  }

  "Locked user creation" should "work correctly" in {
    val testUsername = s"locked_${randomString(10)}"
    val password = generatePassword()
    
    try {
      // Create locked user
      val user = createLockedUser(testUsername, password)
      
      // Verify user is locked
      LoginAttempt.userIsLocked(localIdentityProvider, testUsername) shouldBe true
    } finally {
      // Cleanup
      cleanupTestUser(testUsername)
    }
  }

  "Unvalidated user creation" should "work correctly" in {
    val testUsername = s"unvalidated_${randomString(10)}"
    val password = generatePassword()
    
    try {
      // Create unvalidated user
      val user = createUnvalidatedUser(testUsername, password)
      
      // Verify user is not validated
      user.validated.get shouldBe false
    } finally {
      // Cleanup
      cleanupTestUser(testUsername)
    }
  }
}
