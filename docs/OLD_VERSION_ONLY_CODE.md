# Code that only versions below v5.1.0 can reach

OBP versions below v5.1.0 are now disabled. The kept API sets are OBP v5.1.0, v6.0.0 and v7.0.0, Berlin Group v1.3 and v2, and UK Open Banking v2.0, v3.1 and v4.0.1. This document lists the endpoint implementations in the v1.2.1 to v5.0.0 files that none of the kept sets can reach. It also lists the helper code and the tests that only those implementations use. That code has been commented out, one version at a time, in the commits that follow this document.

The analysis was made on `origin/develop` at commit `8dfeb284a` (2026-11-26).

## Summary

| | Count |
|---|---|
| ResourceDocs in v1.2.1 to v5.0.0 | 615 |
| Reachable from a kept version (set K) | 475 |
| Reachable only from a version below v5.1.0 (set O) | **140 docs, 134 handlers** |
| Lines in O handlers and their ResourceDoc blocks | about 5,800 |
| Helper symbols used only by O | 20 (7 commented out, 13 public ones kept) |
| Connector methods used only by O | 0 |
| O endpoints with no replacement in K | **0** |

Every O endpoint is shadowed by a newer handler with the same verb and the same path. A request for that path under `/obp/v5.1.0/`, `/obp/v6.0.0/` or `/obp/v7.0.0/` is already served by the newer handler. So commenting the O handler out removes no capability from a kept version.

## How the route table is built

Requests go through one chain, `Http4sApp.baseServices` (`obp-api/src/main/scala/code/api/util/http4s/Http4sApp.scala:145`). It first tries the CORS handler and the apps, status and resource-docs pages. Then it tries v7.0.0, v6.0.0, v5.1.0 and v5.0.0. Next come Berlin Group v2, UK Open Banking v2.0, v3.1 and v4.0.1, and Berlin Group v1.3. Last come v4.0.0 down to v1.2.1. After those come dynamic entities and endpoints, DirectLogin, SIWE, the alive check, domain APIs and a JSON 404.

The props `api_disabled_versions` and `api_enabled_versions` are read in one place, `Http4sApp.gate` (`Http4sApp.scala:76`). A disabled version gets an empty route table, so its own prefix such as `/obp/v4.0.0/` returns 404. Nothing else in the chain checks the version.

The versions below v5.1.0 are not disabled by default: `api_disabled_versions` is empty unless a deployment sets it, and this change does not set it. On an instance that leaves them enabled, the old prefixes keep serving every endpoint in set K, and an endpoint in set O answers 404 under its own old prefix, for example `GET /obp/v1.2.1/banks`. The same path under `/obp/v5.1.0/`, `/obp/v6.0.0/` or `/obp/v7.0.0/` is served as before.

Each version file ends with a bridge to the version below it. The bridge rewrites the path prefix and calls the lower version's routes directly, without the gate:

| Bridge | Where | When it passes a request on |
|---|---|---|
| v7.0.0 to v6.0.0 | `Http4s700.scala:7834` | no v7.0.0 route serves the path, and the path is not one of v7.0.0's `excludeEndpoints` |
| v6.0.0 to v5.1.0 | `Http4s600.scala:6753` | no v6.0.0 route serves the path |
| v5.1.0 to v5.0.0 | `Http4s510.scala:5528` | no v5.1.0 route serves the path |
| v5.0.0 to v4.0.0 | `Http4s500.scala:2419` | the v5.0.0 routes did not answer |
| v4.0.0 to v3.1.0, and so on down to v1.3.0 to v1.2.1 | the end of each `Http4sNNN.scala` | the routes of that version did not answer |

So a request for `/obp/v5.1.0/<path>` reaches an implementation in a v1.2.1 file if no newer version serves `<path>`. `ResourceDocMiddleware.isEndpointEnabled` (`ResourceDocMiddleware.scala:134`) says this is intended: disabling a version retires its prefix, but its endpoints stay reachable through newer prefixes.

Inside a version, `routesInOrder` gives the order the routes are tried in. `ResourceDocMatcher.selectByRoute` asks the routes which one serves a request and picks that route's doc. The props `api_disabled_endpoints` and `api_enabled_endpoints` are checked per request in `ResourceDocMiddleware.scala:185`. A disabled endpoint passes the request on to the next link of the chain.

`Http4sResourceDocAggregation.excludeEndpointsV400`, `V510` and `V600` (`Http4sResourceDocAggregation.scala:86`) only hide docs from the resource-docs catalogue. They do not stop routing, because the v5.1.0 and v6.0.0 bridges do not read them. Only v7.0.0 applies its own `excludeEndpoints` to routing.

## How set O was found

A probe test replayed the real chain for every ResourceDoc of v1.2.1 to v5.0.0. For each doc it built the doc's own URL under each kept prefix (v5.1.0, v6.0.0 and v7.0.0). It then asked each version in turn, with `ResourceDocMatcher.selectByRoute` and the version's ordered docs, which route serves that URL. It used the same bridge rules as above. A doc is in K if any kept prefix ends at that doc. It is in O if every kept prefix ends at a different, newer doc.

Three further checks were made by reading the code:

- Every O route pattern was compared, segment by segment, with the pattern of the route that shadows it. In every case the newer pattern accepts every URL the old one accepts. No guard (`if ...`) differs. So no URL reaches an O handler from a kept prefix, not only the sample URLs.
- v1.2.1 `bankById` runs before the middleware and so is not in the probe's index. `GET /banks/BANK_ID` is served by v5.0.0 `getBank` and v6.0.0 `getBank`, so `bankById` is in O too.
- The five v4.0.0 `/management/cascading/...` endpoints exist only when `allow_cascade_delete=true`. No kept version has these paths, so they stay in K.

The probe and the existing `ResourceDocRouteBindingTest` both passed (19 scenarios, 0 failures).

## Things to know before commenting out

