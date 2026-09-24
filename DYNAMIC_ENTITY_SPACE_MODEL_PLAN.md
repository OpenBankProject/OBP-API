# Dynamic Entity space model — SYS is an ordinary bank id

Written 2026-09-22. This is the only document for this work: no separate checklist. Track progress
here by marking items done in place. **Status on 2026-09-24: Phases 1, 2 and 3 are done, and phase 4
for the Record Roles. Phase 5 is dropped. The Definition Roles join phase 6.** The decisions below are settled. Two pieces are in the tree: the storage half of
Phase 1, committed as `25f384baf`, where the two data tables adopted the sentinel and took their
space-scoped unique index, and Phase 3, which refuses just-in-time entitlements in the system space.
Everything else in this plan is still to write.

Working rules: the user commits, the assistant never does. Every phase below is independently
shippable, and the order is the order in which they block each other, not a preference. Single-suite
command for the suites this work touches:
`mvn test -pl obp-api -DfailIfNoTests=false -DwildcardSuites=code.api.v6_0_0.DynamicEntityTest,code.api.v6_0_0.DynamicEntitySystemLevelBankIdTest,code.api.v6_0_0.DynamicEntityAccessFlagsTest`.
CI test shards boot from an **empty H2**, never from the local Postgres, so anything that runs
before Schemifier must survive a database with no tables at all — reproduce that locally with
`OBP_DB_DRIVER=org.h2.Driver OBP_DB_URL="jdbc:h2:mem:OBPTest_$(date +%s);NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=10"`.

## Vocabulary (settled 2026-09-22 — use these words and no others)

| term | code | meaning |
|---|---|---|
| **space** | the `bankId` of a Dynamic Entity or record | the namespace an entity and its records live in. Every entity belongs to exactly one. |
| **system space** | `Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID`, the literal `SYS` | the instance-wide space. It is a space like any other, and `SYS` is its bank id everywhere: storage, URLs, roles, responses. |
| **bank space** | a real `bank_id` | the space belonging to one bank. Nothing about it is special any more; the system space is its sibling, not its parent. |
| **reference** | a field typed `reference:<Entity>` | a link to a record **in the same space**. See Decision 4. |
| **cross-space reference** | not built | a future link that crosses spaces. When it arrives it gets its own type name, such as `cross-space-ref`, so that `reference:` never silently changes meaning. |

Words retired: *system level entity* and *bank level entity* as two kinds of thing (there is one
kind of entity, in one space); *any bank* for anything Dynamic Entity related (see Decision 3).
"Create a system dynamic entity" from now on means exactly "create a dynamic entity at bank `SYS`".

## The model we are going to

| | today | after |
|---|---|---|
| Data URLs | `/obp/dynamic-entity/ENTITY` or `/obp/dynamic-entity/banks/BANK_ID/ENTITY`, unversioned (`Http4sDynamicEntity.scala:91`) | `/obp/v7.0.0/…/banks/BANK_ID/ENTITY` with `BANK_ID=SYS` for the system space |
| Management URLs | `/management/system-dynamic-entities` **or** `/management/banks/BANK_ID/dynamic-entities` — a real branch, different nouns | one route, `BANK_ID=SYS` for the system space |
| URL extractors | every one written twice, a `None` branch and a `Some(bankId)` branch (`DynamicEntityHelper.scala:48-146`) | one branch each |
| Entity roles | `Can…DynamicEntity_SystemENTITY` (`requiresBankId=false`) **or** `Can…DynamicEntity_ENTITY` (`requiresBankId=true`) (`DynamicEntityHelper.scala:1197-1218`) | one name per entity and operation, always `requiresBankId=true` |
| Meta roles | `CanCreateSystemLevelDynamicEntity`, `CanCreateBankLevelDynamicEntity`, `CanCreateAnyBankLevelDynamicEntity` (`ApiRole.scala:955-990`) | one per operation, bank-scoped, granted at `SYS` for the system space |
| Definition storage | `NullRef(DynamicEntity.BankId)` for system rows (`MapppedDynamicEntityProvider.scala:44, 55, 68`) | the `SYS` sentinel, as the data tables already do |
| Record storage | `SYS`, unique on `(BankId, DynamicEntityName, DynamicDataId)` (`MapppedDynamicDataProvider.scala:291`) | unchanged, this half is done |
| Response envelope | `bank_id` present for bank entities, absent for system ones | `bank_id` always present, `SYS` for the system space |
| Granting at `SYS` | whatever grants a system role today, i.e. `canCreateEntitlementAtAnyBank` | its own role, see Decision 5 |

