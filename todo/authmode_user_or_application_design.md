# AuthMode: review the design, especially UserOrApplication

Status: UserXORApplication implemented in the working tree on 2026-10-29 (uncommitted; not yet compiled or
tested). Dynamic Entities accept the former name UserOrApplication as an alias. Written 2026-10-28.

## Principle: attribution follows authorization

Whoever's permission let an action through is the one responsible for it. A record of an
action should say whose grant was used, not only who was logged in:

    action -> grant used (the User's Entitlement, or the Consumer's Scope) -> who created that grant

If an Entitlement let it through, the person acted on authority given to them by its grantor.
If a Scope let it through, the Consumer acted on authority given to it by the Scope's grantor;
for an application, that grantor (and whoever runs the application) is accountable.

Platform App is not part of this. It is a marker plus a convenient way for an application to
declare the Scopes it needs; marking a Consumer grants nothing, and any Consumer can hold
Scopes marked or not. Rules about authority should not depend on the marker.

## The four modes

`APIUtil.handleAccessControlWithAuthMode`:

- **UserOnly** (Entitlements). Clear: a person acts on their own authority.
- **UserAndApplication** (Entitlements AND Scopes). Clear: the person needs the Role, and the
  Scope limits which applications may exercise it. Restricts the application's power.
- **ApplicationOnly** (Scopes). Clear for machine applications: the application acts as itself.
- **UserOrApplication** (Scopes OR Entitlements). Too loose. When both hold the Role nothing
  says which grant was used; when only the Consumer holds it, a person acts on the
  application's authority but is recorded as if it were their own (e.g. `reported_by_user_id`).

## The underlying hole: people can log in through any Consumer

DirectLogin needs only `username`, `password` and `consumer_key` (not the secret), and
consumer keys sit in `.env` files and config. Anyone with an OBP account and a Consumer's key
can call through that Consumer. So:

- under **UserOrApplication**, they get the Consumer's Scopes;
- under **ApplicationOnly** as implemented today, they also pass (only Scopes are checked).

Endpoints already on `UserOrApplication` include log cache (v5.1.0), telemetry
(`Http4s700Telemetry.scala`) and the OIDC / Consumer bootstrap endpoints.

An OAuth2 client-credentials token from OBP-OIDC gets a User of its own (provider
`obp-oidc#client`, id = client id; `OAuth2.scala` `resolveProvider` / `clientCredentialsProvider`),
so an application acting as itself can be told apart from a person.

## Direction to weigh

1. **A Consumer that holds Scopes (a machine Consumer) does not let people log in.**
   Only the client-credentials grant (no DirectLogin, no authorization_code, no redirect URIs);
   the only User ever seen with it is its own `obp-oidc#client` User. This is a property of the
   Consumer's credentials, not of the Platform App marker. Applications that people log in to
   (Portal, API Manager, an operator UI) use other Consumers.
2. **Equivalently, in the check:** a Scope counts only when the Consumer is acting as itself
   (its own client-credentials User, or no User). A person calling through it needs their own
   Entitlement. With 1 in place, this always holds; checking it as well covers Consumers created
   before the rule.
3. **Retire UserOrApplication**, or keep it only where 1/2 make it unambiguous (then the Scope
   path is always the application and the Entitlement path always a person, and attribution is
   clear). Move existing endpoints to ApplicationOnly, UserOnly or UserAndApplication as fits.
