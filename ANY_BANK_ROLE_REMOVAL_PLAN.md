# Retiring the any-bank Roles — every Role names one bank

Written 2026-09-23. Long term direction, nothing built. Track progress here by marking Roles done in
place. The first instance is already decided elsewhere: the Dynamic Entity pair
(`CanCreateAnyBankLevelDynamicEntity`, `CanGetAnyBankLevelDynamicEntities`) goes in
`DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md`, Phase 4, and this document generalises that decision to the
other sixty-one.

**What this is not.** System Roles are not being removed. A Role whose subject is the instance
itself, such as `CanGetAnyUser`, `CanReadMetrics` or `CanGetConfig`, has no bank to name and stays
exactly as it is. What is being removed is the narrower thing: a Role that lets its holder operate
on **any bank**, on data that belongs to one bank at a time. After this work an administrator still
holds instance-wide Roles for instance-wide work; what they no longer hold is a single grant that
reaches every bank's accounts, customers or products at once.

Working rules: the user commits, the assistant never does. Roles are retired one at a time, each
with its own migration, and a Role is only deleted once nothing declares it and every holder has
been expanded. Nothing here requires a big-bang release.

## The principle

**A Role targets one space: one bank, or SYS.** Not two banks, not every bank, not "every bank that
exists plus the ones created next year". Everything below is that sentence applied to the sixty-three
Roles that currently break it.

`SYS` is the system space, and a Role targeting it is an ordinary Role that happens to name that
space — not a more powerful class of Role. That is why granting at `SYS` needs nothing special; see
`DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md`, where a dedicated system-space granting Role was planned and
then dropped for exactly this reason.

### How far the principle reaches — narrow now, wide later (decided 2026-09-24)

**Now: it governs Roles about space-scoped things** — Dynamic Entities, attributes, accounts,
customers, anything owned by one bank. Roles whose subject is the instance rather than a space —
`CanGetAnyUser`, `CanReadMetrics`, `CanGetConfig`, `CanGetConnectorHealth` — keep
`requiresBankId = false` and the empty bank id. Nothing about them changes, and the empty bank id
survives as a third value beside a bank id and `SYS`.

**Mid term: every Role names a space, and the instance-wide ones name `SYS`.** The empty bank id then
disappears, `APIUtil.hasEntitlement` stops choosing between `bankId` and `""`, and eventually the
`requiresBankId` flag itself has nothing left to say. That is the model worth arriving at; it is not
this plan, because it is product-wide rather than about the sixty-three Roles here, and it carries two
visible costs: every existing system Role grant moves from `""` to `SYS`, and every caller granting
one must start sending `bank_id: "SYS"`, which `Add Entitlement`'s
`role.requiresBankId == body.bank_id.nonEmpty` check would enforce the other way round.

Two habits keep that door open while the narrow rule is in force. A new Role about something a bank
owns is scoped to a space from the start, never added at the empty bank id for convenience. And no
new code path should hard-code what the empty bank id means; ask the Role.

#### Caution for whoever picks the wide move up

The tempting shortcut is to let `SYS` mean whatever suits each endpoint: on Get Metrics it would mean
"every metric regardless of the bank id on the record", while on a Dynamic Entity endpoint it means
"the system space only". Do not do that, for two reasons.

**An Entitlement row stops being readable.** `CanReadMetrics` at `SYS` and
`CanGetDynamicEntity_country` at `SYS` look identical in the table, in a listing, and in an audit
export, while one is instance-wide and the other is one namespace among many. Reviewing a grant would
mean knowing, per Role, which convention applies.

**It rebuilds the any-bank Role under a new spelling.** "Metrics at every bank, including banks
created next year" is exactly `CanReadMetricsAtAnyBank`, the shape this plan exists to remove — now
satisfying the letter of "a Role targets one space" while inverting its substance, and passing the
guard test while doing it.

