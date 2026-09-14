# API Product Subscription — making API Products enforceable

Written 2026-09-02, revised 2026-09-02 after checking every code reference against the repo.
Track progress here by marking items done in place. **Status: Phases 0 to 3 coded and green on 2026-09-02 (`ApiProductSubscriptionTest` 9 scenarios, `RateLimitsTest` 12, `CacheKeyFormatTest` 5). Not yet committed. Phase 4 is other repos.**
Background: `../../commands/NMB_legal_2026/FROM_CLAUDE_plans_evolution_OBP_Stripe_OBP_Billing.md`
(relative to this repo; the `commands` directory sits next to `OBP-API-Simon` in `workspace_2024`).

Working rules: the user commits, the assistant never does. Mirror the API Product implementation
file for file (`code/apiproduct`, `code/apiproductattribute`). **New endpoints go in v7.0.0**
(`code/api/v7_0_0/Http4s700.scala`, JSON case classes nested in `JSONFactory700`, tests in
`obp-api/src/test/scala/code/api/v7_0_0/`), even though the API Product endpoints they extend are
v6.0.0; existing v6.0.0 docs may be edited. No Lift endpoints. No new props unless listed here.

## Why

An API Product (v6.0.0) already describes a plan: `collection_id` (which endpoints), six call
limits (how much), `monthly_subscription_amount` (price), `parent_api_product_code` (tiers),
attributes (anything else). Nothing records which consumer holds which product, and nothing
enforces the product's limits or collection. Rate limits and scopes are still set per consumer by
hand, and OBP-Stripe hard-codes its own provisioning (one entitlement, `CanCreateBank`).

The subscription is the missing record. Its **status** is the hook: when it becomes `active`
OBP-API applies the product's rate limits and scopes to the consumer; when it leaves `active`
OBP-API blocks or removes them. Billing systems (OBP-Stripe, OBP-Billing, a bank's own ERP)
only ever change the status. OBP-API core carries no billing vocabulary.

## Vocabulary (use these words and no others)

| term | code | meaning |
|---|---|---|
| **product** | `ApiProduct`, `api_product_code` | the plan. Bank-scoped. Already exists. |
| **subscription** | `ApiProductSubscription`, `api_product_subscription_id` | one consumer holding one product for a period, with a status. |
| **subscriber** | `consumer_id` | the consumer. Rate limits and scopes apply to it. Never a user. |
| **created by** | `created_by_user_id` | audit only, same as `Consumer.createdByUserId`. |
| **billing system** | product attribute `BILLING_SYSTEM` = `none` / `manual` / `stripe` / `invoice_ninja` | which external system collects money for this product. Read by adapters and the portal; OBP-API core reads it only to decide auto-activation. |
| **self-subscribe** | product attribute `SELF_SUBSCRIBE` = `true` / `false` | whether developers may subscribe their own consumers, or only the bank may enrol them. |
| **status** | `status` | `requested`, `active`, `past_due`, `suspended`, `cancelled`. |

Words to avoid: *provider* (means authentication provider in OBP), *plan* in code (it is a
product), *user subscription*.

## Status machine

```
requested --(admin or adapter PUT, or auto if BILLING_SYSTEM is none or absent)--> active
requested --(developer withdraws, admin or adapter)-----------------> cancelled
active    --(adapter: payment failed / invoice overdue)-------------> past_due
active    --(admin: abuse, breach of terms)-------------------------> suspended
past_due  --(adapter: paid)-----------------------------------------> active
past_due  --(admin or adapter)--------------------------------------> suspended
suspended --(admin or adapter: reinstate)---------------------------> active
active | past_due | suspended --(developer, admin or adapter)-------> cancelled
cancelled is terminal. A new subscription is a new record.
```

The table lives in code in `ApiProductSubscriptionStatus.canTransition`; the docs of the status endpoint list it.

Enforcement on each transition (Phase 3). This table assumes the rate-limit semantics of Phase 1
(`0` blocks, `-1` is unlimited); without Phase 1 the `suspended` row would *lift* all limits.

| to | rate limits | scopes |
|---|---|---|
| `active` | create one `RateLimiting` row for the consumer, `bankId = product.bankId`, `fromDate = start_date`, `toDate = end_date`, six limits copied from the product (`-1` passed through) | add a `Scope(bankId, consumerId, roleName)` for each role required by each endpoint in the product's collection |
| `past_due` | **none**. The row keeps the product's limits. `past_due` is a grace period (Stripe's own meaning while it retries a card): the portal shows "payment overdue", the adapter or an admin later moves to `suspended`. Decided 2026-09-02; slowing consumers during grace can come later as a product attribute if a bank asks. | unchanged |
| `suspended` | replace with a row of six `0`s. Rows are summed, so this grants nothing: a consumer whose access came only from this subscription is blocked (sum 0, 429), while calls granted by its other active subscriptions continue. | unchanged, so reinstatement is a limits-only change |
| `cancelled` | delete the row | delete the derived scopes |

