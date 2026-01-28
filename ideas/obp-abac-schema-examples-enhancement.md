# OBP API ABAC Schema Examples Enhancement

## Overview

This document provides comprehensive examples for the `/obp/v6.0.0/management/abac-rules-schema` endpoint in the OBP API. These examples should replace or supplement the current `examples` array in the API response to provide better guidance for writing ABAC rules.

## Current State

The current OBP API returns a limited set of examples that don't cover all 19 available parameters.

## Proposed Enhancement

Replace the `examples` array in the schema response with the following comprehensive set of examples covering all parameters and common use cases.

---

## Recommended Examples Array

### 1. authenticatedUser (User) - Required

Always available - the logged-in user making the request.

```scala
"// Check authenticated user's email domain",
"authenticatedUser.emailAddress.contains(\"@example.com\")",

"// Check authentication provider",
"authenticatedUser.provider == \"obp\"",

"// Check if authenticated user matches target user",
"authenticatedUser.userId == userOpt.get.userId",

"// Check user's display name",
"authenticatedUser.name.startsWith(\"Admin\")",

"// Safe check for deleted users",
"!authenticatedUser.isDeleted.getOrElse(false)",
```

---

### 2. authenticatedUserAttributes (List[UserAttributeTrait]) - Required

Non-personal attributes of the authenticated user.

```scala
"// Check if user has admin role",
"authenticatedUserAttributes.exists(attr => attr.name == \"role\" && attr.value == \"admin\")",

"// Check user's department",
"authenticatedUserAttributes.find(_.name == \"department\").exists(_.value == \"finance\")",

"// Check if user has any clearance level",
"authenticatedUserAttributes.exists(_.name == \"clearance_level\")",

"// Filter by attribute type",
"authenticatedUserAttributes.filter(_.attributeType == AttributeType.STRING).nonEmpty",

"// Check for multiple roles",
"authenticatedUserAttributes.exists(attr => attr.name == \"role\" && List(\"admin\", \"manager\").contains(attr.value))",
```

---

### 3. authenticatedUserAuthContext (List[UserAuthContext]) - Required

Authentication context of the authenticated user.

```scala
"// Check session type",
"authenticatedUserAuthContext.exists(_.key == \"session_type\" && _.value == \"secure\")",

"// Ensure auth context exists",
"authenticatedUserAuthContext.nonEmpty",

"// Check authentication method",
"authenticatedUserAuthContext.exists(_.key == \"auth_method\" && _.value == \"certificate\")",
```

---

### 4. onBehalfOfUserOpt (Option[User]) - Optional

User being acted on behalf of (delegation scenario).

```scala
"// Check if acting on behalf of self",
"onBehalfOfUserOpt.isDefined && onBehalfOfUserOpt.get.userId == authenticatedUser.userId",

"// Safe check delegation user's email",
"onBehalfOfUserOpt.exists(_.emailAddress.endsWith(\"@company.com\"))",

"// Pattern matching for safe access",
"onBehalfOfUserOpt match { case Some(u) => u.provider == \"obp\" case None => true }",

"// Ensure delegation user is different",
"onBehalfOfUserOpt.forall(_.userId != authenticatedUser.userId)",

"// Check if delegation exists",
"onBehalfOfUserOpt.isDefined",
```

---

### 5. onBehalfOfUserAttributes (List[UserAttributeTrait]) - Optional

Attributes of the delegation user.

```scala
"// Check delegation level",
"onBehalfOfUserAttributes.exists(attr => attr.name == \"delegation_level\" && attr.value == \"full\")",

"// Allow if no delegation or authorized delegation",
"onBehalfOfUserAttributes.isEmpty || onBehalfOfUserAttributes.exists(_.name == \"authorized\")",

"// Check delegation permissions",
"onBehalfOfUserAttributes.exists(attr => attr.name == \"permissions\" && attr.value.contains(\"read\"))",
```

---

### 6. onBehalfOfUserAuthContext (List[UserAuthContext]) - Optional

Auth context of the delegation user.

```scala
"// Check for delegation token",
"onBehalfOfUserAuthContext.exists(_.key == \"delegation_token\")",

"// Verify delegation auth method",
"onBehalfOfUserAuthContext.exists(_.key == \"auth_method\" && _.value == \"oauth\")",
```

---

### 7. userOpt (Option[User]) - Optional

Target user being evaluated in the request.

```scala
"// Check if target user matches authenticated user",
"userOpt.isDefined && userOpt.get.userId == authenticatedUser.userId",

"// Check target user's provider",
"userOpt.exists(_.provider == \"obp\")",

"// Ensure user is not deleted",
"userOpt.forall(!_.isDeleted.getOrElse(false))",

"// Check user email domain",
"userOpt.exists(_.emailAddress.endsWith(\"@trusted.com\"))",
```

