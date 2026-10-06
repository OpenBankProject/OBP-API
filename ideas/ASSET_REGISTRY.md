# Draft v4: Asset Registry

**Status**: partly implemented. The registry exists, is seeded, and decides which currency codes are accepted and their decimal places, giving the same answers as the built-in list it replaced. The only changes callers can see so far are two attribute types (`DECIMAL`, `BOOLEAN`) and an amount too large to store being refused instead of wrapped. The progress section below lists what is built and what is next; section 12 lists the open questions.

**Changes in v4 (2026-10-05)**: adds the progress section. `chain_scheme` names the network as well as the chain (§2); questions 10 and 11 are new. Records what the implementation settled or exposed: the seed has no switch (question 6), Mapper cannot declare the partial unique indexes (§2), the 4–10 character rule for non-seeded codes is not enforced yet (§3), the test setup empties the registry between tests (§11), and the unused `MappedCurrency` table (§11). Code references are brought up to date.

**Changes in v3 (2026-10-05)**: amounts are stored exactly, as `DECIMAL(38, 18)` in the asset's main unit, instead of as `Long` minor units (§5). The 9-decimal cap and rescaling at the chain boundary are gone: an on-chain amount is always held exactly, never rounded or cut off. Precision corrections become metadata changes instead of rescaling migrations (§7). `ADA` is the single Cardano code at 6 decimals and `ETH` the single Ethereum code at 18; `lovelace` and `wei` stop being currency codes (§7, §10). Currency codes are case-insensitive (§4). Open questions 5, 7 and 8 are settled.

**Changes in v2**: the precision defect list now comes from the XML rather than memory, and includes currencies whose ISO precision is *lower* than OBP's. The migration handles rescaling downwards. Issuer-less assets are administered at the `SYS` bank instead of through instance-wide roles. Bank-scoped writes check the issuer. Status changes get their own history table. An optional `dti` column is added. A section of implementation notes is added.

## Background

OBP-API assumes every `currency` value is an ISO 4217 code with a decimal precision known in code. This blocks accounts denominated in issued assets (tokenised deposits, stablecoins, debt securities, fund shares) and also produces wrong precision for some existing ISO codes.

Current state:

| Concern | Where | Behaviour |
|---|---|---|
| Validity of a `currency` value | `APIUtil.isValidCurrencyISOCode` (`code/api/util/APIUtil.scala:847`), 29 call sites | Must appear in `media/xml/ISOCurrencyCodes.xml`, or be `XBT`. Case-sensitive: `eur` is rejected |
| Decimal places | `Helper.currencyDecimalPlaces` (`code/util/Helper.scala:158`) | Hard-coded: CZK/JPY/KRW → 0, KWD/OMR → 3, everything else → 2. Case-sensitive: `jpy` gets 2 |
| Amount → stored value | `Helper.convertToSmallestCurrencyUnits` (`code/util/Helper.scala:175`) | `(amount * 10^dp)`, excess decimals cut off silently. An amount too large for a `Long` is refused (it used to wrap into an unrelated, often negative, number) |
| Stored balances / amounts | `MappedBankAccount.accountBalance`, `MappedTransaction.amount`, `MappedTransaction.newAccountBalance`, standing orders, `bankaccountbalance` | `Long`, minor units |
| Currency column width | `MappedBankAccount.accountCurrency`, `MappedTransaction.currency` | `MappedString(10)` |
| Product fee amount | `ProductFee.Amount` | `MappedDecimal(DECIMAL128, 2)`, fixed 2 decimals regardless of currency |

`ISOCurrencyCodes.xml` has 283 entries covering 183 distinct codes, and carries `CcyMnrUnts` (the ISO minor-unit count) for every entry. The code does not read it. The file is not pure ISO 4217: it also contains four crypto entries that the Cardano and Ethereum transaction request flows rely on (`LocalMappedConnector.scala:216`, `LocalMappedConnectorInternal.scala:1354`, `:1429`):

| Code | `CcyMnrUnts` in XML | Note |
|---|---|---|
| `ada` | 6 | Lowercase |
| `lovelace` | 0 | Lowercase, 8 characters |
| `ETH` | 18 | Exact wei amounts do not fit in a `Long` (§5) |
| `wei` | 0 | Lowercase |

Existing precision defects, independent of tokenisation (ISO value from the XML compared with `currencyDecimalPlaces`):

| ISO precision vs OBP | Codes |
|---|---|
| ISO 3, OBP 2 | BHD, IQD, JOD, LYD, TND |
| ISO 4, OBP 2 | CLF, UYW |
| ISO 2, OBP 0 | CZK |
| ISO 0, OBP 2 | BIF, CLP, DJF, GNF, ISK, KMF, PYG, RWF, UGX, UYI, VND, VUV, XAF, XOF, XPF |
| ISO `N.A.`, OBP 2 | XAU, XAG, XPT, XPD (precious metals); XDR, XBA, XBB, XBC, XBD, XSU, XUA (fund and accounting units); XTS (testing), XXX (no currency) |
| Not ISO, OBP 2 | XBT (8 in practice), ETH (18), ada (6) |

