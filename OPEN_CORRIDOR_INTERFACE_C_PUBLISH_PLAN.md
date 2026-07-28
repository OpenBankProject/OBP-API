# Open Corridor — OBP-API: publish the Interface C messages

**Audience:** an agent implementing the OBP-API (Scala) side of Open Corridor.
**Scope:** make OBP-API *record a promise* and *publish the RabbitMQ messages*
that the OBP Bank Node already consumes — `credit_notification` (carrying the
commitment salt) and `settlement_instruction`.

> **Production, not PoC (directive 2026-07-18).** This doc previously framed
> the build as a proof-of-concept and deferred parts "for the PoC". That
> framing is retired: this is production development. Where a shortcut was
> PoC-justified it is now a requirement — see §5.2 (per-bank broker
> connections), §5.3 (transactional outbox), §8. The still-legitimate
> narrowings are *scope* (one corridor, one currency, bilateral,
> settle-on-demand), not *quality or durability*.

This is the **gating piece** of the corridor build. The Bank Node side is built and
verified; what's missing is OBP-API actually sending these messages.

> **How to use this doc:** Section 4 is a **locked wire contract** — the Bank
> Node consumer is already implemented against it, so build OBP-API to match it
> exactly (field names, the OBP envelope, the salt fields). Sections 5–6 are the
> OBP-API build, in order. Confirm current line numbers before editing (cited
> from a survey on 2026-06-14).

> **Updated 2026-07-18** to align with `OPEN_CORRIDOR_SIMPLE_NETTING.md`
> (bilateral settle-on-demand netting on the TransactionRequest model), which
> postdates this plan. Two changes: §5.1 stores promise state on the
> **TransactionRequest** (held at `PENDING`; there is no posted Transaction to
> carry a `PROMISED` status until the settle step), and §5.3's trigger is the
> **settle-pair endpoint** (one pending promise is simply the degenerate case).
> Also since 2026-06-14: the Bank Node consumer transport is
> integration-tested against a real RabbitMQ (every §4.2 error code
> exercised), and the `settlement_instruction` → ADA transfer path has run
> live on Cardano preprod. The wire contract in §4 is proven on the consuming
> side.

> **Updated 2026-07-18 (second pass — naming & settlement-TR decisions):**
> (1) **TR types renamed**: promise = `OPEN_CORRIDOR_PROMISE` (was
> `OPEN_CORRIDOR`), plus a new `OPEN_CORRIDOR_SETTLEMENT` type — see §5.0
> for the rename inventory and `OPEN_CORRIDOR_SIMPLE_NETTING.md` §0 for the
> scheme definition that justifies the prefix. (2) **The settle step mints
> an internal `OPEN_CORRIDOR_SETTLEMENT` TransactionRequest** ("TR B") whose
> execution posts the net Transaction — TR-first convention; TR B doubles as
> the settle-event audit object. (3) **Discharge linkage**: the net
> Transaction's id is recorded on each covered promise TR as a
> `settled_by_transaction_ids` TR attribute; `transaction_ids` keeps its
> causal meaning (empty on promises, populated on TR B). Details:
> `OPEN_CORRIDOR_SIMPLE_NETTING.md` §4a.

---

## 1. Goal and the end-to-end loop

A single Open Corridor payment, Bank A → Bank B, settled on Cardano. Bilateral
settle-on-demand netting per `OPEN_CORRIDOR_SIMPLE_NETTING.md` — promises
accumulate as `PENDING` TRs and an admin settle-pair collapses them; with one
pending promise the net *is* that amount (the degenerate case). Every OBP-API
piece is real.