---

### 8. userAttributes (List[UserAttributeTrait]) - Optional

Attributes of the target user.

```scala
"// Check target user's account type",
"userAttributes.exists(attr => attr.name == \"account_type\" && attr.value == \"premium\")",

"// Check KYC status",
"userAttributes.exists(attr => attr.name == \"kyc_status\" && attr.value == \"verified\")",

"// Check user tier",
"userAttributes.find(_.name == \"tier\").exists(_.value.toInt >= 2)",
```

---

### 9. bankOpt (Option[Bank]) - Optional

Bank context in the request.

```scala
"// Check for specific bank",
"bankOpt.isDefined && bankOpt.get.bankId.value == \"gh.29.uk\"",

"// Check bank name contains text",
"bankOpt.exists(_.fullName.contains(\"Community\"))",

"// Check bank routing scheme",
"bankOpt.exists(_.bankRoutingScheme == \"IBAN\")",

"// Check bank website",
"bankOpt.exists(_.websiteUrl.contains(\"https://\"))",
```

---

### 10. bankAttributes (List[BankAttributeTrait]) - Optional

Bank attributes.

```scala
"// Check bank region",
"bankAttributes.exists(attr => attr.name == \"region\" && attr.value == \"EU\")",

"// Check bank license type",
"bankAttributes.exists(attr => attr.name == \"license_type\" && attr.value == \"full\")",

"// Check if bank is certified",
"bankAttributes.exists(attr => attr.name == \"certified\" && attr.value == \"true\")",
```

---

### 11. accountOpt (Option[BankAccount]) - Optional

Account context in the request.

```scala
"// Check account balance threshold",
"accountOpt.isDefined && accountOpt.get.balance > 1000",

"// Check account currency and balance",
"accountOpt.exists(acc => acc.currency == \"USD\" && acc.balance > 5000)",

"// Check account type",
"accountOpt.exists(_.accountType == \"SAVINGS\")",

"// Check account label",
"accountOpt.exists(_.label.contains(\"Business\"))",

"// Check account number format",
"accountOpt.exists(_.number.length >= 10)",
```

---

### 12. accountAttributes (List[AccountAttribute]) - Optional

Account attributes.

```scala
"// Check account status",
"accountAttributes.exists(attr => attr.name == \"status\" && attr.value == \"active\")",

"// Check account tier",
"accountAttributes.exists(attr => attr.name == \"account_tier\" && attr.value == \"gold\")",

"// Check overdraft protection",
"accountAttributes.exists(attr => attr.name == \"overdraft_protection\" && attr.value == \"enabled\")",
```

---

### 13. transactionOpt (Option[Transaction]) - Optional

Transaction context in the request.

```scala
"// Check transaction amount limit",
"transactionOpt.isDefined && transactionOpt.get.amount < 10000",

"// Check transaction type",
"transactionOpt.exists(_.transactionType.contains(\"TRANSFER\"))",

"// Check transaction currency and amount",
"transactionOpt.exists(t => t.currency == \"EUR\" && t.amount > 100)",

"// Check transaction status",
"transactionOpt.exists(_.status.exists(_ == \"COMPLETED\"))",

"// Check transaction balance after",
"transactionOpt.exists(_.balance > 0)",
```

---

### 14. transactionAttributes (List[TransactionAttribute]) - Optional

Transaction attributes.

```scala
"// Check transaction category",
"transactionAttributes.exists(attr => attr.name == \"category\" && attr.value == \"business\")",

"// Check risk score",
"transactionAttributes.exists(attr => attr.name == \"risk_score\" && attr.value.toInt < 50)",

"// Check if transaction is flagged",
"!transactionAttributes.exists(attr => attr.name == \"flagged\" && attr.value == \"true\")",
```

---

### 15. transactionRequestOpt (Option[TransactionRequest]) - Optional

Transaction request context.

```scala
"// Check transaction request status",
"transactionRequestOpt.exists(_.status == \"PENDING\")",

"// Check transaction request type",
"transactionRequestOpt.exists(_.type == \"SEPA\")",

"// Check bank matches",
"transactionRequestOpt.exists(_.this_bank_id.value == bankOpt.get.bankId.value)",

"// Check account matches",
"transactionRequestOpt.exists(_.this_account_id.value == accountOpt.get.accountId.value)",
```

---

### 16. transactionRequestAttributes (List[TransactionRequestAttributeTrait]) - Optional

Transaction request attributes.

```scala
"// Check priority level",
"transactionRequestAttributes.exists(attr => attr.name == \"priority\" && attr.value == \"high\")",

"// Check if approval required",
"transactionRequestAttributes.exists(attr => attr.name == \"approval_required\" && attr.value == \"true\")",

"// Check request source",
"transactionRequestAttributes.exists(attr => attr.name == \"source\" && attr.value == \"mobile_app\")",
```

