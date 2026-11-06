# TODO — Account access granted to a Consumer

## Context

OBP has two layers of permission, and only one of them can be granted to a Consumer today:

| | Granted to a user | Granted to a Consumer |
|---|---|---|
| **What may be called** (Roles) | Entitlement | Scope |
| **Which accounts, through which view** | Account access | *missing* |

Under `UserXORApplication` an application acting as itself (client credentials: no user, or its own
`obp-oidc#client` user, see `APIUtil.isApplicationActingAsItself`) is authorised by its Scopes. But an
endpoint authorised through a view, such as creating a Transaction Request on
`/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/...`, has nothing for it to use: account access is held only by
users.

The case that raised it: a bank-side service that initiates `OPEN_CORRIDOR_PROMISE` Transaction Requests on
the bank's corridor account, on behalf of the customers whose payments it relays. It needs the
report-back and settlement endpoints (bank Roles, available as Scopes) and the Transaction Request create
(the account's view). Today it runs as a DirectLogin service user who owns the account.

Alternatives set aside:

- **Account access for the Consumer's own `obp-oidc#client` user.** Works without changes, but the same
  principal is the *application* in role checks and a *user* in account access. Its identity also follows
  the consumer key (`idGivenByProvider`), so rotating the key loses the access.
- **A bank Role + Scope for the Transaction Request create.** Any Consumer granted the Scope could initiate
  payments from any account at the bank. Too broad for payment initiation: who may initiate from an account
  belongs on the account.
- **A Consent.** Consents are given by people, on whose behalf an app then acts. Here the Consumer itself
  acts.

## Wished-for direction

Let account access be granted to a Consumer, next to account access granted to a user, so that
"who may use this view of this account" stays on the account and the actor is the Consumer itself.
Attribution follows authorization: action → account access → the Consumer → who granted it.

**Additive and opt-in**, so existing user paths don't change:

- A **separate table**, `consumer_account_access`, shaped like `AccountAccess` with the Consumer in place
  of the user and the grantor recorded (unlike `MappedScope`, which doesn't record who granted it):
  `consumer_account_access_id` (UUID), `consumer_id`, `bank_id`, `account_id`, `view_id`,
  `granted_by_user_id`, created/updated; unique on (`consumer_id`, `bank_id`, `account_id`, `view_id`).
  Not the existing
  `AccountAccess` table: its `consumer_id` column already means something else, namely that a *user's*
  grant may only be used through that Consumer (`ALL-CONSUMERS` by default; `User.hasAccountAccess`). A
  user-less row there would change what lookups by `consumer_id` alone return
  (`AccountAccess.findByBankIdAccountIdViewIdConsumerId`), and `user_fk` is part of the unique index.
- Consulted **only when the caller is an application acting as itself**, and only on endpoints that opt in
  through their auth mode (`ApplicationOnly` / `UserXORApplication`). A person calling through the same
  Consumer never picks up the Consumer's account access: that is the leak the old `UserOrApplication` had
  with Scopes.
- Start with the endpoints that need it (the Transaction Request create), not the whole view-check
  surface.

## Open questions

1. **Which views.** Not `owner`: account holding and "own account" permissions are user concepts, and a
   Consumer should not become an account holder. A dedicated view holding only the permission needed (e.g.
   creating Transaction Requests) is least privilege.
2. **Who may grant it.** The account holder, or someone with a grant-access permission on that view. The
   grant records the grantor.
3. **Consumers that may hold it.** Client credentials only. A Consumer that holds account access, like one
   that holds Scopes, should not let people log in through it.
4. **Declaring it.** The Platform App declaration (`PUT /consumers/current/platform-app`) could gain
   `required_account_access` entries (bank, account, view, `needed_for`), approved by the account holder,
   as Scopes are approved by administrators.
5. **SCA / challenges.** Transaction Request creation can raise a challenge above a threshold, and a
   Consumer can't answer an OTP. Application-initiated requests need a rule (no challenge, or a limit on
   the view). This is a policy question as much as a code one.
6. **Recording the initiator.** The Transaction Request and the Transaction it posts should show that a
   Consumer initiated them. Check how the initiator is stored today; if it is only a user, this is a
   data-model change.
7. **Listing and revoking.** Endpoints to grant, list and revoke, and the account's access list showing
   Consumers next to users.

## Size and risk

The view check takes a user throughout: `checkViewAccessAndReturnView` has about 48 call sites and
`hasAccountAccess` about 22, and `AccountAccess` appears in about 58 files. Making all of that
Consumer-aware would be a large and risky change. Done as above (a new table, one extra branch in the view
check for an application acting as itself, opt-in per endpoint), existing behaviour is untouched, and the
risk sits in the new branch, which tests can cover. The larger work is the policy around it: questions 2,
5 and 6.