- **Disabled endpoints currently fall through to old code.** In v5.0.0 and below, `api_disabled_endpoints` does not stop a request. The request falls through the bridge to the next version down. For example, if v5.0.0 `getBank` is disabled, `GET /obp/v5.1.0/banks/BANK_ID` is served by the v4.0.0 handler, which is in O. Once the O handlers are commented out, such a request returns 404 instead. This matches what disabling an endpoint is meant to do.
- **Thirteen O docs are listed in the v5.1.0, v6.0.0 and v7.0.0 resource-docs today.** Their URL text differs from the newer doc's (for example `BANK_ACCOUNT_ID` against `ACCOUNT_ID`), so the catalogue does not remove them as duplicates. The catalogue therefore documents handlers that never run. Commenting them out removes those entries: v3.1.0 `getSystemView`, `updateSystemView` and `deleteSystemView`; v2.1.0 `createTransactionRequestSandboxTan` and the four `answerTransactionRequestChallenge*` docs; and v1.2.1 `createViewForBankAccount`, `updateViewForBankAccount`, `getPermissionForUserForBankAccount`, `getTransactionsForBankAccount` and `getTransactionByIdForBankAccount`.
- **Three references from other files.** `Http4sResourceDocAggregation.scala` names v3.1.0 `createAccount` (line 92) and v3.0.0 `getUserByUsername` (lines 98 and 110) with `nameOf(...)`. The filter matches by name, so these become the string literals `"createAccount"` and `"getUserByUsername"`, which keeps the filter exactly as it is.
- **v2.1.0 shares two routes between docs.** `createTransactionRequest` serves four docs and `answerTransactionRequestChallenge` serves four docs (one per transaction request type). The v4.0.0 handlers with the same paths accept any type, so both v2.1.0 routes are fully shadowed.
- **Tests.** The tests in the old version folders call the old prefixes, which the test props leave enabled. A test that calls an O endpoint under its own prefix will get 404 once the handler is commented out. Some suites also call O endpoints during setup. The exact list comes from running each version's suites after each commit. The tag references found so far are listed below.

## Riskiest areas

None of these removes a capability, because each one is shadowed by a newer handler. They are listed because they touch money, consent or access rights, so each commit should be reviewed with care.

- **Payments (10 docs).** v2.1.0 `createTransactionRequest*` (SANDBOX_TAN, COUNTERPARTY, SEPA, FREE_FORM) and `answerTransactionRequestChallenge*`, plus v2.1.0 and v3.1.0 `getTransactionRequests`. They are shadowed by v4.0.0 `createTransactionRequest` and `answerTransactionRequestChallenge`, and by v5.1.0 `getTransactionRequests`.
- **Consents (2 docs).** v3.1.0 and v4.0.0 `getConsents` (`GET /banks/BANK_ID/my/consents`), shadowed by v5.1.0 `getMyConsentsByBank`.
- **Entitlements, users, consumers, views and scopes (40 docs).** These include:
  - v2.0.0 `getEntitlements`, `getAllEntitlements` and `createUserCustomerLinks`
  - v3.0.0 `getUser`, `getUserByUserId`, `getUserByUsername`, `getUsers`, `addScope` and `getScopes`
  - v3.1.0 system views and user auth contexts
  - consumer management in v2.1.0 and v3.1.0
  - the view and permission endpoints of v1.2.1

## How it was commented out

There is one commit per version, newest first, so each commit removes the users of a helper before the helper itself. Each commit comments out the O handlers, their ResourceDoc blocks, their `routesInOrder` entries and the helpers in the same `Http4sNNN.scala` that only they use. Every commented block starts with the tag `THIS-CODE-IS-COMMENTED-OUT-BECAUSE-IT-IS-UNREACHABLE-IN-OBP-BEFORE-OBPv5.1.0`, followed by a line saying why, so a search for the tag finds every block. Test tags that named an O handler with `nameOf` now use the same name as a string, so they still compile. Each commit was compiled, with its tests, before the next one. The JSONFactory helpers and `NewStyle.function.ownEntitlement` were commented out in one commit of their own and later restored, because dynamic Scala code may call them (see "Helper code used only by set O"). The tests that only exercised O endpoints follow in another commit.

1. v5.0.0 (2 docs)
2. v4.0.0 (18 docs)
3. v3.1.0 (26 docs), including the `Http4sResourceDocAggregation` change for `createAccount`
4. v3.0.0 (17 docs), including the change for `getUserByUsername`
5. v2.2.0 (13 docs)
6. v2.1.0 (25 docs)
7. v2.0.0 (15 docs)
8. v1.4.0 (4 docs)
9. v1.3.0 (1 doc)
10. v1.2.1 (19 docs)

Code is commented out, not deleted.

v1.2.1 `bankById` ran before the middleware, so `Http4s121` used to wrap it around the middleware. With `bankById` gone, the middleware serves every remaining v1.2.1 route directly.

Three symbols were commented out beyond the 20 helpers below, because commenting out the v2.1.0 handlers left them with no user: `v210SupportedTransactionRequestTypes` (the transaction request types the v2.1.0 route accepted), and `answerChallengeCommonErrors` and `answerChallengeTags`, which the scan had already found unused.

## Set O by version

The handler column gives the line of the route `val` in the version's `Http4sNNN.scala`. Where the three kept prefixes end at different handlers, all three are shown.

### v5.0.0 — 2 docs, 2 handlers, about 58 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv5.0.0-root | GET | `/root` | `Http4s500.scala:134` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv5.0.0-getBanks | GET | `/banks` | `Http4s500.scala:164` | v5.1.0:getBanks / v6.0.0:getBanks / v6.0.0:getBanks |  |

