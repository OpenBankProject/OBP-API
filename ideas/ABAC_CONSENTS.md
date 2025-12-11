# Thoughts on ABAC and Consents

## ABAC Overview

**Attribute-Based Access Control (ABAC)** evaluates access based on attributes:
- **User attributes**: `ABAC_role=teller`, `ABAC_branch=branch_123`, `ABAC_clearance_level=2`
- **Account attributes**: `branch=branch_123`, `account_type=checking`, `vip_status=gold`
- **Context**: time of day, business hours, customer present

Policy example: "Tellers can access accounts where user.branch == account.branch during business hours"

## The Challenge: Real Banking Workflows

Banks need staff access patterns like:
- **Branch-based**: Tellers at branch_123 see accounts at branch_123
- **Role-based**: VIP account managers see VIP accounts
- **Time-limited**: Customer service gets 30-minute access during customer interaction
- **Session-based**: Access expires when session/shift ends
- **Dynamic**: No manual pre-granting of thousands of permissions

Traditional approaches don't fit:
- Individual AccountAccess grants: doesn't scale (thousands of accounts)
- Firehose: too broad (all accounts at bank)
- Manual grants per request: too slow for operations

## Could Consents Work for ABAC?

Instead of real-time policy evaluation on every request, create a **consent** when ABAC policy matches.

### Flow Concept
1. User requests account access
2. No explicit AccountAccess grant exists
3. User has `CanUseABAC` entitlement
4. System evaluates ABAC policy (checks attributes)
5. Policy matches → Create consent with explicit account list
6. Consent valid for short period (15-60 minutes)
7. Subsequent requests check consent (fast lookup)
8. Consent expires → Re-evaluate policy on next request

### Why Consents Could Work
- Time limits built-in (`validFrom`, `validTo`)
- Status management (ACCEPTED, REVOKED, EXPIRED)
- Audit trail (creation, usage, expiry all logged)
- Explicit account list in `views` field
- Can be revoked when attributes change
- Reuses existing infrastructure (no new tables)
- Standard authorization check works (`hasAccountAccess`)

### Consent as Cache
The consent acts as a **cached ABAC decision**:
- Real-time evaluation would be slow (fetch attributes, evaluate policy)
- Consent caches: "User X can access accounts Y,Z at branch_123"
- Fast lookup: is requested account in consent's views list?
- Short TTL ensures freshness (15-60 minutes)
- Revoke on attribute change for immediate effect

## ABAC Consent vs Standard Consent

Structurally identical:
- Same fields: `userId`, `views`, `validFrom`, `validTo`, `status`
- Same table: `MappedConsent`
- Same authorization logic: lookup in `views` list
- Same usage: `hasAccountAccess()` checks for valid consent

Differences:
- **Creation**: Policy evaluation vs customer authorization
- **Lifetime**: 15-60 minutes vs 90+ days
- **Revocation**: Automatic (attribute change) vs manual (customer revokes)
- **Initiator**: System vs customer
- **Purpose**: Staff operations vs TPP access

## Schema Considerations: Marking ABAC Consents

Need to distinguish ABAC-generated consents for audit/management.

### Existing Fields Analysis

**`issuer` (iss in JWT)**
- Standard use: JWT issuer for validation (e.g., `Constant.HostName`, `"https://accounts.google.com"`)
- Used in OAuth2 flows for token validation
- **Should NOT be changed** - would break JWT validation logic
- Keep as standard OBP issuer

**`createdByUserId`**
- Standard consent: Customer who authorized TPP access
- ABAC consent: User whose attributes matched policy (even though not explicitly authorized by them)
- Semantically fits: "consent for this user, created by system evaluation"

**`consumerKey` (aud in JWT)**
- Standard use: OAuth consumer/application
- **Should NOT be overloaded** to mark ABAC - semantically wrong, could break consumer logic

**`apiStandard`**
- Meant for API specifications: "BERLIN_GROUP", "UK_OBWG", "OBP"
- **Should NOT be used** for creation method - wrong purpose

### Option A: Use `note` Field

