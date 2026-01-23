# ABAC Rules Engine

## Overview

The ABAC (Attribute-Based Access Control) Rules Engine allows you to create, compile, and execute dynamic access control rules using Scala functions. This provides flexible, fine-grained access control based on attributes of users, banks, accounts, transactions, and customers.

## Architecture

### Components

1. **AbacRule** - Data model for storing ABAC rules
2. **AbacRuleProvider** - Provider interface for CRUD operations on rules
3. **AbacRuleEngine** - Compiler and executor for ABAC rules
4. **AbacRuleEndpoints** - REST API endpoints for managing and executing rules

### Rule Function Signature

Each ABAC rule is a Scala function with the following signature:

```scala
(
  user: User,
  bankOpt: Option[Bank],
  accountOpt: Option[BankAccount],
  transactionOpt: Option[Transaction],
  customerOpt: Option[Customer]
) => Boolean
```

**Returns:**
- `true` - Access is granted
- `false` - Access is denied

## API Endpoints

All ABAC endpoints are under `/obp/v6.0.0/management/abac-rules` and require authentication.

### 1. Create ABAC Rule
**POST** `/management/abac-rules`

**Role Required:** `CanCreateAbacRule`

**Request Body:**
```json
{
  "rule_name": "admin_only",
  "rule_code": "user.emailAddress.contains(\"admin\")",
  "description": "Only allow access to users with admin email",
  "is_active": true
}
```

**Response:** (201 Created)
```json
{
  "abac_rule_id": "abc123",
  "rule_name": "admin_only",
  "rule_code": "user.emailAddress.contains(\"admin\")",
  "is_active": true,
  "description": "Only allow access to users with admin email",
  "created_by_user_id": "user123",
  "updated_by_user_id": "user123"
}
```

### 2. Get ABAC Rule
**GET** `/management/abac-rules/{ABAC_RULE_ID}`

**Role Required:** `CanGetAbacRule`

### 3. Get All ABAC Rules
**GET** `/management/abac-rules`

**Role Required:** `CanGetAbacRule`

### 4. Update ABAC Rule
**PUT** `/management/abac-rules/{ABAC_RULE_ID}`

**Role Required:** `CanUpdateAbacRule`

### 5. Delete ABAC Rule
**DELETE** `/management/abac-rules/{ABAC_RULE_ID}`

**Role Required:** `CanDeleteAbacRule`

### 6. Execute ABAC Rule
**POST** `/management/abac-rules/{ABAC_RULE_ID}/execute`

**Role Required:** `CanExecuteAbacRule`

**Request Body:**
```json
{
  "bank_id": "gh.29.uk",
  "account_id": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
  "transaction_id": null,
  "customer_id": null
}
```

**Response:**
```json
{
  "rule_id": "abc123",
  "rule_name": "admin_only",
  "result": true,
  "message": "Access granted"
}
```

## Rule Examples

### Example 1: Admin-Only Access
Only users with "admin" in their email can access:
```scala
user.emailAddress.contains("admin")
```

### Example 2: High Balance Accounts
Only allow access to accounts with balance > 10,000:
```scala
accountOpt.exists(account => {
  account.balance.toString.toDoubleOption.exists(_ > 10000.0)
})
```

### Example 3: Specific Bank Access
Only allow access to a specific bank:
```scala
bankOpt.exists(_.bankId.value == "gh.29.uk")
```

### Example 4: Transaction Amount Limit
Only allow access to transactions under 1,000:
```scala
transactionOpt.exists(tx => {
  tx.amount.toString.toDoubleOption.exists(_ < 1000.0)
})
```

### Example 5: Customer Email Domain
Only allow access if customer email is from a specific domain:
```scala
customerOpt.exists(_.email.endsWith("@example.com"))
```

### Example 6: Combined Rules
Multiple conditions combined:
```scala
user.emailAddress.contains("manager") &&
bankOpt.exists(_.bankId.value == "gh.29.uk") &&
accountOpt.exists(_.balance.toString.toDoubleOption.exists(_ > 5000.0))
```