## What already exists (reuse, don't duplicate)

- `Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID` (`constant.scala:83`). Its docstring currently says
  the value "is an internal storage detail and is never published"; Phase 1 has to rewrite that,
  because this plan makes it the published identity of the space.
- `Migration.database.prepareDynamicEntitySpaceScopedIndexes` (`Migration.scala:238`) — the back-fill
  and index-drop shape to copy for the definition table, including its per-step `tableExistsByName`
  guards and its migration-log guard.
- `addEntitlement` does **not** check that the bank exists (`Http4s700.scala:516-546`); it only
  enforces `role.requiresBankId == body.bank_id.nonEmpty`. Grants at `SYS` therefore need no
  carve-out, but every caller that used to send no `bank_id` for a system role must now send `SYS`.
- Joins already resolve within one space (`childJoinInfo`, `Http4sDynamicEntity.scala:117-120`), so
  Phase 2 brings validation into line with them rather than adding a new restriction.
- `APIUtil.hasEntitlement` (`APIUtil.scala:2273-2277`) is where a role's reach is decided: a role
  with `requiresBankId = false` is looked up at `""` whatever bank was asked about. That one line is
  what "any bank" means, and it is why Decision 3 is expressed as a flag and not as a policy.

## Phase 1 — the definition table adopts SYS — **done 2026-09-23, tests green**

The data tables moved in `25f384baf`; `dynamicentity` did not, so the feature currently holds two
conventions for the same concept.

1. `MapppedDynamicEntityProvider.scala:44, 55, 68` — replace `NullRef(DynamicEntity.BankId)` with a
   comparison against the sentinel, and write the sentinel on create.
2. A `runOnce` migration, `MigrationOfDynamicEntityBankIdSentinel`, modelled on
   `prepareDynamicEntitySpaceScopedIndexes`: `UPDATE dynamicentity SET bankid = 'SYS' WHERE bankid IS NULL`,
   self-guarded on the table existing, logged with what it moved.
3. Rewrite the `constant.scala:83` docstring: the sentinel is the published identity of the system
   space, not an internal detail filtered back out at the edge.
4. Tests: extend `DynamicEntitySystemLevelBankIdTest`, which already holds down the property that no
   caller can create a bank whose id collides with the sentinel.

Done as described, plus two things found while in the file. The row level access warning counted a
system entity's existing rows with `NullRef(DynamicData.BankId)`, which has matched nothing since the
data tables moved to the sentinel, so the warning never fired for exactly the entities most likely to
have data. And `delete`'s fallback branch matched by entity name with no bank id, which would have
removed every space's copy of an entity of that name; no caller reaches it today, because both pass a
row this provider just returned, but the signature takes any `DynamicEntityT` and
`DynamicEntityCommons` is what a caller naturally holds.

The back fill is a step inside `prepareDynamicEntitySpaceScopedIndexes`, which Boot calls ungated,
rather than a `runOnce` migration: gated by the `migration_scripts.*` props, an instance with them off
would keep its NULLs and every system definition would become invisible.

Tests: three scenarios in `DynamicEntitySystemLevelBankIdTest` for the sentinel, the reader and the
back fill, plus one for the scoped delete, each checked against a build without the fix.

## Phase 2 — a reference points inside its own space — **done 2026-09-23, tests green**

`recordExists` (`MapppedDynamicDataProvider.scala:148`, named `existsById` and taking no bank id
before this phase) answered by entity and id alone, and the
candidate type list comes from `getDynamicEntities(None, true)`, which is `findAll` across every
space (`DynamicEntityProvider.scala:375`). Before the space-scoped unique index that was harmless,
because a record id was unique instance-wide. It is not any more: a reference in bank A's entity can
now validate against a record in bank B or in `SYS`.

1. `recordExists` takes the space and filters on it; `ReferenceType.validateRefValue`
   (`DynamicEntityProvider.scala:430`) passes it through.
2. `referenceTypeNames` (`DynamicEntityProvider.scala:375`) lists only the entities of the space the
   definition being validated belongs to, so an out-of-space `reference:X` is refused at definition
   time with the existing "unknown type" error rather than at write time.
3. Leave the type grammar alone. Type names are matched by prefix (`RefParamRegx`) and assembled in
   `DynamicEntityProvider.scala:721-724`, which is where a future `cross-space-ref:` slots in.