4. **Record the basis** with the action where it matters: user_id, consumer_id, and whether an
   Entitlement or a Scope was used (ideally the grant's id), so the chain above can be followed.

Later: delegation (an application acting for a person, `on_behalf_of` in v7) needs both
principals and the consent recorded.

## Proposal: replace UserOrApplication with UserXORApplication (2026-10-29)

Each request is either a person acting or an application acting as itself, never a mix:

- **Application acting as itself:** no User, or the User is the application's own
  client-credentials User (provider `obp-oidc#client`, id = client id). Only the Consumer's
  Scopes count.
- **Otherwise a person:** only their own Entitlements count; the Consumer's Scopes are ignored.

Decided by who is acting, not by which credentials are present: a person's request always comes
through a Consumer too, so "not both credentials" never happens. Write that definition next to
the mode, since the name alone can read that way.

Gives: attribution always matches the grant used; a person who logs in with a machine
Consumer's key gets none of its Scopes; and the strict props can apply to the person path only.

Rename rather than change UserOrApplication in place, so every current use (log cache,
telemetry, OIDC / Consumer bootstrap) is reviewed and moved to UserXORApplication, UserOnly or
ApplicationOnly, then remove the OR mode. Machine applications using client credentials (e.g.
OBP-Sentinel) keep working; only a person borrowing a Consumer's Scope stops working, which is
the intent.

## Decided 2026-10-29: go with UserXORApplication. Implementation plan

Checked first: a bare consumer key does not make a request "the application acting as itself".
With no Authorization header, `getUserAndSessionContextFuture` identifies a Consumer only by
certificate (mTLS / TPP signature); a Consumer-Key header is used only for consents, and only
with `consumer_validation_method_for_consent=CONSUMER_KEY_VALUE`. So the application path is
reached by a client-credentials token (its `obp-oidc#client` User) or a certificate.

authMode does two jobs; XOR changes only the second:
1. Authentication: may a request with a Consumer but no User come in at all
   (`ResourceDocMiddleware.authenticate` `isAppMode` -> `applicationAccess`). Unchanged.
2. Role check (`APIUtil.handleAccessControlWithAuthMode`): XOR = if the caller is the
   application acting as itself, Scopes only; otherwise the User's Entitlements only. The
   strict props make the person path Entitlement AND Scope and leave the application path alone.

Steps:
- Add `UserXORApplication` to `EndpointAuthMode` (APIUtil); implement the role check with an
  "is the application acting as itself" helper (no User, or User provider `obp-oidc#client` with
  id = the Consumer's key / azp). Same logic in `Http4s700MyRoles` (its own copy of the check).
- Move every `authMode = UserOrApplication` to `UserXORApplication`. About 50 uses in main:
  - Without Roles (Berlin Group AIS/PIS, UK OB v3.1.0 / v4.0.1, OIDC, Dynamic Entity
    definitions, MyRoles): only job 1 matters, so behaviour is identical. Mechanical.
  - With Roles (v5.1.0 log cache, telemetry, Platform Apps, Grants, some v6.0.0 and v7.0.0):
    behaviour changes as intended (a person no longer uses the Consumer's Scope). Review each;
    some may belong as ApplicationOnly or UserOnly instead.
- Then remove `UserOrApplication` from `EndpointAuthMode`.
- Dynamic Entities: `DynamicEntityAuthMode.UserOrApplication` is a stored, API-visible value
  (`DynamicEntityProvider.scala`, `normalise`, v6.0.0 JSON). Contract decision needed: accept the
  old string as an alias mapped to the new one, and say so in the release notes.
- Docs: Glossary (authMode item, Platform Apps), `OpenAPI31JSONFactory`,
  `introductory_system_documentation.md`, release notes. The Lift baseline JSON
  (`scripts/resource_doc_baseline/lift_resource_docs_v5_1_0.json`, `_v6_0_0.json`) mentions it:
  do not hand-edit; use `parity_allowlist.json` via `allowlist_helper.py` if the audit flags it.
- Tests: ~35 references in 11 suites (`EndpointAuthModeTest`, `DynamicEntityAuthModeTest`,
  BG/UK consent tests, ...). Add: person through a Consumer that holds the Scope -> 403;
  application's own client-credentials User with the Scope -> allowed; person with own
  Entitlement through that Consumer -> allowed and recorded as the person.
- The parked `open-corridor-user-or-application` branch: switch its four endpoints to
  UserXORApplication once the mode exists (rebase onto the XOR branch).

DirectLogin for Consumers with Scopes: not needed for safety under XOR (a person logging in
through such a Consumer gets only their own Entitlements). Optional hardening; separate decision.
Disabling redirect URLs for Consumers with Scopes: rejected (the Portal needs both).

## Per-instance strictness

Decided 2026-10-29: props are fine for this (boot-time is OK). But the override must not flatten
the application path: under XOR it should make the person path UserAndApplication (Entitlement
AND the Consumer's Scope) and leave the application path Scopes-only, so client-credentials
Scope access keeps working.

One instance may want to be strict (e.g. UserAndApplication for people everywhere). Partly
possible today: `require_scopes_for_all_roles=true` and `require_scopes_for_listed_roles=CanX,...`
make the check `userHasTheRoles && hasScope` before the endpoint's authMode is looked at.
Limits:

- Props, so set at boot; changing them needs a restart.
- They flatten every mode, ApplicationOnly included: a machine application's own
  `obp-oidc#client` User would then also need Entitlements for every Role its Scopes cover,
  which breaks machine applications or pushes admins to grant Entitlements to app users.
- They can only tighten, all endpoints or per Role; nothing like "this instance does not
  allow UserOrApplication".

Earlier idea, superseded by the XOR proposal and the props decision above: an instance policy
mapping each mode (UserOnly -> UserAndApplication; UserOrApplication -> UserAndApplication for
people, ApplicationOnly for machine Consumers; ApplicationOnly unchanged), possibly held in the
database and changeable at run time.

## Work parked on this

Branch `open-corridor-user-or-application` (uncommitted, tests not run): sets
`authMode = UserOrApplication` on v7.0.0 attachOpenCorridorPromise,
createOpenCorridorSettlement, createOpenCorridorFeeSettlement and getOpenCorridorSettlement,
with description text, four Scope tests in `Http4s700RoutesTest` and a release-notes entry.
Do not merge it until the rule above is decided. For OBP-Bank-Node the likely end state is a
machine Consumer per node (client-credentials only, Scopes at its own bank) calling these
endpoints, while a person's "Settle now" goes through a different Consumer on their own
Entitlement.
