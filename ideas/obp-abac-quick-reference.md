# OBP API ABAC Rules - Quick Reference Guide

## Most Common Patterns

Quick reference for the most frequently used ABAC rule patterns in OBP API v6.0.0.

---

## 1. Self-Access Checks

**Allow users to access their own data:**

```scala
// Basic self-access
userOpt.exists(_.userId == authenticatedUser.userId)

// Self-access by email
userOpt.exists(_.emailAddress == authenticatedUser.emailAddress)

// Self-access for accounts
accountOpt.exists(_.accountHolders.exists(_.userId == authenticatedUser.userId))
```

---

## 2. Role-Based Access

**Check user roles and permissions:**

```scala
// Admin access
authenticatedUserAttributes.exists(attr => attr.name == "role" && attr.value == "admin")

// Multiple role check
authenticatedUserAttributes.exists(attr => attr.name == "role" && List("admin", "manager", "supervisor").contains(attr.value))

// Department-based access
authenticatedUserAttributes.exists(ua => ua.name == "department" && accountAttributes.exists(aa => aa.name == "department" && ua.value == aa.value))
```

---

## 3. Balance and Amount Checks

**Transaction and balance validations:**

```scala
// Transaction within account balance
transactionOpt.exists(t => accountOpt.exists(a => t.amount < a.balance))

// Transaction within 50% of balance
transactionOpt.exists(t => accountOpt.exists(a => t.amount <= a.balance * 0.5))

// Account balance threshold
accountOpt.exists(_.balance > 1000)

// No overdraft
transactionOpt.exists(t => accountOpt.exists(a => a.balance - t.amount >= 0))
```

---

## 4. Currency Matching

**Ensure currency consistency:**

```scala
// Transaction currency matches account
transactionOpt.exists(t => accountOpt.exists(a => t.currency == a.currency))

// Specific currency check
accountOpt.exists(acc => acc.currency == "USD" && acc.balance > 5000)
```

---

## 5. Bank and Account Validation

**Verify bank and account relationships:**

```scala
// Specific bank
bankOpt.exists(_.bankId.value == "gh.29.uk")

// Account belongs to bank
accountOpt.exists(a => bankOpt.exists(b => a.bankId == b.bankId.value))

// Transaction request matches account
transactionRequestOpt.exists(tr => accountOpt.exists(a => tr.this_account_id.value == a.accountId.value))
```

---

## 6. Customer Validation

**Customer and KYC checks:**

```scala
// Customer email matches user
customerOpt.exists(_.email == authenticatedUser.emailAddress)

// Active customer relationship
customerOpt.exists(_.relationshipStatus == "ACTIVE")

// KYC verified
userAttributes.exists(attr => attr.name == "kyc_status" && attr.value == "verified")

// VIP customer
customerAttributes.exists(attr => attr.name == "vip_status" && attr.value == "true")
```

---

## 7. Transaction Type Checks

**Validate transaction types:**

```scala
// Specific transaction type
transactionOpt.exists(_.transactionType.contains("TRANSFER"))

// Amount limit by type
transactionOpt.exists(t => t.amount < 10000 && t.transactionType.exists(_.contains("WIRE")))

// Transaction request type
transactionRequestOpt.exists(_.type == "SEPA")
```

---

## 8. Delegation (On Behalf Of)

**Handle delegation scenarios:**

```scala
// No delegation or self-delegation only
onBehalfOfUserOpt.isEmpty || onBehalfOfUserOpt.exists(_.userId == authenticatedUser.userId)

// Authorized delegation
onBehalfOfUserOpt.isEmpty || onBehalfOfUserAttributes.exists(_.name == "authorized")

// Delegation to target user
onBehalfOfUserOpt.exists(obu => userOpt.exists(u => obu.userId == u.userId))
```

---

## 9. Tier and Level Matching

**Check tier compatibility:**

```scala
// User tier matches account tier
userAttributes.exists(ua => ua.name == "tier" && accountAttributes.exists(aa => aa.name == "tier" && ua.value == aa.value))

// Minimum tier requirement
userAttributes.find(_.name == "tier").exists(_.value.toInt >= 2)

// Premium account
accountAttributes.exists(attr => attr.name == "account_tier" && attr.value == "premium")
```

---

## 10. IP and Context Checks

**Request context validation:**

```scala
// Internal network
callContext.exists(_.ipAddress.exists(_.startsWith("192.168")))

// Specific HTTP method
callContext.exists(_.verb.exists(_ == "GET"))

// URL path check
callContext.exists(_.url.exists(_.contains("/accounts/")))

// Authentication method
authenticatedUserAuthContext.exists(_.key == "auth_method" && _.value == "certificate")
```

---

## 11. Combined Conditions

**Complex multi-condition rules:**

```scala
// Admin OR self-access
authenticatedUserAttributes.exists(_.name == "role" && _.value == "admin") || userOpt.exists(_.userId == authenticatedUser.userId)

// Manager accessing team member's data
authenticatedUserAttributes.exists(_.name == "role" && _.value == "manager") && userOpt.exists(_.userId != authenticatedUser.userId)

// Verified user with proper delegation
userAttributes.exists(_.name == "kyc_status" && _.value == "verified") && (onBehalfOfUserOpt.isEmpty || onBehalfOfUserAttributes.exists(_.name == "authorized"))
```

---

## 12. Safe Option Handling

**Always use safe patterns:**