4. No back-fill and no compatibility window: validation runs only on writes (`validateEntityJson` is
   called from the `Some(body)` branch of `invokeDynamicConnector`, i.e. CREATE and UPDATE), so
   existing cross-space references keep being served and are refused only on the next re-save.
5. Tests: `DynamicEntityReferenceSpaceTest`, two scenarios — a definition in one space may not
   declare `reference:` to an entity in another, and a record's reference may not resolve to a
   record of the same id in another space, with the same-space case asserted alongside as the
   control. Both were checked against a build with the space checks removed, where they fail.

Done as described, with one addition: `ReferenceType.allReferenceTypeNames` keeps serving the Get
Reference Types endpoint (`Http4s600.scala:2303`), which is a catalogue of the whole instance and
has no bank in its URL. Worth revisiting when the v7 routes land, since a caller defining an entity
at a bank is currently shown types they cannot use.

Status: the definition-side check (`referenceTypeNames(space)`) and the record-side check
(`recordExists(space, entityName, id)`) are both in place; `validateEntityJson` passes the entity's own
`bankId` through `ReferenceType.validateRefValue`. The four `println` calls that were logging every
reference validation to stdout are now `logger.debug`.

## Phase 3 — just-in-time entitlements refuse SYS — **done 2026-09-22, tests green**

Independent of every other phase and safe to land first. With
`create_just_in_time_entitlements=true`, a user holding `canCreateEntitlementAtOneBank` at a bank is
auto-granted whatever role they are missing there (`APIUtil.scala:2348`). Once `SYS` is a bank id,
that path would mint system-space roles on first use.

No role satisfies a just-in-time grant at `SYS`; the request is refused with the ordinary missing-role
error and nothing is written.

Done as follows. The just-in-time block existed twice, identically, in the deprecated
`handleAccessControlRegardingEntitlementsAndScopes` and in the live `handleAccessControlWithAuthMode`,
so it was first extracted into one `APIUtil.grantJustInTimeEntitlements` (`APIUtil.scala:2341`) that
both call; the rule therefore cannot drift between the two. The guard sits beside the existing
consent-user exclusion, and the docstring says why each of the two exclusions is there. Documented in
the Glossary Item `Entitlement` next to the consent-user sentence, and in `sample.props.template`
under `create_just_in_time_entitlements`.

Tests: two scenarios in `JustInTimeEntitlementsTest`, held at the function rather than over HTTP
because no endpoint resolves `SYS` as a bank id until Phase 6. The first proves the refusal and that
no row is written; the second proves an ordinary bank is still granted just in time, which is the
behaviour the guard must not have broken. Both were checked against a deliberately unguarded build:
the first fails there (`true did not equal false`), so it is testing the guard and not the weather.

## Phase 4 — every Dynamic Entity role names exactly one bank — **Record Roles done 2026-09-24, tests green; Definition Roles moved to phase 6**

This is the first instance of a wider direction: `ANY_BANK_ROLE_REMOVAL_PLAN.md` generalises it to
the other sixty-one Roles that reach every bank.

**Split during implementation, 2026-09-24.** Only the Record family moved. Merging the Definition
family means choosing one `requiresBankId` for the merged Role, and the system level management
endpoints — `/management/system-dynamic-entities` — carry no space in their URL, so
`ResourceDocMiddleware.authorizeRoles` resolves them at the empty bank id and a bank scoped Role can
never be satisfied there. Choosing `false` to suit them would widen the bank level Role into one grant
that authorises every bank, which is the shape this work removes. So the Definition merge and re-scope
happen in phase 6, in the same change that gives those endpoints a space. The Record Roles had no such
problem: their handler resolves the space itself.

What that meant in practice, and what it caught:

* `DynamicEntityInfo` emits one name per operation, all `requiresBankId = true`, covering the Record
  Roles, `CanGrantDynamicEntityRowAccess_` and the auto-generated field Roles.
* `Http4sDynamicEntity` gained `spaceOf`, so a role check for a system level entity asks about `SYS`
  rather than the empty bank id.
* **Two production defects surfaced**, both the same shape: the creator auto-grant in `Http4s600` and
  in `Http4s400` wrote the new bank scoped Roles at the empty bank id, so whoever defined a system
  level entity was locked out of it the moment they created it. Both now grant at `bankIdOrSYS`.
