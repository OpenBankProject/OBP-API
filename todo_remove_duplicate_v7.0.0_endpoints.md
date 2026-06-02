# TODO: Remove duplicate v7.0.0 endpoints (handoff)

**Status:** In progress — committed `getBanks` fix + uncommitted removal of 19 identical endpoints.
**Date:** 2026-06-02
**Context:** Several endpoints were added to `Http4s700.scala` purely as http4s migration scaffolding
(the file literally labels them `// ── POC endpoints — one per EndpointHelper category ──`).
Where a v7 endpoint is *behaviourally identical* to an earlier version, it should not live in v7 —
it should cascade. Where v7 *intentionally changed/improved* behaviour, it must stay.

---

## The rule

> Remove a v7.0.0 endpoint **only if it is behaviourally identical** to an earlier version.
> If v7 changed/improved anything (response codes, shape, behaviour), **keep it in v7**.

## How the cascade works (why deletion is safe for identical endpoints)

`Http4s700.scala` defines `v700ToV600Bridge` (search the file for it). Any unmatched
`/obp/v7.0.0/*` request is rewritten to `/obp/v6.0.0/*` and served by `Http4s600`, tagged
with response header `X-OBP-Version-Served: v6.0.0`. The bridge chain is fully continuous:

```
v700 → v600 → v510 → v500 → v400 → v310 → v300 → v220 → v210 → v200 → v140 → v130
```

So deleting a v7 endpoint cascades the request down to wherever that endpoint is actually
defined (e.g. `getExplicitCounterpartyById` has no v5/v6 successor and cascades to its v4 home,
which is the newest shape that exists). Routing is by `resourceDocs` (URL+verb) — removing the
`val` + its `resourceDocs += ResourceDoc(...)` block removes it from both routing and the
`v7ResourceDocIndex` the bridge consults, so the cascade engages automatically.

---

## Work done so far

### 1. `getBanks` — committed (`bd9f8ca84`)
This one was a **regression**, not an improvement: v7 served the older v4 shape
(`BanksJson400`: `id`, `short_name`) while v6 has the newer `BanksJsonV600` (`bank_id`,
`bank_code`). Deleted from v7 so it cascades to v6's correct shape. Test
`Http4s700RoutesTest` updated to assert `bank_id`/`bank_code`. **Already committed.**

### 2. 19 identical endpoints — removed (UNCOMMITTED working-tree change to `Http4s700.scala`)
Each uses the same JSON factory as its lower-version twin and all their v7 test scenarios
still pass when served via the cascade:

```
getBank, getCurrentUser, getCoreAccountById, getPrivateAccountByIdFull,
getExplicitCounterpartyById, getFeatures, getConnectors, getProviders, getUsers,
getCustomersAtOneBank, getCustomerByCustomerId, getAccountsAtBank, getCacheConfig,
getCacheInfo, getDatabasePoolInfo, getStoredProcedureConnectorHealth, getMigrations,
getCacheNamespaces, getScannedApiVersions
```

`Http4s700.scala`: 4061 → 3370 lines; resourceDoc count 64 → 45.
Main + test compile clean; `Http4s700RoutesTest` → **141/141 pass**.

### 3. 3 endpoints KEPT in v7 (NOT identical — improved response codes)
These were caught because deleting them turned their test scenarios red:

| Endpoint | v7 behaviour (kept) | cascaded v6 would give |
|---|---|---|
| `deleteEntitlement` | **204** No Content | 200 (yields `""`) |
| `addEntitlement` (duplicate role) | **409** Conflict | 400 |
| `getUserByUserId` (missing user) | **404** Not Found | 400 (`unboxFullOrFail` default code) |

v6 codes follow the older OBP convention (200-for-DELETE, 400-for-missing). v7's 204/409/404
are the more RESTful, intentional choices — so they stay.

---

## How "identical" was checked (and the GAP — please read)

Two layers were used:

1. **Static shape audit** — compared each v7 handler's `yield` factory/case-class against the
   lower-version handler's. This catches *shape* regressions (it's how `getBanks` was found) but
   only compares the **output type**, not the full handler. It did **not** diff auth helpers,
   declared roles, query-param parsing, error lists, or *which* data is passed into the factory.