MRU and MGA are non-decimal currencies (a `TODO` in `Helper.scala` already notes this); the registry stores them with the precision ISO assigns.

This document proposes an asset registry that replaces both the ISO check and the hard-coded decimal table, and defines how it relates to Products and Accounts.

## Progress

The work is ordered so that each step changes nothing a caller can see until the step that deliberately does, and each answers a question a later step depends on.

| Step | What | State |
|---|---|---|
| 1 | Tests that describe today's currency behaviour: `CurrencyHandlingTest` (`code.util`) | Done (`26e9f9b39`) |
| 2 | Read-only report of stored amounts per currency code: `scripts/asset_registry_currency_report.sql` | Done, not committed |
| 3 | `DECIMAL` and `BOOLEAN` attribute types (§9) | Done (`26e9f9b39`) |
| 4 | `Asset` and `AssetStatusHistory` tables, provider and boot seed (§2, §6, §7) | Done, not committed |
| 5 | Read-only endpoints `GET /assets`, `GET /assets/ASSET_CODE`, reverse chain lookup (§8), and the Glossary entry (§11) | Done, not committed |
| 6 | `isValidCurrencyISOCode` and `currencyDecimalPlaces` read from the registry (§4) | Done, not committed |
| 7 | Case-insensitive codes (§4): codes upper-cased in requests by `ResourceDocMiddleware`, compared ignoring case | Done, not committed |
| Later | Rejecting excess decimals (§4), write endpoints (§8), amount storage (§5, §7 Part A), precision corrections (§7 Part B), folding `lovelace` and `wei` (§7 Part C, §10) | Each changes behaviour or waits on an open question |

**Step 1.** `CurrencyHandlingTest` is a pure unit test (no server, no database). It asserts correct behaviour only. The three known defects are written as the correct expectation inside `pendingUntilFixed`, so they report as pending now and fail, demanding the wrapper's removal, once fixed:
- codes are not accepted in every letter case;
- the codes in the precision table above do not get their ISO minor units;
- `currencyDecimalPlaces` depends on letter case.

Excess decimals being cut off is not tested, because how they should be rejected (§4) is not decided yet.

The same change made `convertToSmallestCurrencyUnits` refuse an amount too large for a `Long`.

**Step 2.** The report runs in one transaction that is made read-only before it reads anything and rolled back at the end. For each currency code and amount column it reports:
- the row count;
- codes that are not upper case;
- rows that would block lowering a precision (question 9);
- `lovelace` and `wei` rows with a fractional amount;
- the largest amount;
- balance records whose account is missing.

On a local sandbox database every check came back clean: 12 codes in use, the largest amount 40,020,000 SGD. That says little about a real deployment, so it should be run against a copy of a production database before §7 is written.

**Step 3.** All eleven attribute-type enumerations (`ProductAttributeType`, `AccountAttributeType` and the rest) and `AttributeType`, used by Attribute Definitions, accept `DECIMAL` and `BOOLEAN`. An Attribute Definition can be for any category, so a type only some enumerations accepted would be unusable for the others. The 26 error messages and 17 ResourceDoc descriptions that listed the types by hand now read from one place, `code.api.util.AttributeTypeDocs`; `AttributeTypeDocsTest` checks every enumeration still has exactly those names. As before, a value is stored as text and not checked against its type.

**Step 4.** `code.asset` holds the `Asset` and `AssetStatusHistory` Mapper entities, the provider `Assets` and the seed `AssetSeed`:
- **`Assets` lookups** find an asset by code, ignoring case.
- **`Assets` writes** refuse a row that breaks the §2 and §3 rules. A code must be 3 to 10 letters or digits, stored upper case. The type and status must be known values. Decimal places must be 0 to 18. Issued types need an issuer, and issuer-less types must not have one.
- **`AssetSeed`** runs at every boot from `Boot.scala`. It inserts the 182 assets of §7 at today's `currencyDecimalPlaces` and never changes an existing row.
- **`AssetSeedTest`** (`code.asset`, CI shard 8) checks the seed against `currencyDecimalPlaces` code by code and checks every rule.

**Step 5.** `code.api.v7_0_0.Http4s700Assets` serves the three read endpoints of §8, with no authentication and no Role:
- **`GET /assets`** filters by `asset_type`, `status`, `chain_scheme` and `issuer_bank_id`. The first three match ignoring letter case. It pages with `limit` (1 to 500, default 500, enough for every seeded asset) and `offset`, and reports `pagination.total`. A bad value for any of them is refused with 400 (OBP-30581) rather than ignored.
- **`GET /assets/ASSET_CODE`** ignores letter case. An unknown code is 404 (OBP-30579).
- **`GET /assets/chain/CHAIN_SCHEME/CHAIN_ASSET_ID`** matches the scheme ignoring case and the id exactly; no match is 404 (OBP-30580). It finds nothing on a real deployment until the write endpoints can set a chain identity.

The JSON is in `JSONFactory700Assets.scala`, the endpoints carry the new `Asset` tag, and the Glossary has an "Asset" entry. `AssetsEndpointTest` (CI shard 6) covers each endpoint, the filters, paging, the 400s and the 404s.