* Consent-carried entitlements have to name the space too; `ConsentUtil` already honours the Role's own
  `requiresBankId`, so it was the caller that needed fixing.
* `MigrationOfDynamicEntityRoleNames` rewrites the stored names across Entitlements, Entitlement
  Requests, Consumer Scopes and Group Role lists, moves the system level ones to `SYS`, moves a Group
  holding only these Roles, and reports mixed Groups and the two `AnyBank` Roles that have no successor.
  Covered by `DynamicEntityRoleRenameMigrationTest`.

Regression after the change: 26 suites, 108 tests, no failures.

**Naming, decided 2026-09-24.** Two families, told apart by what they gate, and neither name changes
with the space — consistency between a system operation and a bank operation is a main purpose of this
work, and a Role whose name changes with the space is the opposite of it.

*Definition Roles* gate creating and editing the definition. *Record Roles* gate writing rows into it.
Today both are called "dynamic entity" Roles, which hides the difference: one lets you define
`country`, the other lets you put `FR` in it.

Each row collapses the Roles on the left into the **single** Role on the right, which is then granted
at `SYS` or at a bank id. Thirteen Definition Role names become six, and each entity's four Record
Roles lose their `_System<Entity>` twin.

| Roles today (all replaced) | the one Role that replaces them |
|---|---|
| `CanCreateSystemLevelDynamicEntity`, `CanCreateBankLevelDynamicEntity`, `CanCreateAnyBankLevelDynamicEntity` | `CanCreateDynamicEntityDefinition` |
| `CanUpdateSystemLevelDynamicEntity`, `CanUpdateBankLevelDynamicEntity` | `CanUpdateDynamicEntityDefinition` |
| `CanDeleteSystemLevelDynamicEntity`, `CanDeleteBankLevelDynamicEntity` | `CanDeleteDynamicEntityDefinition` |
| `CanGetSystemLevelDynamicEntities`, `CanGetBankLevelDynamicEntities`, `CanGetAnyBankLevelDynamicEntities` | `CanGetDynamicEntityDefinitions` |
| `CanDeleteCascadeSystemDynamicEntity` | `CanDeleteCascadeDynamicEntityDefinition` |
| `CanBackupSystemDynamicEntity`, `CanBackupBankLevelDynamicEntity` | `CanBackupDynamicEntityDefinition` |
| `CanCreateDynamicEntity_SystemCountry`, `CanCreateDynamicEntity_Country` | `CanCreateDynamicEntityRecord_Country` |
| `CanGetDynamicEntity_SystemCountry`, `CanGetDynamicEntity_Country` | `CanGetDynamicEntityRecord_Country` |
| `CanUpdateDynamicEntity_SystemCountry`, `CanUpdateDynamicEntity_Country` | `CanUpdateDynamicEntityRecord_Country` |
| `CanDeleteDynamicEntity_SystemCountry`, `CanDeleteDynamicEntity_Country` | `CanDeleteDynamicEntityRecord_Country` |

Every one of them is granted at `SYS` or at a bank id, and none is `requiresBankId = false`.

Backup and cascade delete sit in the Definition family deliberately (confirmed 2026-09-24): backup
creates a `_BAK` definition alongside the copied rows, and cascade delete removes the definition
together with its records. Both act on the definition, whatever they do to the data underneath it.

Two consequences to carry into the release note. For an operator this is a rename **and** a re-scope
at once: `CanCreateDynamicEntity_SystemCountry` at the empty bank id becomes
`CanCreateDynamicEntityRecord_Country` at `SYS`. And the longest generated prefix grows from
`CanCreateDynamicEntity_` to `CanCreateDynamicEntityRecord_`, 29 characters, so an entity name may be
up to 226 characters before the Role name outgrows the 255-character Entitlement column — the same
budget as before, since the old `System` variant was the same length.


1. `ApiRole.scala:955-990` — every Dynamic Entity role becomes `requiresBankId = true`.
   `CanCreateAnyBankLevelDynamicEntity` and `CanGetAnyBankLevelDynamicEntities` are removed, and with
   them the `System`/`BankLevel` pairs: one name per operation.
2. `DynamicEntityInfo.canCreateRole` and its siblings (`DynamicEntityHelper.scala:1197-1218`) stop
   branching on `bankId` and emit one name.
3. Call sites that list the pairs: `Http4s400.scala:1576, 1608, 1801` and `Http4s600.scala:7039`.
4. `checkEntityRole` (`Http4sDynamicEntity.scala:274`) loses its empty-string branch, and its error
   message says `at Bank(SYS)` for the system space like any other.