```
1. Bank A CBS ──A1──▶ Bank A Node ──B(submit OPEN_CORRIDOR_PROMISE TR)──▶ OBP-API   [TR endpoint EXISTS]
2. Bank A Node ── writes Promise commitment to Cardano (it holds the keys)
3. Bank A Node ── reports {tx_hash, blockchain, commitment, salt, preimage} ──▶ OBP-API   [BUILT: §5.1]
4. OBP-API ── admin settle-pair: net = SUM(A→B) − SUM(B→A), then PUBLISHES:            [NEW: §5.2–5.3]
     • obp_credit_notification (+salt)  ──▶ Bank B Node ──A2──▶ Bank B CBS
     • obp_settlement_instruction (net) ──▶ Bank A Node ── settles ADA ──▶ Cardano
5. Bank B recomputes SHA-256(salt‖preimage) == commitment, finds it on-chain. Proof closed.
```

## 2. Division of labour (non-negotiable)

- **Bank Node holds the bank's keys** and performs **all** Cardano writes
  (promise, settlement). It is a separate Rust service at
  `~/Documents/workspace_2024/OBP-Bank-Node`.
- **OBP-API never signs or writes to Cardano as the bank.** It records promise
  references that the node reports back, nets, and publishes the RabbitMQ
  messages. It *may* read the chain to verify, never to act on the bank's behalf.
- **Do not mock OBP-API functionality.** Everything in this doc is built for real
  in this codebase. (A mock *CBS* is fine — that's the bank's system, not OBP-API.)
- **Robustness rule:** the flow may be a thin slice, but OBP-API code is
  production-grade — schema, state machine, audit, MessageDocs, tests. See the
  companion `OBP_API_CHANGES.md` (in the Bank Node repo) for the full netting
  build; this doc is the *publishing subset* needed first.

## 3. What exists in OBP-API today (survey 2026-06-14)

- **`createTransactionRequestOpenCorridor`** — v7.0.0 endpoint at
  `obp-api/src/main/scala/code/api/v7_0_0/Http4s700.scala:3183`. Today it is
  "SIMPLE + a mandatory `originator` block" for Travel Rule. It creates a
  standard `TransactionRequest` of type `OPEN_CORRIDOR` (renamed to
  `OPEN_CORRIDOR_PROMISE`, decision 2026-07-18 — §5.0)
  (`obp-commons/.../model/enums/Enumerations.scala:129`) and persists the
  originator. It does **not** record a promise status, write to Cardano, publish
  anything, or compute a salt/commitment.
- **`OpenCorridorProcessor.scala`**
  (`obp-api/src/main/scala/code/bankconnectors/opencorridor/`) — the OC home; its
  header comment explicitly notes Cardano Promise / netting / settlement are
  future extensions.
- **RabbitMQ connector is client-only RPC.**
  `RabbitMQUtils.sendRequestUndGetResponseFromRabbitMQ` (`:90`) publishes a
  request to `obp_rpc_queue` and awaits a reply on a per-request `replyTo` queue,
  correlating by `correlationId` — exactly the shape we need, but today it's only
  used for OBP-API→adapter calls. `RabbitMQConnectionPool` is single-vhost. There
  are **no** Open Corridor outbound messages, no netting engine, no snapshot
  table, no settlement, no transactional outbox.

**Implication:** the "server-initiated RPC" we need is *structurally the same
publish+await-reply* the connector already does — the new work is (a) connecting
to the **bank's** vhost, (b) the new message DTOs, and (c) the trigger.

## 4. LOCKED wire contract (build OBP-API to match this exactly)

The Bank Node consumer is implemented against this. Source of truth:
`OBP-Bank-Node/crates/obp-bank-node/src/interface_c/types.rs` (+ `router.rs`).

### 4.1 Transport / topology