The test to apply instead is whether the **resource** belongs to a space. A Dynamic Entity does: it
lives in exactly one, so the Role names that space and `SYS` is one value among many. A metrics record
does not, despite carrying a bank id column — the resource is the instance's request log, and the
column is a field of a row rather than ownership. So `CanReadMetrics` is not a space-scoped Role
granted at `SYS`; it is a Role about something with no space, which is the category the narrow rule
keeps at the empty bank id.

If the wide move still looks right after that, it needs a value that honestly means "not
space-scoped", distinct from `SYS`. At which point there are three values again, better named. The
gain being chased was one uniform comparison and the eventual removal of `requiresBankId`; if `SYS`
has to be read per endpoint, `hasEntitlement` stops branching and every caller starts, in more places
and with less visibility.

## Why

`requiresBankId = false` does two quite different jobs today, and only one of them is a problem. On
a Role whose subject is the instance it is simply true: there is no bank, so there is no bank id. On
a Role about per-bank data it means something else — the Role is read at the empty bank id whatever
bank was asked about, so it silently covers all of them. `APIUtil.hasEntitlement`,
`APIUtil.scala:2273-2277`:

```scala
Entitlement.entitlement.vend.getEntitlement(if (role.requiresBankId) bankId else "", userId, role.toString)
```

For the second kind, three consequences follow, and they are the argument for removing those Roles
rather than documenting them better.

1. **The blast radius is unbounded and invisible.** One row authorises an action at every bank, so no
   bank's administrator can see, from their own bank's entitlements, who may act on their data.
2. **It covers banks that do not exist yet.** A bank onboarded next year is inside the grant the day
   it is created, without anybody deciding that.
3. **It cannot be reasoned about per bank**, which is the unit everything else in OBP uses: accounts,
   views, customers, consents and metrics are all per bank. A permission model whose unit differs
   from the data model's unit is where mistakes hide.

A per-bank Role has none of these properties: the row names the bank, the bank's administrator can
list it, and a new bank starts empty.

## Scope — what is in and what is not

In scope: a Role about a **per-bank resource** that reaches every bank — a Role that lets its holder
operate on any bank. Sixty-three Roles match today
(`requiresBankId = false` and a name saying any or all banks). Fifty-nine already have a per-bank
sibling, so retiring them is subtraction rather than design. The four without one need the sibling
written first:

| Role | note |
|---|---|
| `CanCreateAnyBankLevelDynamicEntity` | handled by `DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md` |
| `CanGetAnyBankLevelDynamicEntities` | handled by `DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md` |
| `CanGetRolesWithEntitlementCountsAtAllBanks` | needs a per-bank sibling, or is arguably instance-wide reporting — decide before touching |
| `CanGetViewPermissionsAtAllBanks` | same question |

Out of scope, and staying exactly as they are: **system Roles**, meaning Roles about resources that
are not per bank at all — `CanGetAnyUser`, `CanReadMetrics`, `CanGetConfig`, `CanGetConnectorHealth`
and the rest of the instance-wide administration set. They are system-scoped because their subject
is the instance, not because they span banks. Retiring them would mean inventing a bank id for
something that has none, which is the opposite of this plan; an instance still needs Roles for
instance-wide work.

The test is one question, and a name is not the answer to it: **is the resource this Role acts on
owned by one bank?** If yes and the Role reaches all of them, it is in scope. If the resource has no
bank, the Role is a system Role and is not.

Also out of scope, and deliberately so: reintroducing a wildcard bank id. `SYS` in the Dynamic Entity
work is the name of one space, not a wildcard, and nothing here should produce a bank id meaning
"all".

## What replaces them

A per-bank grant, one row per bank. The honest cost is that granting at twenty banks means twenty
rows, and a new bank needs a new row. Two answers, in the order they should be tried:

1. **A bulk grant call** — one request naming a Role and a list of banks, or a Role and "every bank
   this caller administers", writing the rows individually so that each bank's entitlement list
   stays truthful. This is the mechanism that makes retirement affordable, and it should exist
   before the first widely-held Role is retired.