## Phase 5 — granting at SYS is its own permission — **dropped 2026-09-24**

A dedicated `CanCreateEntitlementAtSystemSpace` was going to be the only thing that authorised
`bank_id == SYS`. It is not being built, and the reason is the premise of this whole plan: `SYS` is an
ordinary space, so a grant there is exactly as consequential as a grant at `obp1` — control over that
space's entities and nothing more. The idea that reaching the system space should need a special key
was inherited from the world where "system level" meant instance-wide power, which is the thing being
removed. Granting at `SYS` therefore needs `canCreateEntitlementAtOneBank` **at SYS**, like anywhere
else, and `Add Entitlement` already accepts that because it never checks that the bank exists.

What that leans on is that **no Role may reach every space at once**. Today
`canCreateEntitlementAtAnyBank` is `requiresBankId = false` (`ApiRole.scala:305`), so its holder can
grant anywhere, `SYS` included. That is not a system-space problem, it is the any-bank problem, and it
belongs to `ANY_BANK_ROLE_REMOVAL_PLAN.md` where the granting Roles are audited like every other pair.
A special SYS role would have been a local patch for a global gap.

The survey below is kept, because it is the audit the any-bank work needs: these are the paths that
write an Entitlement row without going through the Add Entitlement endpoint, and any rule about who
may grant what has to reach all of them.

| path | where | rule |
|---|---|---|
| v7 `addEntitlement` | `Http4s700.scala:546` | the predicate |
| v2.0.0 `addEntitlement`, still live | `Http4s200.scala:1272` | the predicate |
| Entitlement request approval | `Http4s300.scala:1654` is the request; guard the approval that turns it into a row | the predicate |
| Group membership | `Http4s600.scala:2235` — joining a group grants its roles at `group.bankId` | the predicate, both when a group is created at `SYS` and when a member is added |
| Consent-carried entitlements | `ConsentUtil.scala:460` | the predicate, or refuse `SYS` in a consent outright |
| Default entitlements for new users | `APIUtil.scala:4599` | refuse a `SYS`-scoped role rather than write one |
| Just-in-time | `APIUtil.scala:2348` | refuse outright, Phase 3 |
| Dynamic Entity creator auto-grant | `Http4s600.scala:537` | **allowed** — it grants the entity's own roles to whoever was already permitted to create the entity, so it is a consequence of the create permission, not an independent grant |

Consumer Scopes mirror roles and carry a bank id (`MappedScopesProvider.scala:115`). If a scope can
name `SYS`, it needs the same rule, or the application path bypasses the user path.

## Phase 6 — the v7.0.0 routes

Only now, with one storage convention, one role family and one grant rule, are the routes worth
writing. `/obp/dynamic-entity/…` keeps serving unchanged throughout; the two read the same storage.

1. Collapse the extractor pairs in `DynamicEntityHelper.scala:48-146` to one branch each, and key
   `definitionsMap` on `(String, String)` with `SYS` instead of `(Option[String], String)`.
2. **The space is resolved in one place** (decided 2026-09-23). The segment that names a space holds
   a bank id or `SYS`, and exactly one function says which: a real bank is looked up as today and a
   reserved space is let through with no bank. Everything that resolves a space asks that function
   instead of calling `getBank` directly, so the rule is stated once rather than repeated wherever a
   space is read.

   Implement it **without touching `ResourceDocMiddleware`**. `validateBank`
   (`ResourceDocMiddleware.scala:614`) fires on the literal template variable `BANK_ID` and is shared
   by every endpoint in OBP, so relaxing it there would let `/banks/SYS/accounts` past bank validation
   for endpoints that genuinely need a bank, and they would fail further in with something worse than
   a 404. Instead the Dynamic Entity ResourceDocs declare their template with `SPACE_ID`, a
   non-standard all-caps variable the matcher treats as a wildcard and the middleware skips — the
   documented bypass in CLAUDE.md, already used by `FIREHOSE_BANK_ID` and `NEW_ACCOUNT_ID`. The
   handler then calls the resolver itself, in place of today's `bankCheck`
   (`Http4sDynamicEntity.scala:173`). `SPACE_ID` is not in `ResourceDocMatcher.literalAllCapsSegments`,
   so nothing else has to change.

   The served URL is unaffected: a caller still writes `/banks/obp1/...` or `/banks/SYS/...`; only the
   doc's template variable is named differently, which is what the middleware matches on.

   Metrics, per-bank rate limiting, consent scoping and ABAC all take the bank id as a string and
   never resolve it, so they need nothing. The two places that do resolve are the middleware, bypassed
   as above, and `bankCheck`, replaced by the resolver.

   Two tests hold it down: an unknown bank id still gives 404 on a space route, and `SYS` passes
   through to the handler.
