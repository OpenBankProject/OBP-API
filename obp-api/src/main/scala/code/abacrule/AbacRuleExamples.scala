package code.abacrule

/**
 * ABAC Rule Examples
 *
 * This file contains example ABAC rules that can be used as templates.
 * Copy the rule code (the string in quotes) when creating new ABAC rules via the API.
 */
object AbacRuleExamples {

  // ==================== USER-BASED RULES ====================

  /**
   * Example 1: Admin Only Access
   * Only users with "admin" in their email address can access
   */
  val adminOnlyRule: String =
    """user.emailAddress.contains(\"admin\")"""

  /**
   * Example 2: Specific User Provider
   * Only allow users from a specific authentication provider
   */
  val providerCheckRule: String =
    """user.provider == \"obp\""""

  /**
   * Example 3: User Email Domain
   * Only allow users from specific email domain
   */
  val emailDomainRule: String =
    """user.emailAddress.endsWith(\"@example.com\")"""

  /**
   * Example 4: User Has Username
   * Only allow users who have set a username
   */
  val hasUsernameRule: String =
    """user.name.nonEmpty"""

  // ==================== BANK-BASED RULES ====================

  /**
   * Example 5: Specific Bank Access
   * Only allow access to a specific bank
   */
  val specificBankRule: String =
    """bankOpt.exists(_.bankId.value == \"gh.29.uk\")"""

  /**
   * Example 6: Bank Short Name Check
   * Only allow access to banks with specific short name
   */
  val bankShortNameRule: String =
    """bankOpt.exists(_.shortName.contains(\"Example\"))"""

  /**
   * Example 7: Bank Must Be Present
   * Require bank context to be provided
   */
  val bankRequiredRule: String =
    """bankOpt.isDefined"""

  // ==================== ACCOUNT-BASED RULES ====================

  /**
   * Example 8: High Balance Accounts
   * Only allow access to accounts with balance > 10,000
   */
  val highBalanceRule: String =
    """accountOpt.exists(account => {
      |  account.balance.toString.toDoubleOption.exists(_ > 10000.0)
      |})""".stripMargin

  /**
   * Example 9: Low Balance Accounts
   * Only allow access to accounts with balance < 1,000
   */
  val lowBalanceRule: String =
    """accountOpt.exists(account => {
      |  account.balance.toString.toDoubleOption.exists(_ < 1000.0)
      |})""".stripMargin

  /**
   * Example 10: Specific Currency
   * Only allow access to accounts with specific currency
   */
  val currencyRule: String =
    """accountOpt.exists(_.currency == \"EUR\")"""

  /**
   * Example 11: Account Type Check
   * Only allow access to savings accounts
   */
  val accountTypeRule: String =
    """accountOpt.exists(_.accountType == \"SAVINGS\")"""

  /**
   * Example 12: Account Label Contains
   * Only allow access to accounts with specific label
   */
  val accountLabelRule: String =
    """accountOpt.exists(_.label.contains(\"VIP\"))"""

  // ==================== TRANSACTION-BASED RULES ====================

  /**
   * Example 13: Transaction Amount Limit
   * Only allow access to transactions under 1,000
   */
  val transactionLimitRule: String =
    """transactionOpt.exists(tx => {
      |  tx.amount.toString.toDoubleOption.exists(_ < 1000.0)
      |})""".stripMargin

  /**
   * Example 14: Large Transactions Only
   * Only allow access to transactions over 10,000
   */
  val largeTransactionRule: String =
    """transactionOpt.exists(tx => {
      |  tx.amount.toString.toDoubleOption.exists(_ >= 10000.0)
      |})""".stripMargin

  /**
   * Example 15: Specific Transaction Type
   * Only allow access to specific transaction types
   */
  val transactionTypeRule: String =
    """transactionOpt.exists(_.transactionType == \"PAYMENT\")"""

  /**
   * Example 16: Transaction Currency Check
   * Only allow access to transactions in specific currency
   */
  val transactionCurrencyRule: String =
    """transactionOpt.exists(_.currency == \"USD\")"""

