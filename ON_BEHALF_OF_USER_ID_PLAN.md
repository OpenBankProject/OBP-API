# On-behalf-of user id — making ownership-by-the-human automatic

Written 2026-09-02 evening, for pickup 2026-09-03. This is the only document: no separate
checklist. Track progress here by marking items done in place. **Status on 2026-09-19: Phases 0
and 1 are committed (2026-09-02, `2d86f4e9e` and `715989c11`). Phase 3's nineteen explicit-target
guards and the sweep that proves each one is reachable are green
(`ExplicitTargetConsentUserSweepTest`, 2026-09-13), and so are Phase 4's tests
(`AgentDelegationTest`, extended alongside each Phase 2 row; `UserReferenceAttributionPolicyTest`
2026-09-11; `OnBehalfOfOwnershipSweepTest` 2026-09-13). Phase 2 is the
live front and the only one being worked through a table at a time: five providers resolve the
on-behalf-of user today (AccountHolders, UserCustomerLink, DynamicEntity/DynamicData, Bank,
Counterparty), which is eight of the sixty-two references that need wiring; the other fifty-four
are listed by hand in `OnBehalfOfOwnershipSweepTest.notYetWired`, each with the reason it is still
open. The webhook row was audited on 2026-09-16 and deliberately deferred rather than wired; the
two v7 notification-webhook delete endpoints that audit turned out to need are committed
(`f2dddcd16`, 2026-09-17), and everything in Phases 0 to 4 is now committed — nothing in this plan
is sitting in the working tree. Open after that: the rest of Phase 2's mechanical batch, the
`onBehalfOfMode` endpoint tag of Decision 10, which is not built, Decision 12's
`NotImplementedForConsentUser` policy, which is an idea and not built, and Phase 5.** `AbacRuleTests` fails locally for an unrelated props reason (see Phase 1 note 2). Background and the reasoning are on the Portal page `/developers/opey-permissions`
(OBP-Frontend, uncommitted) and in `OBP-Frontend/CONSENT_ESCALATION_GAP.md`.

Working rules: the user commits, the assistant never does. The provider is the mechanism;
endpoint uses of `cc.onBehalfOfUserId` are clarity only, not the fix. A resolver WARN firing in
tests means a site forgot the rule or chose the wrong reference. Single-suite mvn command (clean build is required in this checkout, ~10 min, run detached):
`MAVEN_OPTS="-Xss128m -Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G" mvn -pl obp-api -am clean test -DwildcardSuites=code.api.util.AgentDelegationTest,code.util.ApiSessionTest`.

## Vocabulary (settled 2026-09-02 — use these words and no others)

| term | code | meaning |
|---|---|---|
| **user** | `userId` / `user_id` | the authenticated caller, whatever it is: a logged-in human, a consent user, an agent. Its entitlements and views are what OBP checks. Unchanged. |
| **on-behalf-of user** | `onBehalfOfUserId` / `on_behalf_of_user_id` | the human the user acts for; owner of anything durable the call creates. Equals `userId` when a human acts for themselves. |
| **consent user** | `User.isConsentUser`, `ResourceUser.CreatedByConsentId` | a user row created by a consent; its on-behalf-of user is the consent's `userId`. A durable agent is a consent user with a long-lived consent — there is no other kind of agent. |
| **original user** | `User.isOriginalUser` (= `CreatedByConsentId` empty), already in the commons `User` trait next to `isConsentUser` | a user OBP did not mint as a stand-in for someone else. Says nothing about whether a person or a service account is behind the login — OBP cannot know that without KYC and does not claim to. |

Invariant: **an on-behalf-of user must have `isOriginalUser` true.** One hop, no chains. This is a check on
one column OBP writes itself, not an inference about natural persons. `IsNaturalPerson` and
`PrincipalUserId` are dropped as concepts (never set, never read; see Phase 0): the on-behalf-of
relationship has exactly one record, the consent.

Words retired: *accountable user, principal, shadow, actor / acting user, human user, granting
human, real / effective identity.* "Principal" in particular means the authenticated identity to
a security engineer and the party-acted-for to an agent framework, i.e. opposite ends; do not
reintroduce it. `consenter` (BG/UK) and `consentCreator` (OBP-native) survive only as the names of
the two *sources* the request layer reads the on-behalf-of user from.

`on_behalf_of` already means exactly this role everywhere it appears in OBP today
(`MappedTransactionRequest.mOnBehalfOfUserId`, v6 `on_behalf_of_user_id`, `CallContext.onBehalfOfUser`),
so no clash. AI agents will not use Berlin Group endpoints for the foreseeable future; BG/UK
consents only need to keep working, not to be designed for.

## The model we are going to

Every request under a consent carries three identities, one job each:

| identity | job | today |
|---|---|---|
| **user** (here: the consent user) | authenticates the call; its embedded entitlements/views are what OBP checks; recorded as `user_id` in metrics | correct, keep |
| **consent_reference_id** | on every metric row; resolves user → on-behalf-of user and → exact granted scope | correct, keep |
| **on-behalf-of user** | owner/creator/holder/target of anything durable the call creates for a person | manual per endpoint; wrong by default |

Goal: the persistence layer defaults durable user references to the on-behalf-of user, so
endpoints are correct without remembering. Authorisation stays on the consent user (ConsentUtil's
isolation comment, ~line 1195, explains why act-as-human is not an option;
`experimental_become_user_that_created_consent`, the props toggle that logged the human on in
place of the consent user, was **removed 2026-09-21** — it was the one switch that could turn the
whole delegation model off, and while it existed every property this plan establishes was
conditional on an operator's props file. `Boot.warnAboutRemovedProps` names it for one release so
an instance that still sets it is told rather than silently changed).

## What already exists (reuse, don't duplicate)