### v4.0.0 — 18 docs, 18 handlers, about 776 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv4.0.0-root | GET | `/root` | `Http4s400.scala:568` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv4.0.0-getBanks | GET | `/banks` | `Http4s400.scala:309` | v5.1.0:getBanks / v6.0.0:getBanks / v6.0.0:getBanks |  |
| OBPv4.0.0-getBank | GET | `/banks/BANK_ID` | `Http4s400.scala:342` | v5.0.0:getBank / v6.0.0:getBank / v6.0.0:getBank |  |
| OBPv4.0.0-createBank | POST | `/banks` | `Http4s400.scala:474` | v5.0.0:createBank / v6.0.0:createBank / v6.0.0:createBank |  |
| OBPv4.0.0-getAtms | GET | `/banks/BANK_ID/atms` | `Http4s400.scala:604` | v5.1.0:getAtms |  |
| OBPv4.0.0-getAtm | GET | `/banks/BANK_ID/atms/ATM_ID` | `Http4s400.scala:665` | v5.1.0:getAtm |  |
| OBPv4.0.0-getProducts | GET | `/banks/BANK_ID/products` | `Http4s400.scala:701` | v5.0.0:getProducts / v6.0.0:getProductsV600 / v6.0.0:getProductsV600 |  |
| OBPv4.0.0-getProduct | GET | `/banks/BANK_ID/products/PRODUCT_CODE` | `Http4s400.scala:755` | v5.0.0:getProduct |  |
| OBPv4.0.0-createAtm | POST | `/banks/BANK_ID/atms` | `Http4s400.scala:809` | v5.1.0:createAtm |  |
| OBPv4.0.0-createProduct | PUT | `/banks/BANK_ID/products/PRODUCT_CODE` | `Http4s400.scala:854` | v5.0.0:createProduct |  |
| OBPv4.0.0-getCustomersByAttributes | GET | `/banks/BANK_ID/customers` | `Http4s400.scala:1217` | v5.0.0:getCustomersAtOneBank / v6.0.0:getCustomersAtOneBank / v7.0.0:getCustomersAtOneBank |  |
| OBPv4.0.0-createCustomer | POST | `/banks/BANK_ID/customers` | `Http4s400.scala:1262` | v5.0.0:createCustomer / v6.0.0:createCustomer / v6.0.0:createCustomer |  |
| OBPv4.0.0-getBankAccountsBalancesForCurrentUser | GET | `/banks/BANK_ID/balances` | `Http4s400.scala:1320` | v5.1.0:getBankAccountsBalances |  |
| OBPv4.0.0-getConsents | GET | `/banks/BANK_ID/my/consents` | `Http4s400.scala:2747` | v5.1.0:getMyConsentsByBank | consent |
| OBPv4.0.0-deleteAtm | DELETE | `/banks/BANK_ID/atms/ATM_ID` | `Http4s400.scala:7760` | v5.1.0:deleteAtm |  |
| OBPv4.0.0-updateAtm | PUT | `/banks/BANK_ID/atms/ATM_ID` | `Http4s400.scala:6339` | v5.1.0:updateAtm |  |
| OBPv4.0.0-getBankAccountBalancesForCurrentUser | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/balances` | `Http4s400.scala:4250` | v5.1.0:getAllBankAccountBalances |  |
| OBPv4.0.0-createConsumer | POST | `/management/consumers` | `Http4s400.scala:10744` | v5.1.0:createConsumer | entitlement/user/consumer |

### v3.1.0 — 26 docs, 26 handlers, about 1079 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv3.1.0-root | GET | `/root` | `Http4s310.scala:158` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv3.1.0-getConsumer | GET | `/management/consumers/CONSUMER_ID` | `Http4s310.scala:529` | v5.1.0:getConsumer / v6.0.0:getConsumer / v7.0.0:getConsumer | entitlement/user/consumer |
| OBPv3.1.0-getConsumers | GET | `/management/consumers` | `Http4s310.scala:588` | v5.1.0:getConsumers / v5.1.0:getConsumers / v7.0.0:getConsumers | entitlement/user/consumer |
| OBPv3.1.0-getAdapterInfo | GET | `/adapter` | `Http4s310.scala:697` | v5.0.0:getAdapterInfo |  |
| OBPv3.1.0-getUserAuthContexts | GET | `/users/USER_ID/auth-context` | `Http4s310.scala:816` | v5.0.0:getUserAuthContexts | entitlement/user/consumer |
| OBPv3.1.0-getProductAttribute | GET | `/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID` | `Http4s310.scala:969` | v4.0.0:getProductAttribute |  |
| OBPv3.1.0-getSystemView | GET | `/system-views/SYS_VIEW_ID` | `Http4s310.scala:1284` | v5.0.0:getSystemView | entitlement/user/consumer |
| OBPv3.1.0-getBankAccountsBalances | GET | `/banks/BANK_ID/balances` | `Http4s310.scala:1396` | v5.1.0:getBankAccountsBalances |  |
| OBPv3.1.0-getTransactionRequests | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests` | `Http4s310.scala:1525` | v5.1.0:getTransactionRequests | payment |
| OBPv3.1.0-getProduct | GET | `/banks/BANK_ID/products/PRODUCT_CODE` | `Http4s310.scala:1585` | v5.0.0:getProduct |  |
| OBPv3.1.0-getProducts | GET | `/banks/BANK_ID/products` | `Http4s310.scala:1673` | v5.0.0:getProducts / v6.0.0:getProductsV600 / v6.0.0:getProductsV600 |  |
| OBPv3.1.0-getConsents | GET | `/banks/BANK_ID/my/consents` | `Http4s310.scala:1741` | v5.1.0:getMyConsentsByBank | consent |
| OBPv3.1.0-getPrivateAccountByIdFull | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account` | `Http4s310.scala:1792` | v4.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull |  |
| OBPv3.1.0-deleteSystemView | DELETE | `/system-views/SYS_VIEW_ID` | `Http4s310.scala:2162` | v5.0.0:deleteSystemView | entitlement/user/consumer |
| OBPv3.1.0-createUserAuthContext | POST | `/users/USER_ID/auth-context` | `Http4s310.scala:2477` | v5.0.0:createUserAuthContext | entitlement/user/consumer |
| OBPv3.1.0-createProductAttribute | POST | `/banks/BANK_ID/products/PRODUCT_CODE/attribute` | `Http4s310.scala:2509` | v4.0.0:createProductAttribute |  |
| OBPv3.1.0-callsLimit | PUT | `/management/consumers/CONSUMER_ID/consumer/call-limits` | `Http4s310.scala:2663` | v4.0.0:callsLimit | entitlement/user/consumer |
| OBPv3.1.0-updateSystemView | PUT | `/system-views/SYS_VIEW_ID` | `Http4s310.scala:2794` | v5.0.0:updateSystemView / v6.0.0:updateSystemView / v6.0.0:updateSystemView | entitlement/user/consumer |
| OBPv3.1.0-updateProductAttribute | PUT | `/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID` | `Http4s310.scala:2830` | v4.0.0:updateProductAttribute |  |
| OBPv3.1.0-createCustomer | POST | `/banks/BANK_ID/customers` | `Http4s310.scala:3242` | v5.0.0:createCustomer / v6.0.0:createCustomer / v6.0.0:createCustomer |  |
| OBPv3.1.0-createSystemView | POST | `/system-views` | `Http4s310.scala:3562` | v5.0.0:createSystemView | entitlement/user/consumer |
| OBPv3.1.0-addCardForBank | POST | `/management/banks/BANK_ID/cards` | `Http4s310.scala:3668` | v5.0.0:addCardForBank |  |
| OBPv3.1.0-createUserAuthContextUpdateRequest | POST | `/banks/BANK_ID/users/current/auth-context-updates/SCA_METHOD` | `Http4s310.scala:4001` | v5.0.0:createUserAuthContextUpdateRequest | entitlement/user/consumer |
| OBPv3.1.0-answerUserAuthContextUpdateChallenge | POST | `/banks/BANK_ID/users/current/auth-context-updates/AUTH_CONTEXT_UPDATE_ID/challenge` | `Http4s310.scala:4044` | v5.0.0:answerUserAuthContextUpdateChallenge | entitlement/user/consumer |
| OBPv3.1.0-createProduct | PUT | `/banks/BANK_ID/products/PRODUCT_CODE` | `Http4s310.scala:4120` | v5.0.0:createProduct |  |
| OBPv3.1.0-createAccount | PUT | `/banks/BANK_ID/accounts/NEW_ACCOUNT_ID` | `Http4s310.scala:4430` | v5.0.0:createAccount / v5.0.0:createAccount / v7.0.0:createAccountWithIdV700 |  |

### v3.0.0 — 17 docs, 17 handlers, about 704 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv3.0.0-root | GET | `/root` | `Http4s300.scala:93` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv3.0.0-getViewsForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/views` | `Http4s300.scala:125` | v5.0.0:getViewsForBankAccount | entitlement/user/consumer |
| OBPv3.0.0-getPrivateAccountById | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account` | `Http4s300.scala:347` | v4.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull |  |
| OBPv3.0.0-getCoreAccountById | GET | `/my/banks/BANK_ID/accounts/ACCOUNT_ID/account` | `Http4s300.scala:434` | v4.0.0:getCoreAccountById / v6.0.0:getCoreAccountByIdV600 / v6.0.0:getCoreAccountByIdV600 |  |
| OBPv3.0.0-getFirehoseAccountsAtOneBank | GET | `/banks/FIREHOSE_BANK_ID/firehose/accounts/views/FIREHOSE_VIEW_ID` | `Http4s300.scala:516` | v4.0.0:getFirehoseAccountsAtOneBank | entitlement/user/consumer |
| OBPv3.0.0-getUser | GET | `/users/email/USER_EMAIL/terminator` | `Http4s300.scala:918` | v4.0.0:getUsersByEmail | entitlement/user/consumer |
| OBPv3.0.0-getUserByUserId | GET | `/users/user_id/USER_ID` | `Http4s300.scala:950` | v4.0.0:getUserByUserId | entitlement/user/consumer |
| OBPv3.0.0-getUserByUsername | GET | `/users/username/USERNAME` | `Http4s300.scala:985` | v4.0.0:getUserByUsername | entitlement/user/consumer |
| OBPv3.0.0-createAtm | POST | `/banks/BANK_ID/atms` | `Http4s300.scala:1132` | v5.1.0:createAtm |  |
| OBPv3.0.0-getAtm | GET | `/banks/BANK_ID/atms/ATM_ID` | `Http4s300.scala:1327` | v5.1.0:getAtm |  |
| OBPv3.0.0-getAtms | GET | `/banks/BANK_ID/atms` | `Http4s300.scala:1364` | v5.1.0:getAtms |  |
| OBPv3.0.0-getUsers | GET | `/users` | `Http4s300.scala:1427` | v4.0.0:getUsers / v6.0.0:getUsers / v6.0.0:getUsers | entitlement/user/consumer |
| OBPv3.0.0-getAggregateMetrics | GET | `/management/aggregate-metrics` | `Http4s300.scala:2008` | v5.1.0:getAggregateMetrics / v6.0.0:getAggregateMetrics / v6.0.0:getAggregateMetrics |  |
| OBPv3.0.0-addScope | POST | `/consumers/CONSUMER_ID/scopes` | `Http4s300.scala:2084` | v4.0.0:addScope / v4.0.0:addScope / v7.0.0:addScope | entitlement/user/consumer |
| OBPv3.0.0-getScopes | GET | `/consumers/CONSUMER_ID/scopes` | `Http4s300.scala:2186` | v4.0.0:getScopes | entitlement/user/consumer |
| OBPv3.0.0-getBanks | GET | `/banks` | `Http4s300.scala:2223` | v5.1.0:getBanks / v6.0.0:getBanks / v6.0.0:getBanks |  |
| OBPv3.0.0-bankById | GET | `/banks/BANK_ID` | `Http4s300.scala:2258` | v5.0.0:getBank / v6.0.0:getBank / v6.0.0:getBank |  |

### v2.2.0 — 13 docs, 13 handlers, about 553 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv2.2.0-root | GET | `/root` | `Http4s220.scala:86` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv2.2.0-getViewsForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/views` | `Http4s220.scala:111` | v5.0.0:getViewsForBankAccount | entitlement/user/consumer |
| OBPv2.2.0-createViewForBankAccount | POST | `/banks/BANK_ID/accounts/VIEW_ACCOUNT_ID/views` | `Http4s220.scala:163` | v3.0.0:createViewForBankAccount | entitlement/user/consumer |
| OBPv2.2.0-updateViewForBankAccount | PUT | `/banks/BANK_ID/accounts/ACCOUNT_ID/views/UPD_VIEW_ID` | `Http4s220.scala:236` | v3.0.0:updateViewForBankAccount | entitlement/user/consumer |
| OBPv2.2.0-getExplicitCounterpartiesForAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties` | `Http4s220.scala:362` | v4.0.0:getExplicitCounterpartiesForAccount |  |
| OBPv2.2.0-getExplicitCounterpartyById | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID` | `Http4s220.scala:401` | v4.0.0:getExplicitCounterpartyById |  |
| OBPv2.2.0-createBank | POST | `/banks` | `Http4s220.scala:465` | v5.0.0:createBank / v6.0.0:createBank / v6.0.0:createBank |  |
| OBPv2.2.0-createBranch | POST | `/banks/BANK_ID/branches` | `Http4s220.scala:530` | v3.0.0:createBranch |  |
| OBPv2.2.0-createAtm | POST | `/banks/BANK_ID/atms` | `Http4s220.scala:564` | v5.1.0:createAtm |  |
| OBPv2.2.0-createAccount | PUT | `/banks/BANK_ID/accounts/NEW_ACCOUNT_ID` | `Http4s220.scala:697` | v5.0.0:createAccount / v5.0.0:createAccount / v7.0.0:createAccountWithIdV700 |  |
| OBPv2.2.0-config | GET | `/config` | `Http4s220.scala:767` | v3.1.0:config |  |
| OBPv2.2.0-createConsumer | POST | `/management/consumers` | `Http4s220.scala:847` | v5.1.0:createConsumer | entitlement/user/consumer |
| OBPv2.2.0-createCounterparty | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties` | `Http4s220.scala:914` | v4.0.0:createCounterparty |  |

### v2.1.0 — 25 docs, 19 handlers, about 1306 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv2.1.0-root | GET | `/root` | `Http4s210.scala:110` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv2.1.0-createTransactionRequestSandboxTan | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/SANDBOX_TAN/transaction-requests` | `Http4s210.scala:218` | v4.0.0:createTransactionRequestAccount | payment |
| OBPv2.1.0-createTransactionRequestCounterparty | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests` | `Http4s210.scala:218` | v4.0.0:createTransactionRequestCounterparty | payment |
| OBPv2.1.0-createTransactionRequestSepa | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/SEPA/transaction-requests` | `Http4s210.scala:218` | v4.0.0:createTransactionRequestSepa | payment |
| OBPv2.1.0-createTransactionRequestFreeForm | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/FREE_FORM/transaction-requests` | `Http4s210.scala:218` | v4.0.0:createTransactionRequestFreeForm | payment |
| OBPv2.1.0-answerTransactionRequestChallengeSandbox_tan | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SANDBOX_TAN/transaction-requests/TRANSACTION_REQUEST_ID/challenge` | `Http4s210.scala:429` | v4.0.0:answerTransactionRequestChallenge | payment |
| OBPv2.1.0-answerTransactionRequestChallengeCounterparty | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests/TRANSACTION_REQUEST_ID/challenge` | `Http4s210.scala:429` | v4.0.0:answerTransactionRequestChallenge | payment |
| OBPv2.1.0-answerTransactionRequestChallengeSepa | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SEPA/transaction-requests/TRANSACTION_REQUEST_ID/challenge` | `Http4s210.scala:429` | v4.0.0:answerTransactionRequestChallenge | payment |
| OBPv2.1.0-answerTransactionRequestChallengeFree_form | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/FREE_FORM/transaction-requests/TRANSACTION_REQUEST_ID/challenge` | `Http4s210.scala:429` | v4.0.0:answerTransactionRequestChallenge | payment |
| OBPv2.1.0-getTransactionRequests | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests` | `Http4s210.scala:577` | v5.1.0:getTransactionRequests | payment |
| OBPv2.1.0-getConsumers | GET | `/management/consumers` | `Http4s210.scala:720` | v5.1.0:getConsumers / v5.1.0:getConsumers / v7.0.0:getConsumers | entitlement/user/consumer |
| OBPv2.1.0-getConsumer | GET | `/management/consumers/CONSUMER_ID` | `Http4s210.scala:692` | v5.1.0:getConsumer / v6.0.0:getConsumer / v7.0.0:getConsumer | entitlement/user/consumer |
| OBPv2.1.0-enableDisableConsumers | PUT | `/management/consumers/CONSUMER_ID` | `Http4s210.scala:747` | v3.1.0:enableDisableConsumers | entitlement/user/consumer |
| OBPv2.1.0-getUsers | GET | `/users` | `Http4s210.scala:857` | v4.0.0:getUsers / v6.0.0:getUsers / v6.0.0:getUsers | entitlement/user/consumer |
| OBPv2.1.0-getAtm | GET | `/banks/BANK_ID/atms/ATM_ID` | `Http4s210.scala:946` | v5.1.0:getAtm |  |
| OBPv2.1.0-getBranch | GET | `/banks/BANK_ID/branches/BRANCH_ID` | `Http4s210.scala:982` | v3.0.0:getBranch |  |
| OBPv2.1.0-getProduct | GET | `/banks/BANK_ID/products/PRODUCT_CODE` | `Http4s210.scala:1020` | v5.0.0:getProduct |  |
| OBPv2.1.0-getProducts | GET | `/banks/BANK_ID/products` | `Http4s210.scala:1055` | v5.0.0:getProducts / v6.0.0:getProductsV600 / v6.0.0:getProductsV600 |  |
| OBPv2.1.0-createCustomer | POST | `/banks/BANK_ID/customers` | `Http4s210.scala:1091` | v5.0.0:createCustomer / v6.0.0:createCustomer / v6.0.0:createCustomer |  |
| OBPv2.1.0-getCustomersForUser | GET | `/users/current/customers` | `Http4s210.scala:1163` | v3.0.0:getCustomersForUser | entitlement/user/consumer |
| OBPv2.1.0-getCustomersForCurrentUserAtBank | GET | `/banks/BANK_ID/customers` | `Http4s210.scala:1187` | v5.0.0:getCustomersAtOneBank / v6.0.0:getCustomersAtOneBank / v7.0.0:getCustomersAtOneBank |  |
| OBPv2.1.0-updateBranch | PUT | `/banks/BANK_ID/branches/BRANCH_ID` | `Http4s210.scala:1222` | v3.0.0:updateBranch |  |
| OBPv2.1.0-createBranch | POST | `/banks/BANK_ID/branches` | `Http4s210.scala:1255` | v3.0.0:createBranch |  |
| OBPv2.1.0-updateConsumerRedirectUrl | PUT | `/management/consumers/CONSUMER_ID/consumer/redirect_url` | `Http4s210.scala:1291` | v5.1.0:updateConsumerRedirectURL / v5.1.0:updateConsumerRedirectURL / v7.0.0:updateConsumerRedirectURL | entitlement/user/consumer |
| OBPv2.1.0-getMetrics | GET | `/management/metrics` | `Http4s210.scala:1332` | v5.1.0:getMetrics / v6.0.0:getMetrics / v6.0.0:getMetrics |  |