  // ==================== CUSTOMER-BASED RULES ====================

  /**
   * Example 17: Customer Email Domain
   * Only allow access if customer email is from specific domain
   */
  val customerEmailDomainRule: String =
    """customerOpt.exists(_.email.endsWith(\"@corporate.com\"))"""

  /**
   * Example 18: Customer Legal Name Check
   * Only allow access to customers with specific name pattern
   */
  val customerNameRule: String =
    """customerOpt.exists(_.legalName.contains(\"Corporation\"))"""

  /**
   * Example 19: Customer Mobile Number Pattern
   * Only allow access to customers with specific mobile pattern
   */
  val customerMobileRule: String =
    """customerOpt.exists(_.mobilePhoneNumber.startsWith(\"+44\"))"""

  // ==================== COMBINED RULES ====================

  /**
   * Example 20: Manager with Bank Context
   * Managers can only access specific bank
   */
  val managerBankRule: String =
    """user.emailAddress.contains(\"manager\") &&
      |bankOpt.exists(_.bankId.value == \"gh.29.uk\")""".stripMargin

  /**
   * Example 21: High Value Account Access
   * Only managers can access high-value accounts
   */
  val managerHighValueRule: String =
    """user.emailAddress.contains(\"manager\") &&
      |accountOpt.exists(account => {
      |  account.balance.toString.toDoubleOption.exists(_ > 50000.0)
      |})""".stripMargin

  /**
   * Example 22: Auditor Transaction Access
   * Auditors can only view completed transactions
   */
  val auditorTransactionRule: String =
    """user.emailAddress.contains(\"auditor\") &&
      |transactionOpt.exists(_.status == \"COMPLETED\")""".stripMargin

  /**
   * Example 23: VIP Customer Manager Access
   * Only specific managers can access VIP customer accounts
   */
  val vipManagerRule: String =
    """(user.emailAddress.contains(\"vip-manager\") || user.emailAddress.contains(\"director\")) &&
      |accountOpt.exists(_.label.contains(\"VIP\"))""".stripMargin

  /**
   * Example 24: Multi-Condition Access
   * Complex rule with multiple conditions
   */
  val complexRule: String =
    """user.emailAddress.contains(\"manager\") &&
      |user.provider == \"obp\" &&
      |bankOpt.exists(_.bankId.value == \"gh.29.uk\") &&
      |accountOpt.exists(account => {
      |  account.currency == \"GBP\" &&
      |  account.balance.toString.toDoubleOption.exists(_ > 5000.0) &&
      |  account.balance.toString.toDoubleOption.exists(_ < 100000.0)
      |})""".stripMargin

  // ==================== NEGATIVE RULES (DENY ACCESS) ====================

  /**
   * Example 25: Block Specific User
   * Deny access to specific user
   */
  val blockUserRule: String =
    """!user.emailAddress.contains(\"blocked@example.com\")"""

  /**
   * Example 26: Block Inactive Accounts
   * Deny access to inactive accounts
   */
  val blockInactiveAccountRule: String =
    """accountOpt.forall(_.accountRoutings.nonEmpty)"""

  /**
   * Example 27: Block Small Transactions
   * Deny access to transactions under 10
   */
  val blockSmallTransactionRule: String =
    """transactionOpt.forall(tx => {
      |  tx.amount.toString.toDoubleOption.exists(_ >= 10.0)
      |})""".stripMargin

  // ==================== ADVANCED RULES ====================

  /**
   * Example 28: Pattern Matching on User Email
   * Use regex-like pattern matching
   */
  val emailPatternRule: String =
    """user.emailAddress.matches(\".*@(internal|corporate)\\\\.com\")"""

  /**
   * Example 29: Multiple Bank Access
   * Allow access to multiple specific banks
   */
  val multipleBanksRule: String =
    """bankOpt.exists(bank => {
      |  val allowedBanks = Set(\"gh.29.uk\", \"de.10.de\", \"us.01.us\")
      |  allowedBanks.contains(bank.bankId.value)
      |})""".stripMargin