---

### 17. customerOpt (Option[Customer]) - Optional

Customer context in the request.

```scala
"// Check customer legal name",
"customerOpt.exists(_.legalName.contains(\"Corp\"))",

"// Check customer email matches user",
"customerOpt.isDefined && customerOpt.get.email == authenticatedUser.emailAddress",

"// Check customer relationship status",
"customerOpt.exists(_.relationshipStatus == \"ACTIVE\")",

"// Check customer has dependents",
"customerOpt.exists(_.dependents > 0)",

"// Check customer mobile number exists",
"customerOpt.exists(_.mobileNumber.nonEmpty)",
```

---

### 18. customerAttributes (List[CustomerAttribute]) - Optional

Customer attributes.

```scala
"// Check customer risk level",
"customerAttributes.exists(attr => attr.name == \"risk_level\" && attr.value == \"low\")",

"// Check VIP status",
"customerAttributes.exists(attr => attr.name == \"vip_status\" && attr.value == \"true\")",

"// Check customer segment",
"customerAttributes.exists(attr => attr.name == \"segment\" && attr.value == \"retail\")",
```

---

### 19. callContext (Option[CallContext]) - Optional

Request call context with metadata.

```scala
"// Check if request is from internal network",
"callContext.exists(_.ipAddress.exists(_.startsWith(\"192.168\")))",

"// Check if request is from mobile device",
"callContext.exists(_.userAgent.exists(_.contains(\"Mobile\")))",

"// Only allow GET requests",
"callContext.exists(_.verb.exists(_ == \"GET\"))",

"// Check request URL path",
"callContext.exists(_.url.exists(_.contains(\"/accounts/\")))",

"// Check if request is from external IP",
"callContext.exists(_.ipAddress.exists(!_.startsWith(\"10.\")))",
```

---

## Complex Examples

Combining multiple parameters and conditions:

```scala
"// Admin from trusted domain accessing any account",
"authenticatedUser.emailAddress.endsWith(\"@bank.com\") && accountOpt.exists(_.balance > 0) && bankOpt.exists(_.bankId.value == \"gh.29.uk\")",

"// Manager accessing other user's data",
"authenticatedUserAttributes.exists(_.name == \"role\" && _.value == \"manager\") && userOpt.exists(_.userId != authenticatedUser.userId)",

"// Self-access or authorized delegation with sufficient balance",
"(onBehalfOfUserOpt.isEmpty || onBehalfOfUserOpt.exists(_.userId == authenticatedUser.userId)) && accountOpt.exists(_.balance > 1000)",

"// External high-value transaction with risk check",
"callContext.exists(_.ipAddress.exists(!_.startsWith(\"10.\"))) && transactionOpt.exists(_.amount > 5000) && !transactionAttributes.exists(_.name == \"risk_flag\")",

"// VIP customer with premium account and active status",
"customerAttributes.exists(_.name == \"vip_status\" && _.value == \"true\") && accountAttributes.exists(_.name == \"account_tier\" && _.value == \"premium\") && customerOpt.exists(_.relationshipStatus == \"ACTIVE\")",

"// Verified user with proper delegation accessing specific bank",
"userAttributes.exists(_.name == \"kyc_status\" && _.value == \"verified\") && (onBehalfOfUserOpt.isEmpty || onBehalfOfUserAttributes.exists(_.name == \"authorized\")) && bankOpt.exists(_.bankId.value.startsWith(\"gh\"))",

"// High-tier user with matching customer and account tier",
"userAttributes.exists(_.name == \"tier\" && _.value.toInt >= 3) && accountAttributes.exists(_.name == \"account_tier\" && _.value == \"premium\") && customerAttributes.exists(_.name == \"customer_tier\" && _.value == \"gold\")",

"// Transaction within account balance limits",
"transactionOpt.exists(t => accountOpt.exists(a => t.amount <= a.balance * 0.9))",

"// Same-bank transaction request validation",
"transactionRequestOpt.exists(tr => bankOpt.exists(b => tr.this_bank_id.value == b.bankId.value))",

"// Cross-border transaction with compliance check",
"transactionOpt.exists(_.currency != accountOpt.get.currency) && transactionAttributes.exists(_.name == \"compliance_approved\" && _.value == \"true\")",
```

---

## Object-to-Object Comparison Examples

Direct comparisons between different parameters:

### User Comparisons