- Each bank consumes on **its own vhost**, e.g. `/bank.ke.01.kcs`.
- Queue name: **`obp_rpc_queue`** (the bank's `request_queue`).
- OBP-API publishes one message with AMQP properties:
  - `messageId` = the operation (the four values below),
  - `correlationId` = a UUID,
  - `replyTo` = an OBP-API-side reply queue (per request),
  then awaits the bank's reply on `replyTo`, matched by `correlationId`.
- `MessageId` values: `obp_credit_notification`, `obp_settlement_instruction`,
  `obp_netting_snapshot`, `obp_status_update`.

### 4.2 Reply envelope (what OBP-API receives back)

```json
{
  "inboundAdapterCallContext": { "correlationId": "<uuid>" },
  "status": { "errorCode": "", "backendMessages": [] },
  "data": { }
}
```

`errorCode == ""` means success. Non-empty `errorCode` is a failure the Bank Node
reports — OBP-API must handle these (do **not** treat the payment as delivered):

| `errorCode` | Meaning |
|---|---|
| `OBP-BANK-NODE-COMMITMENT-MISMATCH` | salt+preimage did **not** hash to the commitment — bank refused to credit |
| `OBP-BANK-NODE-CBS-DELIVERY-FAILED` | bank's CBS did not accept the credit |
| `OBP-BANK-NODE-SETTLEMENT-FAILED` | on-chain settlement transfer failed |
| `OBP-BANK-NODE-SETTLEMENT-NOT-CONFIGURED` | that node has no settlement rail |
| `OBP-BANK-NODE-BAD-MESSAGE` | OBP-API sent a malformed body |
| `OBP-BANK-NODE-NOT-IMPLEMENTED` | unknown `messageId` |

### 4.3 `obp_credit_notification` (publish to **Bank B**'s vhost)

The body is `lower_snake_case`. The **three evidence fields are the point** — they
carry the commit–reveal data so Bank B can open Bank A's on-chain commitment
without Bank A's cooperation.

```json
{
  "transaction_request_id": "tr-abc-123",
  "value": { "currency": "KES", "amount": "1500.00" },
  "description": "Invoice 4471",
  "originator": { "name": "Acme Coffee Ltd", "address": "Nairobi" },
  "netting_snapshot_id": "snap-1",
  "promise_id": "<cardano tx id of Bank A's Promise>",
  "promise_blockchain": "cardano",
  "promise_commitment": "<hex SHA-256 Bank A wrote on-chain>",
  "promise_salt": "<the salt Bank A used>",
  "promise_preimage": "<the exact bytes Bank A hashed>"
}
```

Bank B recomputes `SHA-256(promise_salt ‖ promise_preimage)` and compares to
`promise_commitment`. **On mismatch it returns `COMMITMENT-MISMATCH` and does not
credit.** On success, reply `data` = `{ transaction_request_id, verified,
cbs_reference }`.

> `promise_commitment`, `promise_salt`, `promise_preimage` are **opaque** to
> OBP-API — it just relays what Bank A reported (§5.1). OBP-API does not need to
> know the preimage format. Treat them as strings.

### 4.4 `obp_settlement_instruction` (publish to the **debtor**'s vhost — Bank A)

```json
{
  "snapshot_id": "snap-1",
  "settlement_id": "settle-1",
  "settlement_system": "cardano-ada",
  "currency": "KES",
  "amount": "1500.00",
  "creditor_bank_id": "gh.29.uk",
  "creditor_address": "<Bank B's Cardano bech32 address>",
  "idempotency_key": "settle-1"
}
```

`amount` is **major units** (the Bank Node parses to minor units itself, assuming
2 decimals — a documented limitation to revisit for non-2-exponent currencies).
The debtor node settles from its own wallet.

**Idempotency + finality semantics (Bank Node behaviour as of 2026-07-18):**

- `idempotency_key` (falling back to `settlement_id`) is **required** — a
  message with neither is refused as `OBP-BANK-NODE-BAD-MESSAGE`. The node
  keeps a durable settlement record per key and will **never pay twice** for
  the same key.
- The success reply `data` is
  `{ settlement_id, status, tx_id, blockchain, asset, asset_amount, depth,
  finality_depth }`. `status` is one of:
  - `SUBMITTED` — broadcast to the chain, **not yet final**;
  - `FINAL` — confirmed at ≥ `finality_depth` (node config, default 15
    blocks ≈ 5 minutes on Cardano); treat the settlement as settled only now;
  - `SETTLING` — an attempt is in flight (or crashed mid-flight; the node
    will not auto-retry an ambiguous attempt).
- **Redelivery is the polling mechanism.** Re-publishing the same instruction
  (same key) returns the current recorded state — this is how OBP-API
  observes the `SUBMITTED → FINAL` transition without a new message type, and
  it composes with the transactional-outbox redelivery in §5.3. Keep
  redelivering until `status = FINAL` (then flip the OBP-side settlement
  state) or a terminal `OBP-BANK-NODE-SETTLEMENT-FAILED`.
- A failure reply (`SETTLEMENT-FAILED`) may be transient: if the underlying
  cause provably never reached the chain, the node allows the retry on the
  next redelivery; ambiguous failures (transport loss around broadcast) stick
  and repeat the recorded error until reconciled by ops.

### 4.5 `obp_status_update` / `obp_netting_snapshot`

Lighter; the Bank Node records and ACKs. `status_update` =
`{ transaction_request_id, status }`. `netting_snapshot` carries `snapshot_id` (+
whatever snapshot detail). Optional for the first slice.

## 5. The OBP-API build (in order)

### 5.0 Rename the TR types (decided 2026-07-18)

`OPEN_CORRIDOR` → `OPEN_CORRIDOR_PROMISE`; add `OPEN_CORRIDOR_SETTLEMENT` for
the settle leg (§5.3's TR B). Rationale: `PROMISE`/`SETTLEMENT` name
mechanisms, the `OPEN_CORRIDOR_` prefix binds them to the scheme's rules
(`OPEN_CORRIDOR_SIMPLE_NETTING.md` §0). Bare names were rejected: bare
`SETTLEMENT` collides with the unrelated v7 market-trading settlement
endpoints, and a bare generic `PROMISE` would force any future
deferred-settlement product to inherit the Travel-Rule body shape and share
per-type props. The scoped names converge with the Bank Node's
`LEDGER_DESIGN.md` vocabulary (`OPEN_CORRIDOR_PROMISE`).

Rename inventory (OBP-API):
- enum value at `Enumerations.scala:130` (+ add the `OPEN_CORRIDOR_SETTLEMENT` value);
- route literal + ResourceDoc URL template in `Http4s700.scala`
  (`.../transaction-request-types/OPEN_CORRIDOR_PROMISE/transaction-requests`);
- the `OPEN_CORRIDOR` cases in `LocalMappedConnector.scala` (`:5267`) and
  `MappedTransactionRequestProvider.scala` (`:107/:171/:275/:347`);
- prop name `transactionRequests_challenge_threshold_OPEN_CORRIDOR` →
  `..._OPEN_CORRIDOR_PROMISE`;
- `OpenCorridorProcessor.scala:42`'s type constant;
- **add both new names to `literalAllCapsSegments` in `Http4sSupport.scala`**
  so the ResourceDoc matcher treats them as literal path segments, not
  wildcards. (Latent quirk found 2026-07-18: `OPEN_CORRIDOR` itself is
  missing from that set today — the rename must not reproduce that.)
- tests + any dev/test data rows carrying the old type string.

Bank Node side: its submit-TR client URL and any internal type strings
(rename agreed 2026-07-18). The §4 wire contract carries no TR type, so it is
untouched.

### 5.1 Record the promise + accept the report-back

> **BUILT (2026-07-28, branch `open-corridor-salt-relay`).** Endpoint
> `POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/open-corridor/promise`,
> body `{ tx_hash, blockchain, commitment, salt, preimage }` — **field renamed
> from `cardano_tx_hash` to `tx_hash`** (2026-07-28): the chain is already
> identified by `blockchain`, so the hash field is chain-neutral. Build the
> Bank Node report-back client against `tx_hash`. Gated by new bank-level
> role `CanAttachOpenCorridorPromise`. Evidence lands as TR attributes
> (`open_corridor_tx_hash` / `_blockchain` / `_commitment` / `_salt` /
> `_preimage` + `open_corridor_promise_reported_by` / `_reported_at` audit
> side-car, `OpenCorridorProcessor.attachPromiseEvidence`). Idempotent:
> identical re-post returns the stored record; differing evidence refused
> (OBP-40053, append-once); TR row-locked against concurrent double-attach.
> Requires TR type `OPEN_CORRIDOR_PROMISE` (OBP-40051) at status `PENDING`
> (OBP-40052). The hold-at-PENDING routing (§8.4) is also built: `getStatus`
> holds below-threshold promises at `PENDING`, challenge-answer lands at
> `PENDING` (never posts), the pending-TR scheduler skips `OPEN_CORRIDOR*`
> types, and the type's default challenge threshold is effectively infinite.

After Bank A's node writes the Promise to Cardano, it must report the references
back so OBP-API can relay the salt. Expose a way to attach to the existing
TransactionRequest:

- **New inbound endpoint** (v7.0.0, http4s — follow `CLAUDE.md` ResourceDoc
  rules), e.g.
  `POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/open-corridor/promise`
  with body `{ tx_hash, blockchain, commitment, salt, preimage }`.
- **Persist on the TransactionRequest** (revised 2026-07-18, per
  `OPEN_CORRIDOR_SIMPLE_NETTING.md`): the promise **is** the TR held at
  `PENDING` — no Transaction is posted until the settle step, so there is no
  `MappedTransaction` row to carry a `PROMISED` status. Store
  `cardano_tx_hash` / `blockchain` / `commitment` / `salt` / `preimage` as
  **Transaction Request attributes**; the TR `status` stays `PENDING`
  (existing enum value, no schema change). The heavier options this doc
  previously listed (`MappedTransaction.status = PROMISED` + transaction
  attributes, or the OC tables from `OBP_API_CHANGES.md` §1) remain the
  productionization path only if the snapshot-as-audit-object requirement
  returns.
- Audit-log the report-back (who attached which promise evidence, when).
- **The Bank Node's client half of this call does not exist yet either** — its
  dispatcher today stops at `PROMISE_WRITTEN` and reports nothing back
  (`OBP-Bank-Node/crates/obp-bank-node/src/dispatcher.rs`). Coordinate: this
  endpoint's shape is the contract for that new outbox step.

> Why report-back rather than OBP-API reconstructing the preimage: the preimage
> format is a Bank-Node-internal detail (it hashes `{tx_request_id, originating
> bank/account, instruction}`). Relaying keeps OBP-API decoupled from that format
> and keeps the commitment exactly reproducible.

### 5.2 Server-initiated publish capability

> **BUILT (2026-07-28, branch `open-corridor-salt-relay`).**
> `OpenCorridorBankBroker` (Mapper table, unique per bank_id: host/port/vhost/
> credentials/use_ssl + `settlement_address`, schemified in Boot) with v7 admin
> endpoints `PUT|GET|DELETE /banks/BANK_ID/open-corridor/broker` (system role
> `CanConfigureOpenCorridorBroker`; password write-only, never echoed).
> `OpenCorridorPublisher` does publish-and-await-reply to the bank's vhost
> (queue `obp_rpc_queue`, AMQP messageId/correlationId/replyTo, §4.2 envelope
> parse) with one cached auto-recovering connection per bank — deliberately
> self-contained from `RabbitMQUtils` (whose object init hard-requires the
> global `rabbitmq_connector.*` props describing the OBP adapter broker, not
> these per-bank brokers). Errors OBP-40054 (no broker registered) /
> OBP-40055 (publish failed). Open decision 5 (creditor address source) is
> RESOLVED: `settlement_address` lives on the broker registration row.

Add a publish-and-await-reply to the bank's vhost. **Reuse the existing RPC
shape** (`RabbitMQUtils.sendRequestUndGetResponseFromRabbitMQ`, `:90`) — it
already does publish-to-`obp_rpc_queue` + await-on-`replyTo` + correlate. The new
parts:

- **Target the bank's vhost — per-bank connections are required, not
  optional** (revised 2026-07-18). Even one corridor needs **two** vhosts:
  `obp_credit_notification` goes to the creditor bank's vhost and
  `obp_settlement_instruction` to the debtor's. Build a per-bank broker
  registry (bank_id → host/port/vhost/credentials, a small table populated at
  onboarding) and connection handling keyed by bank_id — the
  `RabbitMQConnectionPool` refactor of `OBP_API_CHANGES.md` §4a, or an
  equivalent that manages one connection per bank with reconnect/backoff.
  A hardcoded single-bank connection cannot deliver the flow at all.
- **New `MessageId`s + DTOs** (§5.4).
- Map the bank's reply envelope (§4.2): success on `errorCode == ""`, otherwise
  surface the `errorCode` to the caller / mark the TR `EXCEPTION`.

### 5.3 The trigger: the admin settle-pair endpoint (revised 2026-07-18)

> **BUILT (2026-07-28, branch `open-corridor-salt-relay`).**
> `POST /obp/v7.0.0/open-corridor/settle` `{bank_id_a, bank_id_b, currency}`,
> system role `CanSettleOpenCorridor`, gated by `open_corridor_enabled`
> (OBP-40057). `OpenCorridorSettlement.settlePair`: row-locks each candidate
> promise (re-read under lock, so a concurrent double-trigger cannot settle the
> same promises twice; no-pending re-trigger is a no-op), nets both directions,
> mints + executes TR B between the pair's settlement accounts
> (`OBP-OUTGOING-SETTLEMENT-ACCOUNT` → `OBP-INCOMING-SETTLEMENT-ACCOUNT`),
> writes `settled_by_transaction_ids` AND `settled_by_transaction_request_id`
> (TR B's id — kept even at net zero) attributes on each covered promise,
> flips them COMPLETED, and enqueues the messages into the `OpenCorridorOutbox`
> Mapper table in the same request DB transaction. `OpenCorridorOutboxRelay`
> (Boot-started when `open_corridor_enabled`, `open_corridor.outbox_relay_interval`
> default 10s) publishes with exponential backoff and records each §4.2 reply:
> settlement rows stay PENDING through SUBMITTED/SETTLING (redelivery-as-polling,
> §4.4) until FINAL; refutable business errors (COMMITMENT-MISMATCH etc.) go
> STICKY for operator reconciliation, never swallowed. Fails fast pre-mutation
> when either bank lacks a broker registration or (net ≠ 0) the creditor lacks a
> `settlement_address` (OBP-40056). **Open decision 9 (net-equals-zero) is
> RESOLVED:** TR B is still minted (audit object) and completes with empty
> `transaction_ids`; promises discharge with `settled_by_transaction_request_id`
> only; no settlement instruction is sent; credit notifications still go out.

The trigger is the settle-pair endpoint from `OPEN_CORRIDOR_SIMPLE_NETTING.md`
§6 — **not** a per-promise "settle now" action. With one pending promise it
degenerates to exactly the old behaviour, so nothing is lost and real netting
(N pending promises → one net settlement) is gained:

- `POST /obp/v7.0.0/open-corridor/settle` (shape to taste, e.g. body
  `{ bank_id_a, bank_id_b, currency }`), gated by a **system role** (new
  ApiRole, e.g. `CanSettleOpenCorridor`).
- It, atomically:
  1. queries the `PENDING` `OPEN_CORRIDOR_PROMISE` TRs for the pair+currency,
     both directions; computes `net = SUM(A→B) − SUM(B→A)`; debtor = the side
     that owes, creditor = the other;
  2. mints one internal `OPEN_CORRIDOR_SETTLEMENT` TransactionRequest
     ("TR B") between the pair's settlement accounts and executes it, posting
     **one Transaction** for `abs(net)` (revised 2026-07-18 — TR-first, not a
     direct connector-level posting; the Transaction's id lands in TR B's
     `transaction_ids` causally, as normal, and TR B doubles as the
     settle-event audit object);
  3. writes that Transaction's id into each covered promise TR's
     `settled_by_transaction_ids` attribute (revised 2026-07-18 — NOT into
     `transaction_ids`, which keeps its causal meaning and stays empty on
     promises; see `OPEN_CORRIDOR_SIMPLE_NETTING.md` §4a) and sets them
     `COMPLETED`;
  4. publishes `obp_credit_notification` to **each creditor-side beneficiary
     bank** with the relayed evidence triplet (one per covered inbound TR),
     and `obp_settlement_instruction` (the **net** amount) to the **debtor**'s
     vhost;
  5. records the replies (per §4.2; a non-empty `errorCode` must not be
     swallowed).
- **Idempotency:** mint a stable settlement id when the settle begins and use
  it as `settlement_id` *and* `idempotency_key`; a re-trigger for a pair with
  no `PENDING` TRs is a no-op. Steps 1–3 run in one DB transaction so a
  concurrent double-trigger cannot settle the same promises twice.
- **The transactional outbox is required** (revised 2026-07-18): steps 1–3
  commit money movement in the DB, and the publishes in step 4 must survive a
  crash between commit and publish. Write the outbound messages into an
  outbox table in the *same* DB transaction as steps 1–3
  (`OBP_API_CHANGES.md` §9), with a relay that publishes and records the
  §4.2 replies. Publish-after-commit without the outbox loses
  credit notifications and settlement instructions on a crash — with real
  money that is not acceptable. The Bank Node side is idempotent-friendly
  (`idempotency_key` in metadata, evidence upserts), so redelivery is safe.
- The **scheduled netting engine** (cycle-based settling) remains future work
  by policy choice — settle-on-demand is a legitimate production mode, not a
  shortcut.

### 5.4 DTOs + MessageDocs

- Add `OutBoundCreditNotification` / `OutBoundSettlementInstruction` (and the
  `InBound…` reply shapes matching §4.2) under
  `obp-commons/src/main/scala/com/openbankproject/commons/dto/`, mirroring the
  existing `OutBoundXxx` / `InBoundXxx` naming.
- Add a `messageDocs +=` entry per new message in
  `RabbitMQConnector_vOct2024.scala` (format fixed by existing entries;
  `OBP_API_CHANGES.md` §10). **These lock the wire format** — match §4 exactly.

## 6. Configuration

- `open_corridor_enabled = false` by default (gates the new endpoints).
- Each onboarded bank's broker coords (`host/port/vhost/username/password`) — prop or a
  small table during onboarding.
- The bank's Cardano settlement (creditor) address, so the
  `settlement_instruction` can be addressed (or carry it on the counterparty).

## 7. Testing (robustness rule)

- Unit: the OC status/state-machine transitions; the report-back persistence.
- Connector: publish/await against an in-memory or embedded broker
  (`EmbeddedRabbitMQ.scala` exists under test) — assert the published body
  matches §4 byte-for-field and the reply envelope is parsed.
- Integration: report-back → admin settle → assert both messages published with
  the correct `messageId` and that the evidence triplet is relayed unchanged.
- Per `CLAUDE.md`: comprehensive, not happy-path-only.

## 8. Open decisions for the implementer

1. ~~**Promise storage**~~ — **decided 2026-07-18:** Transaction Request
   attributes on the `PENDING` TR (see §5.1). No new columns, no
   `MappedTransaction.status` reuse.
2. ~~**Multi-tenant broker**~~ — **decided 2026-07-18:** per-bank connection
   registry + connection handling keyed by bank_id (see §5.2). A single-bank
   connection cannot even serve one corridor (two vhosts are involved).
3. ~~**Trigger**~~ — **decided 2026-07-18:** the admin settle-pair endpoint
   (§5.3); the scheduled netting actor is productionization.
4. ~~**Challenge step / where hold-at-`PENDING` lands**~~ — **decided
   2026-07-18.** Mechanics first: `createTransactionRequestv400` is
   threshold-gated per type
   (`transactionRequests_challenge_threshold_OPEN_CORRIDOR`), so there are
   **two** auto-complete landing sites — below threshold the TR posts
   immediately in the create path (`getStatus` → `COMPLETED`,
   `LocalMappedConnector.scala:4660`), at/above it the TR is `INITIATED` +
   challenge and posts in the answer-challenge flow. **Decision:** route
   `OPEN_CORRIDOR` to `PENDING` in **both** branches (never post at create or
   at challenge-answer). Initially set the threshold effectively infinite,
   so no challenge fires — corridor traffic is M2M (OAuth2
   client-credentials + pinned cert; the customer's SCA already happened at
   the originating bank's own channel, and the same M2M credential answering
   its own challenge adds nothing). The threshold seam is deliberately kept:
   lowering it later turns the challenge into an operational **four-eyes
   control** — a bank-ops principal (not the creating client) answers the
   challenge for high-value corridor payments. That is productionization,
   with real limit controls (per-payment cap, daily corridor cap,
   pre-funding/credit-line guard at promise time) the higher priority.
5. **Creditor Cardano address source** (for
   `settlement_instruction.creditor_address`): a prop / onboarding table per
   §6, vs. an attribute on the creditor bank's settlement account. Either
   works; pick one and note it.
6. ~~**TR type naming**~~ — **decided 2026-07-18 (second pass):**
   `OPEN_CORRIDOR_PROMISE` (renamed from `OPEN_CORRIDOR`) +
   `OPEN_CORRIDOR_SETTLEMENT`; scoped names, not bare `PROMISE`/`SETTLEMENT`
   (see §5.0 for rationale and rename inventory,
   `OPEN_CORRIDOR_SIMPLE_NETTING.md` §0 for the scheme definition).
7. ~~**Settlement posting shape**~~ — **decided 2026-07-18 (second pass):**
   via an internal `OPEN_CORRIDOR_SETTLEMENT` TransactionRequest ("TR B"),
   not a direct connector-level posting (§5.3 step 2). Keeps the TR-first
   convention and gives the settle event an audit object.
8. ~~**Discharge linkage**~~ — **decided 2026-07-18 (second pass):** a
   `settled_by_transaction_ids` Transaction Request attribute on each covered
   promise TR; `transaction_ids` keeps its causal meaning everywhere (empty
   on promises, populated on TR B). See §5.3 step 3 and
   `OPEN_CORRIDOR_SIMPLE_NETTING.md` §4a.
9. **Net-equals-zero settle** — when the pair's flows offset exactly, there
   is no amount to post. Decide: complete the covered promises with no
   Transaction (does TR B still get minted, completing with empty
   `transaction_ids`?), or post a zero-amount marker Transaction to preserve
   the linkage. Also decide what `obp_settlement_instruction` looks like in
   this case (probably: none is sent — nothing moves — but the
   `obp_credit_notification`s must still go out). Undecided.

## 9. References

- **Wire contract source of truth:**
  `OBP-Bank-Node/crates/obp-bank-node/src/interface_c/{types.rs,router.rs}`
  — now also proven by the transport integration test
  (`interface_c/transport_tests.rs`, runs against a real broker).
- **The netting model this plan now follows:**
  `OPEN_CORRIDOR_SIMPLE_NETTING.md` (this repo; a copy lives in
  `OBP-Bank-Node/WIP/` — re-copy it there after the 2026-07-18 naming/linkage
  revisions).
- **Full double-entry netting design (productionization):**
  `OBP-Bank-Node/WIP/OBP_API_CHANGES.md`, `OBP-Bank-Node/DOCS/LEDGER_DESIGN.md`
  (Bank Node repo docs were re-foldered 2026-07-17 into `WIP/` and `DOCS/`).
- **Why the salt must reach Bank B (the legal/evidence model):**
  `OBP-Bank-Node/DOCS/how_hashes_would_be_used_by_lawyers_for_bank_b.md`
- **OBP-API conventions:** `CLAUDE.md` (http4s ResourceDoc/MessageDoc, connector
  pattern), `OBP_API_CHANGES.md` §10 (MessageDocs).
- **Existing OC code:** `Http4s700.scala:3183`,
  `bankconnectors/opencorridor/OpenCorridorProcessor.scala`,
  `bankconnectors/rabbitmq/RabbitMQUtils.scala:90`.