**Step 6.** `code.asset.AssetLookup` answers `APIUtil.isValidCurrencyISOCode` and `Helper.currencyDecimalPlaces` from the registry. The 39 call sites are unchanged. The answers are the ones OBP gave before:
- Codes are matched exactly as written, so `eur` is still refused and `jpy` still gets 2 decimal places. Case-insensitivity remains a later step.
- Status is not checked: a suspended code is still accepted until `isUsableAsset` (§4).
- `ada`, `lovelace` and `wei`, which the built-in list accepts but the registry does not hold under those spellings, stay accepted until §7 Part C.
- A code the registry does not hold gets the built-in decimal places.
- The one visible change: `ADA` in upper case is now accepted, as well as `ada`, because the registry holds it.

The registry is read once into memory and read again after any write through `Assets`. Every node seeds the same rows, and nothing else writes assets yet, so no cache expiry is needed; the write endpoints (§8) will need one across nodes. If the registry cannot be read (no database) or is empty (before the boot seed), the built-in list answers and nothing is kept. That list survives as `APIUtil.builtInCurrencyCodes` and `Helper.builtInCurrencyDecimalPlaces`; the seed reads it, so an empty database is still seeded at today's precisions.

`AssetLookupTest` (`code.asset`, CI shard 8) checks that the seeded registry gives the built-in answer for every code, that a newly registered asset is accepted at once with its own decimal places, and that an empty registry falls back. Suites that rewrite the registry restore the seeded one when they end (`RestoresSeededAssetRegistry`), and the test database resets also clear the in-memory copy; otherwise a later suite in the same JVM, such as `CurrencyHandlingTest`, would read a registry holding only test assets. Run with `FundsAvailableTest`, both `TransactionRequestsTest` suites, the v4.0.0 `AccountTest` and `CardanoTransactionRequestTest`: 104 passed, 3 pending (the known defects), none failed.


**Step 7.** Currency codes are case-insensitive. `code.asset.CurrencyCodes` holds the rule:
- **Requests.** Once a static ResourceDoc has matched, `ResourceDocMiddleware.withCurrencyCodesUpperCased` upper-cases the currency codes in the request before the endpoint runs. In the JSON body it changes the string fields that the endpoint's example body uses for a currency code (a name ending in `currency` or `currency_code`, ignoring case and underscores); it edits the text, so amounts keep their exact form. In the query string it changes parameters named the same way. Only values that look like a code (2 to 12 letters or digits) are touched. Dynamic Entity and Dynamic Endpoint requests are left as sent.
- **Path segments.** The only currency codes in a URL path are those of `GET /banks/BANK_ID/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE`, which already upper-cases them, and `GET /assets/ASSET_CODE`, which already ignores case.
- **Stored codes.** About ten comparisons against a stored currency (transaction request against account currency, funds available, settlement accounts, bulk payments, FX) use `CurrencyCodes.same`, so rows written before this rule in another case still match. `AssetLookup` ignores case, so `jpy` gets 0 decimal places. `ada` is no longer a legacy spelling, because it matches `ADA`.
- **Stored rows.** The runOnce migration `upperCaseStoredCurrencyCodes` (`MigrationOfCurrencyCodesUpperCase`) rewrites the codes in every currency column in upper case, `ada` to `ADA` among them, so database queries that select by currency (for example the FX rate lookup) find old rows too. It leaves codes inside stored JSON alone, and a `mappedcurrency` row whose upper-case twin exists. `lovelace` and `wei` become `LOVELACE` and `WEI`; their amounts are converted later (§7 Part C). `CurrencyCodesUpperCaseMigrationTest` covers it.

`CurrencyCodesTest` (unit) covers the rule. The two letter-case defects in `CurrencyHandlingTest` are no longer pending. `AssetLookupTest`, `FundsAvailableTest` (lower-case `eur` now gives 200) and `ExchangeRateTest` (an FX rate created with `eur`/`usd` is stored as `EUR`/`USD` and read back with lower-case path segments) were updated.

---

## 1. Model

Three layers, each with one responsibility:

| Layer | Scope | Answers | Mutability |
|---|---|---|---|
| **Asset** (new) | Global | What are the units? How many decimals? Who issued them? Which on-chain asset are they? | Identity and precision immutable |
| **Product** (existing) | Bank | What does the bank offer, on what terms? (ISIN, coupon, maturity, documents, fees) | Editable |
| **Account** (existing) | Bank | Who holds how many units, under which product? | Balance changes via transactions |

Links:

- `account.currency` → `asset.asset_code` (required; replaces the ISO check).
- `account.product_code` → product at the **account's** bank (existing behaviour; e.g. a custody product at the holder's bank).
- `asset.issuer_bank_id` + `asset.issuer_product_code` → the product at the **issuer's** bank that describes the instrument (optional).

A holder at bank B holding units issued by bank A has an account at B with `currency = <asset_code>` and `product_code = <B's custody product>`. The instrument terms are found via the asset's issuer product at A, not via the holder's account product.

