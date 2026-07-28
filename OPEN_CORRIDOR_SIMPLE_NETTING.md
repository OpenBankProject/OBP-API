# Open Corridor — Simplest Bilateral Netting (on-demand, on the existing OBP transaction model)

**Decision:** the simplest implementation that is *genuinely netting* — **bilateral,
settle-on-demand** — built on OBP's existing Transaction Request / Transaction model.
No snapshot table, no scheduler, no multilateral, no settlement policy entity, no FX.

This is a design note, not a wire contract. The locked RabbitMQ contract lives in
`OPEN_CORRIDOR_INTERFACE_C_PUBLISH_PLAN.md`; the full ledger design lives in the
Bank Node repo's `LEDGER_DESIGN.md`. This doc is the deliberately-minimal slice.

> **Revised 2026-07-18 (naming + settlement linkage).** Three decisions taken
> after this note was first written, folded in below:
> 1. **TR types renamed**: the promise TR type is `OPEN_CORRIDOR_PROMISE`
>    (was `OPEN_CORRIDOR`); the settlement leg gets its own new type
>    `OPEN_CORRIDOR_SETTLEMENT` (see §0 and §4a).
> 2. **The settlement is itself a TransactionRequest** ("TR B"): the settle
>    step mints one `OPEN_CORRIDOR_SETTLEMENT` TR whose execution posts the
>    single net Transaction — keeping OBP's TR-first convention (§4a).
> 3. **Discharge linkage gets its own field**: the net Transaction's id is
>    recorded on each covered promise TR as `settled_by_transaction_ids`
>    (a Transaction Request attribute), NOT written into `transaction_ids`.
>    `transaction_ids` keeps its original causal meaning ("Transactions this
>    TR produced") and therefore stays empty on promise TRs forever (§4a).

---

## 0. What "Open Corridor" is (definition, 2026-07-18)

The `OPEN_CORRIDOR_` prefix on the TR types is earned by the scheme having
real content — `PROMISE`/`SETTLEMENT` name *mechanisms*; **Open Corridor names
the scheme whose rules those mechanisms obey** (the way `SEPA` binds a TR to
that scheme's rules):

**Open Corridor** is a scheme for direct bank-to-bank cross-border payment
corridors, in which:

1. **Payments carry originator identity (Travel Rule).** Every payment
   carries FATF Recommendation 16 originator information (name, address,
   account routing) end-to-end, persisted with the payment record —
   compliance is structural, not bolted on.
2. **Payment and settlement are separated: promise now, settle later.** A
   payment is first recorded as a *promise* — a commitment by the
   originating bank to pay — which accumulates rather than moving money
   immediately.
3. **Settlement is bilateral and netted.** Outstanding promises between a
   pair of banks are offset — `SUM(A→B) − SUM(B→A)` — and the *difference*
   is settled in a single transfer. N promises collapse into one settlement.
4. **Promises are independently verifiable.** Each promise is anchored by a
   salt-based commit–reveal hash written to a public blockchain by the
   originating bank. The beneficiary bank receives the salt and preimage,
   recomputes the hash, and verifies the commitment on-chain — proof that
   does not depend on the originating bank's later cooperation.
5. **Banks keep their keys.** Each participating bank runs its own Bank
   Node, which holds the bank's keys and performs all blockchain writes
   (promise commitments, settlement transfers). OBP-API records, nets, and
   messages — it never signs or moves funds on a bank's behalf.
6. **Settlement rides a public rail.** The net obligation is settled on a
   public blockchain (currently Cardano), not through correspondent-banking
   intermediaries.

**"Open"** (confirmed 2026-07-18): any pair of banks can form a corridor
directly, over open-source software and a public settlement rail, without
correspondent intermediaries or a proprietary network operator.

When the netting build lands, this definition becomes a Glossary entry
(`Glossary.scala`), referenced from both TR-type endpoint descriptions.

---

## 1. What netting is (and isn't)

Netting is the **offsetting**. Its irreducible core is three things, none droppable:

1. **Accumulate** — promises pile up instead of settling immediately.
2. **Sum** — add up what each side owes the other.
3. **Offset** — subtract the two directions, settle the difference.

"Net of one payment" is just the payment — that's deferred settlement, not netting.
The feature is the `SUM ... GROUP BY` and the subtraction in the settle step.

**Worked example (KES):**
```
A → B  1000
A → B  2000
B → A   500
─────────────
net = (1000 + 2000) − 500 = 2500   →   A owes B 2500 KES
```
Three promises (3500 KES gross movement) collapse into **one** 2500 KES settlement.
That compression is the entire point.

---

## 2. Scope decisions ("the fancy table")

| # | Fancy thing | Decision |
|---|---|---|
| 1 | Snapshot batches as a DB entity | **Drop** — "all PENDING promises for this pair right now" is the implicit batch |
| 2 | Netting engine / scheduler | **Drop** — manual admin trigger, not time/volume cycles |
| 3 | Multilateral netting | **Drop** — bilateral only (net two banks at a time); scales by repetition, plugs in later if ever needed |
| 4 | Settlement policy entity | **Drop** — hardcode bilateral + manual |
| 5 | Net-positions endpoint | **Drop** — convenience only |
| 6 | FX conversion | **Drop** — settle in same currency/asset |

**Kept (the irreducible core):** accumulate promises, plus one admin "settle this pair"
endpoint that does `SUM(A→B) − SUM(B→A)`, settles the difference, and marks those
promises settled.

### Notes on the dropped rows

- **#1 Snapshot.** A snapshot is a DB row drawing a hard line around a group of
  promises being netted together (`snapshot_id`, status `OPEN→CLOSED→SETTLING→SETTLED`,
  every promise stamped with its `snapshot_id`). You need it when netting runs on a
  **cycle** — the snapshot freezes "which promises arrived in this window." Settling
  **on demand** instead, the batch boundary is just *whatever is PENDING for this pair
  at the moment the admin clicks settle* — defined implicitly by the query. The
  trade-off accepted: you lose the audit object "batch #47 settled these 12 promises as
  one event." For a PoC, the per-promise `PENDING → COMPLETED` transition is enough trail.

- **#3 Multilateral.** Bilateral nets each *pair* independently; multilateral nets each
  bank against the *whole group* through a central pool. Multilateral is more
  compressive (a 3-bank cycle A→B→C→A can net to zero) but needs a central counterparty,
  synchronized all-banks-at-once cycles (which drags #1 and #2 back in), and harder
  failure handling. **Bilateral is not a dead end at scale:** with N banks you do the
  same dead-simple pair calculation more times (up to N×(N−1)/2 trading pairs), each one
  independent. It scales by *repetition of a simple thing*. Multilateral plugs in later
  by changing only the settle step (sum-by-pair → sum-by-bank-against-pool) without
  touching how promises are recorded.

---

## 3. Yes — this fits the existing OBP transaction model

OBP already distinguishes the two halves of netting:

- **`TransactionRequest`** = the *intent / instruction* to pay. Has a `status`
  lifecycle; does **not** move balance until it completes. → this is the **promise**.
- **`Transaction`** = a *posted* double-entry ledger record that moves balance.
  → this is the **settlement**.

That is exactly the promise/settlement split, expressed in primitives OBP already has.
We do **not** need the Bank Node `LEDGER_DESIGN.md` approach of modelling the promise as
a non-posting `Transaction` with `transaction_kind = OPEN_CORRIDOR_PROMISE` (which needs
a new column and special non-posting transactions). Using a Transaction Request for the
promise is more natural to OBP and less invasive.

### Mapping (verified against the model)

| Netting concept | OBP field |
|---|---|
| Promise (accumulating IOU) | an `OPEN_CORRIDOR_PROMISE` **TransactionRequest** (type renamed 2026-07-18; was `OPEN_CORRIDOR`) — already created by `createTransactionRequestOpenCorridor` |
| "still owed, not settled" | `TransactionRequest.status` held at **`PENDING`** (`Enumerations.scala:333`) |
| who owes whom | `from` (originating bank/account) + `body`'s `to` counterparty (resolves to payee bank) |
| amount / currency | `body.value` |
| "settled by settlement X" | write the net `Transaction`'s id into the promise TR's **`settled_by_transaction_ids`** attribute; set status **`COMPLETED`** (revised 2026-07-18 — `transaction_ids` is NOT used for this; see §4a) |

So the `PROMISED → SETTLED` status is **not a new field** — it's the existing TR status
(`PENDING → COMPLETED`). No schema change on the promise side.

`TransactionRequest` already carries `status: String`, `transaction_ids: String`,
`from: TransactionRequestAccount`, `body`, and `originator` (see
`obp-commons/.../model/CommonModel.scala`).
`TransactionRequestStatus` enum values: `INITIATED, PENDING, NEXT_CHALLENGE_PENDING,
FAILED, COMPLETED, FORWARDED, REJECTED, CANCELLED, CANCELLATION_PENDING`.

---

## 4. The flow in OBP terms

```
1. createTransactionRequestOpenCorridor  → creates the promise TR (type
                                            OPEN_CORRIDOR_PROMISE), leave it at
                                            PENDING (do NOT auto-complete into
                                            a Transaction)

2. promises accumulate as PENDING OPEN_CORRIDOR_PROMISE TransactionRequests

3. admin "settle pair (A, B), currency C":
     net = SUM(value of PENDING A→B TRs) − SUM(value of PENDING B→A TRs)
     debtor   = A if net > 0 else B ; creditor = the other
     mint ONE OPEN_CORRIDOR_SETTLEMENT TransactionRequest ("TR B") between
        A's and B's settlement accounts; its execution posts ONE Transaction
        for abs(net)
     write that Transaction's id into settled_by_transaction_ids of every
        covered promise TR
     set every covered promise TR status = COMPLETED
```

N pending promise TRs collapse into **one** posted Transaction (via one
settlement TR). That N→1 *is* the netting, and it fits cleanly because OBP
never required 1 TR = 1 Transaction.

### 4a. The settlement TR and the two linkage fields (decided 2026-07-18)

**The settlement is itself a TransactionRequest.** The settle step does not
post a Transaction directly at connector level; it mints one internal
`OPEN_CORRIDOR_SETTLEMENT` TR ("TR B") between the pair's settlement accounts,
and TR B's execution posts the net Transaction. Rationale: OBP's convention is
TR-first — a posted Transaction always has an originating TR. TR B is also the
natural audit object for the settle event, which quietly recovers most of the
"batch settled as one event" trail dropped with the snapshot table (§2 row 1).

**Two linkage fields, two meanings.** `transaction_ids` keeps its original,
causal meaning everywhere: "the Transactions this TR *produced*". A promise TR
never produces a Transaction, so its `transaction_ids` stays empty forever;
TR B's `transaction_ids` carries the net Transaction, causally, as normal.
The *discharge* relationship — "the Transaction that *settled* me" — is
recorded on each covered promise TR in a separate field:
**`settled_by_transaction_ids`** (plural to mirror `transaction_ids`'
comma-separated shape and leave headroom for partial/multi-leg settlement).
Stored as a **Transaction Request attribute** (the same mechanism the promise
evidence uses, publish plan §5.1), so the promise side remains
schema-change-free; promote it to a real column only if it later needs
first-class querying.

Note the discharge link is *not* representational: a B→A 500 promise can be
discharged by a 2500 A→B net Transaction — opposite direction, different
amount, between settlement accounts rather than the promise's own accounts.
Reconciliation logic must never assume the linked Transaction matches the
promise TR's body; document this loudly on the endpoints/Glossary when it
lands.

**TR type names.** `OPEN_CORRIDOR_PROMISE` (renamed from `OPEN_CORRIDOR`) and
the new `OPEN_CORRIDOR_SETTLEMENT`. `PROMISE`/`SETTLEMENT` name mechanisms;
the `OPEN_CORRIDOR_` prefix binds them to the scheme's rules (§0) — the
promise must carry the originator block and commit–reveal evidence; the
settlement is the netted discharge over the corridor rail. Bare names were
rejected: a TR type is product-scoped (it selects the body shape and keys
per-type props like the challenge threshold), and bare `SETTLEMENT` collides
with the unrelated v7 market-trading settlement endpoints. The scoped names
also converge with the Bank Node's `LEDGER_DESIGN.md` vocabulary. Rename
inventory: publish plan §5.0.

---

## 5. The one behaviour change required

Today an `OPEN_CORRIDOR_PROMISE` request (pre-rename: `OPEN_CORRIDOR`) is "SIMPLE + a mandatory originator block," and SIMPLE
**completes immediately** — it posts a Transaction and moves money per request. For
netting it must instead **stop at `PENDING`** and post nothing until the settle step.

**Where that change lands (verified against the code; decided 2026-07-18):**
`createTransactionRequestv400` is threshold-gated per type
(`transactionRequests_challenge_threshold_OPEN_CORRIDOR`), so auto-complete
has **two** landing sites: below the threshold the TR posts immediately in
the create path (`getStatus` → `COMPLETED`); at/above it the TR is
`INITIATED` + challenge and posts in the answer-challenge flow. The netting
change routes `OPEN_CORRIDOR_PROMISE` to `PENDING` in **both** branches. For the PoC
the threshold is set effectively infinite (no challenge fires — the corridor
hop is M2M and the customer's SCA already happened at the originating bank);
the seam is kept so a finite threshold can later make the challenge an
ops-desk four-eyes control for high-value payments. Details:
`OPEN_CORRIDOR_INTERFACE_C_PUBLISH_PLAN.md` §8.4.

---

## 6. Build checklist

**Already exists:**
- the `TransactionRequest` model, `PENDING`/`COMPLETED` statuses, `transaction_ids` linkage
- the `createTransactionRequestOpenCorridor` endpoint (`Http4s700.scala:3183`)
- settlement accounts (`OBP-INCOMING-SETTLEMENT-ACCOUNT` /
  `OBP-OUTGOING-SETTLEMENT-ACCOUNT`, per `Glossary.scala`)
- the posted-Transaction machinery

**New work (the whole build — ordering per
`OPEN_CORRIDOR_INTERFACE_C_PUBLISH_PLAN.md` §5, which this note now governs):**
0. Rename the TR type `OPEN_CORRIDOR` → `OPEN_CORRIDOR_PROMISE` and add the
   `OPEN_CORRIDOR_SETTLEMENT` type (rename inventory: publish plan §5.0).
1. Promise report-back endpoint + TR-attribute storage (publish plan §5.1);
   the promise state lives on the `PENDING` TR, never on a Transaction.
2. Hold the `OPEN_CORRIDOR_PROMISE` TR at `PENDING` (stop the auto-complete)
   — in both landing sites: the below-threshold create path and the
   answer-challenge flow (see §5 above).
3. Publish-and-await-reply to the bank's vhost (publish plan §5.2).
4. One admin "settle pair" endpoint (publish plan §5.3): query the PENDING
   pair, compute `SUM(A→B) − SUM(B→A)`, mint the `OPEN_CORRIDOR_SETTLEMENT`
   TR ("TR B") whose execution posts the single net `Transaction` against
   the settlement accounts, record that Transaction's id in each covered
   promise TR's `settled_by_transaction_ids` attribute, mark them
   `COMPLETED` — steps in one DB transaction, with a stable settlement id as
   the idempotency key — then publish the RabbitMQ messages (credit
   notifications to beneficiaries, the net settlement instruction to the
   debtor).

---

## 7. The seam for later

If bilateral is ever outgrown, multilateral plugs into the settle step only
(sum-by-pair → sum-by-bank-against-pool). If a regulator needs the batch-as-one-event
audit object, add the snapshot table (#1) back and stamp each TR. Neither requires
reworking how promises are recorded — the PENDING TransactionRequest is the stable seam.
