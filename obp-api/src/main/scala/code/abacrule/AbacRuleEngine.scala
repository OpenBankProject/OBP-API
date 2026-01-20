package code.abacrule

import code.api.util.{APIUtil, CallContext, DynamicUtil}
import code.bankconnectors.Connector
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.entitlement.Entitlement
import com.openbankproject.commons.model._
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * ABAC Rule Engine for compiling and executing Attribute-Based Access Control rules
 */
object AbacRuleEngine {

  // Cache for compiled ABAC rule functions
  private val compiledRulesCache: concurrent.Map[String, Box[AbacRuleFunction]] = 
    new ConcurrentHashMap[String, Box[AbacRuleFunction]]().asScala

  /**
   * Type alias for compiled ABAC rule function
   * Parameters: authenticatedUser (logged in), authenticatedUserAttributes (non-personal), authenticatedUserAuthContext (auth context), authenticatedUserEntitlements (roles),
   *             onBehalfOfUser (delegation), onBehalfOfUserAttributes, onBehalfOfUserAuthContext, onBehalfOfUserEntitlements,
   *             user, userAttributes, bankOpt, bankAttributes, accountOpt, accountAttributes, transactionOpt, transactionAttributes, customerOpt, customerAttributes
   * Returns: Boolean (true = allow access, false = deny access)
   */
  type AbacRuleFunction = (User, List[UserAttributeTrait], List[UserAuthContext], List[Entitlement], Option[User], List[UserAttributeTrait], List[UserAuthContext], List[Entitlement], Option[User], List[UserAttributeTrait], Option[Bank], List[BankAttributeTrait], Option[BankAccount], List[AccountAttribute], Option[Transaction], List[TransactionAttribute], Option[TransactionRequest], List[TransactionRequestAttributeTrait], Option[Customer], List[CustomerAttribute], Option[CallContext]) => Boolean

  /**
   * Compile an ABAC rule from Scala code
   * 
   * @param ruleId Unique identifier for the rule
   * @param ruleCode Scala code that defines the rule function
   * @return Box containing the compiled function or error
   */
  def compileRule(ruleId: String, ruleCode: String): Box[AbacRuleFunction] = {
    compiledRulesCache.get(ruleId) match {
      case Some(cachedFunction) => cachedFunction
      case None =>
        val compiledFunction = compileRuleInternal(ruleCode)
        compiledRulesCache.put(ruleId, compiledFunction)
        compiledFunction
    }
  }

  /**
   * Internal method to compile ABAC rule code
   */
  private def compileRuleInternal(ruleCode: String): Box[AbacRuleFunction] = {
    val fullCode = buildFullRuleCode(ruleCode)
    
    DynamicUtil.compileScalaCode[AbacRuleFunction](fullCode) match {
      case Full(func) => Full(func)
      case Failure(msg, exception, _) => 
        Failure(s"Failed to compile ABAC rule: $msg", exception, Empty)
      case Empty => 
        Failure("Failed to compile ABAC rule: Unknown error")
    }
  }

  /**
   * Build complete Scala code for compilation
   */
  private def buildFullRuleCode(ruleCode: String): String = {
    s"""
       |import com.openbankproject.commons.model._
       |import code.model.dataAccess.ResourceUser
       |import net.liftweb.common._
       |import code.entitlement.Entitlement
       |import code.api.util.CallContext
       |
       |// ABAC Rule Function
       |(authenticatedUser: User, authenticatedUserAttributes: List[UserAttributeTrait], authenticatedUserAuthContext: List[UserAuthContext], authenticatedUserEntitlements: List[Entitlement], onBehalfOfUserOpt: Option[User], onBehalfOfUserAttributes: List[UserAttributeTrait], onBehalfOfUserAuthContext: List[UserAuthContext], onBehalfOfUserEntitlements: List[Entitlement], userOpt: Option[User], userAttributes: List[UserAttributeTrait], bankOpt: Option[Bank], bankAttributes: List[BankAttributeTrait], accountOpt: Option[BankAccount], accountAttributes: List[AccountAttribute], transactionOpt: Option[Transaction], transactionAttributes: List[TransactionAttribute], transactionRequestOpt: Option[TransactionRequest], transactionRequestAttributes: List[TransactionRequestAttributeTrait], customerOpt: Option[Customer], customerAttributes: List[CustomerAttribute], callContext: Option[code.api.util.CallContext]) => {
       |  $ruleCode
       |}
       |""".stripMargin
  }