**Administering bank.** Every asset is administered at exactly one bank, so every write role can be bank-scoped (in line with retiring any-bank roles). For issued types the administering bank is the issuer, `issuer_bank_id`. Assets with no issuer (`FIAT`, `PRECIOUS_METAL`, `CRYPTO`, and the fund and accounting units) are administered at the `SYS` bank, which is an ordinary bank id. `issuer_bank_id` stays null for them: `SYS` administers the row, it does not issue the asset. Every deployment has a `SYS` bank, so seeding can rely on it being there.

## 2. `asset` table

| Column | Type | Required | Mutable | Notes |
|---|---|---|---|---|
| `asset_id` | UUID | yes | no | Internal key |
| `asset_code` | string(10) | yes | no | Unique, ignoring case. The value that appears in `currency` fields. Stored uppercase: `[A-Z0-9]{3,10}`. The legacy lowercase `ada` is seeded as `ADA`; `lovelace` and `wei` are not seeded (§7) |
| `asset_type` | enum | yes | no | See §3 |
| `name` | string(125) | yes | yes | Display name |
| `decimal_places` | int | yes | no (except via the §7 migration) | `0..18`, the asset's true precision; see §5 |
| `issuer_bank_id` | string | for issued types | no | Null for issuer-less types; such assets are administered at `SYS` (§1) |
| `issuer_product_code` | string(50) | no | yes | Product at `issuer_bank_id` describing the instrument |
| `chain_scheme` | string | no | set-once | The chain and network, always both: `CARDANO_MAINNET`, `CARDANO_PREPROD`, `ETHEREUM_SEPOLIA`, … (see below) |
| `chain_asset_id` | string | no | set-once | Cardano: `<policy_id>.<asset_name_hex>`; Ethereum: the token's contract address |
| `dti` | string(9) | no | set-once | ISO 24165 Digital Token Identifier, when one has been assigned |
| `status` | enum | yes | yes | `ACTIVE`, `SUSPENDED`, `RETIRED`, see §6 |
| `created_by_user_id` | string | yes | no | |
| `created_at` / `updated_at` | timestamp | yes | — | |

Indexes: unique `(asset_code)`; unique `(chain_scheme, chain_asset_id)` where not null; unique `(dti)` where not null; index `(issuer_bank_id)`.

*As built (step 4)*: the table is `Asset`, with Mapper column names (`AssetCode`, `DecimalPlaces`, `CreationDate`, `LastUpdate` and so on) and a unique index on `AssetId` as well. Optional columns hold an empty string rather than null when unset. The two partial unique indexes are not declared: they must skip unset values, and Mapper can only declare a plain unique index, which would treat every unset (empty) value as a duplicate of every other. Nothing writes these columns yet; the write endpoints (§8) must check uniqueness themselves, or a migration must create the partial indexes in SQL for each database.

**`chain_scheme` names the network as well as the chain.** The same contract address can exist on Ethereum mainnet, on a test network such as Sepolia, and on networks built on Ethereum such as Polygon or Arbitrum, each time as a different token; Cardano has the same split between mainnet and its preprod and preview test networks. So `(chain_scheme, chain_asset_id)` is only a unique key if the scheme says which network. The form is `<CHAIN>_<NETWORK>`, upper case, and the network is always written, even for mainnet: OBP does not know which network it is on (it is whatever node `ethereum.rpc.url` or the Cardano wallet API points at), so a bare `CARDANO` would be mainnet on one deployment and preprod on another. A network built on Ethereum is its own chain: `POLYGON_MAINNET`, `ARBITRUM_MAINNET`. The accepted values are a fixed list in code, extended when OBP supports another network.

This departs from the account routing vocabulary, where a Cardano address uses the bare scheme `CARDANO` (`RoutingSchemeValidation`, `code/routingscheme/RoutingScheme.scala`). Whether account routings should move to the same network-qualified names is question 10.

`chain_scheme` / `chain_asset_id` are set-once rather than immutable-at-create because a policy id may not exist until the first mint. Once set, they cannot change: reconciliation between OBP balances and on-chain supply depends on the mapping being fixed. `dti` is set-once for the same reason: a DTI is assigned after the token exists.

## 3. `asset_type`

| Value | Issuer | Administered at | Created by |
|---|---|---|---|
| `FIAT` | none | `SYS` | Seed only (§7) |
| `PRECIOUS_METAL` | none | `SYS` | Seed only (§7) |
| `ACCOUNTING_UNIT` | none | `SYS` | Seed only (§7): XDR, XBA–XBD, XSU, XUA, XTS, XXX |
| `CRYPTO` | none | `SYS` | Seed, or `CanCreateAsset` at `SYS` |
| `DEPOSIT_TOKEN` | bank | issuer | `CanCreateAsset` at the issuer |
| `STABLECOIN` | bank | issuer | `CanCreateAsset` at the issuer |
| `DEBT_SECURITY` | bank | issuer | `CanCreateAsset` at the issuer |
| `EQUITY` | bank | issuer | `CanCreateAsset` at the issuer |
| `FUND_SHARE` | bank | issuer | `CanCreateAsset` at the issuer |
| `OTHER` | bank | issuer | `CanCreateAsset` at the issuer |

