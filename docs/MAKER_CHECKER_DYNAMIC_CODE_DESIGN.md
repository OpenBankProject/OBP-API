# Maker / Checker for dynamic code and dynamic configuration

Written 2026-09-05. Status: **phase 1 implemented in the working tree (uncommitted), 2026-09-06**;
phases 2 and 3 not started. Implementation notes that differ from the original draft are marked
*(impl)* below.

## 1. Problem

OBP lets an administrator create, at runtime and via the API, artefacts that change how the API
behaves. Several of them carry executable code, several carry outbound host names, and all of them
go live the moment the `POST` succeeds. There is no draft, pending, or approved state anywhere in
the dynamic family. The single exception is `AbacRule.IsActive`, which the creator sets.

The technical sandbox no longer provides a meaningful second line of defence:

- `DynamicUtil.Sandbox` installs a `SecurityManager`, but on JDK 24 and later (JEP 486) this
  enforces nothing (`DynamicUtil.scala:253-259` logs this).
- The GraalVM JavaScript context is created with `HostAccess.ALL`, `PolyglotAccess.ALL` and
  unrestricted host class lookup (`DynamicUtil.scala:486-529`).
- The OBP call allowlist (`dynamic_code_obp_calls_are_restricted`) is off by default and only
  blocks reflection and `ExecutionContext`; it has no notion of file, network or process access.

So a single holder of `CanCreateDynamicResourceDoc` or `CanCreateConnectorMethod` has remote code
execution in the API JVM, with the connector credentials of the whole instance. Maker/checker is
therefore the primary control for dynamic code, not a convenience.

### Inventory of dynamic components

| Component | Carries | Bank column | Provenance today |
|---|---|---|---|
| DynamicResourceDoc | Scala method body, roles, URL, verb | yes | created/updated by, body hash |
| DynamicMessageDoc | Scala / JS / Java body | yes | created/updated by, body hash |
| ConnectorMethod | Scala / JS / Java body | no | created/updated by, body hash |
| AbacRule | Scala rule code, policy, `IsActive` | no | created/updated by |
| DynamicEndpoint | Swagger, including target host | yes | owner only |
| DynamicEntity | Metadata JSON, access flags | yes | owner only |
| MethodRouting | Connector name, bank pattern, params | pattern | none |
| EndpointMapping | Request / response mapping | yes | none |
| WebUiProps | Name / value rendered in Portal | no | none |
| JsonSchemaValidation | Schema per operation id | no | none |
| AuthenticationTypeValidation | Allowed auth types per operation id | no | none |

Create / update / delete for all of these lives in v3.1.0, v4.0.0 and v6.0.0. v7.0.0 currently
has read-only provenance views for the three code families.

### Existing maker/checker in OBP

`AccountAccessRequest` (v6.0.0) is the template: requestor, checker, checker comment, status,
guarded conditional transition from `INITIATED`, and the `MakerCheckerSameUser` error
(`OBP-30279`) when the checker is the requestor. Transaction requests have a similar helper in
`NewStyle.checkMakerCheckerForTransactionRequest`. Consents and `UserAuthContextUpdate` are
two-step but same-user, so they are not the model.

## 2. Decisions

These were settled in discussion and are the constraints on the rest of the document.

1. **Approve content, not records.** The checker approves a hash of the exact payload. Any edit
   produces a new hash and returns the artefact to pending. The three code families already
   compute `MethodBodyHash`.
2. **Enforce at execution, not only at the API.** `DynamicUtil` refuses to compile or run a body
   whose hash is not the approved hash. This holds against direct database edits and against the
   memoised compile cache.
3. **One generic change-request table.** Not a status column per component. One approval API, one
   audit trail, one diff view.
4. **No bank boundary.** Dynamic code runs in the shared JVM; a "bank-level" resource doc is
   bank-scoped only for visibility and roles. Approval is system level for every target type. The
   change request has **no BankId column**; the proposed payload and the target row already carry
   whatever bank id the target has. There are no bank-level approval roles or endpoints.
5. **Four eyes to enable, one pair to disable.** Deactivating an artefact reduces capability, so a
   single approver may do it directly, with audit. Enabling anything requires a request.
6. **Unapproved code never executes in the instance.** Behavioural testing of a pending artefact
   happens on a different environment (sandbox / UAT). Because approval is by hash, the tested
   bytes and the approved bytes are provably the same. The one permitted exception is a single
   trial execution by the checker, who is already trusted to approve.
7. **Off by default, opt in per target type.** Sandboxes and local development keep today's
   behaviour.
8. **New endpoints go in v7.0.0.** Existing v4.0.0 endpoints keep their shape but return `202`
   with a change request when approval is required.

## 3. Data model

New package `code/dynamicchangerequest` with trait, Mapped model and provider, following the
`accountaccessrequest` package.

