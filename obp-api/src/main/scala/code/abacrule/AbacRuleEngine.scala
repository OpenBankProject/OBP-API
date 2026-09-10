package code.abacrule

import code.api.util.{APIUtil, CallContext, DynamicUtil, ErrorMessages, OBPQueryParam, OBPLimit}
import code.bankconnectors.Connector
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.entitlement.Entitlement
import com.openbankproject.commons.model._
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._
import scala.collection.concurrent
import scala.concurrent.Future

/**
 * ABAC Rule Engine for compiling and executing Attribute-Based Access Control rules
 */
object AbacRuleEngine {

  // Cache for compiled ABAC rule functions
  private val compiledRulesCache: concurrent.Map[String, Box[AbacRuleFunction]] =
    new ConcurrentHashMap[String, Box[AbacRuleFunction]]().asScala

  val StatisticalSampleSize: Int = 20
  val PermissivenessThreshold: Double = 0.50

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
    // Maker/checker execution guard: on a managed instance a rule whose current code hash differs
    // from the checker-approved hash is never compiled or run (see MakerChecker.isApprovedAbacRule).
    if (!code.dynamicchangerequest.MakerChecker.isApprovedAbacRule(ruleId))
      return Failure(ErrorMessages.DynamicArtefactNotApproved)
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
   * Check if a rule code is too permissive (contains tautological expressions that always evaluate to true).
   *
   * Detects two categories:
   * 1. Whole-body tautologies: the entire rule is a trivially-true expression (e.g. "true", "1==1")
   * 2. Sub-expression tautologies: a tautological operand after || makes the whole expression always true
   *
   * Note: "&& true" is NOT flagged — it's redundant but doesn't increase permissiveness.
   *
   * @param ruleCode The rule code to check
   * @return true if the rule code is too permissive
   */
  private def isTooPermissive(ruleCode: String): Boolean = {
    val stripped = ruleCode.trim

    // Whole-body tautology patterns (entire rule is trivially true)
    val wholeBodyTautologies = List(
      """^true$""",                            // bare true
      """^(\d+)\s*==\s*\1$""",                 // numeric identity: 1==1, 42==42
      """"([^"]+)"\s*==\s*"\1"""",             // string identity: "a"=="a", "foo"=="foo"
      """^true\s*==\s*true$""",                // boolean identity: true==true
      """^!false$""",                          // negated false
      """^!\(false\)$"""                        // negated false with parens
    ).map(_.r)

    // Sub-expression tautology patterns (tautology after || anywhere in the rule)
    val subExprTautologies = List(
      """\|\|\s*true(?!\s*==)""",              // || true (but not || true==true, handled separately)
      """\|\|\s*true\s*==\s*true""",           // || true==true
      """\|\|\s*(\d+)\s*==\s*\1""",            // || 1==1, || 42==42
      """\|\|\s*"([^"]+)"\s*==\s*"\1"""",      // || "a"=="a"
      """\|\|\s*!false""",                     // || !false
      """\|\|\s*!\(false\)"""                   // || !(false)
    ).map(_.r)

    val isWholeBodyTautology = wholeBodyTautologies.exists(_.findFirstIn(stripped).isDefined)
    val hasSubExprTautology = subExprTautologies.exists(_.findFirstIn(stripped).isDefined)

    isWholeBodyTautology || hasSubExprTautology
  }

  /**
   * Statistical permissiveness check: compile the candidate rule, evaluate it against a sample
   * of real system users (with no resource context), and reject it if over 50% of users pass.
   *
   * @param ruleCode The rule code to check
   * @return Future[Boolean] - true if the rule is statistically too permissive
   */
  private def isStatisticallyTooPermissive(ruleCode: String): Future[Boolean] = {
    val compiledBox = compileRuleInternal(ruleCode)
    compiledBox match {
      case Failure(_, _, _) | Empty =>
        // Compilation error caught elsewhere
        Future.successful(false)
      case Full(compiledFunc) =>
        val ns = code.api.util.NewStyle.function
        Users.users.vend.getAllUsersF(List(OBPLimit(StatisticalSampleSize))).flatMap { userEntitlementPairs =>
          if (userEntitlementPairs.isEmpty) {
            Future.successful(false)
          } else {
            val evaluationFutures = userEntitlementPairs.map { case (resourceUser, entitlementsBox) =>
              val userId = resourceUser.userId
              val entitlements = entitlementsBox.openOr(Nil)
              val userAsUser: User = resourceUser

              val attributesF = ns.getNonPersonalUserAttributes(userId, None).map(_._1).recover { case _ => Nil }
              val authContextF = ns.getUserAuthContexts(userId, None).map(_._1).recover { case _ => Nil }

              for {
                attributes <- attributesF
                authContext <- authContextF
              } yield {
                try {
                  compiledFunc(
                    userAsUser, attributes, authContext, entitlements,
                    None, Nil, Nil, Nil, // onBehalfOfUser
                    None, Nil, // user
                    None, Nil, // bank
                    None, Nil, // account
                    None, Nil, // transaction
                    None, Nil, // transactionRequest
                    None, Nil, // customer
                    None // callContext
                  )
                } catch {
                  case _: Exception => false
                }
              }
            }

            Future.sequence(evaluationFutures).map { results =>
              val passCount = results.count(_ == true)
              val total = results.size
              passCount.toDouble / total > PermissivenessThreshold
            }
          }
        }
    }
  }

