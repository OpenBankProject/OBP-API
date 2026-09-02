# On-behalf-of user id — making ownership-by-the-human automatic

Written 2026-09-02 evening, for pickup 2026-09-03. This is the only document: no separate
checklist. Track progress here by marking items done in place. **Status: Phase 0 done 2026-09-02 (uncommitted): clean build green, `AgentDelegationTest` /
`ApiSessionTest` / `ConsentOwnershipTests` all pass (21 tests). Phase 1 next.** Background and the reasoning are on the Portal page `/developers/opey-permissions`
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
`experimental_become_user_that_created_consent` stays deprecated).

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

Not renamed: local `val humanUserId = cc.onBehalfOfUserId` in the createBank endpoints (`Http4s220:471`, `Http4s500:469`, `Http4s600:877`) and `Http4s700.humanAndAgentUserIds` — locals, Phase 3 touches those endpoints anyway; `consenter` (a source, name is accurate), `User.isConsentUser` / `Constant.consent_user`
(the kind of user), `mOnBehalfOfUserId` and `on_behalf_of_user_id` (already right). The ABAC rule
engine's `onBehalfOfUser` parameter (`AbacRuleEngine.scala:33,164`) is a separate rule-input slot,
always `None` today; leave it, it already uses the right word.

Done when (both satisfied 2026-09-02): `grep -rn "PrincipalUserId\|principalUserId\|IsNaturalPerson\|isNaturalPerson\|humanUser\b\|accountableUserId" obp-api/src obp-commons/src` is empty and `AgentDelegationTest`, `ApiSessionTest`, `ConsentOwnershipTests` pass.

## Phase 1 — one resolver, policy-aware entry point (decided: lives in `Users`)

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
def onBehalfOfUserIdOf(userId: String): Box[String]

/** True when `userId` acts for itself and may own durable state. */
def actsForSelf(userId: String): Boolean = onBehalfOfUserIdOf(userId).exists(_ == userId)

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
    case KeepUserId         => userId
    case UseOnBehalfOfUserId => onBehalfOfUserId
    case Reject             => userId   // unreachable: attributionOf fails first
  }
}

// ---- the entry point providers actually call ---------------------------------------------
/** Attribution for writing column `ref` as `userId`. Applies `ref.policy`:
 *  KeepUserId / UseOnBehalfOfUserId → Full(attribution), WARN naming `ref` when isDelegated
 *  Reject → Full(attribution) if !isDelegated, else Failure(InvalidUserId … names a consent user) */
def attributionOf(userId: String, ref: UserReference): Box[Attribution]

/** Convenience for single-column writers. */
def attributedUserId(userId: String, ref: UserReference): Box[String] = attributionOf(userId, ref).map(_.userIdToStore)
```

`UserReference` is the Phase-2 policy file as code (main tree, see Phase 2). The `ref` argument is
chosen by provider code, never from the request, so the "no caller-asserted input" property of
`onBehalfOfUserIdOf` still holds. `consentId` is derived inside the resolver, never passed in, for
the same reason.

Implementation notes:

1. `onBehalfOfUserIdOf` = the chain `addEntitlement` and `CallContext.accountableUserId` both
   inline today (`ResourceUser.find(By(userId_)) → CreatedByConsentId → getConsentByConsentId →
   consent.userId`). Both then delegate to it; CallContext
   keeps its `consentCreator.or(consenter)` precedence in front.
2. Cache (decided): `Caching.memoizeSyncWithImMemory` (Guava via scalacache, already used),
   key `onBehalfOfUserIdOf:<userId>`, TTL 10 min. Memoise humans (answer = self) and bound
   consent users. Do **not** memoise the "consent user whose consent names no human yet" branch,
   or a BG consent bound a minute later stays pinned to the consent user for the TTL.
3. Agents (decided, see Decisions 4): an agent is a consent user; there is no consent-less
   agent and no second column. The resolver is one hop (no chains) and asserts the target row
   `isOriginalUser`; if not, WARN and `Failure`. The `Reject` policy on consent creation by a
   consent user is what keeps chains from ever being written.
4. Every delegated attribution logs WARN with the `ref` name, `userId`, `onBehalfOfUserId`,
   `consentId`. A WARN firing in tests means a site chose the wrong reference or a policy is wrong.
5. **Check before coding**: for OBP-native consents, `CallContext` prefers the JWT's
   `createdByUserId` (`consentCreator`) while the resolver follows `consent.userId`. Same person
   when a human creates their own consent in the Portal; verify no creation path sets `mUserId`
   to someone other than the creator (`MappedConsentProvider.scala:54,205,232,279`). If one does,
   decide which wins and write it down here.

Call sites after Phase 1:

```scala
// ApiSession.scala
def onBehalfOfUser: Box[User] = consentCreator.or(consenter).or(user)          // was humanUser
def onBehalfOfUserId: String =                                                  // was accountableUserId
   consentCreator.or(consenter).map(_.userId).filter(_.nonEmpty)
    .openOr(Users.users.vend.onBehalfOfUserIdOf(user.map(_.userId).openOr("")))