  /**
   * Example 30: Balance Range Check
   * Only allow access to accounts within balance range
   */
  val balanceRangeRule: String =
    """accountOpt.exists(account => {
      |  account.balance.toString.toDoubleOption.exists(balance =>
      |    balance >= 1000.0 && balance <= 50000.0
      |  )
      |})""".stripMargin

  /**
   * Example 31: OR Logic - Multiple Valid Conditions
   * Allow access if any condition is true
   */
  val orLogicRule: String =
    """user.emailAddress.contains(\"admin\") ||
      |user.emailAddress.contains(\"manager\") ||
      |user.emailAddress.contains(\"director\")""".stripMargin

  /**
   * Example 32: Nested Option Handling
   * Safe navigation through optional values
   */
  val nestedOptionRule: String =
    """bankOpt.isDefined &&
      |accountOpt.isDefined &&
      |accountOpt.exists(_.accountRoutings.nonEmpty)""".stripMargin

  /**
   * Example 33: Default to True (Allow All)
   * Simple rule that always grants access (useful for testing)
   */
  val allowAllRule: String = """true"""

  /**
   * Example 34: Default to False (Deny All)
   * Simple rule that always denies access
   */
  val denyAllRule: String = """false"""

  /**
   * Example 35: Context-Aware Rule
   * Different logic based on what context is available
   */
  val contextAwareRule: String =
    """if (transactionOpt.isDefined) {
      |  // If transaction context exists, apply transaction rules
      |  transactionOpt.exists(tx =>
      |    tx.amount.toString.toDoubleOption.exists(_ < 10000.0)
      |  )
      |} else if (accountOpt.isDefined) {
      |  // If only account context exists, apply account rules
      |  accountOpt.exists(account =>
      |    account.balance.toString.toDoubleOption.exists(_ > 1000.0)
      |  )
      |} else {
      |  // Default case
      |  user.emailAddress.contains(\"admin\")
      |}""".stripMargin

  // ==================== HELPER FUNCTIONS ====================

  /**
   * Get all example rules as a map
   */
  def getAllExamples: Map[String, String] = Map(
    "admin_only" -> adminOnlyRule,
    "provider_check" -> providerCheckRule,
    "email_domain" -> emailDomainRule,
    "has_username" -> hasUsernameRule,
    "specific_bank" -> specificBankRule,
    "bank_short_name" -> bankShortNameRule,
    "bank_required" -> bankRequiredRule,
    "high_balance" -> highBalanceRule,
    "low_balance" -> lowBalanceRule,
    "currency" -> currencyRule,
    "account_type" -> accountTypeRule,
    "account_label" -> accountLabelRule,
    "transaction_limit" -> transactionLimitRule,
    "large_transaction" -> largeTransactionRule,
    "transaction_type" -> transactionTypeRule,
    "transaction_currency" -> transactionCurrencyRule,
    "customer_email_domain" -> customerEmailDomainRule,
    "customer_name" -> customerNameRule,
    "customer_mobile" -> customerMobileRule,
    "manager_bank" -> managerBankRule,
    "manager_high_value" -> managerHighValueRule,
    "auditor_transaction" -> auditorTransactionRule,
    "vip_manager" -> vipManagerRule,
    "complex" -> complexRule,
    "block_user" -> blockUserRule,
    "block_inactive_account" -> blockInactiveAccountRule,
    "block_small_transaction" -> blockSmallTransactionRule,
    "email_pattern" -> emailPatternRule,
    "multiple_banks" -> multipleBanksRule,
    "balance_range" -> balanceRangeRule,
    "or_logic" -> orLogicRule,
    "nested_option" -> nestedOptionRule,
    "allow_all" -> allowAllRule,
    "deny_all" -> denyAllRule,
    "context_aware" -> contextAwareRule
  )

  /**
   * Get example by name
   */
  def getExample(name: String): Option[String] = getAllExamples.get(name)

  /**
   * List all available example names
   */
  def listExampleNames: List[String] = getAllExamples.keys.toList.sorted
}