### v2.0.0 — 15 docs, 15 handlers, about 533 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv2.0.0-root | GET | `/root` | `Http4s200.scala:100` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv2.0.0-corePrivateAccountsAllBanks | GET | `/my/accounts` | `Http4s200.scala:151` | v3.0.0:corePrivateAccountsAllBanks / v3.0.0:corePrivateAccountsAllBanks / v7.0.0:corePrivateAccountsAllBanks |  |
| OBPv2.0.0-getPrivateAccountsAtOneBank | GET | `/banks/BANK_ID/accounts` | `Http4s200.scala:225` | v4.0.0:getPrivateAccountsAtOneBank / v6.0.0:getAccountsAtBank / v6.0.0:getAccountsAtBank |  |
| OBPv2.0.0-privateAccountsAtOneBank | GET | `/banks/BANK_ID/accounts/private` | `Http4s200.scala:319` | v3.0.0:privateAccountsAtOneBank |  |
| OBPv2.0.0-getCoreAccountById | GET | `/my/banks/BANK_ID/accounts/ACCOUNT_ID/account` | `Http4s200.scala:679` | v4.0.0:getCoreAccountById / v6.0.0:getCoreAccountByIdV600 / v6.0.0:getCoreAccountByIdV600 |  |
| OBPv2.0.0-getCoreTransactionsForBankAccount | GET | `/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions` | `Http4s200.scala:715` | v3.0.0:getCoreTransactionsForBankAccount |  |
| OBPv2.0.0-accountById | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account` | `Http4s200.scala:748` | v4.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull |  |
| OBPv2.0.0-getPermissionForUserForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID` | `Http4s200.scala:829` | v3.0.0:getPermissionForUserForBankAccount | entitlement/user/consumer |
| OBPv2.0.0-createAccount | PUT | `/banks/BANK_ID/accounts/NEW_ACCOUNT_ID` | `Http4s200.scala:874` | v5.0.0:createAccount / v5.0.0:createAccount / v7.0.0:createAccountWithIdV700 |  |
| OBPv2.0.0-createCustomer | POST | `/banks/BANK_ID/customers` | `Http4s200.scala:1072` | v5.0.0:createCustomer / v6.0.0:createCustomer / v6.0.0:createCustomer |  |
| OBPv2.0.0-getCustomers | GET | `/users/current/customers` | `Http4s200.scala:1598` | v3.0.0:getCustomersForUser | entitlement/user/consumer |
| OBPv2.0.0-getCurrentUser | GET | `/users/current` | `Http4s200.scala:1144` | v3.0.0:getCurrentUser / v6.0.0:getCurrentUser / v7.0.0:getCurrentUser | entitlement/user/consumer |
| OBPv2.0.0-createUserCustomerLinks | POST | `/banks/BANK_ID/user_customer_links` | `Http4s200.scala:1195` | v4.0.0:createUserCustomerLinks |  |
| OBPv2.0.0-getEntitlements | GET | `/users/USER_ID/entitlements` | `Http4s200.scala:1320` | v4.0.0:getEntitlements | entitlement/user/consumer |
| OBPv2.0.0-getAllEntitlements | GET | `/entitlements` | `Http4s200.scala:1394` | v3.1.0:getAllEntitlements | entitlement/user/consumer |