```scala
"// Authenticated user is the target user (self-access)",
"userOpt.isDefined && userOpt.get.userId == authenticatedUser.userId",

"// Authenticated user's email matches target user's email",
"userOpt.exists(_.emailAddress == authenticatedUser.emailAddress)",

"// Authenticated user and target user have same provider",
"userOpt.exists(_.provider == authenticatedUser.provider)",

"// Acting on behalf of the target user",
"onBehalfOfUserOpt.isDefined && userOpt.isDefined && onBehalfOfUserOpt.get.userId == userOpt.get.userId",

"// Delegation user matches authenticated user (self-delegation)",
"onBehalfOfUserOpt.isEmpty || onBehalfOfUserOpt.get.userId == authenticatedUser.userId",

"// Authenticated user is NOT the target user (other user access)",
"userOpt.exists(_.userId != authenticatedUser.userId)",

"// Both users from same domain",
"userOpt.exists(u => authenticatedUser.emailAddress.split(\"@\")(1) == u.emailAddress.split(\"@\")(1))",

"// Target user's name contains authenticated user's name",
"userOpt.exists(_.name.contains(authenticatedUser.name))",
```

### Customer-User Comparisons

```scala
"// Customer email matches authenticated user email",
"customerOpt.exists(_.email == authenticatedUser.emailAddress)",

"// Customer email matches target user email",
"customerOpt.isDefined && userOpt.isDefined && customerOpt.get.email == userOpt.get.emailAddress",

"// Customer mobile number matches user attribute",
"customerOpt.isDefined && userAttributes.exists(attr => attr.name == \"mobile\" && customerOpt.get.mobileNumber == attr.value)",

"// Customer and user have matching legal names",
"customerOpt.exists(c => userOpt.exists(u => c.legalName.contains(u.name)))",
```

### Account-Transaction Comparisons

```scala
"// Transaction amount is less than account balance",
"transactionOpt.isDefined && accountOpt.isDefined && transactionOpt.get.amount < accountOpt.get.balance",

"// Transaction amount within 50% of account balance",
"transactionOpt.exists(t => accountOpt.exists(a => t.amount <= a.balance * 0.5))",

"// Transaction currency matches account currency",
"transactionOpt.exists(t => accountOpt.exists(a => t.currency == a.currency))",

"// Transaction would not overdraw account",
"transactionOpt.exists(t => accountOpt.exists(a => a.balance - t.amount >= 0))",

"// Transaction balance matches account balance after transaction",
"transactionOpt.exists(t => accountOpt.exists(a => t.balance == a.balance - t.amount))",

"// Transaction amount matches account's daily limit attribute",
"transactionOpt.isDefined && accountAttributes.exists(attr => attr.name == \"daily_limit\" && transactionOpt.get.amount <= attr.value.toDouble)",

"// Transaction type allowed for account type",
"transactionOpt.exists(t => accountOpt.exists(a => (a.accountType == \"CHECKING\" && t.transactionType.exists(_.contains(\"DEBIT\"))) || (a.accountType == \"SAVINGS\" && t.transactionType.exists(_.contains(\"TRANSFER\")))))",
```

### Bank-Account Comparisons

```scala
"// Account belongs to the specified bank",
"accountOpt.isDefined && bankOpt.isDefined && accountOpt.get.bankId == bankOpt.get.bankId.value",

"// Account currency matches bank's primary currency attribute",
"accountOpt.exists(a => bankAttributes.exists(attr => attr.name == \"primary_currency\" && attr.value == a.currency))",

"// Account routing matches bank routing scheme",
"accountOpt.exists(a => bankOpt.exists(b => a.accountRoutings.exists(_.scheme == b.bankRoutingScheme)))",
```

### Transaction Request Comparisons

```scala
"// Transaction request bank matches account bank",
"transactionRequestOpt.exists(tr => accountOpt.exists(a => tr.this_bank_id.value == a.bankId))",

"// Transaction request account matches the account in context",
"transactionRequestOpt.exists(tr => accountOpt.exists(a => tr.this_account_id.value == a.accountId.value))",

"// Transaction request bank matches the bank in context",
"transactionRequestOpt.exists(tr => bankOpt.exists(b => tr.this_bank_id.value == b.bankId.value))",

"// Transaction and transaction request have matching amounts",
"transactionOpt.isDefined && transactionRequestOpt.isDefined && transactionOpt.get.amount == transactionRequestOpt.get.charge.value.toDouble",

"// Transaction request counterparty bank is different from this bank",
"transactionRequestOpt.exists(tr => bankOpt.exists(b => tr.counterparty_id.value != b.bankId.value))",
```

### Attribute Cross-Comparisons

