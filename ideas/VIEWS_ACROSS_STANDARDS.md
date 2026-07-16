# Views Across API Standards (OBP native, Berlin Group, UK Open Banking)

Status: idea / analysis (2026-07-16). Question under discussion: **is it good to have views
explicitly and exactly named after UK and BG consents?**

Short answer: **no for Berlin Group, accidentally-yes for UK.** A view should be named for the
*capability it grants* (what data is visible), not for the *standard/channel that asked*.

## 1. Current state — how each standard uses views

The shared spine is identical everywhere: a consent carries a list of
`(bank_id, account_id, view_id)` triples (`ConsentJWT.views: List[ConsentView]`,
`ConsentUtil.scala:55`), authorization grants the user real access to those views
(`grantAccessToViews`, `ConsentUtil.scala:373-399` — revoke-then-regrant per view), and at
request time the endpoint resolves a `ViewId`, checks access, and uses the returned `View` to
moderate the response. What differs per standard is *which* view IDs flow through that pipe and
*who chooses them*.

### OBP native (v1.2.1 → v7.0.0)
The view is a first-class URL parameter (`VIEW_ID`), resolved by middleware into `cc.view`.
The caller picks the view; nothing is hardcoded. Arbitrary system and custom views.

### Berlin Group v1.3
Fully view-gated, but through a **dedicated BG system-view vocabulary**
(`constant.scala:151-169`): `ReadAccountsBerlinGroup`, `ReadBalancesBerlinGroup`,
`ReadTransactionsBerlinGroup`, `InitiatePaymentsBerlinGroup`.

- The BG consent `access` object is translated into `ConsentView`s keyed to those views —
  `createBerlinGroupConsentJWT` / `updateBerlinGroupConsentJWT`
  (`ConsentUtil.scala:823-855, 913-1015`), IBAN → `(bankId, accountId)` resolution via
  `getBankAccountByIban`.
- Each AIS endpoint hardcodes the matching view: balances →
  `SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID` (`Http4sBGv13AIS.scala:216, 266`), transactions →
  `SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID` (`:287-289, :380-382`), accounts →
  `SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID` (`:439, :468`).
- The TPP never names a view; the standard's consent scopes map onto them.

### UK Open Banking v3.1 / v4.0.1
Also view-gated, but reusing the **generic** `Read*` system views with a detail-or-basic
fallback:

- Accounts: `SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID` `.or(BASIC)` via `checkViewAccessAndReturnView`
  (`Http4sUKOBv310Accounts.scala:44-58`; `Http4sUKOBv401AccountInfo.scala:324-338`).
- Balances: `SYSTEM_READ_BALANCES_VIEW_ID` (`Http4sUKOBv310Balances.scala:111-116`).
- Transactions: detail/basic pair via `checkViewsAccessAndReturnView`
  (`Http4sUKOBv310Transactions.scala:308-315`; `Http4sUKOBv401AccountInfo.scala:2218`).
  UKOB additionally checks the consent itself (`checkUKConsent`) + `passesPsd2Aisp` before the
  view check.
- Consent side: `createConsentJWT` (UK path, `ConsentUtil.scala:1040-1104`) sets
  **`view_id = permission`** — the OBUK permission strings become the granted view IDs directly
  (`:1063-1088`). If `bankId`/`accountIds` are absent it creates account-unscoped grants with
  `bank_id = null, account_id = null` (`:1078-1085`).

### Known inconsistencies (as of 2026-07-16)
1. **BG v2 AIS endpoints are pure mocks** — no auth, no consent, no view check
   (`Http4sBGv2AIS.scala:49-135`). If BG v2 goes live, the whole consent→view chain from v1.3
   needs porting.
2. **UKOB v2.0.0 hardcodes the `"owner"` view** for all AIS endpoints
   (`Http4sUKOBv200AIS.scala:130, 150`) — a TPP needs full owner access instead of a scoped
   read view.
3. **Two parallel PSD2 view vocabularies** for the same concept: BG's `*BerlinGroup` views vs
   UK's generic `Read*` views.
4. **UK `createConsentJWT` trusts the raw permission string as a view ID** and can create
   account-unscoped grants — correctness rests entirely on callers passing valid view IDs.