### Example 7: User Provider Check
Only allow access from specific authentication provider:
```scala
user.provider == "obp" && user.emailAddress.nonEmpty
```

### Example 8: Time-Based Access (using Java time)
Access only during business hours (requires additional imports in the engine):
```scala
{
  val hour = java.time.LocalTime.now().getHour
  hour >= 9 && hour <= 17
}
```

## Programmatic Usage

### Compile a Rule
```scala
import code.abacrule.AbacRuleEngine

val ruleCode = """user.emailAddress.contains("admin")"""
val compiled = AbacRuleEngine.compileRule("rule123", ruleCode)
```

### Execute a Rule
```scala
import code.abacrule.AbacRuleEngine
import com.openbankproject.commons.model._

val result = AbacRuleEngine.executeRule(
  ruleId = "rule123",
  user = currentUser,
  bankOpt = Some(bank),
  accountOpt = Some(account),
  transactionOpt = None,
  customerOpt = None
)

result match {
  case Full(true) => println("Access granted")
  case Full(false) => println("Access denied")
  case Failure(msg, _, _) => println(s"Error: $msg")
  case Empty => println("Rule not found")
}
```

### Execute Multiple Rules (AND Logic)
All rules must pass:
```scala
val result = AbacRuleEngine.executeRulesAnd(
  ruleIds = List("rule1", "rule2", "rule3"),
  user = currentUser,
  bankOpt = Some(bank)
)
```

### Execute Multiple Rules (OR Logic)
At least one rule must pass:
```scala
val result = AbacRuleEngine.executeRulesOr(
  ruleIds = List("rule1", "rule2", "rule3"),
  user = currentUser,
  bankOpt = Some(bank)
)
```

### Validate Rule Code
```scala
val validation = AbacRuleEngine.validateRuleCode(ruleCode)
validation match {
  case Full(msg) => println(s"Valid: $msg")
  case Failure(msg, _, _) => println(s"Invalid: $msg")
  case Empty => println("Validation failed")
}
```

### Cache Management
```scala
// Clear entire cache
AbacRuleEngine.clearCache()

// Clear specific rule
AbacRuleEngine.clearRuleFromCache("rule123")

// Get cache statistics
val stats = AbacRuleEngine.getCacheStats()
println(s"Cached rules: ${stats("cached_rules")}")
```

## Security Considerations

### Sandboxing
The ABAC engine can execute rules in a sandboxed environment with restricted permissions. Configure via:
```properties
dynamic_code_sandbox_permissions=[]
```

### Code Validation
All rule code is compiled before execution. Invalid Scala code will be rejected at creation/update time.

### Best Practices

1. **Test Rules Before Activating**: Use the execute endpoint to test rules with sample data
2. **Keep Rules Simple**: Complex logic is harder to debug and maintain
3. **Use Descriptive Names**: Name rules clearly to indicate their purpose
4. **Document Rules**: Use the description field to explain what the rule does
5. **Review Regularly**: Audit active rules periodically
6. **Version Control**: Keep rule code in version control alongside application code
7. **Fail-Safe**: Consider what happens if a rule fails - default to deny access

## Performance

### Compilation Caching
- Compiled rules are cached in memory
- Cache is automatically populated on first execution
- Cache is cleared when rules are updated or deleted
- Manual cache clearing available via `AbacRuleEngine.clearCache()`

### Execution Performance
- First execution: ~100-500ms (compilation + execution)
- Subsequent executions: ~1-10ms (cached execution)

## Database Schema

The `MappedAbacRule` table stores:

| Column | Type | Description |
|--------|------|-------------|
| id | Long | Primary key |
| mAbacRuleId | String(255) | Unique UUID |
| mRuleName | String(255) | Human-readable name |
| mRuleCode | Text | Scala function code |
| mIsActive | Boolean | Whether rule is active |
| mDescription | Text | Rule description |
| mCreatedByUserId | String(255) | User ID who created rule |
| mUpdatedByUserId | String(255) | User ID who last updated rule |
| createdAt | Timestamp | Creation timestamp |
| updatedAt | Timestamp | Last update timestamp |

