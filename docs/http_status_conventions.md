# HTTP status conventions

Reference for status codes OBP-API returns in cases where the obvious answer and the historical
answer differ. Apply this when writing or changing an endpoint.

Current scope: duplicate-create (409). The sibling cases — "not found" answering 400 instead of 404,
"forbidden" answering 400 instead of 403 — follow the same shape and are noted at the end, but have
not been surveyed.

## Duplicate create → 409 Conflict

A `POST` / `PUT` that refuses because the resource already exists must answer **409 Conflict**, not
400. The request is well-formed; the server simply cannot create the resource. 409 preserves the
audit signal that nothing was created, and lets a client treat a duplicate-create as safe to ignore
rather than as malformed input it must not retry.

### How to write it

```scala
Helper.booleanToFuture(failMsg = XxxAlreadyExists, failCode = 409, cc = Some(cc)) { check }
```

`failCode` defaults to 400, which is why the older sites return 400 — the parameter was simply
omitted. The call works unchanged inside any `EndpointHelpers.with*` block. If a native
`Conflict(...)` path is ever needed, add a `withConflictOn(predicate, errorMessage)` helper rather
than scattering raw `IO` responses through handlers.

Pair the fix with a duplicate-creation scenario in that version's routes test asserting both the 409
and the message body. `Http4s700RoutesTest`'s entitlement scenario is the model.

### Which versions return which, and why

Measured 2026-09-14 by scanning every `booleanToFuture` / `tryons` call in `Http4s*.scala` whose
failure message is an `*AlreadyExists` constant (ResourceDoc `errorResponseBodies` entries excluded —
those name the error without choosing a status).

| | 409 | 400 |
|---|---|---|
| v1.4.0 – v5.1.0 | 0 | 29 |
| v6.0.0 | 10 | 0 |
| v7.0.0 | 5 | 1 |

**v6.0.0 is entirely correct** — all ten sites were fixed in place while v6 was pre-GA: `POST /banks`,
account-access-requests, the chat-room create/participant endpoints, the reaction endpoints.

**v7.0.0 is correct except one site**, `Http4s700.scala:4697` (`AccountIdAlreadyExists` in
`createAccountCommon`), which was missed.

**v1.4.0 – v5.1.0 still answer 400 everywhere**, and this is an open question rather than a backlog:
changing them alters an observable status code on versions clients are pinned to. See "The open
decision" below. **Do not "fix" these to make the versions consistent** — the inconsistency is known,
and unifying it in either direction is a contract decision, not a cleanup.

<details>
<summary>The 29 sites on v1.4.0 – v5.1.0 (inventory, not a work queue)</summary>