3. The envelope carries `bank_id` always, `SYS` included. This is the signature change that makes
   v7.0.0 the right home, and the reason the data endpoints stop being served from an unversioned
   prefix: today there is no version to branch on, so this contract cannot be changed at all.
4. Management endpoints collapse to one route with `BANK_ID=SYS`.

## Phase 7 — the entitlement migration

One `runOnce`, logging what it moved, reading existing rows and writing new ones:

| from | to |
|---|---|
| `Can*DynamicEntity_System<Entity>` at `""` | `Can*DynamicEntity_<Entity>` at `SYS` |
| `CanCreateAnyBankLevelDynamicEntity` / `CanGetAnyBankLevelDynamicEntities` at `""` | one bank-scoped row per existing bank, plus one at `SYS` |
| `canCreateEntitlementAtAnyBank` holders | one `CanCreateEntitlementAtSystemSpace` row each (Decision 6) |

The third row is a **one-time expansion, never a standing rule**: `addEntitlement` must not go on
implying the system-space role from `canCreateEntitlementAtAnyBank`, or the separation applies only
to the people who happened to exist on migration day.

## Phase 8 — documentation and deprecation

The Glossary Items `Dynamic-Entities` and `Dynamic-Entity-Access-Model` describe the system/bank
split as two kinds of thing throughout, and the access-model table's five routes are written around
it. They need rewriting to the space vocabulary, including the reason Dynamic Entities are the only
role family in OBP without an `AtAnyBank` variant — without that recorded, someone will eventually
restore it as an oversight. Then deprecate `/obp/dynamic-entity/…`.

## Decisions (settled 2026-09-22)

1. `SYS` is the bank id of the system space everywhere — storage, URLs, roles, responses — and stops
   being an internal detail filtered out at the edge.
2. "Create a system dynamic entity" means "create a dynamic entity at bank `SYS`". The management
   URL branch disappears with it.
3. Every Dynamic Entity role is `requiresBankId = true` and names exactly one bank; the
   `*AnyBankLevel*` roles go. Accepted cost: a bank created later needs its own grant, and an
   existing "any bank" holder is expanded to one row per bank at migration time with future banks
   uncovered. Groups do not soften this — a group entitlement row carries its own `bank_id`
   (`JSONFactory6.0.0.scala:2086-2095`), so groups bundle users, not banks.
4. A `reference:` points inside its own space. A cross-space link may come later under its own name.
5. ~~Granting at `SYS` requires `CanCreateEntitlementAtSystemSpace`.~~ **Reversed 2026-09-24**:
   `SYS` is an ordinary space, so granting there needs `canCreateEntitlementAtOneBank` at `SYS` like
   any bank. Stopping one Role from reaching every space is the any-bank plan's job, not a special
   case here.
6. ~~The migration grants that role to existing `canCreateEntitlementAtAnyBank` holders.~~ Moot,
   since there is no such role.
7. Just-in-time entitlements never grant at `SYS`.

## Risks

- **A missed grant path is a back door into the system space.** The table in Phase 5 is the audit;
  anything added later that calls `Entitlement.entitlement.vend.addEntitlement` directly needs the
  predicate too. A sweep test that greps for direct calls, in the style of
  `OnBehalfOfOwnershipSweepTest`, is the way to keep it honest.
- **`SYS` reaching code that resolves a bank.** `bankCheck` is the obvious one; metrics, rate
  limiting, consents and ABAC each resolve a bank from a request and will meet `SYS` for the first
  time. Symptom is a 404 or an empty lookup, not an error that names the cause.
- **The wildcard is genuinely gone.** If the instance onboards banks continuously, Decision 3 turns
  into standing operational work. The mitigation is a bulk-grant endpoint, not a wildcard role.
- **Phase 6 before Phase 4 would ship the ambiguity.** If the routes land while
  `CanCreateAnyBankLevelDynamicEntity` still exists, that role starts covering the system space by
  accident, because `SYS` will by then be a bank id and its lookup ignores which bank was asked about.