### v1.4.0 — 4 docs, 4 handlers, about 136 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv1.4.0-root | GET | `/root` | `Http4s140.scala:72` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv1.4.0-getBranches | GET | `/banks/BANK_ID/branches` | `Http4s140.scala:204` | v3.0.0:getBranches |  |
| OBPv1.4.0-getAtms | GET | `/banks/BANK_ID/atms` | `Http4s140.scala:248` | v5.1.0:getAtms |  |
| OBPv1.4.0-getProducts | GET | `/banks/BANK_ID/products` | `Http4s140.scala:289` | v5.0.0:getProducts / v6.0.0:getProductsV600 / v6.0.0:getProductsV600 |  |

### v1.3.0 — 1 docs, 1 handlers, about 28 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv1.3.0-root | GET | `/root` | `Http4s130.scala:65` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |

### v1.2.1 — 19 docs, 19 handlers, about 650 lines

| operation_id | verb | path | handler | served instead by (v5.1.0 / v6.0.0 / v7.0.0) | area |
|---|---|---|---|---|---|
| OBPv1.2.1-root | GET | `/root` | `Http4s121.scala:152` | v5.1.0:root / v6.0.0:root / v7.0.0:root |  |
| OBPv1.2.1-getBanks | GET | `/banks` | `Http4s121.scala:179` | v5.1.0:getBanks / v6.0.0:getBanks / v6.0.0:getBanks |  |
| OBPv1.2.1-getPrivateAccountsAllBanks | GET | `/accounts` | `Http4s121.scala:248` | v2.0.0:getPrivateAccountsAllBanks |  |
| OBPv1.2.1-publicAccountsAllBanks | GET | `/accounts/public` | `Http4s121.scala:309` | v2.0.0:publicAccountsAllBanks |  |
| OBPv1.2.1-getPrivateAccountsAtOneBank | GET | `/banks/BANK_ID/accounts` | `Http4s121.scala:341` | v4.0.0:getPrivateAccountsAtOneBank / v6.0.0:getAccountsAtBank / v6.0.0:getAccountsAtBank |  |
| OBPv1.2.1-privateAccountsAtOneBank | GET | `/banks/BANK_ID/accounts/private` | `Http4s121.scala:372` | v3.0.0:privateAccountsAtOneBank |  |
| OBPv1.2.1-publicAccountsAtOneBank | GET | `/banks/BANK_ID/accounts/public` | `Http4s121.scala:403` | v2.0.0:publicAccountsAtOneBank |  |
| OBPv1.2.1-accountById | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account` | `Http4s121.scala:432` | v4.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull / v6.0.0:getPrivateAccountByIdFull |  |
| OBPv1.2.1-updateAccountLabel | POST | `/banks/BANK_ID/accounts/ACCOUNT_ID` | `Http4s121.scala:478` | v4.0.0:updateAccountLabel |  |
| OBPv1.2.1-getViewsForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/views` | `Http4s121.scala:524` | v5.0.0:getViewsForBankAccount | entitlement/user/consumer |
| OBPv1.2.1-createViewForBankAccount | POST | `/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/views` | `Http4s121.scala:580` | v3.0.0:createViewForBankAccount | entitlement/user/consumer |
| OBPv1.2.1-updateViewForBankAccount | PUT | `/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/views/CUSTOM_VIEW_ID` | `Http4s121.scala:645` | v3.0.0:updateViewForBankAccount | entitlement/user/consumer |
| OBPv1.2.1-getPermissionsForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/permissions` | `Http4s121.scala:747` | v2.0.0:getPermissionsForBankAccount | entitlement/user/consumer |
| OBPv1.2.1-getPermissionForUserForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID` | `Http4s121.scala:780` | v3.0.0:getPermissionForUserForBankAccount | entitlement/user/consumer |
| OBPv1.2.1-getOtherAccountsForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts` | `Http4s121.scala:978` | v3.0.0:getOtherAccountsForBankAccount |  |
| OBPv1.2.1-getOtherAccountByIdForBankAccount | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID` | `Http4s121.scala:1005` | v3.0.0:getOtherAccountByIdForBankAccount |  |
| OBPv1.2.1-getTransactionsForBankAccount | GET | `/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/TRANSACTIONS_VIEW_ID/transactions` | `Http4s121.scala:1843` | v3.0.0:getTransactionsForBankAccount / v6.0.0:getTransactionsForBankAccount / v6.0.0:getTransactionsForBankAccount |  |
| OBPv1.2.1-getTransactionByIdForBankAccount | GET | `/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/TRANSACTIONS_VIEW_ID/transactions/TRANSACTION_ID/transaction` | `Http4s121.scala:1879` | v3.1.0:getTransactionByIdForBankAccount |  |
| OBPv1.2.1-bankById | GET | `/banks/BANK_ID` | `Http4s121.scala:215` | v5.0.0:getBank / v6.0.0:getBank / v6.0.0:getBank |  |

