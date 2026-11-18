# Remove the endpoint auth mode

Status: built on 2026-11-10, not yet committed. Steps 2, 4, 5, 6 and 7 are done (see "What was built" at the end).
Steps 1 and 3 remain. This replaces an earlier proposal (2026-11-09) to let
installations override each endpoint's auth mode. It follows on from `authmode_user_or_application_design.md`, and
changes one principle from that note: see "The Platform App marker" below.

## The issue

Every static endpoint has an `authMode` in its ResourceDoc: UserOnly, ApplicationOnly, UserXORApplication or
UserAndApplication. It mixes two different questions into one setting:

- **Is a user required?** This is already answered in two other places: by the handler's helper (`withUser`
  refuses a call without a user), and by the ResourceDoc's error list and Roles.
- **Where may the Role come from: a person's Entitlement, or a Consumer's Scope?** This follows from who is acting
  on each request, which is a fact about the request, not about the endpoint.

Because the mode repeats what other places say, it drifts from them. An inventory script, since removed, found
on 2026-11-10:

- 1,349 endpoints with the default UserOnly, which nobody chose for them;
- 13 endpoints that are UserXORApplication although their handler uses a person;
- 21 endpoints open to applications whose handler still insists on a user.

The mode also cannot express what installations actually want. It is the same for every installation, but one
bank may let its integration app delete Entitlements and another may not. That decision is already made, per
installation, when a Role is granted.

What the mode does do, in practice, is guard against a muddle in what a Scope means (see below). That guard
belongs where the muddle starts, not on each endpoint.

## How we got here

| Date | Who | Change |
|---|---|---|
| 2018-05-04 | Hongwei | Scopes: Roles held by a Consumer instead of a user |
| 2019-05-06 | Marko | `require_scopes_for_all_roles` and `require_scopes_for_listed_roles`: a Scope as a **limit on people** (a person needs the Entitlement, and the app they use needs the Scope) |
| 2022-02-09 | Marko | `allow_roles_or_scopes`, renamed `allow_entitlements_or_scopes`: one global switch letting a Scope stand **instead of** an Entitlement |
| 2026-03-05 | Simon | The global switch replaced by a per-endpoint `authMode`; first used by `verifyUserCredentials` for OBP-OIDC |
| 2026-09-03 | Simon | Dynamic Entities get their own `auth_mode` |
| 2026-10-29 | Simon | UserOrApplication replaced by UserXORApplication |

## The two meanings of a Scope

A Scope on a Consumer means one of two things today, and the code cannot tell which:

| Meaning | What it says |
|---|---|
| A limit on people (since 2019) | People may use Role X through this application. The person still needs the Entitlement. |
| A permission for the application (since 2022) | This application may use Role X itself. |

The risk is that a Scope granted as a limit on people is read as a permission for the application. Common
practice keeps the two apart. Microsoft Entra ID, for example, has "delegated permissions" and "application
permissions" as separate kinds of grant.

## The new direction

**Remove `authMode` from static endpoints.** Each request is then decided by who is acting:

- **A person acting:** the Role comes from their Entitlement. If the installation uses `require_scopes_for_*`,
  the application they use must also hold the Scope. This is the Scope as a limit on people.
- **The application acting as itself:** the Role comes from its Consumer's Scope. This is the Scope as a
  permission for the application.

**The application counts as acting as itself only if both of these hold:**

1. **It proved its identity as the application.** That means a client-credentials token, or a client
   certificate. Identification comes first. A public client (for example a browser app using PKCE), a consumer
   key on its own, or a person's DirectLogin token does not prove it.
2. **Its Consumer is a Platform App.** An administrator marks it, with the Role `CanCreatePlatformApp`.

A Consumer that is not a Platform App can still hold Scopes, but they only ever work as a limit on people. This
settles the two meanings of a Scope: the Platform App marker says which one applies.

**Endpoints that need a person keep requiring one in their code.** For example, `/my/...` endpoints use
`withUser` and the caller's own identity. No setting on the endpoint is needed, and nothing can drift.

### The Platform App marker

The 2026-10-29 note said the marker grants nothing, so rules must not depend on it. This direction changes that
on purpose:

- Marking a Consumer as a Platform App still grants no Scopes. It lets the application declare the Scopes it
  needs (`PUT /obp/v7.0.0/consumers/current/platform-app`), and an administrator then grants them.
- Being a Platform App now also means its Scopes are permissions for the application itself.
- We define a Platform App by that: any application that acts with Scopes by itself is a Platform App. This
  includes an installation's own applications, such as the Portal, the API Manager, Sentinel, Bank-Node and
  Keycloak. It also includes a third party's backend that needs to act as itself.

### How it fits the standards

This is the standard confidential-client pattern under an OBP name. OAuth leaves the access rules inside an API to
the API, so OBP does not go against any standard here. The relevant standards are in the glossary item "OAuth
Standards and OBP".

### Consumer grant types