Codes for non-seeded types must be 4–10 characters, so they cannot collide with a current or future ISO 4217 code. This is not enforced yet: `Assets.createAsset` accepts 3 to 10 characters for every type, because the seed uses it for 3-letter ISO codes. The create endpoint (§8) must enforce it.

## 4. Validation changes

- **Currency codes are case-insensitive.** `eur`, `Eur` and `EUR` all name the same asset. Every input is normalised to the stored uppercase form at the API boundary, before validation, comparison or storage, so the rest of the code only ever sees the canonical code. Accepting lowercase without normalising is not enough: `"eur" != "EUR"` in the comparisons transaction requests make against the account's currency, and `currencyDecimalPlaces("jpy")` returns 2 today. Existing rows holding `ada` are rewritten to `ADA`; rows holding `lovelace` or `wei` are converted as described in §7. `FundsAvailableTest` (v3.1.0) currently asserts that `eur` is rejected with 400 and changes with this rule.
- `isValidCurrencyISOCode(code)` → `isUsableAsset(code)`: the asset exists and `status = ACTIVE`. All 29 call sites switch over; the old name stays as a deprecated alias for one release.
- Read paths (GET account, GET transactions) accept any existing asset regardless of status, so suspended or retired holdings remain visible.
- `currencyDecimalPlaces(code)` → `asset.decimal_places`, cached (TTL cache, invalidated on asset write).
- `convertToSmallestCurrencyUnits` already refuses an amount that does not fit in a `Long` (step 1), but it still cuts off excess decimals silently.
- **Amounts are never rounded or cut off.** An amount with more decimal places than the asset's `decimal_places` is rejected with 400. New error code: *invalid amount precision for asset* (number assigned at implementation). An amount that is too large for the storage column (§5) is rejected the same way.
- Transaction requests: `value.currency` must equal the from-account's `currency`. Cross-asset movement goes through an explicit FX/exchange type, never through implicit conversion.
- `fx.scala` fallback rates are unaffected; FX endpoints validate both legs with `isUsableAsset`.

## 5. Amount storage

**Rule: OBP holds every amount exactly.** Amounts are money; they are never rounded, cut off or rescaled to fit the storage.

Today amounts are stored as `Long` minor units (`(amount * 10^dp).toLong`). A `Long` holds at most about 9.22 × 10^18, which cannot hold exact amounts for high-precision assets. At 18 decimal places (ETH, and many Ethereum tokens such as DAI) the largest balance a `Long` can hold is about 9.2 ETH, while total ETH supply is about 1.2 × 10^26 wei. Any cap on `decimal_places` small enough to fit a `Long` means incoming on-chain amounts below the cap cannot be credited exactly, and OBP's ledger stops matching the chain. Rounding hides that; rejecting does not help either, because the funds are already in the bank's wallet.

So the amount columns change from `Long` minor units to **`DECIMAL(38, 18)` holding the amount in the asset's main unit** (`12.45` EUR, not `1245` cents):

| Column | Today | After |
|---|---|---|
| `MappedBankAccount.accountBalance` | `MappedLong`, minor units | `DECIMAL(38, 18)`, main unit |
| `MappedTransaction.amount`, `MappedTransaction.newAccountBalance` | `MappedLong`, minor units | same |
| `MappedStandingOrder.AmountValue` | `MappedLong`, minor units | same |
| `BankAccountBalance.BalanceAmount` | `MappedLong`, minor units | same |
| `ProductFee.Amount` | `MappedDecimal(DECIMAL128, 2)` | same |

