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
import scala.concurrent.{Await, Future}
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
  ): Future[Box[Boolean]] = {
    val ruleBox = MappedAbacRuleProvider.getAbacRuleById(ruleId)
    ruleBox match {
      case Failure(msg, ex, chain) => Future.successful(Failure(msg, ex, chain))
      case Empty => Future.successful(Empty)
      case Full(rule) =>
        if (!rule.isActive) {
          Future.successful(Failure(s"ABAC Rule ${rule.ruleName} is not active"))
        } else {
          // Fetch authenticated user
          val authenticatedUserBox = Users.users.vend.getUserByUserId(authenticatedUserId)
          authenticatedUserBox match {
            case Failure(msg, ex, chain) => Future.successful(Failure(msg, ex, chain))
            case Empty => Future.successful(Empty)
            case Full(authenticatedUser) =>
              
              // Create futures for all async operations
              val authenticatedUserAttributesFuture = 
                code.api.util.NewStyle.function.getNonPersonalUserAttributes(authenticatedUserId, Some(callContext)).map(_._1)
              
              val authenticatedUserAuthContextFuture = 
                code.api.util.NewStyle.function.getUserAuthContexts(authenticatedUserId, Some(callContext)).map(_._1)
              
              val authenticatedUserEntitlementsFuture = 
                code.api.util.NewStyle.function.getEntitlementsByUserId(authenticatedUserId, Some(callContext))
              
              val onBehalfOfUserFuture = onBehalfOfUserId match {
                case Some(obUserId) => Future.successful(Users.users.vend.getUserByUserId(obUserId).map(Some(_)))
                case None => Future.successful(Full(None))
              }
              
              val onBehalfOfUserAttributesFuture = onBehalfOfUserId match {
                case Some(obUserId) =>
                  code.api.util.NewStyle.function.getNonPersonalUserAttributes(obUserId, Some(callContext)).map(_._1)
                case None => Future.successful(List.empty[UserAttributeTrait])
              }
              
              val onBehalfOfUserAuthContextFuture = onBehalfOfUserId match {
                case Some(obUserId) =>
                  code.api.util.NewStyle.function.getUserAuthContexts(obUserId, Some(callContext)).map(_._1)
                case None => Future.successful(List.empty[UserAuthContext])
              }
              
              val onBehalfOfUserEntitlementsFuture = onBehalfOfUserId match {
                case Some(obUserId) =>
                  code.api.util.NewStyle.function.getEntitlementsByUserId(obUserId, Some(callContext))
                case None => Future.successful(List.empty[Entitlement])
              }
              
              val userFuture = userId match {
                case Some(uId) => Future.successful(Users.users.vend.getUserByUserId(uId).map(Some(_)))
                case None => Future.successful(Full(None))
              }
              
              val userAttributesFuture = userId match {
                case Some(uId) =>
                  code.api.util.NewStyle.function.getNonPersonalUserAttributes(uId, Some(callContext)).map(_._1)
                case None => Future.successful(List.empty[UserAttributeTrait])
              }
              
              val bankFuture = bankId match {
                case Some(bId) =>
                  code.api.util.NewStyle.function.getBank(BankId(bId), Some(callContext)).map(_._1).map(bank => Full(Some(bank))).recover {
                    case _ => Full(None)
                  }
                case None => Future.successful(Full(None))
              }
              
              val bankAttributesFuture = bankId match {
                case Some(bId) =>
                  code.api.util.NewStyle.function.getBankAttributesByBank(BankId(bId), Some(callContext)).map(_._1)
                case None => Future.successful(List.empty[BankAttributeTrait])
              }
              
              val accountFuture = (bankId, accountId) match {
                case (Some(bId), Some(aId)) =>
                  code.api.util.NewStyle.function.getBankAccount(BankId(bId), AccountId(aId), Some(callContext)).map(_._1).map(account => Full(Some(account))).recover {
                    case _ => Full(None)
                  }
                case _ => Future.successful(Full(None))
              }
              
              val accountAttributesFuture = (bankId, accountId) match {
                case (Some(bId), Some(aId)) =>
                  code.api.util.NewStyle.function.getAccountAttributesByAccount(BankId(bId), AccountId(aId), Some(callContext)).map(_._1)
                case _ => Future.successful(List.empty[AccountAttribute])
              }
              
              val transactionFuture = (bankId, accountId, transactionId) match {
                case (Some(bId), Some(aId), Some(tId)) =>
                  code.api.util.NewStyle.function.getTransaction(BankId(bId), AccountId(aId), TransactionId(tId), Some(callContext)).map(_._1).map(trans => Full(Some(trans))).recover {
                    case _ => Full(None)
                  }
                case _ => Future.successful(Full(None))
              }
              
              val transactionAttributesFuture = (bankId, transactionId) match {
                case (Some(bId), Some(tId)) =>
                  code.api.util.NewStyle.function.getTransactionAttributes(BankId(bId), TransactionId(tId), Some(callContext)).map(_._1)
                case _ => Future.successful(List.empty[TransactionAttribute])
              }
              
              val transactionRequestFuture = transactionRequestId match {
                case Some(trId) =>
                  code.api.util.NewStyle.function.getTransactionRequestImpl(TransactionRequestId(trId), Some(callContext)).map(_._1).map(tr => Full(Some(tr))).recover {
                    case _ => Full(None)
                  }
                case _ => Future.successful(Full(None))
              }
              
              val transactionRequestAttributesFuture = (bankId, transactionRequestId) match {
                case (Some(bId), Some(trId)) =>
                  code.api.util.NewStyle.function.getTransactionRequestAttributes(BankId(bId), TransactionRequestId(trId), Some(callContext)).map(_._1)
                case _ => Future.successful(List.empty[TransactionRequestAttributeTrait])
              }
              
              val customerFuture = (bankId, customerId) match {
                case (Some(bId), Some(cId)) =>
                  code.api.util.NewStyle.function.getCustomerByCustomerId(cId, Some(callContext)).map(_._1).map(cust => Full(Some(cust))).recover {
                    case _ => Full(None)
                  }
                case _ => Future.successful(Full(None))
              }
              
              val customerAttributesFuture = (bankId, customerId) match {
                case (Some(bId), Some(cId)) =>
                  code.api.util.NewStyle.function.getCustomerAttributes(BankId(bId), CustomerId(cId), Some(callContext)).map(_._1)
                case _ => Future.successful(List.empty[CustomerAttribute])
              }
              
              // Combine all futures
              for {
                authenticatedUserAttributes <- authenticatedUserAttributesFuture
                authenticatedUserAuthContext <- authenticatedUserAuthContextFuture
                authenticatedUserEntitlements <- authenticatedUserEntitlementsFuture
                onBehalfOfUserOpt <- onBehalfOfUserFuture
                onBehalfOfUserAttributes <- onBehalfOfUserAttributesFuture
                onBehalfOfUserAuthContext <- onBehalfOfUserAuthContextFuture
                onBehalfOfUserEntitlements <- onBehalfOfUserEntitlementsFuture
                userOpt <- userFuture
                userAttributes <- userAttributesFuture
                bankOpt <- bankFuture
                bankAttributes <- bankAttributesFuture
                accountOpt <- accountFuture
                accountAttributes <- accountAttributesFuture
                transactionOpt <- transactionFuture
                transactionAttributes <- transactionAttributesFuture
                transactionRequestOpt <- transactionRequestFuture
                transactionRequestAttributes <- transactionRequestAttributesFuture
                customerOpt <- customerFuture
                customerAttributes <- customerAttributesFuture
              } yield {
                // Compile and execute the rule
                val compiledFuncBox = compileRule(ruleId, rule.ruleCode)
                compiledFuncBox.flatMap { compiledFunc =>
                  (for {
                    onBehalfOfUser <- onBehalfOfUserOpt
                    user <- userOpt
                    bank <- bankOpt
                    account <- accountOpt
                    transaction <- transactionOpt
                    transactionRequest <- transactionRequestOpt
                    customer <- customerOpt
                  } yield {
                    tryo {
                      compiledFunc(authenticatedUser, authenticatedUserAttributes, authenticatedUserAuthContext, authenticatedUserEntitlements, onBehalfOfUser, onBehalfOfUserAttributes, onBehalfOfUserAuthContext, onBehalfOfUserEntitlements, user, userAttributes, bank, bankAttributes, account, accountAttributes, transaction, transactionAttributes, transactionRequest, transactionRequestAttributes, customer, customerAttributes, Some(callContext))
                    }
                  }).flatten
                }
              }
          }
        }
    }
  }

  /**
   * Synchronous wrapper for executeRule - DEPRECATED
   * This function blocks the thread and should be avoided. Use the async version instead.
   * 
   * @deprecated Use the async executeRule that returns Future[Box[Boolean]] instead
   */
  @deprecated("Use async executeRule that returns Future[Box[Boolean]]", "6.0.0")
  def executeRuleSync(
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
    try {
      Await.result(executeRule(
        ruleId = ruleId,
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
      ), 30.seconds)
    } catch {
      case _: java.util.concurrent.TimeoutException =>
        Failure("ABAC rule execution timed out")
      case ex: Exception =>
        Failure(s"ABAC rule execution failed: ${ex.getMessage}")
    }
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
  ): Future[Box[Boolean]] = {
    val rules = MappedAbacRuleProvider.getActiveAbacRulesByPolicy(policy)
    
    if (rules.isEmpty) {
      // No rules for this policy - default to allow
      Future.successful(Full(true))
    } else {
      // Execute all rules and check if at least one passes
      val ruleFutures = rules.map { rule =>
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
      
      // Wait for all rule executions to complete
      Future.sequence(ruleFutures).map { results =>
        // Count successes and failures
        val successes = results.filter {
          case Full(true) => true
          case _ => false
        }

        // At least one rule must pass (OR logic)
        Full(successes.nonEmpty)
      }
    }
  }

  /**
   * Synchronous wrapper for executeRulesByPolicy - DEPRECATED
   * This function blocks the thread and should be avoided. Use the async version instead.
   * 
   * @deprecated Use async executeRulesByPolicy that returns Future[Box[Boolean]] instead
   */
  @deprecated("Use async executeRulesByPolicy that returns Future[Box[Boolean]]", "6.0.0")
  def executeRulesByPolicySync(
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
    try {
      Await.result(executeRulesByPolicy(
        policy = policy,
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
      ), 30.seconds)
    } catch {
      case _: java.util.concurrent.TimeoutException =>
        Failure("ABAC rules execution timed out")
      case ex: Exception =>
        Failure(s"ABAC rules execution failed: ${ex.getMessage}")
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