1. `CallContext.accountableUserId` (`ApiSession.scala:244`): `onBehalfOfUser.or(consenter)` else DB chain `ResourceUser.CreatedByConsentId → MappedConsent.userId` else self. **Phase 0 renames it → `onBehalfOfUserId`.**
2. `CallContext.humanUser` (`ApiSession.scala:118`) = `onBehalfOfUser.or(consenter).or(user)`; 3 readers (`Http4s510:4720`, `ApiSession:126`, a comment in `ConsentUtil:1941`). **Phase 0 renames it → `onBehalfOfUser`.**
3. `CallContext.onBehalfOfUser` field (`ApiSession.scala:43`): the OBP-native consent's *creator*, from the JWT `createdByUserId`. A source, not the resolved value. **Phase 0 renames it → `consentCreator`** so the resolved method can take the name.
4. `CallContext.consenter` (`ApiSession.scala:51`): the PSU who authorised a BG/UK consent, from `consent.userId`. A source. Keep the name.
5. `MappedEntitlements.addEntitlement` (`MappedEntitlements.scala:161`): inline copy of the same chain; redirects untagged grants to the on-behalf-of user; exemption `createdByProcess == Constant.consent_user`.
6. `APIUtil.isConsentUser(userId)` (`APIUtil.scala:2258`) and `User.isConsentUser` (commons `UserModel.scala:70`, `= createdByConsentId.nonEmpty`).
7. `ResourceUser.PrincipalUserId` + `IsNaturalPerson` (`ResourceUser.scala:98-103`, added 2026-03-07): **never set, never read** — only plumbed through `createResourceUser`, no caller passes them, no reader outside the accessors. Every row has the defaults. **Both dropped in Phase 0.** `PrincipalUserId` would have been a second, denormalised record of the on-behalf-of relationship for consent-less agents; there are no consent-less agents (an agent's scope *is* a consent), so the consent chain is the only record.
8. `User.isOriginalUser` (commons `UserModel.scala:69`, `= createdByConsentId.isEmpty`): already the predicate the invariant uses; not touched.
9. `MappedTransactionRequest.mOnBehalfOfUserId` (`MappedTransactionRequestProvider.scala:172,299`): precedent for a "record both" table (`mUserId` + `mOnBehalfOfUserId`).
10. Tests: `AgentDelegationTest` (resolver chain, 113 lines), `FrozenClassTest` (pattern for "every X must be listed"), `ConsentObpTest`.

## Phase 0 — renames and drops (mechanical, one commit, no behaviour change except row 8) — **done 2026-09-02, tests green**

Phase 0 stands alone: it can ship without Phase 1 and leaves the code consistent. Purpose: make
the code speak the vocabulary above before any new code is written, so Phase 1 is not built on
names it then has to rename. Everything here is a drop of something never set, or a
rename of an accessor, plus one documentation fix. No schema migration.

| # | today | after | where | notes |
|---|---|---|---|---|
| 1 ✅ | `ResourceUser.PrincipalUserId`, `User.principalUserIdOption`, `createResourceUser(…, principalUserId)` | *dropped* | `ResourceUser.scala:101,143`, commons `UserModel.scala:75`, `Users.scala:86`, `LiftUsers.scala:323,362` | never set, never read, no caller passes it; the consent chain is the only record of on-behalf-of |
| 2 ✅ | `ResourceUser.IsNaturalPerson`, `User.isNaturalPerson`, `createResourceUser(…, isNaturalPerson)` | *dropped* | `ResourceUser.scala:98,142`, commons `UserModel.scala:74`, `Users.scala:85`, `LiftUsers.scala:322,358` | never set, never read |
| 3 ✅ | DB columns `resourceuser.principaluserid`, `resourceuser.isnaturalperson` | left in place | any DB started since 2026-03-07 | Mapper never drops columns; harmless (null / true). Drop by hand when convenient: `ALTER TABLE resourceuser DROP COLUMN principaluserid; ALTER TABLE resourceuser DROP COLUMN isnaturalperson;` |
| 4 ✅ | glossary `isNaturalPerson`, `principalUserId` | one `on_behalf_of_user_id` entry | `docs/introductory_system_documentation.md:4316,4334` | |
| 5 ✅ | `CallContext.onBehalfOfUser` (field) | `consentCreator` | `ApiSession.scala:43`; set `ConsentUtil.scala:574`; read `ConsentUtil.scala:583-588`, `Http4s600.scala:207-208`, `Http4s700.scala:950-951`, `MappedTransactionRequestProvider.scala:172`, tests `ApiSessionTest.scala:145`, `AgentDelegationTest.scala:109` | it holds the OBP-consent *creator*, a source, not the resolved value |
| 6 ✅ | `CallContext.humanUser` | `onBehalfOfUser` | `ApiSession.scala:118,126`; `Http4s510.scala:4716,4720`; comments `ConsentUtil.scala:1941`, `ConsentOwnershipTests.scala:51` | the resolved `Box[User]`: `consentCreator.or(consenter).or(user)` |
| 7 ✅ | `CallContext.accountableUserId` | `onBehalfOfUserId` | `ApiSession.scala:244` + 20 endpoint/connector sites + `AgentDelegationTest` (7) + comments in `MappedMetrics`, `APIMetrics`, `MigrationOfActivityDashboardIndexes`, `ResourceUser.scala:152` | body unchanged in Phase 0; Phase 1 makes it delegate to the resolver |
| 8 ✅ | v6/v7 `/users/current` JSON field `on_behalf_of` reads the `consentCreator` field only (null for BG/UK consents) | reads `consentCreator.or(consenter)` — the delegated value, **not** the resolved `onBehalfOfUser`, whose `.or(user)` fallback would show a plain user as their own on-behalf-of | `Http4s600.scala:174-213`, `Http4s700.scala:949-956`; endpoint comment ("impersonation headers", stale; fixed). No resource-doc text mentions the field, nothing to change there | **optional, not needed by Phase 1.** Additive behaviour change: BG/UK consent callers get the consenter instead of null; everyone else unchanged (Decisions 8); release-note it |
| 9 ✅ | — | **checked 2026-09-02: yes, always the same user.** | `MappedConsent.scala:205,232,279`; `ConsentUtil.scala:1359,1457,1647`; create endpoints `Http4s310:4451`, `Http4s500:1265`, `Http4s510:5025` | An OBP consent names its user twice: the row column `mUserId` and the JWT claim `createdByUserId`. OBP-native: all three create endpoints pass the logged-in `user` to both. BG/UK: both empty at creation, both set to the authorising user at authorisation (`updateConsentUser` + `updateUserIdOfBerlinGroupConsentJWT`). So `CallContext`'s two sources (`onBehalfOfUser` field from the claim, `consenter` from the column) always carry one value. **Decided: keep the two source fields separate anyway** (`consentCreator`, `consenter`) — explicit about where each came from; the resolved `onBehalfOfUser` (row 6) is the one to read. |

Not renamed: local `val humanUserId = cc.onBehalfOfUserId` in the createBank endpoints (`Http4s220:471`, `Http4s500:469`, `Http4s600:877`) and `Http4s700.humanAndAgentUserIds` — locals, Phase 2 touches those endpoints anyway; `consenter` (a source, name is accurate), `User.isConsentUser` / `Constant.consent_user`
(the kind of user), `mOnBehalfOfUserId` and `on_behalf_of_user_id` (already right). The ABAC rule
engine's `onBehalfOfUser` parameter (`AbacRuleEngine.scala:33,164`) is a separate rule-input slot,
always `None` today; leave it, it already uses the right word.

Done when (both satisfied 2026-09-02): `grep -rn "PrincipalUserId\|principalUserId\|IsNaturalPerson\|isNaturalPerson\|humanUser\b\|accountableUserId" obp-api/src obp-commons/src` is empty and `AgentDelegationTest`, `ApiSessionTest`, `ConsentOwnershipTests` pass.

## Phase 1 — one resolver + the complete policy file (decided: lives in `Users`) — **done 2026-09-02, tests green**

Phase 1 delivers the design whole: the resolver, the policy-aware entry point, **and every row of
the policy file**. Nothing calls the rows until Phase 2, but the file is declarative and one line
per row, so there is no reason to ship it in pieces.

Resolver home: trait `code.users.Users`, impl `LiftUsers`. Chosen over a separate object because
`LiftUsers` is the only writer of `CreatedByConsentId`, it
is already injectable (`Users.users.vend`, 156 call sites), and it already imports consent code, so
no new dependency edge. A separate `code.<x>.<X>` object was rejected on naming: every such object
with a `vend` is a table-backed provider and the name would read as a new table.

```scala
// ---- the raw chain, no policy ----------------------------------------------------------
/** The on-behalf-of user for `userId`.
 *  consent user → the consent's userId (authoritative, read at call time: BG/UK consents
 *                 bind their human only at authorisation, so don't copy it at creation)
 *  original user    → userId unchanged
 *  Fails closed: unknown user / dangling consent id / empty human → userId unchanged (+ WARN).
 *  Invariant: the result row is an original user (isOriginalUser); a consent user whose consent names
 *  another consent user is a data bug (WARN + Failure — the only case that cannot fall back).
 *  Takes only the id on purpose: nothing request-asserted (body/header/query) can steer it. */
def resolveOnBehalfOfUserId(userId: String): Box[String]

/** True when `userId` acts for itself and may own durable state. */
def actsForSelf(userId: String): Boolean = resolveOnBehalfOfUserId(userId).exists(_ == userId)

// ---- what a provider gets back: everything it should store, plus the log line -----------
case class Attribution(
  userId:           String,          // the authenticated caller
  onBehalfOfUserId: String,          // who owns what it creates; == userId for a human acting alone
  consentId:        Option[String],  // the consent behind a consent user, if any
  ref:              UserReference    // the column this was computed for; carried for logging
) {
  def isDelegated: Boolean = userId != onBehalfOfUserId
  /** the single value for the column `ref` names, per its policy */
  def userIdToStore: String = ref.policy match {
    case UseAuthenticatedUserId         => userId
    case UseOnBehalfOfUserId => onBehalfOfUserId
    case Reject             => userId   // unreachable: attributionOf fails first
  }
}

// ---- the entry point providers actually call ---------------------------------------------
/** Attribution for writing column `ref` as `userId`. Applies `ref.policy`:
 *  UseAuthenticatedUserId / UseOnBehalfOfUserId → Full(attribution), WARN naming `ref` when isDelegated
 *  Reject → Full(attribution) if !isDelegated, else Failure(InvalidUserId … names a consent user) */
def attributionOf(userId: String, ref: UserReference): Box[Attribution]

/** Convenience for single-column writers. */
def attributedUserId(userId: String, ref: UserReference): Box[String] = attributionOf(userId, ref).map(_.userIdToStore)
```

`UserReference` is the policy file as code (main tree, see "The policy file" below). The `ref` argument is
chosen by provider code, never from the request, so the "no caller-asserted input" property of
`resolveOnBehalfOfUserId` still holds. `consentId` is derived inside the resolver, never passed in, for
the same reason.

Implementation notes:

1. `resolveOnBehalfOfUserId` = the chain `addEntitlement` and `CallContext.accountableUserId` both
   inline today (`ResourceUser.find(By(userId_)) → CreatedByConsentId → getConsentByConsentId →
   consent.userId`). Both then delegate to it; CallContext
   keeps its `consentCreator.or(consenter)` precedence in front.
2. Cache (as built): a private Guava cache in `LiftUsers` (the `Caching` wrapper cannot skip
   memoising selected answers), TTL from props `on_behalf_of_user_id.cache_ttl_seconds`, default
   600, `0` disables. Memoises original users (answer = self) and bound consent users. Note for
   local test runs: `AbacRuleTests` (and the other dynamic-code suites) return 400 on rule creation
   unless `allow_user_generated_scala_code=true` is in the test props, as CI sets it; that is
   unrelated to this work. Does **not**
   memoise "consent user whose consent names no human yet", dangling ids, or the invariant
   failure, so a BG consent bound a minute later is seen at once.
3. Agents (decided, see Decisions 4): an agent is a consent user; there is no consent-less
   agent and no second column. The resolver is one hop (no chains) and asserts the target row
   `isOriginalUser`; if not, WARN and `Failure`. The `Reject` policy on consent creation by a
   consent user is what keeps chains from ever being written.
4. Every delegated attribution logs WARN with the `ref` name, `userId`, `onBehalfOfUserId`,
   `consentId`. A WARN firing in tests means a site chose the wrong reference or a policy is wrong.
5. **Checked 2026-09-02 (Phase 0 row 9): the two sources always agree.** `CallContext` prefers
   the JWT claim `createdByUserId` (`consentCreator`); the resolver follows the row column
   `consent.userId`. Every OBP-native create endpoint (`Http4s310:4451`, `Http4s500:1265`,
   `Http4s510:5025`) writes both from the same logged-in `user`; BG/UK set both to the authorising
   user at authorisation. No path writes them differently, so precedence is a no-op today and the
   resolver's answer equals the request-layer answer. Kept as two fields anyway (decided).

Call sites after Phase 1:

```scala
// ApiSession.scala
def onBehalfOfUser: Box[User] = consentCreator.or(consenter).or(user)          // was humanUser
def onBehalfOfUserId: String =                                                  // was accountableUserId
   consentCreator.or(consenter).map(_.userId).filter(_.nonEmpty)
    .openOr(Users.users.vend.resolveOnBehalfOfUserId(user.map(_.userId).openOr("")))

// MappedEntitlements.addEntitlement: the magic-string exemption becomes a reference choice
val ref = if (createdByProcess == Constant.consent_user) UserReference.Entitlement_UserId_ConsentScope
          else UserReference.Entitlement_UserId
for { targetUserId <- Users.users.vend.attributedUserId(userId, ref); ... }

// MappedTransactionRequestProvider: a record-both table, one call, two columns
for { a <- Users.users.vend.attributionOf(userId, UserReference.TransactionRequest_UserId) } yield
   tr.mUserId(a.userId).mOnBehalfOfUserId(a.onBehalfOfUserId)
```

### The policy file — an attribution policy for every user-reference column (from a grep of Mapped classes)

**Written 2026-09-02: `obp-api/src/main/scala/code/users/UserReference.scala` is now the source of
truth — 75 references (13 `UseAuthenticatedUserId`, 59 `UseOnBehalfOfUserId`, 3 `Reject`), 6
not-a-user-id exclusions.** The tables below were the draft; the file was
generated from an inventory of every model in `ToSchemify.models` and covers more than the tables.
Columns the draft missed, and the policy given (change in the file if wrong):

| policy | added |
|---|---|
| `UseAuthenticatedUserId` | `AuthUser.user` (login row), `OpenIDConnectToken.AuthUserPrimaryKey`, `MappedUserRefreshes.mUserId`, `MetricArchive.userId`, `DynamicDataAccess.GrantedBy` (audit) |
| `UseOnBehalfOfUserId` | `MappedUserScope.mUserId`, `DirectDebit.UserId`, `DynamicData.UserId`, `DynamicDataAccess.UserId`, `MappedCounterpartyWhereTag.user`, `MappedTag.user`, `MappedWhereTag.user`, `MappedTransactionImage.user`, `MappedCustomerMessage.user`, `MappedKycDocument.user`, `MappedKycStatus.user`, `MappedSocialMedia.user`, `MappedKycCheck.user`, `SignatoryPanel.UserIds`, `ChatMessage.MentionedUserIds` |
| `Reject` | `Token.userForeignKey` (OAuth token issued to a consent user) |
| not a user id | `MappedBankAccount.holder`, `MappedTransaction.counterpartyAccountHolder`, `AccountAccessRequest.CheckerComment`, `MappedKycCheck.mStaffName`, `MappedMeeting.mStaffToken`, `MappedEntitlement.mCreatedByProcess`, `ResourceUser.userId_` / `CreatedByConsentId` / `CreatedByUserInvitationId` |

`AccountAccessRequest` is three references (requestor, target, checker). Record-both tables are one
reference with two fields (`TransactionRequest_UserId`). Classes are named as fully-qualified strings, not
`classOf`, so the file imports nothing and cannot trigger Mapper initialisation.

Rule: **the agent owns nothing durable.** Only the consent's own authorisation rows stay on the consent user.

Every user-reference column gets exactly one **attribution policy**, which says what value the
column takes when the user is a consent user (or an agent user with an on-behalf-of user):

| policy | meaning |
|---|---|
| `UseAuthenticatedUserId` | the authenticated user's own id; no resolver |
| `UseOnBehalfOfUserId` | the on-behalf-of user's id, via the resolver in the provider |
| `Reject` | the request is refused with 400 |

"Record both" below is a table-level description: one `UseAuthenticatedUserId` column and one
`UseOnBehalfOfUserId` column on the same row. Such tables make one `attributionOf` call with a
table-level reference and write both fields of the `Attribution`.

The policy file is **main-tree Scala**, because `Users.attributionOf` reads it at runtime
(proposed: `obp-api/src/main/scala/code/users/UserReference.scala`):

```scala
sealed trait AttributionPolicy
object AttributionPolicy {
  case object UseAuthenticatedUserId extends AttributionPolicy
  case object UseOnBehalfOfUserId    extends AttributionPolicy
  case object Reject                 extends AttributionPolicy
}

/** One value per user-reference column (or per record-both table). */
sealed abstract class UserReference(val policy: AttributionPolicy, val mapperClass: String,
                                    val fields: List[String], val note: String)
object UserReference {
  case object AccountAccess_UserFk              extends UserReference(UseAuthenticatedUserId, "code.views.system.AccountAccess",            List("user"), "…")
  case object Entitlement_UserId_ConsentScope    extends UserReference(UseAuthenticatedUserId, "code.entitlement.MappedEntitlement",         List("mUserId"), "only when createdByProcess == consent_user …")
  case object Entitlement_UserId                extends UserReference(UseOnBehalfOfUserId   , "code.entitlement.MappedEntitlement",         List("mUserId"), "the role holder …")
  case object AccountHolders_User               extends UserReference(UseOnBehalfOfUserId   , "code.accountholders.MapperAccountHolders",   List("user"), "…")
  case object Bank_CreatedByUserId              extends UserReference(UseOnBehalfOfUserId   , "code.model.dataAccess.MappedBank",           List("CreatedByUserId"), "…")
  case object TransactionRequest_UserId extends UserReference(UseOnBehalfOfUserId, "code.transactionrequests.MappedTransactionRequest", List("mUserId", "mOnBehalfOfUserId"), "record both")
  case object Consent_UserId                    extends UserReference(Reject,                 "code.consent.MappedConsent",                 List("mUserId"), "…")
  case object Consumer_CreatedByUserId          extends UserReference(Reject,                 "code.model.Consumer",                        List("createdByUserId"), "…")
  // … one per user-reference column in ToSchemify.models — 75 today
  val all: List[UserReference] = List(...)   // the frozen test walks this
}
```

**Naming (settled 2026-09-15).** A reference is named after the **column it governs**, not after a
role: `<Table>_<Column>`, with the `Mapped`/`Mapper` class prefix and Lift's `m` field prefix
dropped. So `MappedBank.CreatedByUserId` → `Bank_CreatedByUserId`, `MapperAccountHolders.user` →
`AccountHolders_User`.

A reference that governs **two** columns — the record-both tables, and the tables where one policy
covers a `CreatedByUserId`/`UpdatedByUserId` pair — is named after the first only, and `fields`
carries both: `MappedTransactionRequest.{mUserId, mOnBehalfOfUserId}` → `TransactionRequest_UserId`.
Enumerating the second column in the name was tried and dropped, because it made `Table_A_B`
ambiguous: `TransactionRequest_UserId_OnBehalfOfUserId` had a second *column* in third position
while `Entitlement_UserId_ConsentScope` has a *disambiguator* there, and nothing in the name said
which. `fields` is the contract and the name is only the handle, so `Table_Column` now holds
everywhere and a third part means exactly one thing. The earlier role labels (`BankCreator`,
`AccountHolderUser`, …) read as English but did not say which column they wrote, which is the one
thing a reader of this table needs. One invented disambiguator: `MappedEntitlement.mUserId` carries
two policies, so the consent-engine one is `Entitlement_UserId_ConsentScope`.

The underscore is doing real work and is not decoration. `Counterparty_CreatedByUserId`
is legible where the run-together form was not, and it marks the table/column boundary that a reader
otherwise has to guess at (`AccountHolders_User` vs `AccountHolder_sUser`). A dot would read better
still, but a Scala `case object` identifier cannot contain one — it would mean nesting each table in
its own wrapper object, which regroups the file **by table** when the thing that governs behaviour is
the **policy**, and three tables (`MappedEntitlement`, `ChatMessage`, `DynamicDataAccess`) carry two
policies each and would have to straddle the sections. An underscore buys the same boundary flat.

`mapperClass` is a fully-qualified **string**, not `classOf`, so the file imports nothing and cannot
trigger Mapper initialisation; `note` carries the reason, and every one of the 75 has one.

Carrying `mapper` + `fields` on each value is what lets the Phase-4 frozen test tie every
reflected Mapper column to exactly one reference (one column may have two references only when
they differ by process, as `MappedEntitlement.mUserId` does).

### UseAuthenticatedUserId — authorisation materialisation, NO resolver
| # | class | field | note |
|---|---|---|---|
| 1 | `views/system/AccountAccess` | user id | views copied from the JWT each request; ALL_CONSUMERS rows; has lifecycle GC |
| 2 | `entitlement/MappedEntitlements` | `mUserId` **only when** `createdByProcess == consent_user` | existing exemption |
| 3 | `model/dataAccess/ResourceUser` | itself | the consent user's own row |
| 4 | `userlocks/UserLocks` | `UserId` | lock the user (a consent user never logs in; effectively unused) |
| 5 | `transactionChallenge/MappedExpectedChallengeAnswer` | `ExpectedUserId` | challenge is answered by the initiating user |
| 6 | `chat/MappedChatMessage` | `SenderUserId` | sender = the user is truthful; `MentionedUserIds` are humans by construction |
| 7 | `api/pemusage/MappedPemUsage` | `LastUserId` | audit |

### Record both — `UseAuthenticatedUserId` column + `UseOnBehalfOfUserId` column on one row
| # | class | user field | on-behalf-of field | action |
|---|---|---|---|---|
| 8 | `metrics/MappedMetrics` | `userId` | via `consent_reference_id` | none |
| 9 | `metrics/ConnectorTrace` | `userId` | via consent ref | none |
| 10 | `transactionrequests/MappedTransactionRequestProvider` | `mUserId` | `mOnBehalfOfUserId` | make `mOnBehalfOfUserId` use the resolver (today `onBehalfOfUser.or(consenter)` only — misses the DB chain) |
| 11 | `entitlement/MappedEntitlements` | `mGrantedByUserId` (audit: who granted) | `mUserId` (target, redirected) | none |

### UseOnBehalfOfUserId — ownership / attribution, resolver in the provider's create/link
| # | class | field(s) | provider entry point to guard |
|---|---|---|---|
| 12 | `accountholders/MapperAccountHolders` | `user` FK | `getOrCreateAccountHolder(user, …)` (:39) — resolve `user` first |
| 13 | `usercustomerlinks/MappedUserCustomerLink` | `mUserId` | `createUserCustomerLink(userId, …)` (:14) |
| 14 | `accountapplication/MappedAccountApplication` | `mUserId` | **Corrected 2026-09-15: do NOT make the provider default.** The note above predates the Phase 3 guards. `user_id` is always explicit at `Http4s310.scala:3228` — `userId = postedData.user_id`, never defaulted to the caller — so there is no implicit-self path for a redirect to fire on. If one did fire it would silently substitute an id the caller explicitly named, turning a correct 400 into a quiet rewrite and inverting the doctrine (explicit → refuse, implicit → redirect). Same shape as `AccountAccessRequest_TargetUserId`: guard at the endpoint, which is already done, and reclassify rather than wire. |
| 15 | `accountaccessrequest/AccountAccessRequest` | `RequestorUserId`, `TargetUserId`, `CheckerUserId` | create + approve (v6 endpoints already guard target) |
| 16 | `entitlementrequest/MappedEntitlementRquests` | `mUserId` | create (v3.0 endpoint resolves already) |
| 17 | `apicollection/ApiCollection` | `UserId` | create |
| 18 | `users/MappedUserAttribute` | `UserId` | create/update |
| 19 | `users/UserAgreement`, `users/UserInitAction` | `UserId` | create |
| 20 | `context/MappedUserAuthContext`, `…Update` | `mUserId` | create (consent copies the human's contexts into ConsentAuthContext separately — that path is fine) |
| 21 | `dynamicEntity/*` (3), `dynamicEndpoint/*`, `dynamicResourceDoc`, `dynamicMessageDoc`, `connectormethod/ConnectorMethod`, `abacrule/AbacRuleTrait` | `UserId` / `CreatedByUserId` / `UpdatedByUserId` | create/update |
| 22 | `metadata/counterparties/MapperCounterparties` | `mCreatedByUserId` | create |
| 23 | `model/dataAccess/MappedBank` | `CreatedByUserId` | create (creator-grant already resolved at endpoint) |
| 24 | `organisation/Organisation`, `payeelookup/PayeeLookup`, `routingscheme/RoutingScheme`, `utilitypayment/UtilityPaymentCallback` | `CreatedByUserId` | create |
| 25 | `standingorders/MappedStandingOrder` | `UserId` | create |
| 26 | `mandate/MandateTrait` | `CreatedByUserId`, `UpdatedByUserId`, `UserIds` | create/update |
| 27 | `webhook/*` (3) | `CreatedByUserId` / `mCreatedByUserId` | create |
| 28 | `chat/MappedChatRoom`, `MappedParticipant`, `MappedReaction`, `ChatEmailDigestState` | `CreatedByUserId` / `UserId` | create (Portal chat: a human's room, participation, reaction) |
| 29 | `crm/MappedCrmEventProvider` | `mUserId` | create |
| 30 | `kyccheck` `mStaffUserId`, `meetings` `mStaffUserId`/`mCustomerUserId` | | create (staff = human operator) |

### Reject — a consent user must not do this at all
| # | class | why |
|---|---|---|
| 31 | `consent/MappedConsent.mUserId` (consent creating a consent) | nested delegation; 400 at the create endpoints |
| 32 | `model/OAuth.createdByUserId` (tokens/consumers minted by a consent user) | credentials outlive the consent; 400 |

### Phase 1 deliverables (all ✅ 2026-09-02)

1. `obp-api/src/main/scala/code/users/UserReference.scala`: `AttributionPolicy`, `Attribution`, and `UserReference` with **one case object per row of the tables above (all 32)** and `all` listing them. Not a database table, and not these markdown tables: the markdown is the working draft, the Scala file is what runs (via `Users.attributionOf`) and what `UserReferenceAttributionPolicyTest` (Phase 4) checks.
2. `Users` trait: `resolveOnBehalfOfUserId`, `actsForSelf`, `attributionOf`, `attributedUserId`.
3. `LiftUsers`: the implementation, with the cache rule and the `isOriginalUser` check.
4. `CallContext.onBehalfOfUserId` delegates to the resolver (precedence kept).
5. `MappedEntitlements.addEntitlement` via `attributedUserId` with `Entitlement_UserId_ConsentScope` / `Entitlement_UserId`.
6. `MappedTransactionRequestProvider` via one `attributionOf(userId, TransactionRequest_UserId)` call, both columns.
7. `AgentDelegationTest` scenarios (Phase 4, item 1) green; grep for any other inline copy of the chain and point it at the resolver.

## Manual tests after Phase 1 (litmus, against a running instance) — **run 2026-09-03**

Run against the local instance on :8080 (commit `73cceba36`, which includes Phase 1), H = the
Opey DirectLogin user (a super admin), C = an IMPLICIT consent on bank `simonopey` carrying
`CanCreateEntitlementAtOneBank` and `CanCreateAccount`, granted to Opey's consumer. Results:

| # | result |
|---|---|
| 1 | ✅ as C: `user_id` = consent user, `on_behalf_of.user_id` = H. As H: `on_behalf_of` null. Observation: `on_behalf_of` embeds H's full entitlement and view lists (large); not this plan's concern. |
| 2 | ✅ as written the step is stale: v2.0.0 and v7.0.0 `addEntitlement` both refuse an explicit consent-user `USER_ID` with 400 `OBP-30107` (Phase 3 doctrine, already in place), so the provider redirect is not reachable from the endpoint; it is covered by `AgentDelegationTest`. **Bug found**: v7 returned 403 `UserHasMissingRoles` for C although C held `CanCreateEntitlementAtOneBank@simonopey` — the v7 doc declared the roles, so the middleware checked them without a bank (no `BANK_ID` in the URL) and only super admins got through. Fixed: doc `.disableAutoValidateRoles()`, handler checks against `body.bank_id` with 403 (same as v2.0.0, whose doc has `None`). |
| 3 | ✅ C's only `mappedentitlement` rows are its two `consent_user` rows. Also found: 11 pre-fix strand rows dated 2026-08-31 (`manual` grants on consent users, e.g. `CanCreateBank`, `CanCreateEntitlementAtOneBank@simon.bank`) — the incident this plan came from; clean-up by hand is the operator's call (Phase 5 item 3). |
| 4 | ✅ `mappedtransactionrequest` row: `muserid` = C, `monbehalfofuserid` = H; one WARN `attribution TransactionRequest: … writing on-behalf-of user H`. Listed as H under `transaction_requests_with_charges`. |
| 5 | ✅ (confirms the gap) as C, `POST /obp/v5.1.0/my/consents/IMPLICIT` returned 201 with a consent whose `mUserId` is C. Row deleted by hand afterwards. Phase 3 makes this a 400. |
| 6 | ⏭ no BG sandbox; the not-cached case is covered by `AgentDelegationTest` ("BG-style: … NOT pinned in the cache"). |
| 7 | ⏭ needs a props change and a restart of the shared local instance; not done. |

Set-up once: a human H logged in (Portal / API Explorer), an OBP-native consent C granted by H
with roles that let it act (e.g. `CanCreateEntitlementAtOneBank`, `CanCreateAccount`), and the
consent JWT for C. Calls "as C" send `Consent-JWT: <jwt>` plus the consumer key; calls "as H" use
H's normal token.

1. **Who am I / on whose behalf.** As C: `GET /obp/v6.0.0/users/current`. Expect `user_id` = C's
   consent user, `on_behalf_of.user_id` = H. As H: `on_behalf_of` is null. (Row 8 of Phase 0.)
2. **Entitlement redirect.** As C: `POST /obp/v7.0.0/users/<C's consent user id>/entitlements`
   with a role C may grant. Expect 201 and the entitlement's `user_id` = H, not C. Then
   `GET /obp/v6.0.0/users/current` as H shows the role. Log has one WARN from
   `attribution Entitlement_UserId` naming C, H, and the consent id.
3. **Consent-engine exemption.** Create a new consent as H and use it once. The consent user's own
   rows in `entitlement` (createdByProcess `consent_user`) are on the consent user, not on H.
4. **Payment attribution.** As C: create a transaction request (`SANDBOX_TAN` is enough) on one of
   H's accounts. Expect the row in `transactionrequest`: `muserid` = C's consent user,
   `monbehalfofuserid` = H. Then `GET .../transaction-requests` as H lists it.
5. **Reject.** As C: `POST /obp/v5.1.0/my/consents/IMPLICIT` (create a consent while being a
   consent user). Until Phase 3 this still succeeds — it is the litmus that Phase 3 is needed.
   After Phase 3: 400 `OBP-30107` naming `Consent_UserId`.
6. **BG late binding (if a BG sandbox is set up).** Create a BG consent via the TPP flow, call
   `/users/current` with it before authorisation: `on_behalf_of` null. Authorise as H, call again
   within a minute: `on_behalf_of.user_id` = H (proves the unbound answer was not cached).
7. **Cache.** Set `on_behalf_of_user_id.cache_ttl_seconds=0` in props, repeat 2: same result, and
   the log shows the chain walked on every call. Restore the default.

## Phase 2 — provider guards (UseOnBehalfOfUserId)

Pattern, one line at the top of each create/link method, naming the column being written:

```scala
for {
  ownerId <- Users.users.vend.attributedUserId(userId, UserReference.AccountHolders_User)  // WARNs when delegated
  ...
```

Providers that return a plain value rather than a `Box` either grow a `Box` (preferred) or
`openOr(userId)` with a comment. Both ways to be wrong — forgetting the call, or naming the wrong
reference — are caught by the Phase-4 sweep; the second is also visible in review.

1. Providers that take a `User` (AccountHolders): resolve to id, re-fetch the on-behalf-of `User` once (cached).
2. Keep endpoint-level `cc.onBehalfOfUserId` uses; they become redundant clarity, not the mechanism.
3. `UseAuthenticatedUserId` writers that share a provider method with a `UseOnBehalfOfUserId` path (views materialiser, consent entitlements) pass a different `UserReference` (e.g. `Entitlement_UserId_ConsentScope` vs `Entitlement_UserId`); no more string-typed exemptions.

Order of attack (highest strand-risk first): AccountHolders → UserCustomerLink → AccountApplication → UserAuthContext → ApiCollection/UserAttribute → the rest mechanically. That order has been departed from twice and both times for a reason worth repeating: DynamicEntity/DynamicData (row 3) came early because the Portal and API Manager conversation entities needed it, and Bank (row 4) came early because the stranding it describes had already happened on the live instance. So the named order now applies to what is left, not to what has been done.

Progress:

| # | provider | status |
|---|---|---|
| 1 | `MapperAccountHolders.getOrCreateAccountHolder` (`AccountHolders_User`) | ✅ 2026-09-03. Resolves `user.userId` via `attributedUserId`, re-fetches the on-behalf-of `User` once when delegated, writes the row for it. All five callers (v5/v7 createAccount via `BankAccountCreation`, holding accounts, `AfterApiAuth`, `AuthUser.refreshUser`, sandbox import) go through it. `AgentDelegationTest` has three scenarios (consent user → human holds; original user unchanged; unbound consent fails closed). Endpoint-level `cc.onBehalfOfUserId` in v5/v7 createAccount stays as clarity. |
| 2 | `MappedUserCustomerLink.createUserCustomerLink` | ✅ 2026-09-10, committed. Provider resolves via `linkOwnerUserId` on the three methods keyed by a single user id: `createUserCustomerLink`, `getOCreateUserCustomerLink`, and the two-argument `getUserCustomerLink`. Those three had to move together: the two-argument lookup is every caller's "already linked?" pre-check immediately before a create, and the table carries `UniqueIndex(mUserId, mCustomerId)` — a redirected create paired with an unredirected pre-check passes the check on the consent user and then breaks the index on the human (500, not the intended 400 `CustomerAlreadyExistsForUser`, since `createUserCustomerLink` has no `tryo`). `getUserCustomerLinksByUserId` is deliberately **not** resolved: it also serves the admin lookup at `GET /banks/BANK_ID/user_customer_links/users/USER_ID`, where the id is an explicit target and rewriting it would silently answer a different question; endpoints meaning "my links" pass the resolved id themselves. Phase 3 guards added to all five explicit-target callers (v1.4.0 `addCustomer`, v2.0.0 / v2.1.0 `createCustomer` — guarded only when `user_id` is supplied, since an omitted one means the caller and the provider redirects it — and v2.0.0 / v4.0.0 `createUserCustomerLinks`), each with `InvalidUserId` added to the ResourceDoc error list and a digest-bound `parity_allowlist.json` entry. `AgentDelegationTest` has five scenarios (consent user → human; original user unchanged; unbound consent fails closed; the pre-check asks about the row the create would write; listing by user id is not redirected). 33 scenarios green. |
| 3 | `DynamicData.UserId` (`DynamicData_UserId`), `DynamicEntity.UserId` (`DynamicEntity_UserId`) | ✅ 2026-09-04, committed. Provider `MappedDynamicDataProvider` resolves the caller on **every** entry point (save, update, get, getAll, delete, existsData): personal rows are keyed by the same column on reads and writes, so the redirect must be symmetric or a consent user could not read back what it wrote. Definition creator resolved in `MappedDynamicEntityProvider.createOrUpdate`. **Decided 2026-09-04 (access control): a consent user gets no `personal_requires_role=false` waiver** — on `/my` endpoints it must hold the entity's role, so a Consent has to name the entity explicitly before its holder reaches the human's personal rows (`Http4sDynamicEntity.personalRoleWaived`); the projection read path resolves the owner the same way (`personalRowOwner`). Doc strings of the six My endpoints say so; `UserHasMissingRoles` is now always in their error lists. Tests: `AgentDelegationTest` (provider + definition) and `DynamicEntityConsentUserTest` (HTTP: human no role → 201; consent without role → 403 naming the role; consent with roles → 201, row readable by both, stored on the human). Consumer: the Portal / API Manager Opey conversation entities (`obp_portal_opey_conversation`, `obp_manager_opey_conversation`); the apps write those as the human; **built 2026-09-04 in OBP-Frontend** (definitions, startup bootstrap, `ConversationRecorder`, rows under My Data). **Out of scope here: row-level (ACL) entities** — `DynamicDataAccess.UserId` bootstrap grant and the `allows` checks both stay on the consent user (consistent with each other: rows strand, nothing leaks); `DynamicDataAccess_UserId` is a later Phase 2 row. |
| 4 | `MappedBank.CreatedByUserId` (`Bank_CreatedByUserId`) | ✅ 2026-09-14. `LocalMappedConnector.bankCreatorUserId` resolves the caller before `createOrUpdateBank` stamps the row. Two sources in the order `CallContext.onBehalfOfUserId` uses: the request layer (`consentCreator`/`consenter`, which a BG/UK consent carries on the request and the stored chain cannot know) wins, otherwise `attributedUserId(_, Bank_CreatedByUserId)` walks the stored chain, applies the policy and logs the delegation. **Closes the defect seen in the wild 2026-09-03**: a bank created through Opey under a temporary consent had `createdbyuserid` = the consent user, so it dropped out of every "banks created by me" read once that consent was revoked. Only one of the four `MappedBank.create` sites sets the column — the Boot, sandbox-import and internal-connector paths have no user and leave it empty. The read side (`Http4s700` self-service quota) already counted via `humanAndAgentUserIds`, so it keeps matching either way; write and read now agree on the human. `AgentDelegationTest` has five scenarios (original user unchanged; consent user → human; unbound consent fails closed; request-layer consenter wins; no authenticated user leaves it empty), and the `Bank_CreatedByUserId` entry is gone from `OnBehalfOfOwnershipSweepTest.notYetWired` — the ratchet fails if it comes back. 49 scenarios green. |
| 5 | `MappedCounterparty.mCreatedByUserId` (`Counterparty_CreatedByUserId`) | ✅ 2026-09-15, and the first row decided as **record both** rather than redirect. `MapperCounterparties.counterpartyCreators` makes one `attributionOf` call and writes the actor to `mCreatedByUserId` and the human to a new `mCreatedByOnBehalfOfUserId`. Two reasons it is not a redirect: (a) a counterparty is the control on **where money may be sent**, so "which agent created this" has to be answerable from the row rather than by correlating a timestamp against a metrics table with its own retention — the same argument that made `MappedTransactionRequest` record both; (b) `mCreatedByUserId` is published as `created_by_user_id` on the **v2.2.0 and v4.0.0** counterparty responses, so redirecting it would make a STABLE field report a human for something an agent did (`CounterpartyTest` still green, confirming the API is unchanged). Safe at provider level because none of the four call sites takes a user id from the request — v2.2.0 `createCounterparty`, v4.0.0 `createExplicitCounterparty` and `createCounterpartyForAnyAccount`, and the v5.0.0 VRP consent flow all pass the caller's own id — so there is no explicit target a redirect could silently substitute. The new column is **internal**: not on `CounterpartyTrait` (obp-commons, implemented by the remote-connector DTOs) and not in any JSON, because adding a field to those STABLE responses would change the frozen contract; a v7 read can expose it later. No migration — Schemifier adds new columns, as it did for `MappedTransactionRequest.mOnBehalfOfUserId`. **Limitation**: the provider receives only a `String`, so unlike `bankCreatorUserId` it cannot honour the request layer's `consentCreator`/`consenter`; a BG/UK consent with no stored human yet falls back to the actor, which is the documented fail-closed behaviour. `AgentDelegationTest` has four scenarios (original user in both columns; consent user → actor + human; unbound consent fails closed; a broken chain still names the actor rather than blanking the audit column). 53 scenarios green. |
| 6 | the three webhook creator columns: `AccountWebhook_CreatedByUserId`, `SystemAccountNotificationWebhook_CreatedByUserId`, `BankAccountNotificationWebhook_CreatedByUserId` | **Deferred 2026-09-16, deliberately, and the reason is written down in `todo/webhook_attribution.md`.** The audit corrected two things this plan believed. First, the creator column is *not* an ownership key: the only read that treats it as one, `getAccountWebhooksByUserIdFuture`, has no caller anywhere in the repo, while the live list endpoint `getAccountWebhooks` (`Http4s310.scala:625`) is gated on `canGetWebhooks`, returns every webhook at the bank and treats `user_id` as an optional filter the caller supplies. Editing does not consult the column either. So nobody is locked out of an agent-created webhook, and the `UserReference` comment that claimed otherwise has been corrected. Second, `created_by_user_id` is published on the v3.1.0 and both v4.0.0 responses, so a redirect would make a STABLE field report a person for something an agent did. The agreed direction is therefore **record both**, as `MappedCounterparty` does, and the argument is sharper here than for a counterparty because nothing garbage-collects a webhook when its Consent is revoked (Phase 5 item 3 declines revocation GC), so an agent-created webhook keeps POSTing account events forever with `created_by_user_id` naming an identity that no longer exists. It is not built, because the webhook code had not been read in a long time and the audit found two dead paths in it. The three entries in `OnBehalfOfOwnershipSweepTest.notYetWired` therefore carry their own `webhookDeferred` reason instead of the generic `mechanicalBatch` one, so that nobody picks them up as a quick win. **The second dead path is now closed**: `deleteSystemAccountNotificationWebhookFuture` and `deleteBankAccountNotificationWebhookFuture` existed but no endpoint called either, so a notification webhook could not be removed over the API by anyone. v7.0.0 gained `deleteSystemAccountNotificationWebhook` (`DELETE /web-hooks/account/notifications/on-create-transaction/WEBHOOK_ID`, role `canDeleteSystemAccountNotificationWebhook`) and `deleteBankAccountNotificationWebhook` (the same path under `/banks/BANK_ID`, role `canDeleteAccountNotificationWebhookAtOneBank`), both answering 204, with `NotificationWebhookNotFound` (OBP-30151) and `DeleteWebhookError` (OBP-30152). A webhook belonging to another bank reads as 404 rather than 403, because the role is held per bank and 403 would let a caller with the role at one bank discover which webhook ids exist at every other bank. Eight scenarios in `Http4s700RoutesTest`; 179 scenarios green on 2026-09-17. The endpoints, the two roles and the two error codes are committed in `f2dddcd16`. Adding attribution to a row that could not be deleted would have been the wrong order. **What "deferred" means on the wire, since the word does not say it:** nothing is refused and nothing is redirected. A consent user holding `canCreateWebhook` that POSTs to `/banks/BANK_ID/account-web-hooks` gets the ordinary **201**, and the row is stamped with the *agent's* id, because all three create endpoints pass `user.userId` straight to the provider (`Http4s310.scala:2473`, `Http4s400.scala:5638`, `Http4s400.scala:5661`) and no provider calls `attributionOf`. The human is recorded nowhere on the row. That is exactly the pre-plan behaviour, so "deferred" is the status quo continuing, not a hold: the webhook keeps firing after the Consent is revoked and `created_by_user_id` then names an identity that no longer exists. Decision 12 is the proposal to make that state say so out loud instead of looking like success. The first dead path, `getAccountWebhooksByUserIdFuture` with no caller, is untouched. |

**After Phase 2 — the third set of things a Consent carries.** Personal resources are owned, not
granted, so delegating them through entity Roles over-grants. Design settled 2026-09-04 in
`ideas/CONSENT_MY_RESOURCES.md` (`my_resources` wrapper with `personal_dynamic_entities`,
`api_collections`, ... as typed lists). **Built 2026-09-04 and committed, for `personal_dynamic_entities`**; the
interim entity-Role gate is replaced by the `my_resources` check (`Http4sDynamicEntity.consentCoversPersonalResource`).
Client side (OBP-MCP, Opey, OBP-Frontend) built 2026-09-04 too, see the note.

## Phase 3 — explicit-target guards (endpoint 400s)

Doctrine (settled 2026-09-01): implicit self → redirect in provider; explicit `USER_ID` naming a consent user → 400 `InvalidUserId … names a consent user`. Already done: addEntitlement (v2.0/v7), addUserToGroup (v6), createAccount (v2.0/v3.1/v4.0/v5.0/v7), grantUserAccessToViewById (v5.1), account access requests (v6), account applications (v3.1). To sweep: API collections, user attributes, auth contexts, KYC/meeting staff ids (createUserCustomerLink was done 2026-09-10, row 2 of Phase 2). Webhooks were on this list and came off it on 2026-09-16: no webhook endpoint takes a user id as a target, since neither create body carries one, and the only `user_id` any of them accepts is the optional filter on the v3.1.0 `getAccountWebhooks` read, which names nothing durable. There is nothing there for an explicit-target guard to refuse, so the webhook question is entirely a Phase 2 one. `Reject` columns refuse in the provider (`attributionOf` returns Failure); endpoints map that to 400 and may keep an early explicit check for a nicer message, but the floor holds without them.

**Tests — ✅ 2026-09-13, `code/api/sweep/ExplicitTargetConsentUserSweepTest.scala`, 7 scenarios green** (shard 8, the catch-all; `code.api.sweep` is not in the shard table). 20 probes over the 19 guards that exist today, driven in-process through `Http4sApp.httpApp` like the other sweeps in that package. Shape:

- The caller is an ordinary human holding every role (`SweepFixtures.omniscientCaller`), **not** a consent user — the guard is about the id in the request, not about who is asking, and a consent-user caller would additionally have to get past the provider redirect to prove anything.
- Each probe asserts 400 **and** that the message starts with `InvalidUserId` and names the agent identity. Status alone is not enough: several of these guards sit behind checks that fire first (customer-number availability on v1.4.0/v2.0.0/v2.1.0, grant permission on v5.1.0, maker/checker on v6.0.0), and a probe that tripped one of those would pass while testing nothing.
- **Verified by negative control, not by reading**: re-run with an ordinary user id in place of the consent user, 14 of the 20 probes answer 200/201 (they reached the guard and passed it) and the other 6 answer a *later* check (409 entitlement exists, 404 group/customer not found, 409 request exists). So every probe demonstrably reaches its guard.
- `updateAccountApplicationStatus` (v3.1) and `approveAccountAccessRequest` (v6) read the target off a **stored row**, not the request. Those rows are written through their providers, because the creation-side guard means the API can no longer produce one — which is the situation those two guards exist for.
- 20 probes / 19 guards: v7.0.0's `createAccountV700` (POST) and `createAccountWithIdV700` (PUT) share one `createAccountCommon` guard, and the PUT arm validates the account id before reaching it, so both routes are probed.
- A seventh scenario **counts the guards at their source** — lines interpolating `InvalidUserId` alongside "an agent identity minted by a Consent", a shape no other code in the tree has — and fails if the count and this table disagree. A hand-written table of nineteen near-identical things drifts; a twentieth guard added next month is otherwise covered by nothing, with nothing saying so.
- `SweepFixtures` gained `realAccountId` and a shared `callApi`, because `SweepFixturesDuplicationTest` polices exactly this kind of copy-paste inside that package. **Follow-up not taken**: `AuthSweepTest`, `FailureSweepTest` and `SuccessSweepTest` still carry their own `call` copies and `AuthSweepTest` its own account lookup — migrating those three onto the shared helpers is a small mechanical diff, deliberately left out of this change.

The 19 guards, by version: v1.4.0 `addCustomer`; v2.0.0 `createAccount`, `createCustomer`, `createUserCustomerLinks`, `addEntitlement`; v2.1.0 `createCustomer`; v3.1.0 `createAccountApplication`, `updateAccountApplicationStatus`, `createAccount`; v4.0.0 `createUserCustomerLinks`, `addAccount`, `createSettlementAccount`; v5.0.0 `createAccount`; v5.1.0 `grantUserAccessToViewById`; v6.0.0 `addUserToGroup`, `createAccountAccessRequest`, `approveAccountAccessRequest`; v7.0.0 `addEntitlement`, `createAccountCommon`.

## Phase 4 — tests

1. **`AgentDelegationTest`** — extend: `resolveOnBehalfOfUserId` for original user / consent user / dangling consent (fails closed) / cache hit after consent later bound (BG case) / consent whose user is itself a consent user → Failure; `attributionOf` for each of the three policies.
2. **`UserReferenceAttributionPolicyTest`** — ✅ **2026-09-11**, `obp-api/src/test/scala/code/users/`, 6 scenarios green (shard 8, the catch-all). Iterates `ToSchemify.models`, reflects Mapper fields matching `(?i)userid|createdby|grantedby|holder`, and asserts: every such column is named by a `UserReference` or listed in `notUserIdColumns`; every `UserReference` names a Mapper that is in the schema; every named field exists; a column named by several references has references that differ by *policy* (the deliberate case is `MappedEntitlement.mUserId` — `Entitlement_UserId` vs `Entitlement_UserId_ConsentScope`); no column is both given a policy and excluded; and no `notUserIdColumns` entry is inert.

   **Found on first run — the map was not complete:**
   - `ApiProductSubscription.CreatedByUserId` and `DynamicGlossaryItem.CreatedByUserId` had no policy at all. Both tables landed after the policy file was written, which is exactly the drift this test exists to catch. Added as `ApiProductSubscription_CreatedByUserId` / `DynamicGlossaryItem_CreatedByUserId`, both `UseOnBehalfOfUserId` (consistent with the other `CreatedByUserId` references).
   - `PemUsageLastUser` named `code.api.pemusage.PemUsage`, which is **not in `ToSchemify.models`** — so it has no table. It is an unwired stub: `MappedPemUsageProvider`'s body is empty and nothing outside its own package references it. The policy entry was removed; if PemUsage is ever wired up this test will demand it back. **The dead stub itself was left in place** — deleting a feature skeleton is a separate call.
   - Four `notUserIdColumns` entries (`AccountAccessRequest.CheckerComment`, `DynamicChangeRequest.CheckerComment`, `MappedKycCheck.mStaffName`, `MappedMeeting.mStaffToken`) excluded columns the pattern never catches, i.e. gave no cover while looking like they did. Removed, reasons kept as a comment.

   **Pattern width was measured, not guessed.** A wider pattern (adding `user|staff|checker|requestor|owner|sender|granted`) surfaces 19 columns, of which 17 are noise (`Username`, `superUser`, `UseRowLevelAccess`, `UserAgreementId`, `userAuthenticationURL`, …) and **zero** are genuine unclaimed user-id columns. The narrow pattern does miss bare `user` / `user_fk` style names, but every such column in the schema is already declared, so there is no live gap. Kept narrow; do not re-litigate without re-measuring.
3. **`OnBehalfOfOwnershipSweepTest`** — ✅ **2026-09-13**, `code/api/sweep/`, 4 scenarios green (shard 8, the catch-all). Its subject is the *redirect* — the implicit-self half of the doctrine, where the caller IS a consent user — so it is the complement of `ExplicitTargetConsentUserSweepTest` (Phase 3), not a superset of it.

   **Departure from the plan as written, deliberate.** "Call every `UseOnBehalfOfUserId` create endpoint with the consent JWT; assert no row in any such table references the consent user's id" is red on the day it is written and stays red for months: **8 of the 59 `UseOnBehalfOfUserId` references are wired today** (`TransactionRequest_UserId`, `Entitlement_UserId`, `AccountHolders_User`, `UserCustomerLink_UserId`, `DynamicEntity_UserId`/`DynamicData_UserId`, `Bank_CreatedByUserId`, `Counterparty_CreatedByUserId` — Phase 2 rows 1–5), and all 3 `Reject` references are unwired. A permanently red suite is one people learn to ignore — the same reasoning `AuthSweepTest.expectedAuthDeviation` records for its own two entries. So the shape is a **shrink-only ratchet**:

   - **Inventory (source scan).** Every `UseOnBehalfOfUserId` / `Reject` reference is either named somewhere in `main` outside the policy file, or listed in `notYetWired` with a reason. Neither → fail (a new table nobody decided about). Listed *and* now used → fail, so wiring one forces the list to shrink. The list is **written out by hand, 54 entries**, not derived from "what main does not reference": a derived list agrees with reality by construction and both assertions would be checking it against a copy of itself — the failure mode `SweepCoverageDriftCheckTest` exists to prevent elsewhere in that package.
   - **Ownership (runtime, the real property).** A Consent is minted for `resourceUser1` over the wire (`POST /my/consents/IMPLICIT` → challenge → `Consent-JWT`), the consent user asks `/users/current` for its own id, then creates an account with `user_id` omitted. The account holder must be the human; and no row in **any** `UseOnBehalfOfUserId` column may reference the consent user, except in the tables the inventory says are unwired.
   - **The scan works (runtime, negative control).** The same consent user creates an API collection — `ApiCollection_UserId` is unwired — and the scan must *find* that row. Without it, the ownership scenario passes just as happily when the scan reads nothing at all. The scenario also asserts `ApiCollection_UserId` is still unwired, so it fails loudly rather than silently rotting once it gets wired.

   **The scan reads SQL, not the Mapper**, because the columns are three different shapes: the `user_id` string, a `MappedLongForeignKey` to `ResourceUser`'s primary key (`MapperAccountHolders.user`), and a list of ids (`SignatoryPanel.UserIds`). `DBUtil.runQuery` stringifies every column, so one comparison — equal to the primary key, or containing the user_id — covers all three.

   **Found on first run: the scan needs the shared-column discriminator.** `MappedEntitlement.mUserId` is named by `Entitlement_UserId` (`UseOnBehalfOfUserId`) *and* `Entitlement_UserId_ConsentScope` (`UseAuthenticatedUserId`), split by `createdByProcess == consent_user`. Scanning the column as a whole reports every Consent's own materialised scope as a leak — rows that are *meant* to sit on the consent user and are revoked with it. Fixed by a `sharedColumnDiscriminator` map naming the field the provider branches on, plus a fourth scenario asserting every policy-disagreeing column has one. That is the other half of `UserReferenceAttributionPolicyTest`'s "two references on one column must differ by policy": where they differ, the scan has to be told how.
4. Existing `ConsentObpTest` / `ConsentTest` keep passing (35033 now only AnyBank).

## Phase 5 — follow-through

1. Portal page `/developers/opey-permissions`: shrink "Attribution Is Not Yet Universal" to one line once the sweep test is green; use the vocabulary above there too.
2. Memory: write `on-behalf-of-user-id-plan` (none exists yet) pointing at this file, then mark built.
3. Optional later: consent revocation GC for consent-user rows (`UseAuthenticatedUserId`) — still declined for now.

## Decisions (settled 2026-09-02)

1. Vocabulary: `user_id` (authenticated caller, unchanged) and `on_behalf_of_user_id` (the human acted for). See the table at the top for what each retired word maps to.
2. Drops and renames that follow: drop `ResourceUser.PrincipalUserId` and `ResourceUser.IsNaturalPerson` (never set; a consent-less agent does not exist, so the consent chain is the only on-behalf-of record); `CallContext.onBehalfOfUser` field → `consentCreator`; `CallContext.humanUser` → `onBehalfOfUser`; `CallContext.accountableUserId` → `onBehalfOfUserId`; `AccountableOwnershipSweepTest` → `OnBehalfOfOwnershipSweepTest`.
3. Resolver home: `Users` trait + `LiftUsers`. See Phase 1 for why not a separate object.
4. **Invariant: the on-behalf-of user is always an original user** — `isOriginalUser`, i.e. `CreatedByConsentId` empty (in 2026; reversing this would be a deliberate decision, not a default). Three rules make it so: (a) the `Reject` policy: a consent user cannot create a consent, so no consent ever names a consent user; (b) the resolver is one hop and checks `isOriginalUser` on the row it lands on — a non-original target is a data bug: WARN and `Failure`, not fall back to the caller; (c) therefore every consent user, durable agents included, has an original user behind it, and "agents own nothing durable" is a corollary. `IsNaturalPerson` is dropped: OBP cannot know whether a person or a service account is behind an IdP login without KYC, and will not pretend to. "Original user" is structural and says nothing about persons.
5. Policy-aware entry point: providers call `attributionOf(userId, ref)` and name the column; the policy decides; the returned `Attribution` carries everything the caller should store and is the one place delegation is logged. Record-both tables are one reference with two fields.
6. Cache: in-memory Guava via `Caching.memoizeSyncWithImMemory`, 10 min TTL, never memoise the not-yet-bound consent case.
7. The `Reject` policy covers consent creation and OAuth consumer/token creation by a consent user.
8. v6/v7 `/users/current` JSON field `on_behalf_of`: today set only for OBP-native consents (from the JWT creator), null for BG/UK consents although they have a human. After Phase 0 row 8 it reads `consentCreator.or(consenter)`, so BG/UK consent callers get the consenter too; plain users and OBP-consent callers see no change. It must not read the resolved `onBehalfOfUser`, whose `.or(user)` fallback would show every plain user as their own on-behalf-of. Optional; not needed by Phase 1. Accepted as correct; note in the release notes.

## Decisions (2026-09-10/11)

9. **SCA/OTP delivery is NOT part of the attribution sweep.** `APIUtil.getPhoneNumbersByUserId` /
   `getEmailsByUserId` (called by `LocalMappedConnector` to deliver EMAIL and SMS challenges) still read
   by the caller's own id, so a consent-user-initiated challenge finds no phone number and nothing is
   sent. Resolving them to the on-behalf-of user would deliver the OTP to the human — correct in itself,
   and the same conclusion `ConsentUtil.scala` already reaches for Berlin Group ("the OTP would go to the
   TPP and never reach the PSU") — but the flow it unblocks is the human reading a code off their phone
   and handing it to the agent, which is the relay pattern SCA exists to prevent. Failing closed is the
   better state until agent-initiated SCA has its own answer (the Consent carrying the authorisation, or
   the human answering the challenge directly in the Portal). Its own decision, not a ride-along.

10. **Endpoint-level tag: `onBehalfOfMode`, verb-shaped values.** The projection of `AttributionPolicy`
   onto endpoints is not 1:1 — redirect-vs-guard is a distinction that exists only at endpoint level
   (`AccountAccessRequest_TargetUserId` is policy `UseOnBehalfOfUserId` but an explicit target the endpoint
   refuses), and most endpoints touch no user column at all. So the endpoint enum needs one value the
   column enum lacks, plus a default. Field name mirrors its `ResourceDoc` sibling `authMode:
   EndpointAuthMode`; values are verb-shaped rather than reusing the policy words:
   `ActsForCallerOnly` (default), `ActsForOnBehalfOfUser`, `ActsForConsentUser`, `RequiresOriginalUser`,
   `RefusesConsentUser`. The tag is documentation plus a test hook, never a permission — enforcement
   stays in the provider redirect and the endpoint guards, because a consent user can call any version.
   `OnBehalfOfOwnershipSweepTest` checks the declaration against observed writes, so a wrong claim fails
   the build. Not yet built.

11. **Visibility is granted by resource *type*, all-or-nothing within its scope — never by provenance.**
   Ownership answers the human's side: the row says H, so H sees it through every existing endpoint.
   It creates the agent's side, which Simon named exactly: *"it's like dropping stones into a well, it can
   never check they are there."* An agent that cannot read back its own work will retry, duplicate, or
   report success it never verified. So agents must read — but **not** by provenance ("the rows I created").
   Two reasons. (a) A provenance-filtered view is partial and the agent cannot explain it to itself: some of
   H's customers and not others, with no way to tell *doesn't exist* from *exists but not mine* — precisely
   the ambiguity that makes automated callers act badly and confidently. (b) It would require every table to
   record which consent wrote each row, i.e. the per-table on-behalf column this model rejects. (Provenance
   *is* recorded where it is genuinely about accountability — `MappedTransactionRequest` stores both
   `mUserId` and `mOnBehalfOfUserId` — but that is audit, not an access rule.) So: if the Consent names the
   type and scope, the agent sees **all** of the granting User's rows of that type, exactly as the User does;
   if not, it sees none and is told which `my_resources` entry is missing. Never an empty list where a 403 is
   meant. **Built 2026-09-11** for linked Customers: `my_resources.linked_customers` (`bank_id` + `actions`,
   mirroring `personal_dynamic_entities`), claim `ConsentLinkedCustomers`, `coversLinkedCustomers` /
   `linkedCustomerBankIds`, shape validation in `Consent.validateMyResources`, and two v7 reads behind it —
   `getMyCustomersAtBank` (`GET /banks/BANK_ID/my/customers`) and `getMyCustomers` (`GET /my/customers`,
   union over the Banks the Consent names). Older versions are untouched and fail closed, which is the only
   direction in which version-scoping an agent-aware read is safe (Decision 6 of `ai_agent_talk.md`: the
   version is not a security boundary — a consent user may call v5). Covered by `AgentDelegationTest`
   (the claim model, the json/claim round trip and the shape validation) and by
   `code.api.v7_0_0.CustomerConsentUserTest` (6 scenarios on the wire, with a real consent JWT: the granting
   User reads their own; a Consent granting nothing gets 403 and not an empty list; a Consent naming the Bank
   reads the granting User's Customers; a grant for another Bank does not open this one; a write-only grant
   does not grant reading; a malformed entry is refused at consent creation). Shard 6 (`code.api.v7_0_0`).

12. **`NotImplementedForConsentUser` — a fourth policy, so an unwired reference answers instead of
   stranding a row.** *Simon's idea, 2026-09-19; recorded here, not built, not yet decided.* Today the
   doctrine has two answers for a consent user: **redirect** (the provider writes the human, Phase 2) and
   **reject** (400 `InvalidUserId`, Phase 3, for a request that explicitly names a consent user). The 54
   references in `OnBehalfOfOwnershipSweepTest.notYetWired` have neither, so they get a third answer nobody
   chose: **success**, with the agent's id in the column. That is the worst of the three, because it is
   indistinguishable from the wired case at the call site — the caller gets a 201 and a row that will
   strand. The proposal is to make "not wired yet" a *declared* state that answers on the wire:
   a fourth `AttributionPolicy` value, `NotImplementedForConsentUser`, whose `attributionOf` returns a
   `Failure` carrying a new error — `NotImplementedForConsentUser = "OBP-10062: ..."` is the next free code
   — mapping to **501**, which is the honest status: not "you may not", but "this server cannot yet do this
   correctly for an agent identity." An original user calling the same endpoint is unaffected, since the
   policy only fires when the caller is a consent user.

   Why it is worth doing. (a) It is **discoverable before the call**: the error joins the endpoint's
   `ResourceDoc` error list, so an agent reading the resource docs — which is how OBP-MCP and Opey plan
   their calls — can see which endpoints are agent-safe without trying them. That is the "return the
   development process and progress" part: the API itself reports how far this plan has got, per endpoint,
   instead of a markdown file nobody outside this repo reads. (b) It makes the Phase 4 ratchet **runtime**
   rather than test-only: `notYetWired` and the policy file stop being two lists that can disagree.
   (c) It converts a silent future defect into a loud present one — the webhook case (row 6) is precisely
   a row that succeeds now and misbehaves months later.

   Why it is not simply switched on for all 54. It is a **behaviour change for working agent flows**: an
   agent that creates an API collection or a user attribute today gets a 201, and would get a 501. So the
   value is chosen **per reference, deliberately**, exactly like `record-both` was for
   `Counterparty_CreatedByUserId` — the question for each row becomes "wire it, or declare it not
   implemented?", and the row is not allowed to stay silent. The natural first candidate is the three
   webhook references, where the deferral is already written down and the consequence of succeeding is
   durable (nothing GCs a webhook on revocation). Open sub-questions, none of them blocking the idea:
   whether 501 or 403 reads better to a client library that retries on 5xx; whether the endpoint or the
   provider emits it (provider, for the same reason the redirect lives there — but then the error has to
   survive the `Box` → HTTP mapping with its status intact); and whether the declaration belongs on the
   `UserReference` alone or also on Decision 10's `onBehalfOfMode` endpoint tag, which is the thing a
   `ResourceDoc` can actually carry.

## Risks

1. **Silent redirects hide bugs** → WARN on every delegated attribution + the sweep test; redirects are the net, endpoints stay explicit.
2. **BG/UK consents with no human yet**: resolver returns the consent user (fails closed); those flows don't create `UseOnBehalfOfUserId` objects before authorisation — verify in the sweep.
3. **Delete/lookup asymmetry**: rows are on the on-behalf-of user, so "delete by consent user id" finds nothing — acceptable because explicit targets are rejected.
4. **Perf**: one cached read per write for consent callers only.