// MappedEntitlements.addEntitlement: the magic-string exemption becomes a reference choice
val ref = if (createdByProcess == Constant.consent_user) UserReference.ConsentEntitlementUser
          else UserReference.EntitlementUser
for { targetUserId <- Users.users.vend.attributedUserId(userId, ref); ... }

// MappedTransactionRequestProvider: a record-both table, one call, two columns
for { a <- Users.users.vend.attributionOf(userId, UserReference.TransactionRequest) } yield
   tr.mUserId(a.userId).mOnBehalfOfUserId(a.onBehalfOfUserId)
```

## Phase 2 — assign an attribution policy to every user-reference column (from a grep of Mapped classes)

Rule: **the agent owns nothing durable.** Only the consent's own authorisation rows stay on the consent user.

Every user-reference column gets exactly one **attribution policy**, which says what value the
column takes when the user is a consent user (or an agent user with an on-behalf-of user):

| policy | meaning |
|---|---|
| `KeepUserId` | the authenticated user's own id; no resolver |
| `UseOnBehalfOfUserId` | the on-behalf-of user's id, via the resolver in the provider |
| `Reject` | the request is refused with 400 |

"Record both" below is a table-level description: one `KeepUserId` column and one
`UseOnBehalfOfUserId` column on the same row. Such tables make one `attributionOf` call with a
table-level reference and write both fields of the `Attribution`.

The policy file is **main-tree Scala**, because `Users.attributionOf` reads it at runtime
(proposed: `obp-api/src/main/scala/code/users/UserReference.scala`):

```scala
sealed trait AttributionPolicy
object AttributionPolicy {
  case object KeepUserId          extends AttributionPolicy
  case object UseOnBehalfOfUserId extends AttributionPolicy
  case object Reject              extends AttributionPolicy
}