2. **Groups**, which already exist: a group entitlement row carries its own `bank_id`
   (`JSONFactory6.0.0.scala:2086-2095`), so groups remove the per-user multiplication but not the
   per-bank one. Useful, not sufficient.

Neither is a wildcard: after both, the rows still say which banks, and a bank created later is still
outside until someone adds it. That is the property being bought.

## How one Role is retired

Per Role, in this order. Each step is a separate commit and the Role keeps working throughout.

1. **Audit.** Where is it declared in a `ResourceDoc` role list, and where is it checked inline? The
   two patterns behave differently: in a doc list it is usually the second element of
   `Some(List(canX, canXAtAnyBank))`, where removing it narrows the permission; inline it is often a
   *bypass* inside an authorisation OR-chain ("has the view permission **or** holds this Role"),
   where removing it changes who gets in by a different route. Sixteen files reference at least one
   any-bank Role today, concentrated in `Http4s600.scala` (29), `Http4s400.scala` (26) and
   `Http4s510.scala` (18).
2. **Make sure the per-bank sibling exists** and is declared alongside it everywhere the any-bank
   one is. For fifty-nine Roles this is already true.
3. **Tell the operators.** A release note naming the Role, what stops working and how to grant it
   again per bank. Existing rows are **not** migrated: a grant held at the system scope covered every
   bank, and expanding it would write one row per bank per holder for a permission an operator may
   only have wanted at one or two — on a 214-bank instance that is 214 rows per holder per Role.
   Operators find who is affected by listing the Role's Entitlements with an empty bank id, and
   re-grant deliberately. The stale rows authorise nothing afterwards and can be deleted.
4. **Remove it from the ResourceDoc role lists**, leaving the per-bank sibling. The Role still
   exists and is still honoured anywhere it is checked inline.
5. **Deprecate the name** for one release: `Add Entitlement` refuses to grant it, the Glossary says
   what to use instead, and existing rows keep working. An instance that scripted the old grant gets
   an error that names the replacement rather than silent behaviour change.
6. **Delete** the Role and any rows left over.

## Order

Retire by risk, not alphabetically. Highest first:

1. Roles that **write** per-bank data: create, update and delete on accounts, customers, products,
   ATMs, branches, counterparties, attributes.
2. Roles that **grant or reveal access**: anything touching views, account access, or entitlements.
3. Roles that **read** per-bank data.
4. Roles on per-bank configuration and metadata.

Within each band, prefer the Roles with the fewest holders on real instances — that is an operator
question, not one this repository can answer, so the first step of each retirement is asking.

## Stopping the tide

Retirement only converges if new any-bank Roles stop being added. **Done 2026-09-23**:
`code.api.sweep.AnyBankScopeSweepTest` holds two allowlists, each of which only ever shrinks.

* 63 Roles whose name says any or all banks and which are declared `requiresBankId = false`.
* 17 endpoints whose URL carries `BANK_ID` while every Role they declare is system scoped, of
  which **6 were fixed on 2026-09-23** and are gone from the list, leaving 11.

Both are computed from the running API rather than from the source text: the Roles come from
`ApiRole.availableRoles` and their own `requiresBankId`, and the endpoints from
`EndpointCatalog.all`, which is `Http4s700.allResourceDocs` deduplicated by URL and verb. Each
guard fails in both directions — a new offender that is not on the list, and a line on the list
that no longer offends — so the lists cannot silently grow or go stale. Both directions were
checked by perturbing the lists and watching each one fail.

## A related defect, already fixed

While auditing this, the just-in-time entitlement path turned out to grant a system-scoped Role at a
bank id — a row `Add Entitlement` refuses to write by hand, at a scope no check reads — and to let
the request through because the write succeeded rather than because the permission held. Fixed in
`APIUtil.grantJustInTimeEntitlements` with tests in `JustInTimeEntitlementsTest`.