- **Why main units.** The stored value no longer depends on `decimal_places`, so correcting an asset's precision (JOD 2 → 3) changes no stored data (§7). With minor units, the same stored number means a different amount at each precision.
- **Why `DECIMAL(38, 18)`.** 38 digits is the largest precision every supported database accepts (SQL Server's maximum). It leaves 20 digits before the decimal point (up to 10^20 − 1 main units) and 18 after, which covers ETH at full precision with total supply to spare. `decimal_places` is therefore capped at 18, the storage scale, and the asset's `decimal_places` (not the column) decides how many of those 18 places an amount may use.
- **The public model does not change.** `BankAccount.balance` and the transaction amount are already `BigDecimal` in `obp-commons` (`BankingModel.scala:213`); only the mapped storage and the two conversion helpers (`convertToSmallestCurrencyUnits`, `smallestCurrencyUnitToBigDecimal`) change, and the helpers can be removed.
- **Connector-sourced accounts** (non-mapped connectors) already report amounts as decimals and are unaffected.

Recommended `decimal_places`: the asset's real precision. 6 for `ADA` and most Cardano native assets (CIP-68 convention), 18 for `ETH`, 2 for deposit tokens mirroring a 2-decimal fiat, 0 for securities held in whole units.

## 6. Status

```
ACTIVE ⇄ SUSPENDED
ACTIVE → RETIRED
SUSPENDED → RETIRED
```

- `ACTIVE`: usable for account creation and transactions.
- `SUSPENDED`: no new accounts, no transaction requests; reads permitted. For regulatory freezes and incident response. Also the way a deployment switches off seeded codes it does not handle (§12, question 6).
- `RETIRED`: terminal. Matured bond, redeemed fund. Reads permitted.

Every status change writes a row to an `asset_status_history` table (built in step 4 as `AssetStatusHistory`; empty until an endpoint changes a status): `asset_id`, `from_status`, `to_status`, `reason` (free text, required), `changed_by_user_id`, `changed_at`. The metrics trail is not enough for this: metrics can be switched off (`write_metrics`), and they record API calls, not state transitions. A regulator asking "who froze this asset, when, and why" needs the history table.

## 7. Seeding and migration

At boot, the registry is seeded idempotently from `ISOCurrencyCodes.xml` (`FIAT`; `PRECIOUS_METAL` for XAU/XAG/XPT/XPD; `ACCOUNTING_UNIT` for the other `N.A.` codes) plus `CRYPTO` for `XBT`, `ADA` and `ETH`. `lovelace` and `wei` are not seeded: they are the smallest units of `ADA` and `ETH`, not separate assets (1 ADA = 10^6 lovelace, 1 ETH = 10^18 wei), and a ledger holds one code per asset. Seeding never overwrites an existing row.

*Built in step 4* (`code.asset.AssetSeed`): 182 assets, 179 from the file plus `XBT`, `ADA` and `ETH`, each at the precision `currencyDecimalPlaces` gives it today. That is also Part B step 1 below. A code listed for several countries takes the name of its first entry. The seed has no property to switch it off (question 6). If several instances boot at once, the unique index lets one insert win, and the others count the code as already present.

With amounts stored in main units (§5), the migration has two parts: convert storage once, then correct precisions as metadata.

**Part A: convert the amount columns.** For each column in the §5 table, in one `runOnce` migration per table, in a single transaction:

1. Drop any database view that depends on the table's amount columns (see §11). Recreate a view afterwards only if something still needs it.
2. Rename the existing `Long` column to `<column>_legacy_minor_units` (for example `accountbalance_legacy_minor_units`). This is the backup: the original stored values, untouched.
3. Add the new `DECIMAL(38, 18)` column under the original name, and fill it with `legacy_minor_units / 10^legacy_dp`, where `legacy_dp` is what `currencyDecimalPlaces` returns for the row's currency today. This is exact: the stored value was written at that precision, and dividing by a power of ten in decimal arithmetic loses nothing.
4. Verify every row before committing: `new_value * 10^legacy_dp = legacy_minor_units`, and no new value is null where the legacy one was not. Any mismatch rolls the whole table back, and the migration reports it.
5. Write the `legacy_dp` used for each currency into the migration log entry, so the backup can be read back without depending on the old hard-coded table.

After the migration, application code neither reads nor writes the `_legacy_minor_units` columns; rows created later leave them null. They are kept as a backup until a separate, later migration drops them, once the new columns have been in production long enough to trust (a decision for later, not part of this change).

**Part B: correct precisions.** After Part A, `decimal_places` only governs validation, so:

1. Seed each code with the precision OBP uses today (the `currencyDecimalPlaces` table). Behaviour is unchanged. *Done by the step 4 seed.*
2. Where the target precision is **higher** than the legacy one (BHD, IQD, JOD, LYD, TND, CLF, UYW, CZK, XBT → 8, ETH → 18, ADA → 6): update `decimal_places`. No stored data changes; existing amounts simply have trailing zeros.
3. Where the target precision is **lower** (the ISO 0 codes in §Background): update `decimal_places` only if no stored amount in that code has non-zero digits beyond the new precision. Otherwise the code keeps its legacy precision and the migration reports the code and the number of offending rows. It never rounds.
4. `PRECIOUS_METAL` and `ACCOUNTING_UNIT` stay at 2 unless a deployment chooses otherwise, since ISO defines no minor unit.

**Part C: fold `lovelace` and `wei` into `ADA` and `ETH`.** Rows with currency `lovelace` get currency `ADA` and amount `amount / 10^6`; rows with `wei` get `ETH` and `amount / 10^18`. Both are exact in `DECIMAL(38, 18)`. A `lovelace` or `wei` amount with a fractional part (possible today, because both got 2 decimal places) is not a real on-chain amount: those rows are reported and left unconverted, never rounded. Rows with `ada` become `ADA`.

Each code and each table is migrated in its own transaction, so one failing check does not hold back the others.

## 8. Endpoints (v7.0.0)

All write endpoints are bank-scoped. The bank in the path is the asset's administering bank (§1): the issuer for issued types, `SYS` for issuer-less ones.

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/obp/v7.0.0/assets` | none | Query: `asset_type`, `issuer_bank_id`, `status`, `chain_scheme`, `limit`, `offset`. No authentication |
| GET | `/obp/v7.0.0/assets/ASSET_CODE` | none | No authentication |
| GET | `/obp/v7.0.0/assets/chain/CHAIN_SCHEME/CHAIN_ASSET_ID` | none | Reverse lookup for reconciliation. No authentication |
| POST | `/obp/v7.0.0/banks/BANK_ID/assets` | `CanCreateAsset` | At an issuer: issued types only, `issuer_bank_id` = `BANK_ID`. At `SYS`: `CRYPTO` only, `issuer_bank_id` null |
| PUT | `/obp/v7.0.0/banks/BANK_ID/assets/ASSET_CODE` | `CanUpdateAsset` | Mutable fields only (§2); 400 on any immutable field |
| PUT | `/obp/v7.0.0/banks/BANK_ID/assets/ASSET_CODE/status` | `CanUpdateAssetStatus` | Separate role so freeze authority can be granted alone. Body carries `status` and `reason` (§6) |
| GET | `/obp/v7.0.0/banks/BANK_ID/assets/ASSET_CODE/status-history` | `CanGetAssetStatusHistory` | Rows from `asset_status_history` |
| DELETE | `/obp/v7.0.0/banks/BANK_ID/assets/ASSET_CODE` | `CanDeleteAsset` | Only if no account, transaction, product fee or limit references the code. Seeded assets cannot be deleted |

**Administering-bank check.** Because `asset_code` is unique across the whole instance but the write paths are scoped to one bank, every PUT and DELETE must check that `BANK_ID` is the asset's administering bank (the issuer, or `SYS` when there is no issuer). Without this, a user holding `CanUpdateAsset` at bank B could change an asset issued by bank A. A mismatch returns 404, so the endpoint does not confirm that the code exists at another bank.

Seeded assets (`FIAT`, `PRECIOUS_METAL`, `ACCOUNTING_UNIT`, seeded `CRYPTO`) accept `PUT .../status` at `SYS` only. They cannot be created or deleted via the API.

### POST body

```json
{
  "asset_code": "TZBOND29",
  "asset_type": "DEBT_SECURITY",
  "name": "Example Bank 2029 Fixed Rate Note",
  "decimal_places": 0,
  "issuer_product_code": "TZBOND29",
  "chain_scheme": "CARDANO_MAINNET",
  "chain_asset_id": "<policy_id>.<asset_name_hex>",
  "dti": null
}
```

### Response

```json
{
  "asset_id": "7a1c…",
  "asset_code": "TZBOND29",
  "asset_type": "DEBT_SECURITY",
  "name": "Example Bank 2029 Fixed Rate Note",
  "decimal_places": 0,
  "issuer_bank_id": "example.bank.tz",
  "issuer_product_code": "TZBOND29",
  "chain_scheme": "CARDANO_MAINNET",
  "chain_asset_id": "<policy_id>.<asset_name_hex>",
  "dti": null,
  "status": "ACTIVE",
  "created_by_user_id": "…",
  "created_at": "2026-10-05T10:00:00Z",
  "updated_at": "2026-10-05T10:00:00Z"
}
```

If `issuer_product_code` is given, the product must exist at `BANK_ID`.

## 9. Products

No change to the product model is required. Conventions and fixes:

**Instrument attributes.** An issuer product describing an asset uses product attributes. Recommended names (convention, not enforced in v1):

| Name | Type |
|---|---|
| `isin` | STRING |
| `face_value` | DECIMAL |
| `coupon_rate` | DECIMAL |
| `coupon_frequency` | STRING |
| `issue_date` | DATE_WITH_DAY |
| `maturity_date` | DATE_WITH_DAY |
| `terms_sha256` | STRING |

**Fixes needed:**

- *Done in step 3*: `DECIMAL` and `BOOLEAN` attribute types, so rates and face values need not be stored as `DOUBLE`.
- `ProductFee.Currency` must pass `isUsableAsset`; `ProductFee.Amount` scale must follow the fee currency's `decimal_places` rather than a fixed 2.

**Not in v1:** per-category required-attribute schemas (e.g. every `DEBT_SECURITY` issuer product must carry `maturity_date`). See §12.

## 10. Chain relationship

The registry stores the on-chain identity of an asset; it does not mint, burn or transfer. On-chain writes belong to the bank's own node holding the bank's keys. OBP-API may read the chain to reconcile `chain_asset_id` supply against the sum of OBP balances in `asset_code`.

The existing `CARDANO` transaction request body carries `assets: [{policy_id, asset_name, quantity}]` (`LocalMappedConnectorInternal.scala:1360`). With the registry in place, those entries can be resolved to an `asset_code` via the reverse lookup endpoint.

**Cardano amounts.** The Cardano request carries the amount twice: `value` (OBP currency and amount, today `("lovelace", "1000000")`) and `to.amount` (`quantity` with `unit: "lovelace"`), and nothing checks that they agree. After this change, `value` is in `ADA` (`("ADA", "1")`) and `to.amount` stays in lovelace, because `unit` is Cardano protocol vocabulary and `quantity` is the chain's integer amount. The request is rejected unless `value.amount * 10^6 == to.amount.quantity`. `lovelace` in `value.currency` is rejected with an error naming `ADA`, not silently converted. This changes the v6.0.0 Cardano request in place (v6.0.0 is not yet stable). The same applies to `ETH` and `wei` in the Ethereum request.

## 11. Implementation notes

- **Entity naming.** The new Mapper classes must not start with `Mapped` (e.g. `Asset` and `AssetStatusHistory`, not `MappedAsset`), and column objects must not be `m` followed by an uppercase letter. `MappedClassNameTest` enforces this.
- **Documentation.** The feature needs a Glossary entry ("Asset", explaining the three layers and the administering bank) as well as ResourceDocs for each endpoint. Internal notes such as this file do not count. The entry exists since step 5; it says what the registry does not decide yet, and that sentence must change in step 6.
- **SQL on PostgreSQL 16+.** The per-code migrations in §7 build SQL from code values and column names. Test them on PostgreSQL 16 or later, which rejects a bind parameter followed directly by a letter (`$1AND`) that 14 accepts.
- **Views over amount columns.** PostgreSQL refuses to change or rename a column a view depends on, so each Part A migration drops the dependent views first, in the same transaction, and recreates one only if something still needs it. Never drop all views on every boot. Today no OBP view depends on these columns: the only ones that did, `v_fast_firehose_accounts` and `mv_fast_firehose_accounts` (both selecting `mappedbankaccount.accountbalance`), are already dropped by `MigrationOfDropFastFireHoseViews`, and nothing replaces them. The migration should still look up dependent views in the database catalog rather than assume none exist, because a deployment may have created its own; their names belong in the migration log.
- **Tests empty the registry.** The test setup empties every table in `ToSchemify.models` after each test, so the rows the boot seed inserts do not survive into later tests. That is harmless while nothing reads the registry. Step 6 must either exclude `Asset` from that wipe (as `Consumer` and `AuthUser` are) or seed it in test setup, or every currency check in the test suite will fail.
- **`MappedCurrency`.** The existing `code.fx.MappedCurrency` table (code, name, symbol) is never written or read; it is only the foreign-key target of `MappedFXRate`. It is a candidate for removal once FX rates refer to the registry.
- **No silent rounding in Scala.** Scala's `BigDecimal` uses `MathContext.DECIMAL128` by default, which keeps 34 significant digits and rounds arithmetic results beyond that. A `DECIMAL(38, 18)` value can have 38. Amount arithmetic (balance updates, sums, comparisons) must use `MathContext.UNLIMITED`, or check the result is exact; Lift's `MappedDecimal(DECIMAL128, …)` has the same limit and is not suitable for the new columns as is. Add tests at 38 digits.

## 12. Open questions

1. **Retirement precondition.** Should `RETIRED` require zero outstanding balance across all banks in this OBP instance? The check is cheap for mapped accounts, impossible for connector-sourced ones.
2. **Multi-instance scope.** `asset_code` is unique per OBP instance. Two OBP instances could register the same code for different assets. Options: prefix issued codes with an issuer identifier, or rely on `chain_asset_id` / ISIN / ISO 24165 DTI as the cross-instance identity and treat `asset_code` as local. *Leaning*: treat `asset_code` as local, and use the chain identity, ISIN or DTI for cross-instance identity.
3. **DTI.** *Leaning, adopted in v2*: the nullable, set-once `dti` column is in §2 now, since it costs little and avoids a later migration. Still open: whether any endpoint should require it.
4. **Required attributes per asset type.** Enforce the §9 attribute set for issued types at product-link time, or leave as convention?
5. **Decimal cap.** *Settled in v3*: amounts are stored exactly as `DECIMAL(38, 18)` (§5), so `decimal_places` is capped at 18 by the storage scale, not by `Long`. Still open: is any planned asset above 18 decimals? NEAR, for example, has 24. Such an asset would need a wider scale, which reduces the digits left before the decimal point.
6. **Seeding vs. config.** Should deployments be able to disable seeded fiat codes (e.g. a node that only handles TZS and one token)? *Adopted in step 4*: the seed has no property; a deployment suspends codes it does not handle via `status = SUSPENDED` at `SYS`, so there is one model and one audit trail. Nothing can suspend a code until the status endpoint (§8) exists.
7. **`ada` and `lovelace`.** *Settled in v3*: one code, `ADA`, at 6 decimal places; `lovelace` remains only as the Cardano protocol unit in the request body (§7 Part C, §10).
8. **`ETH` precision.** *Settled in v3*: `ETH` at its full 18 decimal places, stored exactly (§5); `wei` is folded into `ETH` (§7 Part C).
9. **Codes that fail the downward check.** When a code like ISK keeps legacy precision because some stored amounts have a fractional part, what then? Leave it at 2 permanently, or produce a report so the bank can correct the data and rerun? The step 2 report counts the affected rows; a local sandbox has none, so this needs a run against a copy of real data.
10. **Account routing schemes and networks.** Asset `chain_scheme` values name the network (§2), but a Cardano address on an account still uses the bare routing scheme `CARDANO`, which is mainnet on one deployment and preprod on another. Should account routings move to `CARDANO_MAINNET` and so on? That would change existing routing rows and the Open Corridor settlement account's routing, so it needs its own migration.
11. **Chain identity of native coins.** `ADA` and `ETH` are the chains' own currencies, not tokens, so they have no policy id or contract address. *Leaning*: they have no chain identity; the reverse lookup is for tokens only, and a plain ADA or ETH amount is recognised by the transaction request type.