Indexes:
- `mAbacRuleId` (unique)
- `mRuleName`

## Error Handling

### Common Errors

**Compilation Errors:**
```
Failed to compile ABAC rule: not found: value accountBalanc
```
→ Fix typos in rule code

**Runtime Errors:**
```
Execution error: java.lang.NullPointerException
```
→ Use safe navigation with `Option` types

**Inactive Rule:**
```
ABAC Rule admin_only is not active
```
→ Set `is_active: true` when creating/updating

### Safe Code Patterns

❌ **Unsafe:**
```scala
account.balance.toString.toDouble > 1000.0
```

✅ **Safe:**
```scala
accountOpt.exists(_.balance.toString.toDoubleOption.exists(_ > 1000.0))
```

## Integration Examples

### Protecting an Endpoint
```scala
// In your endpoint implementation
for {
  (Full(user), callContext) <- authenticatedAccess(cc)
  (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
  (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
  
  // Check ABAC rules
  allowed <- Future {
    AbacRuleEngine.executeRulesAnd(
      ruleIds = List("bank_access_rule", "account_limit_rule"),
      user = user,
      bankOpt = Some(bank),
      accountOpt = Some(account)
    )
  } map {
    unboxFullOrFail(_, callContext, "ABAC access check failed", 403)
  }
  
  _ <- Helper.booleanToFuture(s"Access denied by ABAC rules", cc = callContext) {
    allowed
  }
  
  // Continue with endpoint logic...
} yield {
  // ...
}
```

## Roadmap

Future enhancements:
- [ ] Rule versioning
- [ ] Rule testing framework
- [ ] Rule analytics/logging
- [ ] Rule templates library
- [ ] Visual rule builder UI
- [ ] Rule impact analysis
- [ ] A/B testing for rules
- [ ] Rule scheduling (time-based activation)
- [ ] Rule dependencies/chaining
- [ ] Machine learning-based rule suggestions

## Technical Implementation Notes

### Lazy Initialization Pattern

The `AbacRuleEndpoints` trait uses lazy initialization to avoid `NullPointerException` during startup:

```scala
// Lazy initialization block - called when first endpoint is accessed
private lazy val abacResourceDocsRegistered: Boolean = {
  registerAbacResourceDocs()
  true
}

lazy val createAbacRule: OBPEndpoint = {
  case "management" :: "abac-rules" :: Nil JsonPost json -> _ => {
    abacResourceDocsRegistered // Triggers initialization
    // ... endpoint implementation
  }
}
```

**Why this is needed:**
- Traits are initialized before concrete classes
- `implementedInApiVersion` is provided by the mixing class
- Without lazy initialization, `ResourceDoc` creation would fail with null API version
- Lazy initialization ensures all values are set before first use

### Timestamp Fields

The `MappedAbacRule` class uses Lift's `CreatedUpdated` trait which automatically provides:
- `createdAt`: Timestamp when rule was created
- `updatedAt`: Timestamp when rule was last updated

These fields are:
- ✅ Stored in the database
- ✅ Automatically managed by Lift Mapper
- ❌ Not exposed in JSON responses (to keep API responses clean)
- ✅ Available internally for auditing

The JSON response only includes `created_by_user_id` and `updated_by_user_id` for tracking who modified the rule.

### Thread Safety

- **Rule Compilation**: Synchronized via ConcurrentHashMap
- **Cache Access**: Thread-safe through concurrent collections
- **Lazy Initialization**: Scala's lazy val is thread-safe by default
- **Database Access**: Handled by Lift Mapper's connection pooling

## Support

For issues or questions:
- Check the OBP API documentation
- Review existing rules in your deployment
- Test rules using the execute endpoint
- Check logs for compilation/execution errors

## License

Open Bank Project - AGPL v3