```scala
note = "ABAC_GENERATED|policy=branch_teller_access|user_branch=branch_123|timestamp=1234567890"

// Query
consents.filter(_.note.contains("ABAC_GENERATED"))
```

**Pros**: 
- No schema change
- Works immediately
- Can include rich metadata

**Cons**: 
- String parsing needed
- Less structured than proper field
- Queries less efficient

### Option B: Add `source` Field (Recommended)

Add to `MappedConsent`:

```scala
object mSource extends MappedString(this, 50) {
  override def defaultValue = "CUSTOMER_GRANTED"
}

override def source: String = mSource.get

// Values: 
// - "CUSTOMER_GRANTED" (standard Open Banking)
// - "ABAC_GENERATED" (policy-based)
// - "SYSTEM_GENERATED" (admin/system)
```

**Pros**: 
- Clean, structured
- Easy to query: `By(MappedConsent.mSource, "ABAC_GENERATED")`
- Clear semantics
- Future-proof (other generation methods)

**Cons**: 
- Requires database migration
- Changes to consent schema

**Migration**:
```sql
ALTER TABLE mappedconsent ADD COLUMN msource VARCHAR(50) DEFAULT 'CUSTOMER_GRANTED';
CREATE INDEX idx_mappedconsent_source ON mappedconsent(msource);
```

## Implementation Ideas

### User Setup with Non-Personal Attributes

Users get ABAC attributes (non-personal):

```json
POST /users/USER_ID/entitlements
{ "role_name": "CanUseABAC", "bank_id": "" }

POST /users/USER_ID/non-personal-attributes
[
  { "name": "ABAC_role", "type": "STRING", "value": "teller" },
  { "name": "ABAC_branch", "type": "STRING", "value": "branch_123" },
  { "name": "ABAC_department", "type": "STRING", "value": "retail" },
  { "name": "ABAC_clearance_level", "type": "INTEGER", "value": "2" }
]
```

**Naming convention**: `ABAC_` prefix distinguishes control attributes from business attributes.

### Account Setup

```json
POST /banks/BANK_ID/accounts/ACCOUNT_ID/attributes
[
  { "name": "branch", "type": "STRING", "value": "branch_123" },
  { "name": "account_type", "type": "STRING", "value": "checking" },
  { "name": "vip_status", "type": "STRING", "value": "gold" },
  { "name": "customer_id", "type": "STRING", "value": "customer_456" }
]
```

### Access Flow Sketch

```scala
def hasAccountAccess(view, bankIdAccountId, user, callContext): Boolean = {
  
  // Standard checks first
  if (isPublicView(view)) return true
  if (hasAccountFirehoseAccess(view, user)) return true
  if (user.hasExplicitAccountAccess(view, bankIdAccountId, callContext)) return true
  
  // ABAC check
  if (hasEntitlement(user.userId, "CanUseABAC")) {
    
    // Check for existing ABAC consent with this account
    val existingConsent = getABACConsents(user.userId).find { c =>
      c.source == "ABAC_GENERATED" &&  // If we add source field
      c.views.exists(cv => cv.account_id == bankIdAccountId.accountId.value) &&
      c.validTo > now &&
      c.status == "ACCEPTED"
    }
    
    if (existingConsent.isDefined) return true  // Fast path: cached
    
    // No cached consent, evaluate ABAC policy
    val decision = evaluateABACPolicy(user, bankIdAccountId, view)
    
    if (decision.allowed) {
      // Find all accounts matching same policy pattern
      val matchingAccounts = findAccountsMatchingPolicy(user, decision)
      
      // Create consent with explicit account list
      createABACConsent(
        user = user,
        accounts = matchingAccounts,  // Explicit list in views
        viewId = decision.viewId,
        validTo = now + decision.durationMinutes.minutes,
        source = "ABAC_GENERATED",
        note = s"Policy: ${decision.policyName}, Reason: ${decision.reason}"
      )
      return true
    }
  }
  
  false
}
```

### Example Policies