  /**
   * Execute an ABAC rule by IDs (objects are fetched internally)
   * 
   * @param ruleId The ID of the rule to execute
   * @param authenticatedUserId The ID of the authenticated user (the person logged in)
   * @param onBehalfOfUserId Optional ID of user being acted on behalf of (delegation scenario)
   * @param userId The ID of the target user to evaluate (defaults to authenticated user if not provided)
   * @param callContext Call context for fetching objects
   * @param bankId Optional bank ID
   * @param accountId Optional account ID
   * @param viewId Optional view ID (for future use)
   * @param transactionId Optional transaction ID
   * @param transactionRequestId Optional transaction request ID
   * @param customerId Optional customer ID
   * @return Box[Boolean] - Full(true) if allowed, Full(false) if denied, Failure on error
   */
  def executeRule(
    ruleId: String,
    authenticatedUserId: String,
    onBehalfOfUserId: Option[String] = None,
    userId: Option[String] = None,
    callContext: CallContext,
    bankId: Option[String] = None,
    accountId: Option[String] = None,
    viewId: Option[String] = None,
    transactionId: Option[String] = None,
    transactionRequestId: Option[String] = None,
    customerId: Option[String] = None
  ): Box[Boolean] = {
    for {
      rule <- MappedAbacRuleProvider.getAbacRuleById(ruleId)
      _ <- if (rule.isActive) Full(true) else Failure(s"ABAC Rule ${rule.ruleName} is not active")
      
      // Fetch authenticated user (the actual person logged in)
      authenticatedUser <- Users.users.vend.getUserByUserId(authenticatedUserId)
      
      // Fetch non-personal attributes for authenticated user
      authenticatedUserAttributes = Await.result(
        code.api.util.NewStyle.function.getNonPersonalUserAttributes(authenticatedUserId, Some(callContext)).map(_._1),
        5.seconds
      )
      
      // Fetch auth context for authenticated user
      authenticatedUserAuthContext = Await.result(
        code.api.util.NewStyle.function.getUserAuthContexts(authenticatedUserId, Some(callContext)).map(_._1),
        5.seconds
      )
      
      // Fetch entitlements for authenticated user
      authenticatedUserEntitlements = Await.result(
        code.api.util.NewStyle.function.getEntitlementsByUserId(authenticatedUserId, Some(callContext)),
        5.seconds
      )
      
      // Fetch onBehalfOf user if provided (delegation scenario)
      onBehalfOfUserOpt <- onBehalfOfUserId match {
        case Some(obUserId) => Users.users.vend.getUserByUserId(obUserId).map(Some(_))
        case None => Full(None)
      }
      
      // Fetch attributes for onBehalfOf user if provided
      onBehalfOfUserAttributes = onBehalfOfUserId match {
        case Some(obUserId) =>
          Await.result(
            code.api.util.NewStyle.function.getNonPersonalUserAttributes(obUserId, Some(callContext)).map(_._1),
            5.seconds
          )
        case None => List.empty[UserAttributeTrait]
      }
      
      // Fetch auth context for onBehalfOf user if provided
      onBehalfOfUserAuthContext = onBehalfOfUserId match {
        case Some(obUserId) =>
          Await.result(
            code.api.util.NewStyle.function.getUserAuthContexts(obUserId, Some(callContext)).map(_._1),
            5.seconds
          )
        case None => List.empty[UserAuthContext]
      }
      
      // Fetch entitlements for onBehalfOf user if provided
      onBehalfOfUserEntitlements = onBehalfOfUserId match {
        case Some(obUserId) =>
          Await.result(
            code.api.util.NewStyle.function.getEntitlementsByUserId(obUserId, Some(callContext)),
            5.seconds
          )
        case None => List.empty[Entitlement]
      }
      
      // Fetch target user if userId is provided
      userOpt <- userId match {
        case Some(uId) => Users.users.vend.getUserByUserId(uId).map(Some(_))
        case None => Full(None)
      }
      
      // Fetch attributes for target user if provided
      userAttributes = userId match {
        case Some(uId) =>
          Await.result(
            code.api.util.NewStyle.function.getNonPersonalUserAttributes(uId, Some(callContext)).map(_._1),
            5.seconds
          )
        case None => List.empty[UserAttributeTrait]
      }
      
      // Fetch bank if bankId is provided
      bankOpt <- bankId match {
        case Some(bId) => 
          tryo(Await.result(
            code.api.util.NewStyle.function.getBank(BankId(bId), Some(callContext)).map(_._1),
            5.seconds
          )).map(Some(_))
        case None => Full(None)
      }
      
      // Fetch bank attributes if bank is provided
      bankAttributes = bankId match {
        case Some(bId) =>
          Await.result(
            code.api.util.NewStyle.function.getBankAttributesByBank(BankId(bId), Some(callContext)).map(_._1),
            5.seconds
          )
        case None => List.empty[BankAttributeTrait]
      }
      
      // Fetch account if accountId and bankId are provided
      accountOpt <- (bankId, accountId) match {
        case (Some(bId), Some(aId)) =>
          tryo(Await.result(
            code.api.util.NewStyle.function.getBankAccount(BankId(bId), AccountId(aId), Some(callContext)).map(_._1),
            5.seconds
          )).map(Some(_))
        case _ => Full(None)
      }
      
      // Fetch account attributes if account is provided
      accountAttributes = (bankId, accountId) match {
        case (Some(bId), Some(aId)) =>
          Await.result(
            code.api.util.NewStyle.function.getAccountAttributesByAccount(BankId(bId), AccountId(aId), Some(callContext)).map(_._1),
            5.seconds
          )
        case _ => List.empty[AccountAttribute]
      }
      
      // Fetch transaction if transactionId, accountId, and bankId are provided
      transactionOpt <- (bankId, accountId, transactionId) match {
        case (Some(bId), Some(aId), Some(tId)) =>
          tryo(Await.result(
            code.api.util.NewStyle.function.getTransaction(BankId(bId), AccountId(aId), TransactionId(tId), Some(callContext)).map(_._1),
            5.seconds
          )).map(trans => Some(trans))
        case _ => Full(None)
      }
      
      // Fetch transaction attributes if transaction is provided
      transactionAttributes = (bankId, transactionId) match {
        case (Some(bId), Some(tId)) =>
          Await.result(
            code.api.util.NewStyle.function.getTransactionAttributes(BankId(bId), TransactionId(tId), Some(callContext)).map(_._1),
            5.seconds
          )
        case _ => List.empty[TransactionAttribute]
      }
      
      // Fetch transaction request if transactionRequestId is provided
      transactionRequestOpt <- transactionRequestId match {
        case Some(trId) =>
          tryo(Await.result(
            code.api.util.NewStyle.function.getTransactionRequestImpl(TransactionRequestId(trId), Some(callContext)).map(_._1),
            5.seconds
          )).map(tr => Some(tr))
        case _ => Full(None)
      }
      
      // Fetch transaction request attributes if transaction request is provided
      transactionRequestAttributes = (bankId, transactionRequestId) match {
        case (Some(bId), Some(trId)) =>
          Await.result(
            code.api.util.NewStyle.function.getTransactionRequestAttributes(BankId(bId), TransactionRequestId(trId), Some(callContext)).map(_._1),
            5.seconds
          )
        case _ => List.empty[TransactionRequestAttributeTrait]
      }
      
      // Fetch customer if customerId and bankId are provided
      customerOpt <- (bankId, customerId) match {
        case (Some(bId), Some(cId)) =>
          tryo(Await.result(
            code.api.util.NewStyle.function.getCustomerByCustomerId(cId, Some(callContext)).map(_._1),
            5.seconds
          )).map(cust => Some(cust))
        case _ => Full(None)
      }
      
      // Fetch customer attributes if customer is provided
      customerAttributes = (bankId, customerId) match {
        case (Some(bId), Some(cId)) =>
          Await.result(
            code.api.util.NewStyle.function.getCustomerAttributes(BankId(bId), CustomerId(cId), Some(callContext)).map(_._1),
            5.seconds
          )
        case _ => List.empty[CustomerAttribute]
      }
      
      // Compile and execute the rule
      compiledFunc <- compileRule(ruleId, rule.ruleCode)
      result <- tryo {
        compiledFunc(authenticatedUser, authenticatedUserAttributes, authenticatedUserAuthContext, authenticatedUserEntitlements, onBehalfOfUserOpt, onBehalfOfUserAttributes, onBehalfOfUserAuthContext, onBehalfOfUserEntitlements, userOpt, userAttributes, bankOpt, bankAttributes, accountOpt, accountAttributes, transactionOpt, transactionAttributes, transactionRequestOpt, transactionRequestAttributes, customerOpt, customerAttributes, Some(callContext))
      }
    } yield result
  }
  


