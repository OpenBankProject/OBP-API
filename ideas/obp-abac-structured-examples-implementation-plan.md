# OBP ABAC Structured Examples Implementation Plan

## Goal

Convert the ABAC rule schema examples from simple strings to structured objects with:
- `category`: String - Grouping/category of the example
- `title`: String - Short descriptive title
- `code`: String - The actual Scala code example
- `description`: String - Detailed explanation of what the code does

## Example Structure

```json
{
  "category": "User Attributes",
  "title": "Account Type Check",
  "code": "userAttributes.exists(attr => attr.name == \"account_type\" && attr.value == \"premium\")",
  "description": "Check if target user has premium account type attribute"
}
```

## Implementation Steps

### Step 1: Update JSON Case Class

**File**: `OBP-API/obp-api/src/main/scala/code/api/v6_0_0/JSONFactory6.0.0.scala`

**Current code** (around line 413-419):
```scala
case class AbacRuleSchemaJsonV600(
    parameters: List[AbacParameterJsonV600],
    object_types: List[AbacObjectTypeJsonV600],
    examples: List[String],
    available_operators: List[String],
    notes: List[String]
)
```

**Change to**:
```scala
case class AbacRuleExampleJsonV600(
    category: String,
    title: String,
    code: String,
    description: String
)

case class AbacRuleSchemaJsonV600(
    parameters: List[AbacParameterJsonV600],
    object_types: List[AbacObjectTypeJsonV600],
    examples: List[AbacRuleExampleJsonV600],  // Changed from List[String]
    available_operators: List[String],
    notes: List[String]
)
```

### Step 2: Update API Endpoint

**File**: `OBP-API/obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`

**Location**: The `getAbacRuleSchema` endpoint (around line 4891-5070)

**Find this line** (around line 5021):
```scala
examples = List(
```

**Replace the entire examples List with structured examples**.

See the comprehensive list in Section 3 below.

### Step 3: Structured Examples List

Replace the `examples = List(...)` with this:

```scala
examples = List(
  // === Authenticated User Examples ===
  AbacRuleExampleJsonV600(
    category = "Authenticated User",
    title = "Check Email Domain",
    code = """authenticatedUser.emailAddress.contains("@example.com")""",
    description = "Verify authenticated user's email belongs to a specific domain"
  ),
  AbacRuleExampleJsonV600(
    category = "Authenticated User",
    title = "Check Provider",
    code = """authenticatedUser.provider == "obp"""",
    description = "Verify the authentication provider is OBP"
  ),
  AbacRuleExampleJsonV600(
    category = "Authenticated User",
    title = "User Not Deleted",
    code = """!authenticatedUser.isDeleted.getOrElse(false)""",
    description = "Ensure the authenticated user account is not marked as deleted"
  ),

  // === Authenticated User Attributes ===
  AbacRuleExampleJsonV600(
    category = "Authenticated User Attributes",
    title = "Admin Role Check",
    code = """authenticatedUserAttributes.exists(attr => attr.name == "role" && attr.value == "admin")""",
    description = "Check if authenticated user has admin role attribute"
  ),
  AbacRuleExampleJsonV600(
    category = "Authenticated User Attributes",
    title = "Department Check",
    code = """authenticatedUserAttributes.find(_.name == "department").exists(_.value == "finance")""",
    description = "Check if user belongs to finance department"
  ),
  AbacRuleExampleJsonV600(
    category = "Authenticated User Attributes",
    title = "Multiple Role Check",
    code = """authenticatedUserAttributes.exists(attr => attr.name == "role" && List("admin", "manager", "supervisor").contains(attr.value))""",
    description = "Check if user has any of the specified management roles"
  ),

  // === Target User Examples ===
  AbacRuleExampleJsonV600(
    category = "Target User",
    title = "Self Access",
    code = """userOpt.exists(_.userId == authenticatedUser.userId)""",
    description = "Check if target user is the authenticated user (self-access)"
  ),
  AbacRuleExampleJsonV600(
    category = "Target User",
    title = "Same Email Domain",
    code = """userOpt.exists(u => authenticatedUser.emailAddress.split("@")(1) == u.emailAddress.split("@")(1))""",
    description = "Check both users share the same email domain"
  ),

  // === User Attributes ===
  AbacRuleExampleJsonV600(
    category = "User Attributes",
    title = "Premium Account Type",
    code = """userAttributes.exists(attr => attr.name == "account_type" && attr.value == "premium")""",
    description = "Check if target user has premium account type attribute"
  ),
  AbacRuleExampleJsonV600(
    category = "User Attributes",
    title = "KYC Verified",
    code = """userAttributes.exists(attr => attr.name == "kyc_status" && attr.value == "verified")""",
    description = "Verify target user has completed KYC verification"
  ),

  // === Account Examples ===
  AbacRuleExampleJsonV600(
    category = "Account",
    title = "Balance Threshold",
    code = """accountOpt.exists(_.balance > 1000)""",
    description = "Check if account balance exceeds threshold"
  ),
  AbacRuleExampleJsonV600(
    category = "Account",
    title = "Currency and Balance",
    code = """accountOpt.exists(acc => acc.currency == "USD" && acc.balance > 5000)""",
    description = "Check account has USD currency and balance over 5000"
  ),
  AbacRuleExampleJsonV600(
    category = "Account",
    title = "Savings Account Type",
    code = """accountOpt.exists(_.accountType == "SAVINGS")""",
    description = "Verify account is a savings account"
  ),

  // === Transaction Examples ===
  AbacRuleExampleJsonV600(
    category = "Transaction",
    title = "Amount Limit",
    code = """transactionOpt.exists(_.amount < 10000)""",
    description = "Check transaction amount is below limit"
  ),
  AbacRuleExampleJsonV600(
    category = "Transaction",
    title = "Transfer Type",
    code = """transactionOpt.exists(_.transactionType.contains("TRANSFER"))""",
    description = "Verify transaction is a transfer type"
  ),

  // === Customer Examples ===
  AbacRuleExampleJsonV600(
    category = "Customer",
    title = "Email Matches User",
    code = """customerOpt.exists(_.email == authenticatedUser.emailAddress)""",
    description = "Verify customer email matches authenticated user"
  ),
  AbacRuleExampleJsonV600(
    category = "Customer",
    title = "Active Relationship",
    code = """customerOpt.exists(_.relationshipStatus == "ACTIVE")""",
    description = "Check customer has active relationship status"
  ),

  // === Object-to-Object Comparisons ===
  AbacRuleExampleJsonV600(
    category = "Object Comparisons - User",
    title = "Self Access by User ID",
    code = """userOpt.exists(_.userId == authenticatedUser.userId)""",
    description = "Verify target user ID matches authenticated user (self-access)"
  ),
  AbacRuleExampleJsonV600(
    category = "Object Comparisons - Customer/User",
    title = "Customer Email Matches Target User",
    code = """customerOpt.exists(c => userOpt.exists(u => c.email == u.emailAddress))""",
    description = "Verify customer email matches target user"
  ),
  AbacRuleExampleJsonV600(
    category = "Object Comparisons - Account/Transaction",
    title = "Transaction Within Balance",
    code = """transactionOpt.exists(t => accountOpt.exists(a => t.amount < a.balance))""",
    description = "Verify transaction amount is less than account balance"
  ),
  AbacRuleExampleJsonV600(
    category = "Object Comparisons - Account/Transaction",
    title = "Currency Match",
    code = """transactionOpt.exists(t => accountOpt.exists(a => t.currency == a.currency))""",
    description = "Verify transaction currency matches account currency"
  ),
  AbacRuleExampleJsonV600(
    category = "Object Comparisons - Account/Transaction",
    title = "No Overdraft",
    code = """transactionOpt.exists(t => accountOpt.exists(a => a.balance - t.amount >= 0))""",
    description = "Ensure transaction won't overdraw account"
  ),

  // === Attribute Cross-Comparisons ===
  AbacRuleExampleJsonV600(
    category = "Object Comparisons - Attributes",
    title = "User Tier Matches Account Tier",
    code = """userAttributes.exists(ua => ua.name == "tier" && accountAttributes.exists(aa => aa.name == "tier" && ua.value == aa.value))""",
    description = "Verify user tier level matches account tier level"
  ),
  AbacRuleExampleJsonV600(
    category = "Object Comparisons - Attributes",
    title = "Department Match",
    code = """authenticatedUserAttributes.exists(ua => ua.name == "department" && accountAttributes.exists(aa => aa.name == "department" && ua.value == aa.value))""",
    description = "Verify user department matches account department"
  ),

  // === Complex Multi-Object Examples ===
  AbacRuleExampleJsonV600(
    category = "Complex Scenarios",
    title = "Trusted Employee Access",
    code = """authenticatedUser.emailAddress.endsWith("@bank.com") && accountOpt.exists(_.balance > 0) && bankOpt.exists(_.bankId.value == "gh.29.uk")""",
    description = "Allow bank employees to access accounts with positive balance at specific bank"
  ),
  AbacRuleExampleJsonV600(
    category = "Complex Scenarios",
    title = "Manager Accessing Team Data",
    code = """authenticatedUserAttributes.exists(_.name == "role" && _.value == "manager") && userOpt.exists(_.userId != authenticatedUser.userId)""",
    description = "Allow managers to access other users' data"
  ),
  AbacRuleExampleJsonV600(
    category = "Complex Scenarios",
    title = "VIP with Premium Account",
    code = """customerAttributes.exists(_.name == "vip_status" && _.value == "true") && accountAttributes.exists(_.name == "account_tier" && _.value == "premium")""",
    description = "Check for VIP customer with premium account combination"
  ),

  // === Chained Validation ===
  AbacRuleExampleJsonV600(
    category = "Chained Validation",
    title = "Full Customer Chain",
    code = """userOpt.exists(u => customerOpt.exists(c => c.email == u.emailAddress && accountOpt.exists(a => transactionOpt.exists(t => t.accountId.value == a.accountId.value))))""",
    description = "Validate complete chain: User → Customer → Account → Transaction"
  ),

  // === Real-World Business Logic ===
  AbacRuleExampleJsonV600(
    category = "Business Logic",
    title = "Loan Approval",
    code = """customerAttributes.exists(ca => ca.name == "credit_score" && ca.value.toInt > 650) && accountOpt.exists(_.balance > 5000)""",
    description = "Check credit score above 650 and minimum balance for loan approval"
  ),
  AbacRuleExampleJsonV600(
    category = "Business Logic",
    title = "Wire Transfer Authorization",
    code = """transactionOpt.exists(t => t.amount < 100000 && t.transactionType.exists(_.contains("WIRE"))) && authenticatedUserAttributes.exists(_.name == "wire_authorized")""",
    description = "Verify user is authorized for wire transfers under limit"
  ),
  AbacRuleExampleJsonV600(
    category = "Business Logic",
    title = "Joint Account Access",
    code = """accountOpt.exists(a => a.accountHolders.exists(h => h.userId == authenticatedUser.userId || h.emailAddress == authenticatedUser.emailAddress))""",
    description = "Allow access if user is one of the joint account holders"
  ),

  // === Safe Option Handling ===
  AbacRuleExampleJsonV600(
    category = "Safe Patterns",
    title = "Pattern Matching",
    code = """userOpt match { case Some(u) => u.userId == authenticatedUser.userId case None => false }""",
    description = "Safe Option handling using pattern matching"
  ),
  AbacRuleExampleJsonV600(
    category = "Safe Patterns",
    title = "Using exists()",
    code = """accountOpt.exists(_.balance > 0)""",
    description = "Safe way to check Option value using exists method"
  ),
  AbacRuleExampleJsonV600(
    category = "Safe Patterns",
    title = "Using forall()",
    code = """userOpt.forall(!_.isDeleted.getOrElse(false))""",
    description = "Safe negative condition using forall (returns true if None)"
  ),

  // === Error Prevention ===
  AbacRuleExampleJsonV600(
    category = "Common Mistakes",
    title = "WRONG - Unsafe get()",
    code = """accountOpt.get.balance > 1000""",
    description = "❌ WRONG: Using .get without checking isDefined (can throw exception)"
  ),
  AbacRuleExampleJsonV600(
    category = "Common Mistakes",
    title = "CORRECT - Safe exists()",
    code = """accountOpt.exists(_.balance > 1000)""",
    description = "✅ CORRECT: Safe way to check account balance using exists()"
  )
),
```