**Branch Teller**:
```scala
if (user.ABAC_role == "teller" &&
    user.ABAC_branch == account.branch &&
    account.account_type in ["checking", "savings"] &&
    isBusinessHours) {
  grant(duration = 60.minutes, view = "teller")
}
```

**VIP Account Manager**:
```scala
if (user.ABAC_role == "vip_account_manager" &&
    account.vip_status in ["gold", "platinum"]) {
  grant(duration = 240.minutes, view = "owner")
}
```

**Customer Service Session**:
```scala
if (user.ABAC_role == "customer_service" &&
    user.ABAC_customer_session_active == account.customer_id &&
    sessionAge < 30.minutes) {
  grant(duration = 30.minutes, view = "customer_service")
}
```

**Branch Manager**:
```scala
if (user.ABAC_role == "branch_manager" &&
    user.ABAC_branch == account.branch) {
  grant(duration = 480.minutes, view = "owner")
}
```

**Compliance Officer**:
```scala
if (user.ABAC_role == "compliance_officer" &&
    user.ABAC_clearance_level >= 4) {
  grant(duration = 480.minutes, view = "auditor")
}
```

## Attribute Changes and Revocation

Hook into attribute deletion/update:

```scala
override def deleteUserAttribute(userId: String, attributeName: String): Box[Boolean] = {
  val result = super.deleteUserAttribute(userId, attributeName)
  
  if (attributeName.startsWith("ABAC_")) {
    // Revoke all ABAC-generated consents for this user
    getABACConsents(userId).foreach { consent =>
      consent.mStatus("REVOKED")
        .mNote(s"${consent.note}|AUTO_REVOKED: ${attributeName} removed at ${now}")
        .save()
    }
  }
  
  result
}
```