## Helper code used only by set O

Only the 7 private helpers in the table are commented out. The 13 public ones (`ownEntitlement`, `commonTxReqErrors` and the JSONFactory functions) are kept, because dynamic Scala code (connector methods, dynamic endpoints and dynamic resource docs) is compiled at run time and may call them; the compiler of this repository never sees that code. `DynamicCodePublicHelpersTest` compiles dynamic Scala code that refers to each of the 13, so commenting one out, renaming it or changing its parameter types fails that suite.

Found by a fixpoint over every member declared in the old version folders and every `NewStyle.function` method. A symbol counts when each reference to it, apart from its declaration, is inside an O handler, an O ResourceDoc block or another such symbol. Tests are not counted as users, because a test that only covers O goes with it.

| Symbol | Declared at | Used by |
|---|---|---|
| `def ownEntitlement` | `NewStyle.scala:1060` | createConsumer |
| `def publicBankAccountsListToJson` | `Http4s121.scala:120` | publicAccountsAllBanks, publicAccountsAtOneBank |
| `def createTransactionsJSON` | `JSONFactory1.2.1.scala:508` | getTransactionsForBankAccount |
| `def createOtherBankAccountsJSON` | `JSONFactory1.2.1.scala:675` | getOtherAccountsForBankAccount |
| `def createAtmsJson` | `JSONFactory1_4_0.scala:290` | getAtms |
| `val createUserCustomerLinksrequiredEntitlementsText` | `Http4s200.scala:89` | v2.0.0:createUserCustomerLinks |
| `val commonTxReqErrors` | `Http4s210.scala:237` | v2.1.0:createTransactionRequestSandboxTan |
| `def createTransactionRequestImpl` | `Http4s210.scala:297` | createTransactionRequest |
| `def answerChallengeImpl` | `Http4s210.scala:507` | answerTransactionRequestChallenge |
| `def createConsumerJSONs` | `JSONFactory2.1.0.scala:517` | getConsumers |
| `def createViewImpl` | `Http4s220.scala:202` | createViewForBankAccount |
| `def updateViewImpl` | `Http4s220.scala:259` | updateViewForBankAccount |
| `def createCounterpartyImpl` | `Http4s220.scala:1009` | createCounterparty |
| `def createCounterpartiesJSON` | `JSONFactory2.2.0.scala:524` | getExplicitCounterpartiesForAccount |
| `def transformV220ToBranch` | `JSONFactory2.2.0.scala:716` | createBranch |
| `def transformToAtmFromV220` | `JSONFactory2.2.0.scala:742` | createAtm |
| `def createAtmsJsonV300` | `JSONFactory3.0.0.scala:1078` | getAtms |
| `def transformToAtmFromV300` | `JSONFactory3.0.0.scala:1096` | createAtm |
| `def createAtmsJsonV400` | `JSONFactory4.0.0.scala:1905` | getAtms |
| `def transformToAtmFromV400` | `JSONFactory4.0.0.scala:1909` | createAtm, updateAtm |