## Benefits of Structured Examples

### 1. Better UI/UX
- Examples can be grouped by category in the UI
- Searchable by title or description
- Code can be syntax highlighted separately
- Easier to filter and navigate

### 2. Better for AI/LLM Integration
- Clear structure for AI to understand
- Category helps with semantic search
- Description provides context for code generation
- Title provides quick summary

### 3. Better for Documentation
- Can generate categorized documentation automatically
- Can create searchable example libraries
- Easier to maintain and update
- Better for auto-completion in IDEs

### 4. API Response Example

**Before (flat strings)**:
```json
{
  "examples": [
    "// Check if authenticated user matches target user",
    "authenticatedUser.userId == userOpt.get.userId"
  ]
}
```

**After (structured)**:
```json
{
  "examples": [
    {
      "category": "Target User",
      "title": "Self Access",
      "code": "userOpt.exists(_.userId == authenticatedUser.userId)",
      "description": "Check if target user is the authenticated user (self-access)"
    }
  ]
}
```

## Testing

After implementation, test:

1. **API Response**: Call `GET /obp/v6.0.0/management/abac-rules-schema` and verify JSON structure
2. **Compilation**: Ensure Scala code compiles without errors
3. **Frontend**: Update any frontend code that consumes this endpoint
4. **Backward Compatibility**: Consider if any clients depend on the old string format

## Rollout Strategy

### Option A: Breaking Change (Recommended)
- Implement in v6.0.0 as shown above
- Document as breaking change in release notes
- Provide migration guide for clients

### Option B: Maintain Backward Compatibility
- Add new field `structured_examples` alongside existing `examples`
- Keep old `examples` as List[String] with just the code
- Deprecate old field, remove in v7.0.0

## Full Example Count

The implementation should include approximately **60-80 structured examples** covering:

- 3-4 examples per parameter (19 parameters) = ~60 examples
- 10-15 object-to-object comparison examples
- 5-10 complex multi-object scenarios
- 5 real-world business logic examples
- 4-5 safe pattern examples
- 2-3 error prevention examples

Total: ~80-100 examples

## Notes

- Use triple quotes `"""` for code strings to avoid escaping issues
- Keep code examples concise but realistic
- Ensure all examples are valid Scala syntax
- Test examples can actually compile/execute
- Categories should be consistent and logical
- Descriptions should explain the "why" not just the "what"

## Related Files

- Enhancement spec: `obp-abac-schema-examples-enhancement.md`
- Implementation summary (after): `obp-abac-schema-examples-implementation-summary.md`

---

**Status**: Ready for Implementation  
**Priority**: Medium  
**Estimated Effort**: 2-3 hours  
**Version**: OBP API v6.0.0