  /**
   * Helper to lift a Box value into a Future, converting Failure/Empty to a failed Future.
   */
  private def boxToFuture[T](box: Box[T], errorMsg: String = "Not found"): Future[T] = box match {
    case Full(v) => Future.successful(v)
    case Failure(msg, ex, _) => Future.failed(new RuntimeException(msg, ex.openOr(null)))
    case Empty => Future.failed(new RuntimeException(errorMsg))
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
   * @return Future[Box[Boolean]] - Full(true) if allowed, Full(false) if denied, Failure on error
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
    val ccOpt = Some(callContext)
    val ns = code.api.util.NewStyle.function

    // Validate rule exists and is active (synchronous)
    val ruleBox = for {
      rule <- MappedAbacRuleProvider.getAbacRuleById(ruleId)
      _ <- if (rule.isActive) Full(true) else Failure(s"ABAC Rule ${rule.ruleName} is not active")
    } yield rule

    ruleBox match {
      case Full(rule) =>
        // Validate authenticated user exists (synchronous)
        Users.users.vend.getUserByUserId(authenticatedUserId) match {
          case Full(authenticatedUser) =>
            // Fire off all independent async fetches in parallel
            val authenticatedUserAttributesF = ns.getNonPersonalUserAttributes(authenticatedUserId, ccOpt).map(_._1)
            val authenticatedUserAuthContextF = ns.getUserAuthContexts(authenticatedUserId, ccOpt).map(_._1)
            val authenticatedUserEntitlementsF = ns.getEntitlementsByUserId(authenticatedUserId, ccOpt)

            val onBehalfOfUserOpt: Box[Option[User]] = onBehalfOfUserId match {
              case Some(obUserId) => Users.users.vend.getUserByUserId(obUserId).map(Some(_))
              case None => Full(None)
            }

            val onBehalfOfUserAttributesF = onBehalfOfUserId match {
              case Some(obUserId) => ns.getNonPersonalUserAttributes(obUserId, ccOpt).map(_._1)
              case None => Future.successful(List.empty[UserAttributeTrait])
            }
            val onBehalfOfUserAuthContextF = onBehalfOfUserId match {
              case Some(obUserId) => ns.getUserAuthContexts(obUserId, ccOpt).map(_._1)
              case None => Future.successful(List.empty[UserAuthContext])
            }
            val onBehalfOfUserEntitlementsF = onBehalfOfUserId match {
              case Some(obUserId) => ns.getEntitlementsByUserId(obUserId, ccOpt)
              case None => Future.successful(List.empty[Entitlement])
            }

            val userOpt: Box[Option[User]] = userId match {
              case Some(uId) => Users.users.vend.getUserByUserId(uId).map(Some(_))
              case None => Full(None)
            }

            val userAttributesF = userId match {
              case Some(uId) => ns.getNonPersonalUserAttributes(uId, ccOpt).map(_._1)
              case None => Future.successful(List.empty[UserAttributeTrait])
            }

            val bankOptF = bankId match {
              case Some(bId) => ns.getBank(BankId(bId), ccOpt).map(r => Full(Some(r._1)): Box[Option[Bank]]).recover {
                case e => Failure(e.getMessage)
              }
              case None => Future.successful(Full(None): Box[Option[Bank]])
            }

            val bankAttributesF = bankId match {
              case Some(bId) => ns.getBankAttributesByBank(BankId(bId), ccOpt).map(_._1)
              case None => Future.successful(List.empty[BankAttributeTrait])
            }

            val accountOptF = (bankId, accountId) match {
              case (Some(bId), Some(aId)) =>
                ns.getBankAccount(BankId(bId), AccountId(aId), ccOpt).map(r => Full(Some(r._1)): Box[Option[BankAccount]]).recover {
                  case e => Failure(e.getMessage)
                }
              case _ => Future.successful(Full(None): Box[Option[BankAccount]])
            }

            val accountAttributesF = (bankId, accountId) match {
              case (Some(bId), Some(aId)) => ns.getAccountAttributesByAccount(BankId(bId), AccountId(aId), ccOpt).map(_._1)
              case _ => Future.successful(List.empty[AccountAttribute])
            }

            val transactionOptF = (bankId, accountId, transactionId) match {
              case (Some(bId), Some(aId), Some(tId)) =>
                ns.getTransaction(BankId(bId), AccountId(aId), TransactionId(tId), ccOpt).map(r => Full(Some(r._1)): Box[Option[Transaction]]).recover {
                  case e => Failure(e.getMessage)
                }
              case _ => Future.successful(Full(None): Box[Option[Transaction]])
            }

            val transactionAttributesF = (bankId, transactionId) match {
              case (Some(bId), Some(tId)) => ns.getTransactionAttributes(BankId(bId), TransactionId(tId), ccOpt).map(_._1)
              case _ => Future.successful(List.empty[TransactionAttribute])
            }

            val transactionRequestOptF = transactionRequestId match {
              case Some(trId) =>
                ns.getTransactionRequestImpl(TransactionRequestId(trId), ccOpt).map(r => Full(Some(r._1)): Box[Option[TransactionRequest]]).recover {
                  case e => Failure(e.getMessage)
                }
              case _ => Future.successful(Full(None): Box[Option[TransactionRequest]])
            }

            val transactionRequestAttributesF = (bankId, transactionRequestId) match {
              case (Some(bId), Some(trId)) => ns.getTransactionRequestAttributes(BankId(bId), TransactionRequestId(trId), ccOpt).map(_._1)
              case _ => Future.successful(List.empty[TransactionRequestAttributeTrait])
            }

            val customerOptF = (bankId, customerId) match {
              case (Some(bId), Some(cId)) =>
                ns.getCustomerByCustomerId(cId, ccOpt).map(r => Full(Some(r._1)): Box[Option[Customer]]).recover {
                  case e => Failure(e.getMessage)
                }
              case _ => Future.successful(Full(None): Box[Option[Customer]])
            }

            val customerAttributesF = (bankId, customerId) match {
              case (Some(bId), Some(cId)) => ns.getCustomerAttributes(BankId(bId), CustomerId(cId), ccOpt).map(_._1)
              case _ => Future.successful(List.empty[CustomerAttribute])
            }

            // Combine all futures and execute the rule
            for {
              authenticatedUserAttributes <- authenticatedUserAttributesF
              authenticatedUserAuthContext <- authenticatedUserAuthContextF
              authenticatedUserEntitlements <- authenticatedUserEntitlementsF
              obUserAttributes <- onBehalfOfUserAttributesF
              obUserAuthContext <- onBehalfOfUserAuthContextF
              obUserEntitlements <- onBehalfOfUserEntitlementsF
              uAttributes <- userAttributesF
              bankOptBox <- bankOptF
              bankAttrs <- bankAttributesF
              accountOptBox <- accountOptF
              accountAttrs <- accountAttributesF
              transactionOptBox <- transactionOptF
              transactionAttrs <- transactionAttributesF
              transactionRequestOptBox <- transactionRequestOptF
              transactionRequestAttrs <- transactionRequestAttributesF
              customerOptBox <- customerOptF
              customerAttrs <- customerAttributesF
            } yield {
              // Chain the Box results together
              for {
                obUser <- onBehalfOfUserOpt
                uOpt <- userOpt
                bOpt <- bankOptBox
                aOpt <- accountOptBox
                tOpt <- transactionOptBox
                trOpt <- transactionRequestOptBox
                cOpt <- customerOptBox
                compiledFunc <- compileRule(ruleId, rule.ruleCode)
                result <- tryo {
                  compiledFunc(
                    authenticatedUser, authenticatedUserAttributes, authenticatedUserAuthContext, authenticatedUserEntitlements,
                    obUser, obUserAttributes, obUserAuthContext, obUserEntitlements,
                    uOpt, uAttributes,
                    bOpt, bankAttrs,
                    aOpt, accountAttrs,
                    tOpt, transactionAttrs,
                    trOpt, transactionRequestAttrs,
                    cOpt, customerAttrs,
                    ccOpt
                  )
                }
              } yield result
            }

          case Failure(msg, ex, _) => Future.successful(Failure(msg, ex, Empty))
          case Empty => Future.successful(Failure(s"User not found: $authenticatedUserId"))
        }

      case Failure(msg, ex, chain) => Future.successful(Failure(msg, ex, chain))
      case Empty => Future.successful(Empty)
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
   * @return Future[Box[Boolean]] - Full(true) if at least one rule passes (OR logic), Full(false) if all fail
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
      // No rules for this policy - default to deny
      Future.successful(Full(false))
    } else {
      // Execute all rules in parallel and check if at least one passes
      val resultFutures = rules.map { rule =>
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

      Future.sequence(resultFutures).map { results =>
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
   * Execute all active ABAC rules with a specific policy and return detailed results.
   * Returns which rule IDs denied access (for error reporting).
   *
   * @return Future[Box[(Boolean, List[String])]] - Full((true, Nil)) if any rule passes,
   *         Full((false, failingRuleIds)) if all fail, Full((false, Nil)) if no rules exist
   */
  def executeRulesByPolicyDetailed(
    policy: String,
    authenticatedUserId: String,
    callContext: CallContext,
    bankId: Option[String] = None,
    accountId: Option[String] = None,
    viewId: Option[String] = None
  ): Future[Box[(Boolean, List[String])]] = {
    val rules = MappedAbacRuleProvider.getActiveAbacRulesByPolicy(policy)

    if (rules.isEmpty) {
      // No rules for this policy - default to deny
      Future.successful(Full((false, Nil)))
    } else {
      // Execute all rules in parallel and collect results with rule IDs
      val resultFutures = rules.map { rule =>
        executeRule(
          ruleId = rule.abacRuleId,
          authenticatedUserId = authenticatedUserId,
          callContext = callContext,
          bankId = bankId,
          accountId = accountId,
          viewId = viewId
        ).map(result => (rule.abacRuleId, result))
      }

      Future.sequence(resultFutures).map { results =>
        val passed = results.exists {
          case (_, Full(true)) => true
          case _ => false
        }

        if (passed) {
          Full((true, Nil))
        } else {
          val failingRuleIds = results.collect {
            case (ruleId, Full(false)) => ruleId
            case (ruleId, Failure(_, _, _)) => ruleId
            case (ruleId, Empty) => ruleId
          }
          Full((false, failingRuleIds.toList))
        }
      }
    }
  }

  /**
   * Validate ABAC rule code by attempting to compile it
   *
   * @param ruleCode The Scala code to validate
   * @return Box[String] - Full("OK") if valid, Failure with error message if invalid
   */
  def validateRuleCode(ruleCode: String): Box[String] = {
    if (isTooPermissive(ruleCode)) {
      Failure("ABAC rule is too permissive: the rule code contains a tautological expression that would always grant access. Please write a rule that checks specific attributes.")
    } else {
      compileRuleInternal(ruleCode) match {
        case Full(_) => Full("ABAC rule code is valid")
        case Failure(msg, _, _) => Failure(s"Invalid ABAC rule code: $msg")
        case Empty => Failure("Failed to validate ABAC rule code")
      }
    }
  }

  /**
   * Async validation that includes both sync checks (regex + compilation) and
   * the statistical permissiveness check against real system users.
   *
   * @param ruleCode The Scala code to validate
   * @return Future[Box[String]] - Full("ABAC rule code is valid") if valid, Failure with error message if invalid
   */
  def validateRuleCodeAsync(ruleCode: String): Future[Box[String]] = {
    val syncResult = validateRuleCode(ruleCode)
    syncResult match {
      case f @ Failure(_, _, _) =>
        Future.successful(f)
      case Full(_) =>
        isStatisticallyTooPermissive(ruleCode).map { tooPermissive =>
          if (tooPermissive)
            Failure(ErrorMessages.AbacRuleStatisticallyTooPermissive)
          else
            Full("ABAC rule code is valid")
        }.recover {
          case _ => Failure(ErrorMessages.AbacRuleStatisticallyTooPermissive)
        }
      case Empty =>
        Future.successful(Empty)
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