  /**
   * Execute all active ABAC rules with a specific policy (OR logic - at least one must pass)
   * @param logic The logic to apply: "AND" (all must pass), "OR" (any must pass), "XOR" (exactly one must pass)
   * 
   * @param policy The policy to filter rules by
   * @param authenticatedUserId The ID of the authenticated user
   * @param onBehalfOfUserId Optional ID of user being acted on behalf of
   * @param userId The ID of the target user to evaluate
   * @param callContext Call context for fetching objects
   * @param bankId Optional bank ID
   * @param accountId Optional account ID
   * @param viewId Optional view ID
   * @param transactionId Optional transaction ID
   * @param transactionRequestId Optional transaction request ID
   * @param customerId Optional customer ID
   * @return Box[Boolean] - Full(true) if at least one rule passes (OR logic), Full(false) if all fail
   */
  def executeRulesByPolicy(
    policy: String,
    authenticatedUserId: String,
    onBehalfOfUserId: Option[String] = None,
    userId: Option[String] = None,
    callContext: CallContext,
    bankId: Option[String] = None,
    accountId: Option[String] = None,
    viewId: Option[String] = None,
    transactionId: Option[String] = None,
    transactionRequestId: Option[String] = None,
    customerId: Option[String] = None
  ): Box[Boolean] = {
    val rules = MappedAbacRuleProvider.getActiveAbacRulesByPolicy(policy)
    
    if (rules.isEmpty) {
      // No rules for this policy - default to allow
      Full(true)
    } else {
      // Execute all rules and check if at least one passes
      val results = rules.map { rule =>
        executeRule(
          ruleId = rule.abacRuleId,
          authenticatedUserId = authenticatedUserId,
          onBehalfOfUserId = onBehalfOfUserId,
          userId = userId,
          callContext = callContext,
          bankId = bankId,
          accountId = accountId,
          viewId = viewId,
          transactionId = transactionId,
          transactionRequestId = transactionRequestId,
          customerId = customerId
        )
      }
      
      // Count successes and failures
      val successes = results.filter {
        case Full(true) => true
        case _ => false
      }

      // At least one rule must pass (OR logic)
      Full(successes.nonEmpty)
    }
  }

  /**
   * Validate ABAC rule code by attempting to compile it
   * 
   * @param ruleCode The Scala code to validate
   * @return Box[String] - Full("OK") if valid, Failure with error message if invalid
   */
  def validateRuleCode(ruleCode: String): Box[String] = {
    compileRuleInternal(ruleCode) match {
      case Full(_) => Full("ABAC rule code is valid")
      case Failure(msg, _, _) => Failure(s"Invalid ABAC rule code: $msg")
      case Empty => Failure("Failed to validate ABAC rule code")
    }
  }

  /**
   * Clear the compiled rules cache
   */
  def clearCache(): Unit = {
    compiledRulesCache.clear()
  }

  /**
   * Clear a specific rule from the cache
   */
  def clearRuleFromCache(ruleId: String): Unit = {
    compiledRulesCache.remove(ruleId)
  }

  /**
   * Get cache statistics
   */
  def getCacheStats(): Map[String, Any] = {
    Map(
      "cached_rules" -> compiledRulesCache.size,
      "rule_ids" -> compiledRulesCache.keys.toList
    )
  }
}