This ensures attribute changes take immediate effect (even if consent hasn't expired yet).

## Pattern-Based vs Explicit Account List

Two approaches for what goes in `consent.views`:

### Explicit List (Recommended)

Consent contains actual account IDs:
```scala
views = List(
  ConsentView("bank-123", "account-001", "teller"),
  ConsentView("bank-123", "account-002", "teller"),
  // ... 50 more accounts at branch_123
)
```

**Pros**:
- Fast lookup: is account in list?
- Works with existing consent logic
- Clear audit: see exactly which accounts

**Cons**:
- Large list if many accounts (50-100+)
- Must re-create if new accounts added

### Pattern-Based

Consent stores attribute pattern, not account IDs:
```scala
views = List(),  // Empty
abac_pattern = Map(
  "user_branch" -> "branch_123",
  "account_branch" -> "branch_123",
  "account_type" -> "checking,savings"
)
```

On each access, fetch account attributes and check against pattern.

**Pros**:
- Small consent record
- Automatically includes new accounts
- More flexible

**Cons**:
- Must fetch account attributes on each check
- Custom evaluation logic needed
- More complex

**Recommendation**: Start with explicit list. If >100 accounts per consent becomes common, consider pattern-based.

## Real-Time vs Cached Evaluation

**Pure ABAC (Real-time)**:
```
Request → Fetch user attributes → Fetch account attributes → 
Evaluate policy → Allow/Deny
```
- Always current
- Slower (fetch + evaluate each time)
- No consent records

**Consent-Cached ABAC**:
```
Request → Check consent exists → Found? Allow (fast)
                              → Not found? → Evaluate → Create consent → Allow
```
- Fast (list lookup)
- Short TTL (15-60 min) keeps it fresh
- Revoke on attribute change for immediate effect
- Full audit trail

**Hybrid** (could be interesting):
```
Request → Check consent → Valid? → Validate attributes still match → Allow/Re-evaluate
```
- Cache for performance
- Validate attributes on each use for freshness
- Best of both worlds but more complex

## Customer Service Workflow Idea

```scala
POST /customer-service/session/start
{
  "customer_number": "CUST-123",
  "reason": "Customer requesting balance info"
}

// Backend:
// 1. Verify user has ABAC_role=customer_service
// 2. Find customer's accounts
// 3. Create temporary user attribute: ABAC_customer_session_active=CUST-123
// 4. Create ABAC consent for customer's accounts (30 min)
// 5. Return session_id

Response:
{
  "session_id": "session-abc",
  "customer_id": "customer-456",
  "account_ids": ["acc-1", "acc-2"],
  "expires_at": "2024-01-15T10:30:00Z"
}

// User accesses accounts using normal endpoints (no special headers)
GET /banks/bank-123/accounts/acc-1/owner/account
// Works because ABAC consent exists

POST /customer-service/session/end
{
  "session_id": "session-abc"
}

// Backend:
// 1. Remove ABAC_customer_session_active attribute
// 2. Revoke ABAC consents for this session
```

Clean workflow, time-bound, full audit trail.

## Endpoints: Do We Need New Ones?

**No new account endpoints needed** - existing endpoints work transparently because ABAC integrates into `hasAccountAccess()`.

**But might want management endpoints**:

```scala
// List my active ABAC consents
GET /my/abac-consents
Response: List of consent IDs, accounts, expiry times

// Revoke ABAC consent (early)
DELETE /consents/{CONSENT_ID}
// Existing endpoint already works

// Customer service workflow helper
POST /customer-service/session/start
POST /customer-service/session/end

// Admin: view ABAC usage
GET /admin/abac-consents?user_id=X&date_from=Y
GET /admin/abac-policies  // List active policies
```

## Machine Learning Integration Ideas

Track ABAC consent usage and apply ML for anomaly detection:

### Normal Patterns
- Teller at branch_123 accesses 20-40 accounts/day, Mon-Fri 9am-5pm
- Customer service sessions average 3 accounts, duration 15 minutes
- Branch manager accesses 50-100 accounts/day during business hours

### Anomalies to Detect
- **Time anomaly**: Teller accessing accounts at 2am
- **Volume anomaly**: Teller accessing 200 accounts in one day
- **Scope anomaly**: Teller accessing accounts at different branch
- **Pattern anomaly**: Customer service session lasting 4 hours
- **Sequence anomaly**: Rapid access to VIP accounts by new user

### ML Approach
```
Features:
- Time of day
- Day of week
- Number of accounts accessed
- Duration of consent usage
- User role
- Account types accessed
- Deviation from user's normal pattern
- Deviation from role's normal pattern

Model: Isolation Forest or Autoencoder
Output: Anomaly score (0-1)
Action: 
  - Score > 0.8: Alert security, revoke consent
  - Score 0.5-0.8: Flag for review
  - Score < 0.5: Normal
```

Could even auto-revoke consents that trigger anomaly detection.

## Configuration Ideas

```properties
# Enable ABAC
enable_abac=true

# Auto-create consents when policy matches
abac.auto_consent_enabled=true

# Policy durations (minutes)
abac.teller_duration=60
abac.manager_duration=480
abac.customer_service_duration=30
abac.compliance_duration=480

# Business rules
abac.business_hours_start=9
abac.business_hours_end=17
abac.require_customer_present_for_cs=true

# Consent management
abac.cleanup_expired_enabled=true
abac.cleanup_interval_minutes=15
abac.max_accounts_per_consent=100
abac.revoke_on_attribute_change=true

# Security
abac.max_consents_per_user_per_day=50
abac.alert_on_excessive_consent_creation=true
abac.ml_anomaly_detection_enabled=false

# Audit
abac.log_all_evaluations=true
abac.log_denied_attempts=true
```

## Context Mutation Concerns: Should ABAC Auto-Generate Consents?

An important architectural question: **Should the ABAC system automatically generate consents during request processing, or should consent generation be explicit?**

### The Context Mutation Problem

**Proposed Flow:**
1. User calls endpoint with OAuth2/OIDC header + `CanUseAbac` role
2. During request processing, ABAC evaluates policies
3. If policy matches, system creates a consent
4. Consent gets attached to current call context
5. Request proceeds using the newly-created consent

**Why This Is Problematic:**

**1. Violates Principle of Least Surprise**
- Caller makes request with OAuth2 credentials
- Behind the scenes, system creates a persistent consent entity
- This implicit behavior makes debugging and understanding the system harder
- Side effects hidden from the caller violate transparency

**2. Semantic Confusion**
- **Consents** traditionally represent explicit user agreements ("I consent to share my data")
- **ABAC evaluations** are policy-based decisions ("Your attributes match policy criteria")
- Mixing these concepts muddies the semantic waters
- For compliance/regulatory purposes, this distinction matters

**3. Lifecycle Management Complexity**
- When should auto-generated consents expire?
- How do you clean them up?
- What happens if attributes change mid-request?
- Does the consent persist beyond the current call?
- Creates timing issues and potential race conditions

**4. Audit Trail Ambiguity**
- Was this consent user-initiated or system-generated?
- Who authorized it - the user or the policy engine?
- Compliance systems need clear distinctions

### Alternative Approaches

**Option 1: ABAC as Pure Policy Evaluation (Recommended)**

Keep ABAC as a **transparent evaluation layer** without side effects:

```scala
def checkAccess(user: User, resource: Resource, action: Action): Boolean = {
  val attributes = gatherAttributes(user, resource, action)
  val policies = findApplicablePolicies(resource, action)
  
  evaluatePolicies(policies, attributes) match {
    case PolicyResult.Allow(reason) =>
      auditLog.record(s"ABAC allowed: $reason")
      true
    case PolicyResult.Deny(reason) =>
      auditLog.record(s"ABAC denied: $reason")
      false
  }
}
```

The ABAC evaluation should be:
- **Fast** (in-memory policy evaluation)
- **Stateless** (no persistent side effects)
- **Auditable** (logged but not persisted as consent)
- **Transparent** (clear in logs, but invisible to caller)

**Option 2: ABAC Evaluation Result as Request Metadata**

Instead of creating a consent, attach the evaluation result to request context:

```scala
case class RequestContext(
  oauth2Token: Token,
  userRoles: Set[Role],
  abacEvaluation: Option[AbacEvaluationResult] = None
)

case class AbacEvaluationResult(
  decision: Decision,
  matchedPolicies: List[Policy],
  evaluatedAttributes: Map[String, String],
  evaluatedAt: DateTime,
  validUntil: DateTime
)
```

This allows:
- Caching evaluation results within request scope
- Passing results to downstream services
- Audit logging without persistence
- No database writes on every request

**Option 3: Explicit Two-Step Flow**

If you must use consents, make it **explicit**:

```
# Step 1: Acquire ABAC Consent (explicit call)
POST /consents/abac
Authorization: Bearer {oauth2_token}
{
  "resource_type": "account",
  "resource_id": "123",
  "action": "view_balance"
}

Response: 
{
  "consent_id": "abac-consent-xyz",
  "valid_until": "2024-01-15T10:30:00Z",
  "granted_accounts": ["123", "456", "789"]
}

# Step 2: Use the Consent (separate call)
GET /accounts/123/balance
Authorization: Bearer {oauth2_token}
X-ABAC-Consent: abac-consent-xyz
```

**Option 4: Consent as Cache (Current Document Approach)**

The approach described in this document treats consents as a **cache** for ABAC decisions:

- First request: No consent exists → Evaluate ABAC → Create consent → Use it
- Subsequent requests: Consent exists → Skip evaluation → Use cached decision
- Consent expires: Back to evaluation on next request

This is a middle ground but still has mutation concerns:
- ✅ Reuses existing infrastructure
- ✅ Provides caching benefits
- ✅ Creates audit trail
- ⚠️ Still creates side effects on first request
- ⚠️ Requires cleanup/garbage collection
- ⚠️ Database writes on policy evaluation

### When Context Enrichment Is Acceptable

Some forms of context modification are fine:
- **Adding computed attributes** (in-memory, non-persistent)
- **Attaching evaluation results** for downstream use within request
- **Caching policy decisions** within request scope
- **Adding trace/correlation IDs** for debugging

But creating **persistent entities** (like database records) crosses from enrichment to mutation with side effects.

### Recommendation

For production ABAC implementation:

1. **Evaluation Phase**: ABAC evaluates policies (fast, stateless)
2. **Authorization Phase**: Result determines allow/deny
3. **Audit Phase**: Log decision with context
4. **No Consent Generation**: Unless explicitly requested

If caching is needed:
- Use in-memory cache (Redis, Memcached)
- Cache evaluation results, not consents
- Clear cache on attribute changes
- TTL matches policy freshness requirements

If consents are truly needed:
- Make acquisition explicit (separate endpoint)
- Document the semantic difference from user consents
- Implement clear lifecycle management
- Provide revocation endpoints

### Summary: Implicit vs Explicit

| Aspect | Implicit (Auto-Generate) | Explicit (Separate Call) |
|--------|-------------------------|--------------------------|
| Caller experience | Simple, transparent | Two-step, more complex |
| Debugging | Harder, hidden behavior | Easier, clear flow |
| Performance | Better (caching built-in) | Requires separate cache |
| Side effects | Yes (database writes) | Only when requested |
| Semantic clarity | Confused | Clear |
| Audit trail | Ambiguous source | Clear initiator |
| **Recommendation** | ❌ Avoid | ✅ Prefer if using consents |

## Challenges and Open Questions

1. **Schema change**: Add `source` field or use `note`?
   - `source` field cleaner but requires migration
   - `note` field works now but less structured

2. **Large account lists**: What if teller has access to 500 accounts?
   - One consent with 500 views?
   - Multiple consents (e.g., 100 accounts each)?
   - Pattern-based consent?

3. **Consent lifetime**: Balance between performance and freshness
   - 15 min: more real-time, less caching benefit
   - 30 min: balanced
   - 60 min: more caching, less fresh

4. **Policy storage**: Hard-coded in Scala or database?
   - Hard-coded: simpler, requires deployment to change
   - Database: flexible, could have UI, more complex

5. **View selection**: Does policy specify which view to grant?
   - Yes: policy says "grant teller view" (more controlled)
   - No: use requested view (more flexible)

6. **Consent in request header**: 
   - Standard consent: TPP includes Consent-JWT in header
   - ABAC consent: No header needed, just CanUseABAC entitlement
   - This works because consent is cached authorization, not passed token

7. **Attribute sync**: What if account attributes change after consent created?
   - Wait for consent expiry (simpler)
   - Re-validate on each use (more accurate, more complex)
   - Depends on attribute change frequency

8. **Multi-bank**: Can CanUseABAC work across banks?
   - Bank-specific: CanUseABAC at bank-123
   - Global: CanUseABAC at any bank
   - Mix: Some users global, some bank-specific

## Related: Existing OBP Attributes

OBP already has attributes on:
- **Users**: UserAttribute (personal and non-personal)
- **Accounts**: AccountAttribute
- **Transactions**: TransactionAttribute
- **Products**: ProductAttribute
- **Customers**: CustomerAttribute

ABAC can leverage all of these. Example:

```scala
// Transaction-level ABAC
if (user.ABAC_role == "fraud_investigator" &&
    transaction.amount > 10000 &&
    transaction.TransactionAttribute("suspicious") == "true") {
  grant(duration = 120.minutes, view = "auditor")
}
```

The attribute infrastructure is already there - ABAC is about using it for access decisions.

## Summary of Approach

1. User has `CanUseABAC` entitlement
2. User has non-personal ABAC attributes (`ABAC_role`, `ABAC_branch`, etc.)
3. Accounts have attributes (`branch`, `account_type`, etc.)
4. When user requests account access:
   - Check for valid ABAC consent first (fast)
   - If not found, evaluate ABAC policy
   - If policy matches, create consent with explicit account list
   - Consent valid 15-60 minutes
5. Consent contains `source = "ABAC_GENERATED"` (if we add field) or marker in `note`
6. When user's ABAC attributes change, revoke their ABAC consents
7. Full audit trail via consent records
8. Optional: ML anomaly detection on usage patterns

This reuses consent infrastructure while providing dynamic, attribute-based access control suitable for bank staff workflows.