```scala
"// User tier matches account tier",
"userAttributes.exists(ua => ua.name == \"tier\" && accountAttributes.exists(aa => aa.name == \"tier\" && ua.value == aa.value))",

"// Customer segment matches account segment",
"customerAttributes.exists(ca => ca.name == \"segment\" && accountAttributes.exists(aa => aa.name == \"segment\" && ca.value == aa.value))",

"// User's department attribute matches account's department attribute",
"authenticatedUserAttributes.exists(ua => ua.name == \"department\" && accountAttributes.exists(aa => aa.name == \"department\" && ua.value == aa.value))",

"// Transaction risk score less than user's risk tolerance",
"transactionAttributes.exists(ta => ta.name == \"risk_score\" && userAttributes.exists(ua => ua.name == \"risk_tolerance\" && ta.value.toInt <= ua.value.toInt))",

"// Authenticated user role has higher priority than target user role",
"authenticatedUserAttributes.exists(aua => aua.name == \"role_priority\" && userAttributes.exists(ua => ua.name == \"role_priority\" && aua.value.toInt > ua.value.toInt))",

"// Bank region matches customer region",
"bankAttributes.exists(ba => ba.name == \"region\" && customerAttributes.exists(ca => ca.name == \"region\" && ba.value == ca.value))",
```

### Complex Multi-Object Comparisons

```scala
"// User owns account and customer record matches",
"userOpt.exists(u => accountOpt.exists(a => customerOpt.exists(c => u.emailAddress == c.email && a.accountId.value.contains(u.userId))))",

"// Authenticated user accessing their own account through matching customer",
"customerOpt.exists(_.email == authenticatedUser.emailAddress) && accountOpt.exists(a => customerAttributes.exists(_.name == \"customer_id\" && _.value == a.accountId.value))",

"// Transaction within limits for user tier and account type combination",
"transactionOpt.exists(t => userAttributes.exists(ua => ua.name == \"tier\" && ua.value.toInt >= 2) && accountOpt.exists(a => a.accountType == \"PREMIUM\" && t.amount <= 50000))",

"// Cross-reference: authenticated user is account holder and transaction is self-initiated",
"accountOpt.exists(_.accountHolders.exists(_.userId == authenticatedUser.userId)) && transactionOpt.exists(t => t.otherAccount.metadata.exists(_.owner.exists(_.name == authenticatedUser.name)))",

"// Delegation chain: acting user -> on behalf of user -> target user relationship",
"onBehalfOfUserOpt.isDefined && userOpt.isDefined && onBehalfOfUserAttributes.exists(_.name == \"delegator\" && _.value == userOpt.get.userId)",

"// Bank, account, and transaction all in same currency region",
"bankAttributes.exists(ba => ba.name == \"currency_region\" && accountOpt.exists(a => transactionOpt.exists(t => t.currency == a.currency && ba.value.contains(a.currency))))",
```

### Time and Amount Threshold Comparisons

```scala
"// Transaction amount is within user's daily limit attribute",
"transactionOpt.exists(t => authenticatedUserAttributes.exists(attr => attr.name == \"daily_transaction_limit\" && t.amount <= attr.value.toDouble))",

"// Transaction amount below account's overdraft limit",
"transactionOpt.exists(t => accountAttributes.exists(attr => attr.name == \"overdraft_limit\" && t.amount <= attr.value.toDouble + accountOpt.get.balance))",

"// User tier level supports account tier level",
"userAttributes.exists(ua => ua.name == \"max_account_tier\" && accountAttributes.exists(aa => aa.name == \"tier_level\" && ua.value.toInt >= aa.value.toInt))",

"// Transaction request priority matches user priority level",
"transactionRequestAttributes.exists(tra => tra.name == \"priority\" && authenticatedUserAttributes.exists(aua => aua.name == \"max_priority\" && List(\"low\", \"medium\", \"high\").indexOf(tra.value) <= List(\"low\", \"medium\", \"high\").indexOf(aua.value)))",
```

### Geographic and Compliance Comparisons

```scala
"// User's country matches bank's country",
"authenticatedUserAttributes.exists(ua => ua.name == \"country\" && bankAttributes.exists(ba => ba.name == \"country\" && ua.value == ba.value))",

"// Transaction from same region as account",
"callContext.exists(cc => cc.ipAddress.exists(ip => accountAttributes.exists(aa => aa.name == \"region\" && transactionAttributes.exists(ta => ta.name == \"origin_region\" && aa.value == ta.value))))",

"// Customer and bank in same regulatory jurisdiction",
"customerAttributes.exists(ca => ca.name == \"jurisdiction\" && bankAttributes.exists(ba => ba.name == \"jurisdiction\" && ca.value == ba.value))",
```

### Negative Comparison Examples (What NOT to allow)