/** One value per user-reference column (or per record-both table). Naming: <Table><Column-role>. */
sealed abstract class UserReference(val policy: AttributionPolicy, val mapper: Class[_], val fields: List[String])
object UserReference {
  case object AccountAccessUser      extends UserReference(KeepUserId,          classOf[AccountAccess],            List("user"))
  case object ConsentEntitlementUser extends UserReference(KeepUserId,          classOf[MappedEntitlement],        List("mUserId")) // createdByProcess == consent_user
  case object EntitlementUser        extends UserReference(UseOnBehalfOfUserId, classOf[MappedEntitlement],        List("mUserId"))
  case object AccountHolderUser      extends UserReference(UseOnBehalfOfUserId, classOf[MapperAccountHolders],     List("user"))
  case object TransactionRequest     extends UserReference(UseOnBehalfOfUserId, classOf[MappedTransactionRequest], List("mUserId", "mOnBehalfOfUserId")) // record both
  case object ConsentCreator         extends UserReference(Reject,              classOf[MappedConsent],            List("mUserId"))
  case object OAuthConsumerCreator   extends UserReference(Reject,              classOf[Consumer],                 List("createdByUserId"))
  // … one per row of the tables below
  val all: List[UserReference] = List(...)   // the frozen test walks this
}
```

Carrying `mapper` + `fields` on each value is what lets the Phase-5 frozen test tie every
reflected Mapper column to exactly one reference (one column may have two references only when
they differ by process, as `MappedEntitlement.mUserId` does).

### KeepUserId — authorisation materialisation, NO resolver
| # | class | field | note |
|---|---|---|---|
| 1 | `views/system/AccountAccess` | user id | views copied from the JWT each request; ALL_CONSUMERS rows; has lifecycle GC |
| 2 | `entitlement/MappedEntitlements` | `mUserId` **only when** `createdByProcess == consent_user` | existing exemption |
| 3 | `model/dataAccess/ResourceUser` | itself | the consent user's own row |
| 4 | `userlocks/UserLocks` | `UserId` | lock the user (a consent user never logs in; effectively unused) |
| 5 | `transactionChallenge/MappedExpectedChallengeAnswer` | `ExpectedUserId` | challenge is answered by the initiating user |
| 6 | `chat/MappedChatMessage` | `SenderUserId` | sender = the user is truthful; `MentionedUserIds` are humans by construction |
| 7 | `api/pemusage/MappedPemUsage` | `LastUserId` | audit |

### Record both — `KeepUserId` column + `UseOnBehalfOfUserId` column on one row
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
| 14 | `accountapplication/MappedAccountApplication` | `mUserId` | create (v3.1 endpoint already guards; make provider default) |
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

Phase-2 deliverable: `UserReference.scala` in the **main** tree, one case object per row of the tables above, `all` listing them. Not a database table, and not these markdown tables: the markdown is the working draft, the Scala file is what runs (via `Users.attributionOf`) and what `UserReferenceAttributionPolicyTest` (Phase 5) checks.

## Phase 3 — provider guards (UseOnBehalfOfUserId)

Pattern, one line at the top of each create/link method, naming the column being written:

```scala
for {
  ownerId <- Users.users.vend.attributedUserId(userId, UserReference.AccountHolderUser)  // WARNs when delegated
  ...
```

Providers that return a plain value rather than a `Box` either grow a `Box` (preferred) or
`openOr(userId)` with a comment. Both ways to be wrong — forgetting the call, or naming the wrong
reference — are caught by the Phase-5 sweep; the second is also visible in review.

1. Providers that take a `User` (AccountHolders): resolve to id, re-fetch the on-behalf-of `User` once (cached).
2. Keep endpoint-level `cc.onBehalfOfUserId` uses; they become redundant clarity, not the mechanism.
3. `KeepUserId` writers that share a provider method with a `UseOnBehalfOfUserId` path (views materialiser, consent entitlements) pass a different `UserReference` (e.g. `ConsentEntitlementUser` vs `EntitlementUser`); no more string-typed exemptions.

Order of attack (highest strand-risk first): AccountHolders → UserCustomerLink → AccountApplication → UserAuthContext → ApiCollection/UserAttribute → the rest mechanically.

## Phase 4 — explicit-target guards (endpoint 400s)

Doctrine (settled 2026-09-01): implicit self → redirect in provider; explicit `USER_ID` naming a consent user → 400 `InvalidUserId … names a consent user`. Already done: addEntitlement (v2.0/v7), addUserToGroup (v6), createAccount (v2.0/v3.1/v4.0/v5.0/v7), grantUserAccessToViewById (v5.1), account access requests (v6), account applications (v3.1). To sweep: createUserCustomerLink, API collections, user attributes, auth contexts, KYC/meeting staff ids, webhooks with explicit ids. `Reject` columns refuse in the provider (`attributionOf` returns Failure); endpoints map that to 400 and may keep an early explicit check for a nicer message, but the floor holds without them.

## Phase 5 — tests

1. **`AgentDelegationTest`** — extend: `onBehalfOfUserIdOf` for original user / consent user / dangling consent (fails closed) / cache hit after consent later bound (BG case) / consent whose user is itself a consent user → Failure; `attributionOf` for each of the three policies.
2. **`UserReferenceAttributionPolicyTest`** (frozen-style, like `FrozenClassTest`): iterate `ToSchemify.models`, reflect Mapper fields whose name matches `(?i)userid|createdby|grantedby|holder`, assert every (class, field) is named by at least one `UserReference` in `UserReference.all`, and by more than one only where the references differ by process; assert every `UserReference` names real Mapper fields. New tables and renamed columns fail until sorted.
3. **`OnBehalfOfOwnershipSweepTest`**: mint a consent for a test human with generous roles; call every `UseOnBehalfOfUserId` create endpoint with the consent JWT; assert no row in any such table references the consent user's id, and at least one references the human. Also assert `Reject` endpoints return 400.
4. Existing `ConsentObpTest` / `ConsentTest` keep passing (35033 now only AnyBank).

## Phase 6 — follow-through

1. Portal page `/developers/opey-permissions`: shrink "Attribution Is Not Yet Universal" to one line once the sweep test is green; use the vocabulary above there too.
2. Memory: write `on-behalf-of-user-id-plan` (none exists yet) pointing at this file, then mark built.
3. Optional later: consent revocation GC for consent-user rows (`KeepUserId`) — still declined for now.

## Decisions (settled 2026-09-02)

1. Vocabulary: `user_id` (authenticated caller, unchanged) and `on_behalf_of_user_id` (the human acted for). See the table at the top for what each retired word maps to.
2. Drops and renames that follow: drop `ResourceUser.PrincipalUserId` and `ResourceUser.IsNaturalPerson` (never set; a consent-less agent does not exist, so the consent chain is the only on-behalf-of record); `CallContext.onBehalfOfUser` field → `consentCreator`; `CallContext.humanUser` → `onBehalfOfUser`; `CallContext.accountableUserId` → `onBehalfOfUserId`; `AccountableOwnershipSweepTest` → `OnBehalfOfOwnershipSweepTest`.
3. Resolver home: `Users` trait + `LiftUsers`. See Phase 1 for why not a separate object.
4. **Invariant: the on-behalf-of user is always an original user** — `isOriginalUser`, i.e. `CreatedByConsentId` empty (in 2026; reversing this would be a deliberate decision, not a default). Three rules make it so: (a) the `Reject` policy: a consent user cannot create a consent, so no consent ever names a consent user; (b) the resolver is one hop and checks `isOriginalUser` on the row it lands on — a non-original target is a data bug: WARN and `Failure`, not fall back to the caller; (c) therefore every consent user, durable agents included, has an original user behind it, and "agents own nothing durable" is a corollary. `IsNaturalPerson` is dropped: OBP cannot know whether a person or a service account is behind an IdP login without KYC, and will not pretend to. "Original user" is structural and says nothing about persons.
5. Policy-aware entry point: providers call `attributionOf(userId, ref)` and name the column; the policy decides; the returned `Attribution` carries everything the caller should store and is the one place delegation is logged. Record-both tables are one reference with two fields.
6. Cache: in-memory Guava via `Caching.memoizeSyncWithImMemory`, 10 min TTL, never memoise the not-yet-bound consent case.
7. The `Reject` policy covers consent creation and OAuth consumer/token creation by a consent user.
8. v6/v7 `/users/current` JSON field `on_behalf_of`: today set only for OBP-native consents (from the JWT creator), null for BG/UK consents although they have a human. After Phase 0 row 8 it reads `consentCreator.or(consenter)`, so BG/UK consent callers get the consenter too; plain users and OBP-consent callers see no change. It must not read the resolved `onBehalfOfUser`, whose `.or(user)` fallback would show every plain user as their own on-behalf-of. Optional; not needed by Phase 1. Accepted as correct; note in the release notes.

## Risks

1. **Silent redirects hide bugs** → WARN on every delegated attribution + the sweep test; redirects are the net, endpoints stay explicit.
2. **BG/UK consents with no human yet**: resolver returns the consent user (fails closed); those flows don't create `UseOnBehalfOfUserId` objects before authorisation — verify in the sweep.
3. **Delete/lookup asymmetry**: rows are on the on-behalf-of user, so "delete by consent user id" finds nothing — acceptable because explicit targets are rejected.
4. **Perf**: one cached read per write for consent callers only.