So only three transitions write to the row: into `active`, into `suspended`, into `cancelled`.
`requested` and `past_due` are bookkeeping.

**Which row.** One subscription, one row. `createConsumerCallLimits` returns the new `RateLimiting`
with its `rateLimitingId`; the enforcer stores it in `ApiProductSubscription.RateLimitingId`. Every
later change addresses that id (`updateConsumerCallLimits(rateLimitingId, …)`,
`deleteByRateLimitingId(rateLimitingId)`), never consumer-and-date, so a consumer's other rows in
the same period (manual ones, or another subscription's) are never touched and keep summing.
Likewise scopes: each `Scope` the enforcer adds is recorded in `ApiProductSubscriptionScope`, and
only those are deleted. Manual limits and manually granted scopes are never removed.

An admin can still edit or delete a subscription-owned row through the existing
`/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID` endpoints. Two cheap
defences, no coupling from rate limits back to subscriptions: the enforcer treats "row not found"
as *create a fresh row* on `active` / `suspended` (and stores the new id) and as a no-op on
`cancelled`; and the rate-limit resource docs say rows created by a subscription are managed by it
and rewritten on its next status change.

Deriving the scopes: `ApiCollectionEndpoint` rows for `product.collectionId` give operation ids;
`ResourceDocRegistry.getResourceDocs(operationIds)` (`APIUtil.scala` ~line 1562) gives each
`ResourceDoc.roles: Option[List[ApiRole]]`. Roles with `requiresBankId = true` become scopes at
`product.bankId`; the rest become scopes at `""`. Endpoints with no roles need no scope. Views
are out of scope for this plan: a product cannot grant account access.

## How rate limits resolve today (checked 2026-09-02)

All of this is in `code/api/util/RateLimitingUtil.scala`; the plan depends on it, so it is
recorded here rather than assumed.

- `getActiveRateLimitsWithIds(consumerId, date)` (~line 102) is the single source of truth for
  both enforcement and the `active-rate-limits` endpoints. It loads **every** `RateLimiting`
  row for the consumer whose `[fromDate, toDate]` overlaps the current hour
  (`MappedRateLimiting.getActiveCallLimitsByConsumerIdAtDate`, cached per hour).
- `aggregateRateLimits` then, per period, keeps the values `> 0` and **sums** them; if none is
  positive the period becomes `-1`. So `0` and `-1` are both ignored, and rows add up rather than
  the most specific winning. With **no** rows at all, the six `rate_limiting_per_*` props apply.
- `underConsumerLimits` (~line 197) treats any limit `<= 0` as "pass". So today a stored `0`
  means *unlimited*, at both layers.
- `bankId`, `apiName` and `apiVersion` on a row are stored and reported (first defined value)
  but never used for matching. Enforcement is per consumer across all banks and all endpoints.
- `CallLimitPostJsonV600` requires all six `per_*_call_limit` strings, so every row created via
  the API states all six periods explicitly; there is no "unset" period on a row.
- Glossary "Rate Limiting" (`Glossary.scala` ~lines 226, 286, 323) documents "sum positive
  values, `-1` if none" and "`-1` means unlimited". Nothing documents `0`.
- No test asserts the `0` behaviour (`RateLimitsTest.scala:183` looks like one but is a bare
  boolean expression, not an assertion).

Consequences for this plan, before Phase 1: a `suspended` row of zeros would remove all limits;
a product copied to a row sums with any manual row instead of replacing it; a product at bank A
changes the consumer's limits at bank B; and a row with `-1` in a period makes that period
unlimited even when a props default exists.

## Phase 0 — Conventions (no code)

- [x] Document in the API Product resource-doc description that `BILLING_SYSTEM`, `SELF_SUBSCRIBE`,
  `INCLUDED_CALLS_PER_MONTH`, `OVERAGE_PRICE_PER_CALL`, `TRIAL_DAYS` are the recognised attribute
  names. The live resource doc is in `Http4s600.scala` (`createApiProduct`, ~line 14599); the one in
  `APIMethods600.scala` is commented out.
- [x] Same description: state that a product's `-1` limit means *unlimited for that period once
  subscribed* (it is copied literally to the consumer's row, Phase 3), not "inherit the default".
- [x] Glossary item "API Product Subscription" in `code/api/util/Glossary.scala`, next to "API Collection" (~line 3072).

## Phase 1 — Rate-limit semantics: `0` blocks, `-1` is unlimited, no row means default

Prerequisite for Phase 3 only (a `suspended` zero row must block, and under the old rule it lifted all
limits). Phase 2 does not depend on it. Done first because it is small, self-contained and useful
on its own: a bank can set a zero row today and it silently does nothing.

Target semantics, per period, over the consumer's active rows:

| active rows for the period | result | enforcement |
|---|---|---|
| none (no row at all for the consumer) | the `rate_limiting_per_*` prop for that period (`-1` if unset) | as today |
| any row has `0` | `0` | **blocked**: every call gets 429 |
| otherwise, one or more rows `> 0` | their **sum** (unchanged from today) | counted against Redis as today |
| otherwise (all rows `-1`) | `-1` | unlimited; props default does **not** apply, because a row exists and states `-1` |

`0` is absolute: it wins over any positive row, including a manual one. That is what makes
`suspended` work and is also the right meaning for an admin who types `0`.

**Design decision (Simon, 2026-09-02): overlapping rows sum.** Two products held at once (a free
data product and a paid payments product) add up, and a manual row adds to a product row. There is
no partial throttling anywhere in this plan: a subscription's row is either the product's limits
(`active`, `past_due`) or six zeros (`suspended`), and `0` wins over every other row.

- [x] `RateLimitingUtil.aggregateRateLimits` / `sumLimits` (~line 109): implement the table above.
  Keep the "no rows → props defaults" branch exactly as is.
- [x] `RateLimitingUtil.underConsumerLimits` (~line 197): `case 0 => false`; `case l if l < 0 => true`.
  Keep the Redis fail-open behaviour for positive limits.
- [x] `underCallLimits`: when the limit is `0`, the 429 text should say the consumer is blocked
  for that period, not "We only allow 0 requests". Add a `composeMsgBlocked` next to
  `composeMsgAuthorizedAccess`. Headers `X-Rate-Limit-Limit: 0`, `X-Rate-Limit-Remaining: 0`.
  The counter must not be incremented for a blocked call (already true: `incrementConsumerCounters`
  only runs on the pass path).
- [x] **Bug found and fixed on the way**: `Caching.invalidateRateLimitCache` deleted Redis keys matching
  `<prefix><consumerId>_*`, but scalacache stores the hour cache under
  `<namespace>:code.api.cache.Redis.memoizeSyncWithRedis(Some(<prefix><consumerId>_<hour>))()()`, so the
  glob never matched and every create / update / delete of a rate limit logged "Deleted 0 Redis keys".
  A new or changed limit (and a subscription's suspension) only took effect when the hour cache
  expired. Fixed with a leading `*`, as the method-routing invalidation already does; pinned in
  `CacheKeyFormatTest`.
- [x] Anonymous access uses the same `underConsumerLimits` with `user_consumer_limit_anonymous_access`
  (default 1000). After this change a value of `0` blocks all anonymous calls. Intended; add one
  line to the prop's comment in `sample.props.template`.
- [x] `Glossary.scala`: rewrite the "Logic" box (~line 226) and the two "`-1` means unlimited"
  lines (~286, ~323) to state all three values: `0` blocked, `-1` unlimited, no row → default.
- [x] Resource-doc descriptions of the create/update rate-limit endpoints in `Http4s600.scala`
  (~lines 5884–5930) and the `active-rate-limits` endpoints (~5940): same three-value sentence.
- [ ] **Release note and data check** (to do at release time, not in code). Any existing row with `0` in a period starts blocking that
  consumer after deploy. Before release run
  `select rate_limiting_id, consumer_id from ratelimiting where persecondcalllimit = 0 or perminutecalllimit = 0 or perhourcalllimit = 0 or perdaycalllimit = 0 or perweekcalllimit = 0 or permonthcalllimit = 0`
  (column names per the Mapper defaults; check the actual schema) and fix or announce.
- [x] Tests in `obp-api/src/test/scala/code/api/v6_0_0/RateLimitsTest.scala`: a `0` row makes a
  call return 429 and `active-rate-limits` report `0`; a `0` row plus a positive row still reports
  `0`; two positive rows report the sum; an all-`-1` row reports `-1` even when a
  `rate_limiting_per_*` prop is set; no row reports the prop. Also turn line 183 into a real assertion.
- [x] Later, not this plan (noted in `TODO.md`): limits keyed by `operationId` (not URL) so a product's collection can be
  enforced per endpoint, and per-bank matching using the row's `bankId`. Note both in `TODO.md`.

## Phase 2 — The resource

Files, mirroring `code/apiproduct`:

- [x] `code/apiproductsubscription/ApiProductSubscription.scala`: Mapper + trait.
  Columns: `ApiProductSubscriptionId` (MappedUUID), `BankId` (UUIDString), `ApiProductCode`
  (MappedString 50), `ConsumerId` (UUIDString), `Status` (MappedString 20), `StartDate`,
  `EndDate` (MappedDateTime, nullable end = open-ended), `CreatedByUserId` (UUIDString),
  `RateLimitingId` (MappedString 50, the row Phase 3 creates; empty when none),
  `CreatedUpdated`. Indexes: `(ConsumerId)`, `(BankId, ApiProductCode)`. No unique constraint
  (cancelled rows are history); the provider enforces at most one non-cancelled subscription per
  `(consumerId, bankId, apiProductCode)`.
- [x] `code/apiproductsubscription/ApiProductSubscriptionsProvider.scala`: `create`,
  `getById`, `getByConsumerId`, `getByBankIdAndProductCode`, `getByCreatedByUserId`,
  `updateStatus(id, newStatus, endDate: Option[Date])`, `setRateLimitingId(id, rateLimitingId)`.
  `updateStatus` validates the transition table above and returns `InvalidApiProductSubscriptionStatusTransition`.
- [x] `code/apiproductsubscription/ApiProductSubscriptionScope.scala`: join table
  `(ApiProductSubscriptionId, ScopeId)` so Phase 3 can remove exactly what it added.
- [x] `code/apiproductsubscriptionattribute/…`: copy `ApiProductAttribute` and its provider with
  `ApiProductSubscriptionId` in place of `(BankId, ApiProductCode)`. Adapters store
  `STRIPE_SUBSCRIPTION_ID` etc. here.
- [x] `bootstrap/liftweb/Boot.scala`: add the three mappers to the schemify list next to `ApiProduct` (line 997).
- [x] `code/api/util/ApiRole.scala`: the roles in the access model below, all
  `…AtOneBank(requiresBankId = true)`, like `CanCreateApiProduct`. There are deliberately no
  `…AtAnyBank` variants: OBP is moving away from any-bank roles. A billing adapter that serves
  several banks is granted the role at each of them.
- [x] `code/api/util/ErrorMessages.scala`: `ApiProductSubscriptionNotFound`,
  `ApiProductSubscriptionAlreadyExists`, `InvalidApiProductSubscriptionStatus`,
  `InvalidApiProductSubscriptionStatusTransition`, `ConsumerNotOwnedByUser`.
- [x] `code/api/util/ApiTag.scala`: `apiTagApiProductSubscription`.
- [x] `code/api/v7_0_0/JSONFactory7.0.0.scala` (nested in the `JSONFactory700` object, as v7 does):
  `PostApiProductSubscriptionJsonV700(consumer_id, start_date: Option, end_date: Option)`,
  `PutApiProductSubscriptionStatusJsonV700(status, end_date: Option)`,
  `ApiProductSubscriptionJsonV700(… , attributes: Option[List[…]])`, `ApiProductSubscriptionsJsonV700`,
  attribute request/response classes, factory methods and `…Example` values for the docs
  (v7 docs inline examples; `SwaggerDefinitionsJSON` is not used).
- [x] `code/api/util/NewStyle.scala`: wrappers as for API Product. Role checks use
  `handleEntitlementsAndScopes` (the non-deprecated helper; it also honours consumer Scopes, so a
  billing adapter may be given Scopes on its consumer instead of a user entitlement).
- [x] `code/api/v7_0_0/Http4s700.scala`: each route is a `val` immediately followed by its
  `resourceDocs += ResourceDoc(...)` (v7 sorts routes by URL specificity from the docs; there is no
  `.orElse` chain and no separate APIMethods file). Management docs whose bank is the subscription's
  bank, not a `BANK_ID` in the path, declare their roles and call `.disableAutoValidateRoles()`; the
  handler checks the roles itself.

  See "Access model" below for the routes.

## Access model

**Rule zero: a fintech never needs a role.** Subscribing, requesting, listing and cancelling their
own subscriptions are gated only by consumer ownership, which OBP already knows. The bank must
never have to grant a fintech an entitlement to use API Products. Roles exist only for bank
staff and the billing adapter. Do not reintroduce a fintech-side role.

Four actors. Ownership means `Consumer.createdByUserId == caller.userId`, exactly as
`getConsumersForCurrentUser` (v3.1.0) decides it today. Role checks are made at the **product's** `bank_id`.

| actor | how identified | may |
|---|---|---|
| **developer** | owns the consumer; no role | subscribe own consumer; read own subscriptions and their attributes; set status `cancelled` on own subscription. Nothing else. |
| **bank admin** | `…AtOneBank` roles at the product's bank | everything at that bank: subscribe any consumer, read all, any status transition, delete, write attributes. Typical use: approve `requested` → `active`, suspend, reinstate. |
| **billing adapter** | service account with `…AtOneBank` roles at every bank it serves (one Stripe or Invoice Ninja instance may serve several) | status transitions and attribute writes at those banks. In practice only `active`, `past_due`, `cancelled`. |
| **support / auditor** | `CanGetApiProductSubscriptionAtOneBank` | read only. |

Roles:

| role | guards |
|---|---|
| `CanCreateApiProductSubscriptionAtOneBank` | POST, unless the product has `SELF_SUBSCRIBE` true (the default) **and** the caller owns the consumer. Two jobs: the bank enrols a partner's consumer itself (NMB's model), and the bank closes a product to self-service. |
| `CanGetApiProductSubscriptionAtOneBank` | reading subscriptions the caller does not own, incl. by product and by consumer |
| `CanUpdateApiProductSubscriptionStatusAtOneBank` | PUT status, any valid transition. **The sensitive role.** Owners bypass it only for `cancelled`. |
| `CanDeleteApiProductSubscriptionAtOneBank` | DELETE |
| `CanCreateApiProductSubscriptionAttributeAtOneBank`, `CanUpdate…`, `CanDelete…` | attribute writes. Reads follow the subscription read rule. |

Developer endpoints (no role; ownership enforced):

| verb | path | behaviour |
|---|---|---|
| POST | `/banks/BANK_ID/api-products/API_PRODUCT_CODE/subscriptions` | body `{consumer_id, start_date?, end_date?}`. Allowed without a role only when the product's `SELF_SUBSCRIBE` attribute is true or absent **and** the caller owns the consumer; otherwise `CanCreateApiProductSubscription…` is required. Created as `requested`; if the product's `BILLING_SYSTEM` attribute is `none` or absent it becomes `active` at once and Phase 3 fires. Refused if a non-cancelled subscription for the same consumer and product exists. |
| GET | `/my/api-product-subscriptions` | subscriptions of consumers the caller owns, with attributes. |
| GET | `/my/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID` | one of the above, else 404 (not 403, do not leak existence). |
| PUT | `/my/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID/status` | body `{status: "cancelled"}` only; any other value → `InvalidApiProductSubscriptionStatusTransition`. |

Management endpoints (roles):

| verb | path | role |
|---|---|---|
| GET | `/banks/BANK_ID/api-products/API_PRODUCT_CODE/subscriptions` | `CanGetApiProductSubscription…` — subscribers of a product |
| GET | `/management/consumers/CONSUMER_ID/api-product-subscriptions` | `CanGetApiProductSubscription…`. A consumer is not bank-scoped, so the consumer's creator sees everything and anyone else sees only the subscriptions at banks where they hold the role (403 if they hold it nowhere). |
| GET | `/management/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID` | `CanGetApiProductSubscription…` |
| PUT | `/management/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID/status` | `CanUpdateApiProductSubscriptionStatus…`. Body `{status, end_date?}`. The one write adapters make. |
| DELETE | `/management/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID` | `CanDeleteApiProductSubscription…`. Runs the `cancelled` enforcement first, then hard deletes. |
| POST | `/management/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID/attribute` | `CanCreateApiProductSubscriptionAttribute…` |
| PUT / DELETE | `/management/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID/attributes/ATTRIBUTE_ID` | `CanUpdate…` / `CanDelete…ApiProductSubscriptionAttribute…` |
| GET | `/management/api-product-subscriptions/API_PRODUCT_SUBSCRIPTION_ID/attributes` | `CanGetApiProductSubscription…` |

Why not one PUT for everything: the only mutable thing on a subscription after creation is its
status (and `end_date` alongside a status change). A dedicated `/status` route keeps the
sensitive role on one path and makes the adapters' contract a single call.

Which consumer is subscribed: always the `consumer_id` in the POST body, **never** the consumer
making the call. The fintech subscribes from the Portal (or OBP-Stripe), and the calling consumer
is the Portal's own, not the fintech app's. The Portal fills a drop-down from the existing
`GET /obp/v3.1.0/management/users/current/consumers` (`getConsumersForCurrentUser`,
`Http4s310.scala` ~line 584; the fintech's own apps, i.e. `Consumer.createdByUserId == caller`).
There is no `/my/consumers` in v6.0.0; adding one is optional and not part of this plan. The
fintech picks one, and OBP-API re-checks that ownership on POST. A missing `consumer_id` is a 400,
not a default. A fintech with no consumer yet is sent to the existing "register your app" flow
first. The same holds when the bank enrols a partner: the admin names the partner's `consumer_id`
(from `GET /management/consumers`) and holds `CanCreateApiProductSubscription…`.

Two independent gates on POST, both product attributes so a bank can mix open and closed
products with no props:

| attribute | question | default |
|---|---|---|
| `SELF_SUBSCRIBE` | may a developer create a subscription for their own consumer at all? `false` = named partners only, the bank enrols them with `CanCreateApiProductSubscription…`. | `true` |
| `BILLING_SYSTEM` | once created, who moves it from `requested` to `active`? `none`/absent = immediately; `manual` = a bank admin; `stripe` / `invoice_ninja` = the adapter on payment. | absent |

Why owners are not role-guarded by default: a request commits the bank to nothing until
someone with `CanUpdateApiProductSubscriptionStatus…` or an adapter activates it.

Why create is not folded into the status role: anyone who can approve would then also be able to
enrol arbitrary consumers, and a partner-manager role that enrols but cannot activate would be
impossible. One role per verb is also the OBP convention.

- [x] `obp-api/src/test/scala/code/api/sweep/EndpointCatalog.scala`: nothing to register; the catalog is derived from `Http4s700.allResourceDocs`, so the new docs are swept automatically (management docs with `.disableAutoValidateRoles()` fall in the `AutoValidateRolesOff` bucket for the role sweep).
- [x] Tests: `obp-api/src/test/scala/code/api/v7_0_0/ApiProductSubscriptionTest.scala` (extends
  `ServerSetupWithTestData`; products are created through v6.0.0, subscriptions through v7.0.0). Create product, create consumer, subscribe, list mine, list by product, status
  transitions incl. the invalid ones, owner may cancel but not activate, owner may cancel a
  `requested` one, `SELF_SUBSCRIBE=false` refuses an owner's POST without the role and accepts it
  with, a user without the role cannot read or change another developer's subscription (404 on
  read, 403 on status), `AtOneBank` role at the wrong bank is refused, by-consumer is filtered to
  the banks where the role is held, a second non-cancelled subscription for the same product is
  refused.

## Phase 3 — Enforcement

Requires Phase 1.

- [x] `code/apiproductsubscription/ApiProductSubscriptionEnforcer.scala` (single object, called from the
  provider's `updateStatus` and from the auto-activate path in POST):
  - `applyActive(subscription, product)`: `RateLimitingDI.rateLimiting.vend.createConsumerCallLimits(consumerId, from, to, None, None, Some(bankId), Some(perSecond.toString), …)`
    (the six limits are `Option[String]`), store the returned `rateLimitingId` on the subscription;
    derive scopes as described above, `MappedScopesProvider.addScope(bankId, consumerId, roleName)`
    for each, record in `ApiProductSubscriptionScope`.
  - `past_due`: no enforcer call.
  - `applySuspended`: `updateConsumerCallLimits(rateLimitingId, …six "0")`; if the row is gone,
    `createConsumerCallLimits` with six `"0"` and store the new id.
  - `applyReinstate` (`suspended` → `active`): `updateConsumerCallLimits` back to the product's limits;
    if the row is gone, create it as in `applyActive` and store the new id.
  - `applyCancelled`: `deleteByRateLimitingId` (no-op if already gone), `deleteScope` for each
    recorded scope, clear the join rows.
  - Product limits of `-1` are copied as `-1` and mean **unlimited for that period** for this
    consumer (Phase 1 semantics), even where a `rate_limiting_per_*` prop is set. A product that
    sets only a monthly cap therefore lifts the per-second and per-minute defaults for its
    subscribers; a bank that wants those kept must put them on the product. Say so in the product
    resource doc (Phase 0).
- [x] Guard: if `product.collectionId` is empty, no scopes are derived (limits only). If the
  product has no limits set (all six `-1`), no rate-limit row is created on `active`, but
  `suspended` still creates the all-zero row (blocking needs a row) and `cancelled` deletes it.
- [x] Rows created here get `apiVersion = None, apiName = None, bankId = Some(product.bankId)`.
  `bankId` is informational today: the consumer's limit changes at every bank. Rows **sum** with
  any manual rows (see "How rate limits resolve today"); they do not replace them.
- [x] Resource docs of the create/update/delete rate-limit endpoints in `Http4s600.scala`: one sentence
  that rows created by an API Product Subscription are managed by it and rewritten on its next status
  change.
- [ ] Expiry (deferred, in `TODO.md`): for **limits**, a subscription whose `end_date` has passed stops counting for free
  (the row has the same `toDate`). For **scopes** it does not: derived scopes stay until something
  flips the status to `cancelled`. Known gap; a scheduled job to do that is deferred to `TODO.md`.
- [x] Tests extend `ApiProductSubscriptionTest`: after activation `GET /management/consumers/CONSUMER_ID/active-rate-limits`
  shows the product limits and `GET /consumers/CONSUMER_ID/scopes` shows the derived roles; after
  `past_due` nothing has changed; after `suspended` a call with that consumer returns 429 and
  `active-rate-limits` shows `0`; after reinstatement the product limits are back; after cancel both
  limits and scopes are gone; a manual scope added before activation survives cancel; a manual
  rate-limit row added before activation survives cancel and its values are summed with the
  product's while active; deleting the subscription's row by hand and then suspending creates a new
  zero row and updates the stored id.

## Phase 4 — Adapters (other repos, listed for completeness)

- [ ] OBP-Stripe: read products with `BILLING_SYSTEM=stripe` from `GET /obp/v6.0.0/api-products`,
  sync to Stripe Products/Prices (lookup_key = `bank_id:api_product_code`), create the OBP
  subscription before checkout, put `api_product_subscription_id` in checkout and subscription
  metadata, store `STRIPE_SUBSCRIPTION_ID` as a subscription attribute, and on webhooks PUT status
  only. Delete the `CanCreateBank` and user-attribute provisioning code.
- [ ] OBP-Billing: invoice per active subscription with `BILLING_SYSTEM=invoice_ninja`
  (monthly amount + overage from aggregate metrics); add the overdue loop that PUTs `past_due` / `active`.
- [ ] Portal: catalogue from `GET /api-products`, product detail page, usage panel from
  `call-counters` and `active-rate-limits`. The fintech never needs a role; the product's
  attributes pick the button:

  | `SELF_SUBSCRIBE` | `BILLING_SYSTEM` | button | result |
  |---|---|---|---|
  | true | none / absent | **Subscribe** | `active` at once |
  | true | stripe | **Subscribe** | redirect to OBP-Stripe checkout, `active` when paid |
  | true | manual / invoice_ninja | **Request subscription** | `requested`; a bank admin approves, or the invoice adapter activates |
  | false | any | none; "contact the bank" text | the bank enrols the consumer with `CanCreateApiProductSubscription…` |

  The same page shows the consumer's current subscription and status, and a Cancel button
  (`PUT /my/api-product-subscriptions/ID/status`).

## Out of scope

Payments, invoices, tax, refunds (stay in the billing system). Views (a product never grants
account access). Per-user subscriptions. Renaming API Product. Per-endpoint (operationId) and
per-bank rate-limit matching (noted in Phase 1 for later). Throttling during `past_due` (a possible
future product attribute; no prop).

## Open questions

1. Should `POST …/subscriptions` by a developer be allowed for a product whose `BILLING_SYSTEM`
   is `stripe` when no adapter is running? Proposed: yes, it stays `requested`; the portal decides
   what to show.
2. One non-cancelled subscription per consumer per product, or one per consumer per *bank*?
   Proposed: per product, so a consumer can hold a free data product and a paid payments product at once.
   **Resolved**: overlapping rows *sum*, by design (see Phase 1); Phase 3 tests assert it.