2. **Bridge-routed test run (the decisive check)** — `Http4s700RoutesTest` drives
   `Http4s700.wrappedRoutesV700Services`, which *includes* `v700ToV600Bridge`. So deleted
   endpoints' test requests are actually served by the lower version. Passing = the cascaded
   response satisfied that test's assertions. This is exactly how the 3 non-identical endpoints
   were found.

**⚠️ The gap:** "identical" here means *equivalent up to what the v7 tests assert*. Per-endpoint
test depth was **not** audited. If one of the 19 has an untested query-param filter, error path,
or auth edge case that the lower version handles differently, the cascade would diverge silently
and neither check would have caught it.

### Recommended next step (to make it rigorous)
Before/instead of trusting the test coverage, do a runtime equivalence diff per endpoint:

1. On the pre-deletion build, capture each of the 19 endpoints' responses across an input matrix:
   valid request, missing resource, unauthenticated, missing-role, bad query params, malformed body.
2. On the post-deletion (cascade) build, capture the same matrix.
3. Diff status code + body + relevant headers (ignore `X-OBP-Version-Served`). Any non-empty diff =
   not identical → that endpoint should be restored to v7 (like the 3 above).

The git diff `bd9f8ca84^..bd9f8ca84` (getBanks) and the current uncommitted change show the exact
block-removal pattern: delete the `val NAME: HttpRoutes[IO] = ...` plus its
`resourceDocs += ResourceDoc(... http4sPartialFunction = Some(NAME))` block.

---

## Files
- `obp-api/src/main/scala/code/api/v7_0_0/Http4s700.scala` — endpoint defs + `v700ToV600Bridge`
- `obp-api/src/main/scala/code/api/v6_0_0/Http4s600.scala` — cascade target for most
- `obp-api/src/test/scala/code/api/v7_0_0/Http4s700RoutesTest.scala` — the suite (in-process, hits the bridge)
- Lower-version files (`Http4s500/400/...`) — cascade targets for endpoints with no v6 twin

## Decisions for the next developer
- Confirm the 19 with the runtime-diff matrix above (or accept test-coverage-bounded equivalence).
- Decide whether `deleteEntitlement`/`addEntitlement`/`getUserByUserId` should instead have their
  improved codes **ported down into v6** (v6 is not yet STABLE) and then be removed from v7 too —
  that would make v6 canonical with the better semantics. Current choice: keep them in v7.

> **NOTE:** The working-tree removal of the 19 endpoints was **reverted** — `Http4s700.scala` is
> back at HEAD (`getBanks` cascade still committed in `bd9f8ca84`; the other 19 are present again).
> Use the evidence table + script below to redo the removal from scratch.

---

## Appendix A — Per-endpoint evidence (the 19 identical removals)

All 19 build the **same JSON factory** as their lower-version twin (so same response shape).
Line numbers are in `Http4s600.scala` unless noted. `getExplicitCounterpartyById` has no
v5/v6 successor — v4 is the newest shape — so it cascades two extra hops to its v4 home.