```scala
"// Deny if authenticated user is deleted but trying to access active account",
"!(authenticatedUser.isDeleted.getOrElse(false) && accountOpt.exists(a => accountAttributes.exists(_.name == \"status\" && _.value == \"active\")))",

"// Deny if transaction currency doesn't match account currency and no FX approval",
"!(transactionOpt.exists(t => accountOpt.exists(a => t.currency != a.currency)) && !transactionAttributes.exists(_.name == \"fx_approved\"))",

"// Deny if user tier is lower than required tier for account",
"!userAttributes.exists(ua => ua.name == \"tier\" && accountAttributes.exists(aa => aa.name == \"required_tier\" && ua.value.toInt < aa.value.toInt))",

"// Deny if delegation user doesn't have permission for target user",
"!(onBehalfOfUserOpt.isDefined && userOpt.isDefined && !onBehalfOfUserAttributes.exists(attr => attr.name == \"can_access_user\" && attr.value == userOpt.get.userId))",
```

---

## Chained Object Comparisons

Multiple levels of object relationships:

```scala
"// Verify entire chain: User -> Customer -> Account -> Transaction",
"userOpt.exists(u => customerOpt.exists(c => c.email == u.emailAddress && accountOpt.exists(a => transactionOpt.exists(t => t.accountId.value == a.accountId.value))))",

"// Bank -> Account -> Transaction Request -> Transaction alignment",
"bankOpt.exists(b => accountOpt.exists(a => a.bankId == b.bankId.value && transactionRequestOpt.exists(tr => tr.this_account_id.value == a.accountId.value && transactionOpt.exists(t => t.accountId.value == a.accountId.value))))",

"// Authenticated User -> On Behalf User -> Target User -> Customer chain",
"onBehalfOfUserOpt.exists(obu => obu.userId != authenticatedUser.userId && userOpt.exists(u => u.userId == obu.userId && customerOpt.exists(c => c.email == u.emailAddress)))",

"// Transaction consistency: Request -> Transaction -> Account -> Balance",
"transactionRequestOpt.exists(tr => transactionOpt.exists(t => t.amount == tr.charge.value.toDouble && accountOpt.exists(a => t.accountId.value == a.accountId.value && t.balance <= a.balance)))",
```

---

## Aggregation and Collection Comparisons

Comparing collections and aggregated values:

```scala
"// User has at least one matching attribute with target user",
"authenticatedUserAttributes.exists(aua => userAttributes.exists(ua => aua.name == ua.name && aua.value == ua.value))",

"// All required bank attributes match account attributes",
"bankAttributes.filter(_.name.startsWith(\"required_\")).forall(ba => accountAttributes.exists(aa => aa.name == ba.name && aa.value == ba.value))",

"// Transaction attributes subset of allowed account transaction attributes",
"transactionAttributes.forall(ta => accountAttributes.exists(aa => aa.name == \"allowed_transaction_\" + ta.name && aa.value.contains(ta.value)))",

"// Count of user attributes matches minimum for account tier",
"userAttributes.size >= accountAttributes.find(_.name == \"min_user_attributes\").map(_.value.toInt).getOrElse(0)",

"// Sum of transaction amounts in attributes below account limit",
"transactionAttributes.filter(_.name.startsWith(\"amount_\")).map(_.value.toDouble).sum < accountAttributes.find(_.name == \"transaction_sum_limit\").map(_.value.toDouble).getOrElse(Double.MaxValue)",

"// User and customer share at least 2 common attribute types",
"authenticatedUserAttributes.map(_.name).intersect(customerAttributes.map(_.name)).size >= 2",

"// All customer compliance attributes present in bank attributes",
"customerAttributes.filter(_.name.startsWith(\"compliance_\")).forall(ca => bankAttributes.exists(ba => ba.name == ca.name))",
```

---

## Conditional Object Comparisons

Context-dependent object relationships:

```scala
"// If delegation exists, verify delegation user can access target account",
"onBehalfOfUserOpt.isEmpty || (onBehalfOfUserOpt.exists(obu => accountOpt.exists(a => onBehalfOfUserAttributes.exists(attr => attr.name == \"accessible_accounts\" && attr.value.contains(a.accountId.value)))))",

"// If transaction exists, ensure it belongs to the account in context",
"transactionOpt.isEmpty || transactionOpt.exists(t => accountOpt.exists(a => t.accountId.value == a.accountId.value))",

"// If customer exists, verify they own the account or user is customer",
"customerOpt.isEmpty || (customerOpt.exists(c => accountOpt.exists(a => customerAttributes.exists(_.name == \"account_id\" && _.value == a.accountId.value)) || c.email == authenticatedUser.emailAddress))",

"// Either self-access OR manager of target user",
"(userOpt.exists(_.userId == authenticatedUser.userId)) || (authenticatedUserAttributes.exists(_.name == \"role\" && _.value == \"manager\") && userAttributes.exists(_.name == \"reports_to\" && _.value == authenticatedUser.userId))",

"// Transaction allowed if: same currency OR approved FX OR internal transfer",
"transactionOpt.exists(t => accountOpt.exists(a => t.currency == a.currency || transactionAttributes.exists(_.name == \"fx_approved\") || transactionAttributes.exists(_.name == \"type\" && _.value == \"internal\")))",
```