No connector method becomes unused. Of the 57 connector and `NewStyle.function` methods that O handlers call, only `NewStyle.function.ownEntitlement` has no other caller.

Uncertain: references were matched by name, so two symbols with the same name in different files are treated as one. That can only keep a symbol, never drop one wrongly. Symbols reached through reflection or string names would not be seen. Dynamic code is why the public helpers are kept.

### Already unused, outside this task

The same scan found 49 members that nothing references at all today, for example the private `allOwnRoutes` value in eight version files. They are not reachable from any version, kept or not, so they are not part of set O. They are listed here only for a later clean-up:

`NewStyle.scala:2199 getModeratedAccountAttributesByAccount`, `NewStyle.scala:3707 getDynamicEntityByEntityName`, `NewStyle.scala:4645 connectorMethodExists`, `JSONFactory1.2.scala:73 hide_metadata_if_alias`, `JSONFactory1.2.scala:77 can_see_transaction_label`, `Http4s121.scala:99 fail400`, `JSONFactory1.2.1.scala:581 createLocationPlainJSON`, `JSONFactory1_4_0.scala:757 isGeoJSONMultiPolygonStructure`, `JSONFactory1_4_0.scala:1173 transforOldTransactionRequest`, `JSONFactory2.0.0.scala:786 createMeetingJSONs`, `JSONFactory2.0.0.scala:827 createBasicAccountsJson`, `Http4s210.scala:460 answerChallengeCommonErrors`, `Http4s210.scala:466 answerChallengeTags`, `JSONFactory2.1.0.scala:303 AtmJsonPut`, `JSONFactory2.1.0.scala:312 AtmJsonPost`, `JSONFactory2.1.0.scala:326 ProductJsonPut`, `JSONFactory2.1.0.scala:541 createCounterpartyMetaDataJSON`, `JSONFactory2.1.0.scala:674 toBranchJsonPost`, `JSONFactory2.1.0.scala:702 transformToLocation`, `JSONFactory2.2.0.scala:125 AccountsJSONV220`, `JSONFactory2.2.0.scala:678 createUserCustomerViewJsonV220`, `JSONFactory3.1.0.scala:294 PostCustomerResponseJsonV310`, `JSONFactory3.1.0.scala:708 ModeratedCoreAccountJsonV310`, `JSONFactory3.1.0.scala:778 AccountBalancesJson`, `JSONFactory3.1.0.scala:792 createCreditLimitOrderResponseJson`, `JSONFactory3.1.0.scala:795 getCreditLimitOrderResponseJson`, `JSONFactory3.1.0.scala:798 getCreditLimitOrderByRequestIdResponseJson`, `JSONFactory3.1.0.scala:1065 createObpApiLoopbackJson`, `JSONFactory3.1.0.scala:1266 createAccountAttributesJson`, `JSONFactory3.1.0.scala:1301 createConsentsJsonV310`, `JSONFactory4.0.0.scala:316 AccountBalancesJsonV400`, `JSONFactory4.0.0.scala:610 ChallengeJson`, `JSONFactory4.0.0.scala:1272 otpViaWebFormPath`, `JSONFactory4.0.0.scala:1713 createConsentsJsonV400`, `Http4s500.scala:2439 wrappedRoutesV500ServicesWithJsonNotFound`, `JSONFactory5.0.0.scala:210 PostConsentRequestResponseJson`, `JSONFactory5.0.0.scala:302 UpdatedPhysicalCardJsonV500`, `JSONFactory5.0.0.scala:353 UpdatePhysicalCardJsonV500`, and `JSONFactory1_4_0.scala:1053 getTransactionRequestBodyFromJson`, `JSONFactory2.0.0.scala:373 getTransactionRequestBodyFromJson`, `JSONFactory2.2.0.scala:128 AccountJSONV220` (each referenced only by an unused symbol of the same or another name), and `allOwnRoutes` in `Http4s130`, `Http4s140`, `Http4s200`, `Http4s210`, `Http4s220`, `Http4s300`, `Http4s310` and `Http4s400`.