### DynamicChangeRequest

| Field | Notes |
|---|---|
| `DynamicChangeRequestId` | UUID, unique index |
| `TargetType` | enum, see below |
| `TargetId` | empty for `CREATE` |
| `Operation` | `CREATE`, `UPDATE`, `DELETE`, `ACTIVATE`, `DEACTIVATE` |
| `RequestVerb`, `RequestPath` | *(impl)* the maker's original call, e.g. `POST /obp/v4.0.0/management/banks/BANK_ID/dynamic-resource-docs`. This is how the bank scope of a bank-level write is preserved without a BankId column: apply() re-derives it from the path |
| `ProposedPayload` | the exact JSON body the maker sent, stored verbatim |
| `PayloadHash` | SHA-256 of the canonicalised payload |
| `CurrentPayloadHash` | hash of the live artefact at submission time; empty for `CREATE`. Lets the approver detect that the target changed underneath the request |
| `Status` | `INITIATED`, `APPROVED`, `REJECTED`, `WITHDRAWN`, `EXPIRED`, `FAILED` |
| `RequestorUserId` | maker |
| `BusinessJustification` | free text from the maker |
| `CheckerUserId` | set on approval / rejection |
| `CheckerComment` | free text from the checker |
| `CreatedAt`, `ActionedAt`, `ExpiresAt` | |

`DEACTIVATE` rows are written already `APPROVED`, with requestor = checker: deactivation is a
direct action (decision 5) and the row exists only as its audit record.

`FAILED` means "approved, but re-validation or apply failed". Without it, an approval whose apply
step threw would be indistinguishable from one that succeeded.

`TargetType` enum, in `obp-commons` `Enumerations.scala` next to `AccountAccessRequestStatus`:

```
DYNAMIC_RESOURCE_DOC, DYNAMIC_MESSAGE_DOC, CONNECTOR_METHOD, ABAC_RULE,
DYNAMIC_ENDPOINT, DYNAMIC_ENTITY, METHOD_ROUTING, ENDPOINT_MAPPING,
WEBUI_PROPS, JSON_SCHEMA_VALIDATION, AUTHENTICATION_TYPE_VALIDATION
```

Transitions out of `INITIATED` use the same guarded conditional `UPDATE ... WHERE status =
'INITIATED'` that `AccountAccessRequest.scala:87-99` uses, so two checkers cannot both action one
request. Rows are never deleted; the table is the audit log.

### Additions to existing tables

- `ApprovedHash` on DynamicResourceDoc, DynamicMessageDoc, ConnectorMethod, AbacRule.
- `IsActive` (default `true`) on every runtime-loaded dynamic model that lacks it.

The runtime loads a row only if `IsActive` is true and, for code, only if `MethodBodyHash ==
ApprovedHash`. When maker/checker is disabled for a target type the hash check is skipped.

## 4. Endpoints (v7.0.0)

```
POST   /management/dynamic-change-requests
GET    /management/dynamic-change-requests?status=&target_type=&requestor_user_id=&target_id=
GET    /management/dynamic-change-requests/CHANGE_REQUEST_ID
POST   /management/dynamic-change-requests/CHANGE_REQUEST_ID/approval
POST   /management/dynamic-change-requests/CHANGE_REQUEST_ID/rejection
POST   /management/dynamic-change-requests/CHANGE_REQUEST_ID/withdrawal
GET    /my/dynamic-change-requests
```

Nothing under `/banks/BANK_ID/` (decision 4).

### Submission

Two routes into the queue:

- **Interception (the common case).** When `dynamic_code_requires_approval` is true and the target
  type is listed, the existing v4.0.0 / v6.0.0 create, update and delete endpoints validate and
  compile as they do today, then instead of applying they store a change request and return
  `202 Accepted` with the change request JSON. Clients, API Explorer and the Portal need no new
  payload format.
- **Explicit `POST /management/dynamic-change-requests`** for tooling that wants to submit with a
  justification up front:

```json
{
  "target_type": "DYNAMIC_RESOURCE_DOC",
  "operation": "UPDATE",
  "target_id": "…",
  "proposed_payload": { …exact v4.0.0 body… },
  "business_justification": "…"
}
```

The server computes the hash; the client never supplies it on submission. *(impl)* `bank_id` is an
optional field of this explicit submission only; it selects the bank-level v4 endpoint whose path is
recorded in `request_path`. The caller must hold the role the direct write would demand.

### Approval

```json
POST …/approval
{ "payload_hash": "sha256:…", "checker_comment": "…" }
```

The hash in the body must equal the stored `PayloadHash`, otherwise `DynamicChangeRequestHashMismatch`.
This forces the checker's client to display what is being approved and makes a re-submission race
fail loudly. On approval the server, in order:

1. checks `CheckerUserId != RequestorUserId` (`MakerCheckerSameUser`, no bypass);
2. checks the target's current hash still equals `CurrentPayloadHash`
   (`DynamicChangeRequestStale` otherwise);
3. re-validates: compile again, run the dependency validator again, check any host against
   `dynamic_code_approval_egress_host_allowlist`;
4. applies the payload through the same provider call the v4.0.0 endpoint uses today, sets
   `ApprovedHash` and `IsActive`;
5. invalidates caches (section 8);
6. transitions the request with the guarded update. If 3 or 4 fail the request goes to `FAILED`
   with the error in `CheckerComment`.

Rejection and withdrawal take `{ "comment": "…" }`. Withdrawal is by the requestor only.
*(impl)* Hashes are bare SHA-256 hex, matching the existing `MethodBodyHash` column; a `sha256:`
prefix is accepted on approval. `payload_hash` is over the canonical JSON of the request body;
`current_payload_hash` and `ApprovedHash` are the target's decoded method body hash (for ABAC
rules, the hash of `rule_code`).

### Reading

`GET …/CHANGE_REQUEST_ID` returns the proposed payload, the live payload if the target exists,
both hashes, and the status history. A client can render a diff from that.

```json
{
  "dynamic_change_request_id": "…",
  "target_type": "DYNAMIC_RESOURCE_DOC",
  "target_id": "…",
  "operation": "UPDATE",
  "status": "INITIATED",
  "payload_hash": "sha256:…",
  "current_payload_hash": "sha256:…",
  "proposed_payload": { … },
  "current_payload": { … },
  "requestor_user_id": "…",
  "business_justification": "…",
  "checker_user_id": "",
  "checker_comment": "",
  "created_at": "…",
  "actioned_at": "",
  "expires_at": "…"
}
```

The existing v7.0.0 read-only views for resource docs, connector methods and message docs gain
`approved_hash`, `is_active` and `last_change_request_id`.

### Deactivation (direct)

```
POST /management/dynamic-resource-docs/ID/deactivation      (and per family)
```

Single approver, no request. Audited by writing a `DynamicChangeRequest` row with operation
`DEACTIVATE` and status `APPROVED`, requestor = checker. (Alternative: a separate audit table.
Reusing the request table keeps one audit surface.) Re-activation is a normal request with
operation `ACTIVATE` and needs a second person.

### Checker trial execution (phase 3)

A pending resource doc may be invoked once at its own URL with `?change_request_id=…` by a holder
of the approver role while the request is `INITIATED`. This is the only path that runs unapproved
code, and it is limited to the person who is about to approve it. Gated by
`dynamic_code_approval_allow_checker_trial_execution`.

## 5. Roles

Two new roles, both system level, in `ApiRole.scala`:

- `CanApproveDynamicChangeRequest` — approve, reject, deactivate, trial-execute.
- `CanGetDynamicChangeRequests` — list and read all requests.

Makers use the existing `CanCreate*` / `CanUpdate*` / `CanDelete*` roles, including the bank-level
variants; those still decide who may *submit*. `/my/dynamic-change-requests` needs no role.

No `AtAnyBank` or `BankLevel` variants of the new roles.

## 6. Props

```
dynamic_code_requires_approval=false
dynamic_code_approval_target_types=DYNAMIC_RESOURCE_DOC,DYNAMIC_MESSAGE_DOC,CONNECTOR_METHOD,ABAC_RULE
dynamic_code_delete_requires_approval=true
dynamic_code_approval_request_ttl_hours=168
dynamic_code_approval_guard_cache_ttl_seconds=10
dynamic_code_approval_allow_checker_trial_execution=false
dynamic_code_approval_egress_host_allowlist=
```

*(impl)* The family was first written as `maker_checker.dynamic.*`; renamed 2026-09-06 because
"enabled" did not say what was enforced. `dynamic_code_requires_approval` names the effect: writes
to the listed types need a second person, and the runtime runs only approved code. Underscores
throughout, since OBP maps a prop to its environment variable by replacing dots with underscores anyway.

- `dynamic_code_requires_approval=false` means behaviour is exactly today's.
- `target_types` lists what is intercepted; the default is the four code families.
- `egress_host_allowlist` applies to DynamicEndpoint swagger hosts and to
  `PUT …/dynamic-endpoints/ID/host`. Empty means unrestricted, so existing installs do not break.
- `allow_checker_trial_execution` is the one deliberate weakening, off by default.

A scheduled job (or lazy check on read) moves `INITIATED` rows past `ExpiresAt` to `EXPIRED`.

## 7. Error messages

New block in `ErrorMessages.scala`, next free numbers after `OBP-30345`:

| Name | Meaning |
|---|---|
| `DynamicChangeRequestNotFound` | |
| `DynamicChangeRequestNotInitiated` | already actioned, withdrawn or expired |
| `DynamicChangeRequestHashMismatch` | approval body hash differs from stored hash |
| `DynamicChangeRequestStale` | target changed since submission |
| `DynamicChangeRequestTargetTypeNotManaged` | type not in `target_types` |
| `DynamicChangeRequestApprovalRequired` | returned with `202` by intercepted endpoints |
| `DynamicChangeRequestEgressHostNotAllowed` | |
| `DynamicArtefactInactive` | runtime refused to load / execute |
| `DynamicArtefactNotApproved` | body hash ≠ approved hash |

`MakerCheckerSameUser` (`OBP-30279`) is reused.

## 8. Enforcement and caches

- `DynamicUtil.compileScalaCode`, `createJsFunction`, `createJavaFunction` take the approved hash
  and refuse when it does not match the body hash (only when the feature is enabled for that type).
- Approval, rejection-after-failed-apply and deactivation call
  `NewStyle.function.invalidateDynamicResourceDocCaches()` (which today is only called from the
  DynamicEntity paths) and clear the relevant entry in `DynamicUtil.dynamicCompileResult` /
  `memoDynamicFunction`. Those memos are keyed by code string and are never cleared today.
- MethodRouting and EndpointMapping already have invalidation helpers; approval calls them.

## 9. Migration

All new columns are additive with defaults, so existing rows stay active. When
`dynamic_code_requires_approval` is first turned on, a one-off step sets `ApprovedHash` to the
current `MethodBodyHash` for every existing code row. Without this, enabling the feature would
silently disable every dynamic endpoint already in production. This should be a bootstrap task
that runs once and logs what it did, not a manual SQL script.

*(impl)* `MakerChecker.seedApprovedHashesIfEnabled()` runs from `Boot` and is guarded by a
`MigrationScriptLog` row named `seedDynamicCodeApprovedHashes`, so it runs **once per database**,
not at every boot. Repeating it at every boot would have blessed any row inserted directly into the
database with a blank `ApprovedHash`, which is exactly the path decision 2 closes. After the seed, a
row without an approved hash (direct insert, or created while the prop was off) stays unexecutable
until a second person `ACTIVATE`s it through a change request. A failed seed is logged as
unsuccessful and retried at the next boot.

## 9a. Where phase 1 lives *(impl)*

- `code/dynamicchangerequest/`: trait, Mapped model + provider, and `MakerChecker` (config,
  hashing, execution guard, intercept, approve/apply, reject, withdraw, deactivate, boot seeding).
- Execution guard call sites: `DynamicResourceDocsEndpointGroup`, `InternalConnector.getFunction`,
  `DynamicConnector.getFunction`, `AbacRuleEngine.compileRule`.
- Interception: `Http4s400` (dynamic resource docs, dynamic message docs, connector methods) and
  `Http4s600` (abac-rules). Intercepted handlers use `executeFutureWithStatus` /
  `withUserAndStatus` so they can answer 201/200/204 or 202.
- Endpoints and JSON: `Http4s700` (seven change request endpoints plus four deactivation endpoints),
  `JSONFactory700`. Test: `code.api.v7_0_0.DynamicChangeRequestTest`.
- Seeding runs from `Boot.scala` via `MakerChecker.seedApprovedHashesIfEnabled()`, once per
  database (see section 9).
- A `CREATE` request has an empty `target_id` until approval; `approve` writes the id of the row it
  created back to the request so the request points at what it made.
- Not in phase 1: egress allowlist, checker trial execution, `last_change_request_id` on the read
  views, non-code target types (the enum lists them; `MakerChecker.applicableTargetTypes` gates apply).

## 10. Rollout

1. **Code families.** Table, provider, the seven endpoints, interception in the v4.0.0 / v6.0.0
   create-update-delete paths for the four code types, hash check in `DynamicUtil`, `IsActive` and
   `ApprovedHash` on those four models, deactivation endpoints, migration step, tests in
   `v7_0_0`.
2. **Egress and routing.** DynamicEndpoint host and the allowlist, MethodRouting, EndpointMapping,
   DynamicEntity.
3. **Presentation and validation, plus UX.** WebUiProps, JsonSchemaValidation,
   AuthenticationTypeValidation, the diff fields on the read views, checker trial execution.

## 11. Open questions

- **Delete.** `require_approval_for_delete` defaults to true here; deleting does not expand
  capability but does break consumers. Could reasonably default to false.
- **Deactivation audit.** Reuse the request table (as above) or a separate audit table.
- **Canonicalisation.** The hash must be over a canonical JSON form (sorted keys, no
  insignificant whitespace) or a client that reorders keys will produce a spurious "different
  content". Use the same canonicaliser on submission, approval and the runtime check.
- **Portal.** A checker queue page in the Portal is the natural UI; out of scope for the API work.