The endpoint-side half of that mismatch is still open and belongs to this plan: seventeen endpoints
carry `BANK_ID` in their URL while every Role in their `ResourceDoc` is system-scoped, so the bank in
the URL does not narrow the permission at all. They are effectively any-bank Roles without the
name, and each one is a candidate for a per-bank sibling. The authoritative list is the allowlist in
`AnyBankScopeSweepTest`, read from the running API; the survey below groups them by what they touch:

- ~~five counterparty attribute endpoints, v6 (`canCreateCounterpartyAttribute` and siblings)~~ —
  **fixed 2026-09-23**
- `createCustomViewManagement`, v6 (`canCreateCustomView`)
- `getUsersWithAccountAccess`, v6 (`canSeeAccountAccessForAnyUser`)
- `accountCurrencyCheck` and `orphanedAccountCheck`, v5.1 (`canGetSystemIntegrity`)
- ~~`getAdapterInfoForBank`, v3.0 (`canGetAdapterInfoAtOneBank` — the name says one bank, the flag
  said otherwise)~~ — **fixed 2026-09-23**
- `getAccountAccessTrace`, v7 (`canGetAccountAccessTrace`)
- the three AMQP broker endpoints, v7 (`canConfigureAmqpBankBroker`)
- two dynamic message doc endpoints, `GET` and `PUT` on `/management/banks/BANK_ID/dynamic-message-docs`
- `POST /banks/BANK_ID/utility-payments/UTILITY_TRANSACTION_REQUEST_ID/vend-result`

The last three were missed by the source-text audit that first found this and were caught by the
guard reading the live catalog, which is the argument for the guard being runtime rather than a
grep.

## Done so far

**2026-09-23 — six endpoints, six Roles.** The five Counterparty Attribute Roles and
`CanGetAdapterInfoAtOneBank` are now `requiresBankId = true`. These were the cases needing no
judgement: every other attribute Role in OBP names a bank, and the adapter Role's own name said one
bank while its flag said every bank.

Shape of the change, which is the recipe for the rest:

1. The tests moved first. `CounterpartyAttributeTest` and `GetAdapterInfoTest` granted at the system
   scope; they now grant at the bank, which made them fail against the old flags, and one new
   scenario in each asserts that a grant at *another* bank authorises nothing.
2. The six flags flipped, with a comment saying why and pointing here.
3. A release note dated 23/09/2026 naming the seven Roles, saying that Entitlements held at the
   system scope stop authorising these endpoints, and giving the call to grant each Role again per
   bank. No migration: expansion was written and then dropped deliberately, because reproducing a
   grant that covered every bank means one row per bank per holder — 214 rows each on this instance —
   for a permission the operator may only have wanted at one or two banks.
4. The six lines came out of the endpoint allowlist in `AnyBankScopeSweepTest`, which the guard
   requires — a stale entry fails the suite just as a new offender does.

Tests after the change: 11 suites, 38 tests, no failures.

Eleven endpoints remain on the list, and all of them need a decision rather than a flag: whether
`CanGetSystemIntegrity`, `CanConfigureAmqpBankBroker`, `CanGetAccountAccessTrace`,
`CanSeeAccountAccessForAnyUser`, `CanCreateCustomView`, the two dynamic message doc Roles and the
utility payments one are meant to be instance-wide or per bank.

## Risks

- **Retiring a bypass Role silently locks people out.** A Role used inside an OR-chain is not a
  requirement, and expanding its holders per bank does not reproduce its effect. Audit step 1 exists
  to catch this; when a Role is a bypass, the retirement is a behaviour change to discuss, not a
  mechanical migration.
- **Operators who scripted grants.** Step 5 is what turns a silent change into an error message;
  skipping it is what makes an upgrade look like a break.
- **Half-retired Roles are worse than either end state.** A Role removed from some doc lists but not
  others gives two answers to the same question depending on the endpoint. One Role, one commit
  series, finished.
- **The bulk grant call is load-bearing.** Without it, step 3 turns into manual work at every
  instance and the plan stalls at the first widely-held Role.