## Tests

These test files name an O endpoint in a scenario tag, so they cover it directly. In each version's commit, the scenarios that call an O endpoint are commented out. Scenarios in the same file that cover K endpoints stay.

| Test file | O endpoints it names |
|---|---|
| `code/api/DirectLoginTest.scala` | v2.0.0 getCurrentUser |
| `code/api/v2_2_0/API2_2_0Test.scala` | v2.2.0 getViewsForBankAccount, v2.2.0 createViewForBankAccount, v2.2.0 updateViewForBankAccount |
| `code/api/v2_2_0/BankTest.scala` | v2.2.0 createBank |
| `code/api/v3_0_0/AccountTest.scala` | v3.0.0 getCoreAccountById, v3.0.0 getFirehoseAccountsAtOneBank |
| `code/api/v3_0_0/FirehoseTest.scala` | v3.0.0 getFirehoseAccountsAtOneBank |
| `code/api/v3_0_0/UserTest.scala` | v3.0.0 getUser, v3.0.0 getUserByUserId, v3.0.0 getUserByUsername, v3.0.0 getUsers |
| `code/api/v3_0_0/ViewsTests.scala` | v3.0.0 getViewsForBankAccount |
| `code/api/v3_1_0/AccountTest.scala` | v3.1.0 getBankAccountsBalances, v3.1.0 createAccount, v3.0.0 getPrivateAccountById, v2.0.0 getPrivateAccountsAtOneBank |
| `code/api/v3_1_0/CardAttributeTest.scala` | v3.1.0 addCardForBank |
| `code/api/v3_1_0/CardTest.scala` | v3.1.0 addCardForBank |
| `code/api/v3_1_0/ConsentTest.scala` | v3.0.0 getUserByUserId |
| `code/api/v3_1_0/ConsumerTest.scala` | v3.1.0 getConsumer, v3.1.0 getConsumers |
| `code/api/v3_1_0/CustomerTest.scala` | v3.1.0 createCustomer |
| `code/api/v3_1_0/GetAdapterInfoTest.scala` | v3.1.0 getAdapterInfo |
| `code/api/v3_1_0/ProductAttributeTest.scala` | v3.1.0 getProductAttribute, v3.1.0 createProductAttribute, v3.1.0 updateProductAttribute |
| `code/api/v3_1_0/ProductTest.scala` | v3.1.0 getProduct, v3.1.0 getProducts, v3.1.0 createProduct |
| `code/api/v3_1_0/RateLimitTest.scala` | v3.1.0 callsLimit |
| `code/api/v3_1_0/SystemViewsTests.scala` | v3.1.0 getSystemView, v3.1.0 deleteSystemView, v3.1.0 updateSystemView, v3.1.0 createSystemView |
| `code/api/v3_1_0/TransactionRequestTest.scala` | v3.1.0 getTransactionRequests |
| `code/api/v3_1_0/TransactionTest.scala` | v3.1.0 getTransactionRequests, v3.0.0 getCoreAccountById, v2.2.0 createCounterparty |
| `code/api/v3_1_0/UserAuthContextTest.scala` | v3.1.0 getUserAuthContexts, v3.1.0 createUserAuthContext |
| `code/api/v3_1_0/UserAuthContextUpdateTest.scala` | v3.1.0 createUserAuthContextUpdateRequest, v3.1.0 answerUserAuthContextUpdateChallenge |
| `code/api/v4_0_0/AccountBalanceTest.scala` | v4.0.0 getBankAccountsBalancesForCurrentUser, v4.0.0 getBankAccountBalancesForCurrentUser |
| `code/api/v4_0_0/AtmsTest.scala` | v4.0.0 getAtms, v4.0.0 getAtm, v4.0.0 createAtm, v4.0.0 deleteAtm, v4.0.0 updateAtm |
| `code/api/v4_0_0/BankTests.scala` | v4.0.0 createBank |
| `code/api/v4_0_0/ConsentTests.scala` | v4.0.0 getConsents |
| `code/api/v4_0_0/CustomerAttributesTest.scala` | v4.0.0 getCustomersByAttributes |
| `code/api/v4_0_0/CustomerTest.scala` | v4.0.0 createCustomer |
| `code/api/v4_0_0/ForceErrorValidationTest.scala` | v3.1.0 createCustomer |
| `code/api/v4_0_0/ProductTest.scala` | v4.0.0 getProducts, v4.0.0 getProduct, v4.0.0 createProduct |
| `code/api/v5_0_0/AccountTest.scala` | v3.0.0 getPrivateAccountById, v2.0.0 getPrivateAccountsAtOneBank |
| `code/api/v5_0_0/ProductTest.scala` | v4.0.0 getProducts, v4.0.0 getProduct |
| `code/api/v5_1_0/ConsentObpTest.scala` | v3.0.0 getUserByUserId |

Other suites call O endpoints by URL without a tag, mostly in the old folders, for example `v1_2_1/API1_2_1Test.scala` and the v2.x tests. They are found by running each version's suites after commenting out its handlers. A failure that comes from an O endpoint is commented out with the code. A failure in a K test means a K dependency was touched, and that piece is restored.