The Consumer grant types built on 2026-11-09 stay as they are. (A person "logs in through a Consumer" when they get
a token with that Consumer's client id or key. They can do this by an OAuth2 login, by DirectLogin, or by renewing such a login.) They are set with
`PUT /management/consumers/CONSUMER_ID/grant-types`. They say whether people can log in through a Consumer, and
whether the application can get a token for itself. The two do not need to be combined. A Platform App whose grant types exclude `client_credentials`
cannot get a token of its own, so it cannot act as itself (decision 1). Whether we keep the grant types at all is
open question 1.

## Dynamic Entities

A Dynamic Entity's `auth_mode` has the same problem. But an entity has no handler code of its own, so part of its
setting is real configuration. Its personal (`/my/`) and row-level endpoints already always need a person,
whatever `auth_mode` says. With the rule above, the remaining choice is whether applications may use the entity's
data on their own at all. That can also be expressed by granting, or not granting, the entity's Scopes to a
Platform App. So `auth_mode` on entities can probably go too. This is an open question.

## Effect, measured on 2026-11-10

Removing the mode applies the same rule to every endpoint:

| Endpoints | Today | Afterwards |
|---|---|---|
| 73 UserXORApplication | A person with the Entitlement, or the application acting as itself with the Scope | No change, except that the application must now be a Platform App |
| 224 UserOnly with Roles, whose handler does not insist on a user | Only a person | Also a Platform App acting as itself, if it holds the Scope |
| 397 UserOnly with Roles, whose handler uses `withUser` | Only a person | Also a Platform App acting as itself with its own user (it gets one through client credentials). The handler treats that user as the caller. |
| 728 UserOnly without Roles | A user is required | The Role check does not apply. Handlers that assume a user must be checked, so a call without one is refused cleanly. |

The error for a call with no credentials should stay "User not logged in" (OBP-20001). Otherwise 637 assertions in
158 test files, and every client that checks the error, would change.

## Steps

1. **Audit Scopes.** List which Scopes each Consumer holds, and why. Start with the Platform Apps and with
   installations that use `require_scopes_for_*`. A Scope that a Platform App holds only as a limit on people
   would become a permission for it.
2. **Identify the application first.** Make "acting as itself" mean: proved its identity (client-credentials
   token or client certificate) and its Consumer is a Platform App. Check how a call with only a consumer key is
   treated when `consumer_validation_method_for_consent=CONSUMER_KEY_VALUE`.
3. **Mark the applications that act as themselves today** as Platform Apps: Bank-Node, Sentinel, and the
   Portal, the API Manager and Cohort for their own tokens. This is an operator step, so it needs a release note.
4. **Remove `authMode`** from static ResourceDocs and from the access check, keeping OBP-20001 for calls with no
   credentials. Fix the handlers that assume a user is present.
5. **Decide on Dynamic Entities' `auth_mode`,** and keep the stored value readable for old definitions.
6. **Add a test** that fails when a handler that needs a person can be reached without one. Done as
   `UserOptionalHandlerTest`: a handler whose doc needs only an identified application must not insist on a user.
   The auth sweep (`AuthSweepTest`) covers the endpoints that need a user.
7. **Update the documentation:** the glossary items "API.Endpoint Auth Modes", "Platform Apps" and "Scopes", the
   Scopes section of `sample.props.template` (it still says `UserOrApplication`), and the release notes.

## Decisions (2026-11-10)

1. **What the Platform App marker means:** only that the Consumer's Scopes can be used by the application itself.
   It implies nothing about grant types. If a Platform App's grant types exclude `client_credentials`, it cannot
   get a token of its own, so it cannot act as itself. No extra rule is needed.
2. **Dynamic Entities:** applications that need an entity's data on their own are set up as Platform Apps. So an
   entity's `auth_mode` is not needed. It is removed from the v6.0.0 and v7.0.0 definitions and no longer stored. A
   client that still sends it is not refused: the field is ignored. Entities work like static endpoints.
3. **One Consumer or two for applications that do both:** we cannot require two now. But we aim for it in the long
   run, as better security. For example, the Portal would have one Consumer for people logging in and one for its
   own token.
4. **The name:** we keep "Platform App". It means an accepted application, inside or outside the organisation. This
   may mean more Platform Apps to manage on a sandbox, which is acceptable.
5. **Consumer grant types** (built on 2026-11-09): we keep them only to control how tokens can be obtained through
   a Consumer. They no longer decide whether a call is the application acting as itself. That change was made on
   2026-11-10, in `APIUtil.isApplicationActingAsItself`. So each question has one rule: Platform App decides whether
   the Consumer's Scopes can be used by the application itself, and grant types decide which logins are allowed.

## Open questions

1. Decided: see decision 5.
2. Decided: see decision 2.

## What was built (2026-11-10)

- `ResourceDoc.authMode` and `EndpointAuthMode` are gone. `handleAccessControlRegardingEntitlementsAndScopes` is the
  one access check. It is used by the middleware, the inline Role checks, Dynamic Entities, Files and gRPC.
- Who may call is read from the error list (`ResourceDoc.requiresApplicationButNotUser`). The 22 Berlin Group and UK
  consent and payment endpoints list `ApplicationNotIdentified` in place of `AuthenticatedUserIsRequired`. The
  parity audit does not cover those standards, so no allowlist entries were needed.
- An application acts as itself only if its Consumer is a Platform App and it calls with no User or as its own
  client-credentials User (`obp-oidc#client`, or `keycloak#client` for a Keycloak service account).
- Consumer Account Access no longer needs the endpoint to opt in.
- v7.0.0 Get My Role returns `allowed` in place of `allowed_for_auth_modes`. API Explorer II, OBP-OIDC, Sentinel and
  OBP-Frontend's shared role check read it.
- Dynamic Entity `auth_mode` is removed from the v6.0.0 and v7.0.0 JSON, the provider and the mapped column. API
  Manager no longer offers it, and the shared entity bootstrap no longer treats it as drift.
- The glossary item "API.Endpoint Auth Modes" is replaced by "API.Entitlements and Scopes".
- `UserOptionalHandlerTest` reads the handler of every doc that needs only an identified application, and fails if
  it insists on a user. The inventory script was removed: with no auth modes left, it had nothing to sort by.