5. BG v1.3 uses two different access-check helpers (a local `hasViewAccess`-based
   `checkAccountAccess` for accounts/balances at `Http4sBGv13AIS.scala:57-62` vs
   `ViewNewStyle.checkAccountAccessAndGetView` for transactions). Both enforce, but it's two
   code paths for one job.
6. Legacy Lift BG v1.3 (`AccountInformationServiceAISApi.scala`) is entirely commented-out dead
   code — the live logic is `Http4sBGv13AIS.scala`.

## 2. What the permission definitions reveal

`MapperViews.applyDefaultsForSystemView` (`MapperViews.scala:757-819`):

- All six generic read views — `ReadAccountsBasic`, `ReadAccountsDetail`, `ReadBalances`,
  `ReadTransactionsBasic`, `ReadTransactionsDebits`, `ReadTransactionsDetail` — get the
  **identical** permission set, `SYSTEM_VIEW_PERMISSION_COMMON` (same as `accountant`). The
  basic/detail distinction the names promise is not yet backed by differing permissions.
- `ReadAccountsBerlinGroup` and `ReadBalancesBerlinGroup` get **no permissions at all** — a bare
  entity (`MapperViews.scala:785-787`). Only the BG transactions and payments views have tuned
  sets.

So the standard-branded names are not buying differentiated moderation today. The BG views are
mostly a parallel namespace whose content is empty or duplicative — the naming carries all the
weight, and naming-by-standard is the wrong axis.

## 3. Why standard-named views are the wrong axis

1. **It scales per-standard, not per-capability.** "Read balances" means the same thing in BG,
   UK, STET, and whatever comes next. Standard-named views mean every new standard mints
   another view set, and "who can see balances on this account?" becomes a query over N views
   that must be kept semantically in sync by hand.
2. **Views are OBP's primary access-control model and they're user-visible** — they appear in
   account view lists and in grants. Leaking regulatory brand names into that vocabulary couples
   the core ACL model to external specs' branding and lifecycle (a BG rename in a new spec
   version → stale view ID or a migration).
3. **The real per-standard differences live elsewhere.** BG and UK responses differ in *shape*,
   but shape is handled in each standard's JSONFactory. The view only controls *which underlying
   data is accessible*, and at that layer the standards barely differ.

## 4. Why the UK naming is fine anyway

The UK permission names (`ReadAccountsDetail`, `ReadBalances`, `ReadTransactionsBasic`, …)
happen to *be* a good generic capability vocabulary — they describe data visibility, not a
channel. That's why reusing them as shared system views works, and why UKOB v3.1/v4.0.1 feel
cleaner than BG v1.3.

The wrinkle is the mechanism, not the names: `createConsentJWT` passes the raw permission
string through as the `view_id`, so the UK spec's vocabulary directly owns part of the view
namespace. It works because the names are good; the right shape is an explicit
permission → view mapping table, so a future spec rename doesn't silently mint new views.

## 5. The one honest argument *for* segregation

Grants are per `(user, view, account)`, and `grantAccessToViews` revokes-then-regrants on
consent authorization. If BG and UK consents shared view IDs, revoking a BG consent could
clobber access a still-live UK consent granted on the same view+account. The separate BG
namespace accidentally shields against *cross-standard* interference — though not against two
consents *within* the same standard, so it's a partial fix for a real bug in the wrong layer.

The proper fix is consent-scoped grant accounting (or checking the consent, not just the view,
at request time — which the UK path already partly does via `checkUKConsent`). Once that
exists, the last reason for standard-named views disappears.

## 6. Recommendation

1. **Converge on one capability-named read-view vocabulary** — the existing `Read*` set.
2. **Give basic vs detail genuinely different permission sets** so the names mean something
   (today all six share `SYSTEM_VIEW_PERMISSION_COMMON`).
3. **Retarget BG's consent translation at the shared views** — the translation layer in
   `createBerlinGroupConsentJWT` already exists; it just points at the duplicate views. Treat
   the `*BerlinGroup` views as deprecated, with a migration for existing grants
   (`AccountAccess` rows referencing the old view IDs).
4. **Fix the grant-interference bug at the right layer**: consent-scoped grant accounting or
   request-time consent checks, not view-namespace segregation.
5. **Make the UK permission → view mapping explicit** instead of `view_id = permission`
   passthrough, and validate/scope the account-unscoped (`null` bank/account) grant path.
6. Standard-specific code should be exactly two things per standard: its
   consent-scope → view mapping, and its JSON factory. Never the view names themselves.