---

## Advanced Patterns

Safe Option handling patterns:

```scala
"// Pattern matching for Option types",
"userOpt match { case Some(u) => u.userId == authenticatedUser.userId case None => false }",

"// Using exists for safe access",
"accountOpt.exists(_.balance > 0)",

"// Using forall for negative conditions",
"userOpt.forall(!_.isDeleted.getOrElse(false))",

"// Combining isDefined with get (only when you've checked isDefined)",
"accountOpt.isDefined && accountOpt.get.balance > 1000",

"// Using getOrElse for defaults",
"accountOpt.map(_.balance).getOrElse(0) > 100",
```

---

## Performance Optimization Patterns

Efficient ways to write comparison rules:

```scala
"// Early exit with simple checks first",
"authenticatedUser.userId == \"admin\" || (userOpt.exists(_.userId == authenticatedUser.userId) && accountOpt.exists(_.balance > 1000))",

"// Cache repeated lookups using pattern matching",
"(userOpt, accountOpt) match { case (Some(u), Some(a)) => u.userId == authenticatedUser.userId && a.balance > 1000 case _ => false }",

"// Use exists instead of filter + nonEmpty",
"accountAttributes.exists(_.name == \"status\") // Better than: accountAttributes.filter(_.name == \"status\").nonEmpty",

"// Combine checks to reduce iterations",
"authenticatedUserAttributes.exists(attr => attr.name == \"role\" && List(\"admin\", \"manager\", \"supervisor\").contains(attr.value))",

"// Use forall for negative conditions efficiently",
"transactionAttributes.forall(attr => attr.name != \"blocked\" || attr.value != \"true\")",
```

---

## Real-World Business Logic Examples

Practical scenarios combining object comparisons:

```scala
"// Loan approval: Check customer credit score vs account history and transaction patterns",
"customerAttributes.exists(ca => ca.name == \"credit_score\" && ca.value.toInt > 650) && accountOpt.exists(a => a.balance > 5000 && accountAttributes.exists(aa => aa.name == \"age_months\" && aa.value.toInt > 6)) && !transactionAttributes.exists(_.name == \"fraud_flag\")",

"// Wire transfer authorization: Amount, user level, and dual control",
"transactionOpt.exists(t => t.amount < 100000 && t.transactionType.exists(_.contains(\"WIRE\"))) && authenticatedUserAttributes.exists(_.name == \"wire_authorized\" && _.value == \"true\") && (transactionRequestAttributes.exists(_.name == \"dual_approved\") || t.amount < 10000)",

"// Account closure permission: Self-service only if zero balance, otherwise manager approval",
"accountOpt.exists(a => (a.balance == 0 && userOpt.exists(_.userId == authenticatedUser.userId)) || (authenticatedUserAttributes.exists(_.name == \"role\" && _.value == \"manager\") && accountAttributes.exists(_.name == \"closure_requested\")))",

"// Cross-border payment compliance: Country checks, limits, and documentation",
"transactionOpt.exists(t => bankAttributes.exists(ba => ba.name == \"country\" && transactionAttributes.exists(ta => ta.name == \"destination_country\" && ta.value != ba.value))) && transactionAttributes.exists(_.name == \"compliance_docs_attached\") && t.amount <= 50000 && customerAttributes.exists(_.name == \"international_enabled\")",

"// VIP customer priority processing: Multiple tier checks across entities",
"(customerAttributes.exists(_.name == \"vip_status\" && _.value == \"true\") || accountAttributes.exists(_.name == \"account_tier\" && _.value == \"platinum\") || userAttributes.exists(_.name == \"priority_level\" && _.value.toInt >= 9)) && bankAttributes.exists(_.name == \"priority_processing\" && _.value == \"enabled\")",

"// Fraud prevention: IP, amount, velocity, and customer behavior",
"callContext.exists(cc => cc.ipAddress.exists(ip => customerAttributes.exists(ca => ca.name == \"trusted_ips\" && ca.value.contains(ip)))) && transactionOpt.exists(t => t.amount < userAttributes.find(_.name == \"daily_limit\").map(_.value.toDouble).getOrElse(1000.0)) && !transactionAttributes.exists(_.name == \"velocity_flag\")",

"// Internal employee access: Employee status, department match, and reason code",
"authenticatedUserAttributes.exists(_.name == \"employee_status\" && _.value == \"active\") && authenticatedUserAttributes.exists(aua => aua.name == \"department\" && accountAttributes.exists(aa => aa.name == \"department\" && aua.value == aa.value)) && callContext.exists(_.requestHeaders.exists(_.contains(\"X-Access-Reason\")))",

"// Joint account access: Either account holder can access",
"accountOpt.exists(a => a.accountHolders.exists(h => h.userId == authenticatedUser.userId || h.emailAddress == authenticatedUser.emailAddress)) || customerOpt.exists(c => accountAttributes.exists(aa => aa.name == \"joint_customer_ids\" && aa.value.contains(c.customerId)))",

"// Savings withdrawal limits: Time-based and balance-based restrictions",
"accountOpt.exists(a => a.accountType == \"SAVINGS\" && transactionOpt.exists(t => t.transactionType.exists(_.contains(\"WITHDRAWAL\")) && t.amount <= a.balance * 0.1 && accountAttributes.exists(aa => aa.name == \"withdrawals_this_month\" && aa.value.toInt < 6)))",

"// Merchant payment authorization: Merchant verification and customer spending limit",
"transactionAttributes.exists(ta => ta.name == \"merchant_id\" && transactionRequestAttributes.exists(tra => tra.name == \"verified_merchant\" && tra.value == ta.value)) && transactionOpt.exists(t => customerAttributes.exists(ca => ca.name == \"merchant_spend_limit\" && t.amount <= ca.value.toDouble))",
```