| v7 endpoint | verb + URL | factory (v7 == twin) | cascades to |
|---|---|---|---|
| getBank | GET /banks/BANK_ID | createBankJsonV600 | v6 `getBank` |
| getCurrentUser | GET /users/current | createUserInfoJSON | v6 `getCurrentUser` |
| getCoreAccountById | GET /my/banks/BANK_ID/accounts/ACCOUNT_ID/account | createModeratedCoreAccountJsonV600 | v6 `getCoreAccountByIdV600` |
| getPrivateAccountByIdFull | GET /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account | createBankAccountJSON600 | v6 `getPrivateAccountByIdFull` |
| getExplicitCounterpartyById | GET .../counterparties/COUNTERPARTY_ID | createCounterpartyWithMetadataJson400 | **v4** `Http4s400` (newest shape) |
| getFeatures | GET /features | FeaturesJsonV600 | v6 `getFeatures` |
| getConnectors | GET /system/connectors | createConnectorsJson | v6 `getConnectors` |
| getProviders | GET /providers | createProvidersJson | v6 `getProviders` |
| getUsers | GET /users | createUsersInfoJsonV600 | v6 `getUsers` |
| getCustomersAtOneBank | GET /banks/BANK_ID/customers | createCustomersJson | v6 `getCustomersAtOneBank` |
| getCustomerByCustomerId | GET /banks/BANK_ID/customers/CUSTOMER_ID | createCustomerWithAttributesJson | v6 `getCustomerByCustomerId` |
| getAccountsAtBank | GET /banks/BANK_ID/accounts | BasicAccountsJsonV600 | v6 `getAccountsAtBank` |
| getCacheConfig | GET /system/cache/config | createCacheConfigJsonV600 | v6 `getCacheConfig` |
| getCacheInfo | GET /system/cache/info | createCacheInfoJsonV600 | v6 `getCacheInfo` |
| getDatabasePoolInfo | GET /system/database/pool | createDatabasePoolInfoJsonV600 | v6 `getDatabasePoolInfo` |
| getStoredProcedureConnectorHealth | GET /system/connectors/stored_procedure_vDec2019/health | StoredProcedureConnectorHealthJsonV600 | v6 `getStoredProcedureConnectorHealth` |
| getMigrations | GET /system/migrations | createMigrationScriptLogsJsonV600 | v6 `getMigrations` |
| getCacheNamespaces | GET /system/cache/namespaces | createCacheNamespacesJsonV600 | v6 `getCacheNamespaces` |
| getScannedApiVersions | GET /api/versions | ScannedApiVersionJsonV600 | v6 `getScannedApiVersions` |

**Do NOT remove these 3** (improved codes — see table in the main body):
`deleteEntitlement` (204), `addEntitlement` (409), `getUserByUserId` (404).

## Appendix B — Reproducible removal script

Each endpoint is a `val NAME: HttpRoutes[IO] = ...` immediately followed by its
`resourceDocs += ResourceDoc(... http4sPartialFunction = Some(NAME))` block. The script below
deletes both (plus any immediately-preceding `//` comment lines and one trailing blank line),
asserts no block overlaps, and prints what it removed. It is idempotent on the names listed.

```python
import re
f = "obp-api/src/main/scala/code/api/v7_0_0/Http4s700.scala"
lines = open(f).read().split("\n")

# 19 IDENTICAL endpoints only — the 3 behaviourally-improved ones are deliberately NOT here
targets = ["getBank","getCurrentUser","getCoreAccountById","getPrivateAccountByIdFull",
"getExplicitCounterpartyById","getFeatures","getConnectors","getProviders","getUsers",
"getCustomersAtOneBank","getCustomerByCustomerId","getAccountsAtBank","getCacheConfig",
"getCacheInfo","getDatabasePoolInfo","getStoredProcedureConnectorHealth","getMigrations",
"getCacheNamespaces","getScannedApiVersions"]

val_re  = {t: re.compile(rf"^\s*(lazy )?val {t}: HttpRoutes\[IO\]") for t in targets}
some_re = {t: re.compile(rf"http4sPartialFunction = Some\({t}\)\s*$") for t in targets}

blocks = []
for t in targets:
    vi = next(i for i,l in enumerate(lines) if val_re[t].search(l))
    si = next(i for i,l in enumerate(lines) if some_re[t].search(l))
    assert si > vi, t
    ci = next(i for i in range(si+1, len(lines)) if re.match(r"^\s*\)\s*$", lines[i]))
    start = vi
    while start-1 >= 0 and re.match(r"^\s*//", lines[start-1]):
        start -= 1
    blocks.append((start, ci, t))

blocks.sort()
for (a,b,t),(c,d,t2) in zip(blocks, blocks[1:]):
    assert b < c, f"OVERLAP {t} vs {t2}"

to_del = set()
for a,b,t in blocks:
    end = b
    if end+1 < len(lines) and lines[end+1].strip()=="":
        end += 1
    to_del.update(range(a, end+1))

open(f,"w").write("\n".join(l for i,l in enumerate(lines) if i not in to_del))
print(f"removed {len(to_del)} lines; resourceDoc count should drop 64 -> 45")
```

After running: `mvn test-compile -pl obp-api -am -q` then
`mvn test -pl obp-api -q -DwildcardSuites="code.api.v7_0_0.Http4s700RoutesTest" -DfailIfNoTests=false`
— expect **141/141 pass**. If any scenario goes red, that endpoint is NOT identical → restore it
to v7 (that is precisely how the 3 kept endpoints were identified).