| version | site | constant | endpoint |
|---|---|---|---|
| v1.4.0 | `Http4s140.scala:422` | `CustomerNumberAlreadyExists` | `addCustomer` |
| v2.0.0 | `Http4s200.scala:1071` | `CustomerNumberAlreadyExists` | `createCustomer` |
| v2.0.0 | `Http4s200.scala:1267` | `EntitlementAlreadyExists` | `addEntitlement` |
| v2.1.0 | `Http4s210.scala:1083` | `CustomerNumberAlreadyExists` | `createCustomer` |
| v2.2.0 | `Http4s220.scala:994` | `CounterpartyAlreadyExists` | `createCounterpartyImpl` |
| v3.0.0 | `Http4s300.scala:1670` | `EntitlementRequestAlreadyExists` | `addEntitlementRequest` |
| v3.0.0 | `Http4s300.scala:2093` | `EntitlementAlreadyExists` | `addScope` |
| v3.1.0 | `Http4s310.scala:2846` | `CustomerNumberAlreadyExists` | `updateCustomerNumber` |
| v3.1.0 | `Http4s310.scala:4349` | `AccountIdAlreadyExists` | `createAccount` |
| v4.0.0 | `Http4s400.scala:2654` | `EntitlementAlreadyExists` | `addScope` |
| v4.0.0 | `Http4s400.scala:2889` | `CounterpartyAlreadyExists` | `createExplicitCounterparty` |
| v4.0.0 | `Http4s400.scala:5203` | `ApiCollectionAlreadyExists` | `createMyApiCollection` |
| v4.0.0 | `Http4s400.scala:5231` | `ApiCollectionEndpointAlreadyExists` | `createMyApiCollectionEndpoint` |
| v4.0.0 | `Http4s400.scala:5257` | `ApiCollectionEndpointAlreadyExists` | `createMyApiCollectionEndpointById` |
| v4.0.0 | `Http4s400.scala:9082` | `EndpointTagAlreadyExists` | `createSystemLevelEndpointTag` |
| v4.0.0 | `Http4s400.scala:9110` | `EndpointTagAlreadyExists` | `updateSystemLevelEndpointTag` |
| v4.0.0 | `Http4s400.scala:9137` | `EndpointTagAlreadyExists` | `createBankLevelEndpointTag` |
| v4.0.0 | `Http4s400.scala:9166` | `EndpointTagAlreadyExists` | `updateBankLevelEndpointTag` |
| v4.0.0 | `Http4s400.scala:9361` | `ConnectorMethodAlreadyExists` | `createConnectorMethod` |
| v4.0.0 | `Http4s400.scala:9613` | `DynamicResourceDocAlreadyExists` | `createDynamicResourceDocImpl` (system + bank) |
| v4.0.0 | `Http4s400.scala:9919` | `DynamicMessageDocAlreadyExists` | `createDynamicMessageDocImpl` (system + bank) |
| v4.0.0 | `Http4s400.scala:10332` | `EntitlementAlreadyExists` | `assertTargetUserLacksRoles` |
| v4.0.0 | `Http4s400.scala:10559` | `CounterpartyAlreadyExists` | `createCounterpartyForAnyAccount` |
| v5.0.0 | `Http4s500.scala:479` | `bankIdAlreadyExists` | `createBank` |
| v5.0.0 | `Http4s500.scala:613` | `AccountIdAlreadyExists` | `createAccount` |
| v5.0.0 | `Http4s500.scala:1183` | `CounterpartyAlreadyExists` | `vrpFlow` (side effect of consent creation) |
| v5.0.0 | `Http4s500.scala:1214` | `CounterpartyLimitAlreadyExists` | `vrpFlow` (side effect of consent creation) |
| v5.1.0 | `Http4s510.scala:1575` | `AgentNumberAlreadyExists` | `createAgent` |
| v5.1.0 | `Http4s510.scala:3627` | `CounterpartyLimitAlreadyExists` | `createCounterpartyLimit` |

The two `vrpFlow` sites are a different question from the rest: the counterparty is created as a
*side effect* of consent creation, so the duplicate is not the resource the caller asked for. 409 may
be the wrong answer there even if it is right everywhere else.

</details>

### The open decision

> Is changing 400 → 409 on a duplicate-create acceptable on a STABLE version?

Not breaking in the usual sense — no field moves, no endpoint disappears — but an observable
status-code change on versions clients are pinned to. Three defensible answers:

- **Never on STABLE.** The 29 stay at 400 permanently; v6.0.0 onwards is correct. The table above
  becomes the permanent explanation of why the codes differ by version.
- **Yes, as a documented fix.** A duplicate-create returning 400 is a defect, and a client treating
  400 as "malformed, do not retry" already mishandles it. Ship with release notes.
- **Only on DEPRECATED versions**, where the contract is end-of-life. Probably the worst of the
  three: it makes the status depend on version *status* rather than version number, which is harder
  to document than either absolute rule.

Until this is answered, new endpoints use 409 and existing v1.4.0–v5.1.0 sites are left alone.

### Constants that look related but are not endpoint checks

| constant | where it lives | note |
|---|---|---|
| `DynamicEntityNameAlreadyExists` | `NewStyle.scala:3505`, `:3507`, `:3535` | name-collision validation inside NewStyle, not an endpoint duplicate check |
| `FeaturedApiCollectionAlreadyExists` | `NewStyle.scala:4388`, `Http4s600.scala:14861` | the v6 site is handled; the NewStyle `RuntimeException` is a 500 path and is its own bug |
| `CardAlreadyExists` | `MappedPhisicalCard.scala:204` | returned as a `Failure` at provider level; status comes from the box unwrap, never from a `booleanToFuture` |
| `ConsumerKeyAlreadyExists` | nowhere | defined in `ErrorMessages.scala`, referenced by nothing — dead constant |

## Siblings, not yet surveyed

The same shape almost certainly applies to "not found" cases answering 400 instead of 404, and
"forbidden" cases answering 400 instead of 403. Worth a sweep once the decision above is made — the
answer there sets the precedent for all of them.