```scala
// ✅ CORRECT: Use exists()
accountOpt.exists(_.balance > 1000)

// ✅ CORRECT: Use pattern matching
userOpt match { case Some(u) => u.userId == authenticatedUser.userId case None => false }

// ✅ CORRECT: Use forall() for negative conditions
userOpt.forall(!_.isDeleted.getOrElse(false))

// ✅ CORRECT: Use map() with getOrElse()
accountOpt.map(_.balance).getOrElse(0) > 100

// ❌ WRONG: Direct .get (can throw exception)
// accountOpt.get.balance > 1000
```

---

## 13. Real-World Business Scenarios

### Loan Approval
```scala
customerAttributes.exists(ca => ca.name == "credit_score" && ca.value.toInt > 650) && 
accountOpt.exists(_.balance > 5000) && 
!transactionAttributes.exists(_.name == "fraud_flag")
```

### Wire Transfer Authorization
```scala
transactionOpt.exists(t => t.amount < 100000 && t.transactionType.exists(_.contains("WIRE"))) && 
authenticatedUserAttributes.exists(_.name == "wire_authorized" && _.value == "true")
```

### Joint Account Access
```scala
accountOpt.exists(a => a.accountHolders.exists(h => 
  h.userId == authenticatedUser.userId || 
  h.emailAddress == authenticatedUser.emailAddress
))
```

### Account Closure (Self-service or Manager)
```scala
accountOpt.exists(a => 
  (a.balance == 0 && userOpt.exists(_.userId == authenticatedUser.userId)) || 
  authenticatedUserAttributes.exists(_.name == "role" && _.value == "manager")
)
```

### VIP Priority Processing
```scala
customerAttributes.exists(_.name == "vip_status" && _.value == "true") || 
accountAttributes.exists(_.name == "account_tier" && _.value == "platinum") || 
userAttributes.exists(_.name == "priority_level" && _.value.toInt >= 9)
```

### Cross-Border Transaction Compliance
```scala
transactionAttributes.exists(_.name == "compliance_docs_attached") && 
transactionOpt.exists(_.amount <= 50000) && 
customerAttributes.exists(_.name == "international_enabled" && _.value == "true")
```

---

## 14. Common Mistakes to Avoid

### ❌ Wrong Property Names
```scala
// WRONG - Snake case
user.user_id
account.account_id
user.email_address

// CORRECT - Camel case
user.userId
account.accountId
user.emailAddress
```

### ❌ Wrong Parameter Names
```scala
// WRONG - Missing Opt suffix
user.userId
account.balance
bank.bankId

// CORRECT - Proper naming
authenticatedUser.userId  // No Opt (always present)
userOpt.exists(_.userId == ...)  // Has Opt (optional)
accountOpt.exists(_.balance > ...)  // Has Opt (optional)
bankOpt.exists(_.bankId == ...)  // Has Opt (optional)
```

### ❌ Unsafe Option Access
```scala
// WRONG - Can throw NoSuchElementException
if (accountOpt.isDefined) {
  accountOpt.get.balance > 1000
}

// CORRECT - Safe access
accountOpt.exists(_.balance > 1000)
```

---

## 15. Parameter Reference

### Always Available (Required)
- `authenticatedUser` - User
- `authenticatedUserAttributes` - List[UserAttributeTrait]
- `authenticatedUserAuthContext` - List[UserAuthContext]

### Optional (Check before use)
- `onBehalfOfUserOpt` - Option[User]
- `onBehalfOfUserAttributes` - List[UserAttributeTrait]
- `onBehalfOfUserAuthContext` - List[UserAuthContext]
- `userOpt` - Option[User]
- `userAttributes` - List[UserAttributeTrait]
- `bankOpt` - Option[Bank]
- `bankAttributes` - List[BankAttributeTrait]
- `accountOpt` - Option[BankAccount]
- `accountAttributes` - List[AccountAttribute]
- `transactionOpt` - Option[Transaction]
- `transactionAttributes` - List[TransactionAttribute]
- `transactionRequestOpt` - Option[TransactionRequest]
- `transactionRequestAttributes` - List[TransactionRequestAttributeTrait]
- `customerOpt` - Option[Customer]
- `customerAttributes` - List[CustomerAttribute]
- `callContext` - Option[CallContext]

---

## 16. Useful Operators and Methods

### Comparison
- `==`, `!=`, `>`, `<`, `>=`, `<=`

### Logical
- `&&` (AND), `||` (OR), `!` (NOT)

### String Methods
- `contains()`, `startsWith()`, `endsWith()`, `split()`

### Option Methods
- `isDefined`, `isEmpty`, `exists()`, `forall()`, `map()`, `getOrElse()`

### List Methods
- `exists()`, `find()`, `filter()`, `forall()`, `map()`

### Numeric Conversions
- `toInt`, `toDouble`, `toLong`

---

## Quick Tips

1. **Always use camelCase** for property names
2. **Check Optional parameters** with `exists()`, not `.get`
3. **Use pattern matching** for complex Option handling
4. **Attributes are Lists** - use collection methods
5. **Rules return Boolean** - true = granted, false = denied
6. **Combine conditions** with `&&` and `||`
7. **Test thoroughly** before deploying to production

---

## Getting Full Schema

To get the complete schema with all 170+ examples:

```bash
curl -X GET \
  https://your-obp-instance/obp/v6.0.0/management/abac-rules-schema \
  -H 'Authorization: DirectLogin token=YOUR_TOKEN'
```

---

## Related Documentation

- Full Enhancement Spec: `obp-abac-schema-examples-enhancement.md`
- Before/After Comparison: `obp-abac-examples-before-after.md`
- Implementation Summary: `obp-abac-schema-examples-implementation-summary.md`

---

**Version**: OBP API v6.0.0  
**Last Updated**: 2024  
**Status**: Production Ready ✅