---

## Error Prevention Patterns

Common pitfalls and how to avoid them:

```scala
"// WRONG: accountOpt.get.balance > 1000 (can throw NoSuchElementException)",
"// RIGHT: accountOpt.exists(_.balance > 1000)",

"// WRONG: userOpt.isDefined && accountOpt.isDefined && userOpt.get.userId == accountOpt.get.accountHolders.head.userId",
"// RIGHT: userOpt.exists(u => accountOpt.exists(a => a.accountHolders.exists(_.userId == u.userId)))",

"// WRONG: transactionOpt.get.amount < accountOpt.get.balance (unsafe gets)",
"// RIGHT: transactionOpt.exists(t => accountOpt.exists(a => t.amount < a.balance))",

"// WRONG: authenticatedUser.emailAddress.split(\"@\").last == userOpt.get.emailAddress.split(\"@\").last",
"// RIGHT: userOpt.exists(u => authenticatedUser.emailAddress.split(\"@\").lastOption == u.emailAddress.split(\"@\").lastOption)",

"// Safe list access: Check empty before accessing",
"// WRONG: accountOpt.get.accountHolders.head.userId == authenticatedUser.userId",
"// RIGHT: accountOpt.exists(_.accountHolders.headOption.exists(_.userId == authenticatedUser.userId))",

"// Safe numeric conversions",
"// WRONG: userAttributes.find(_.name == \"tier\").get.value.toInt > 2",
"// RIGHT: userAttributes.find(_.name == \"tier\").exists(attr => scala.util.Try(attr.value.toInt).toOption.exists(_ > 2))",
```

---

## Important Notes to Include

The schema response should also emphasize these notes:

1. **PARAMETER NAMES**: Use exact parameter names: `authenticatedUser`, `userOpt`, `accountOpt`, `bankOpt`, `transactionOpt`, etc. (NOT `user`, `account`, `bank`)

2. **PROPERTY NAMES**: Use camelCase - `userId` (NOT `user_id`), `accountId` (NOT `account_id`), `emailAddress` (NOT `email_address`)

3. **OPTION TYPES**: Only `authenticatedUser`, `authenticatedUserAttributes`, and `authenticatedUserAuthContext` are guaranteed. All others are `Option` types - always check `isDefined` before using `.get`, or use safe methods like `exists()`, `forall()`, `map()`

4. **LIST TYPES**: Attributes are Lists - use Scala collection methods like `exists()`, `find()`, `filter()`, `forall()`

5. **SAFE OPTION HANDLING**: Prefer pattern matching or `exists()` over `isDefined` + `.get`

6. **RETURN TYPE**: Rules must return Boolean - `true` = access granted, `false` = access denied

7. **AUTO-FETCHING**: Objects are automatically fetched based on IDs passed to the execute endpoint

8. **COMMON MISTAKE**: Writing `user.user_id` instead of `userOpt.get.userId` or `authenticatedUser.userId`

---

## Implementation Location

In the OBP-API repository:

- Find the endpoint implementation for `GET /obp/v6.0.0/management/abac-rules-schema`
- Update the `examples` field in the response JSON
- Likely located in APIv6.0.0 package

---

## Testing

After updating, verify:

1. All examples are syntactically correct Scala expressions
2. Examples cover all 19 parameters
3. Examples demonstrate both simple and complex patterns
4. Safe Option handling is demonstrated
5. Common pitfalls are addressed

---

_Document Version: 1.0_  
_Created: 2024_  
_Purpose: Enhancement specification for OBP API ABAC rule schema examples_
