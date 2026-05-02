# Draft v2: `CAPTURE` and `RELEASE` Transaction Request Types

**Status**: design draft, not yet implemented. Section 6 is the open question that needs to be resolved before code lands.

## Background

OBP already has a `HOLD` transaction request type (`POST /banks/{BANK_ID}/accounts/{ACCOUNT_ID}/owner/transaction-request-types/HOLD/transaction-requests`, see `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala:180`). HOLD moves funds from a parent account into an auto-created `HOLDING`-type sub-account, linked back via the `RELEASER_ACCOUNT_ID` account attribute.

What's missing:

- A `CAPTURE` step that commits held funds to a counterparty (turns the reservation into a real transfer to the recipient).
- A `RELEASE` step that returns held funds to the parent account (cancels the reservation).

Both are needed to support trading-style settlement sagas (offer/order → match → capture vs cancel → release), where funds must be reserved before a counterparty is known.

This document drafts those two new types.

---

## Changes since v1

- Path uses generic `{ACCOUNT_ID}` (not a HOLDING-specific path segment) — same as every other transaction-request type.
- Destination uses ACCOUNT shape (`{bank_id, account_id}`), no counterparty resolution.
- `RELEASE` destination comes from the referenced HOLD's `from_account_id`, not from the holding account's `RELEASER_ACCOUNT_ID` attribute.
- The `hold_transaction_request_id` field is **flagged as still under discussion** — see §6.

---

## 1. Endpoints

```
POST /obp/v6.0.0/banks/{BANK_ID}/accounts/{ACCOUNT_ID}/owner/transaction-request-types/CAPTURE/transaction-requests
POST /obp/v6.0.0/banks/{BANK_ID}/accounts/{ACCOUNT_ID}/owner/transaction-request-types/RELEASE/transaction-requests
```

`{ACCOUNT_ID}` is the source of the transfer — i.e., the HOLDING sub-account that received the funds when the HOLD ran. The path doesn't enforce "type = HOLDING" explicitly; the integrity check falls out of the body validation (§6).

## 2. `CAPTURE` body — `TransactionRequestBodyCaptureJsonV600`

```json
{
  "hold_transaction_request_id": "abc-123-...",
  "to": {
    "bank_id": "gh.29.uk",
    "account_id": "seller-fiat-account"
  },
  "value": { "currency": "EUR", "amount": "250.00" },
  "description": "Settlement of trade trade-789"
}
```

| Field | Required | Purpose |
|---|---|---|
| `hold_transaction_request_id` | TBD (§6) | Linkage to originating HOLD |
| `to.bank_id` / `to.account_id` | yes | In-bank ACCOUNT-style destination |
| `value.currency` / `value.amount` | yes | Amount and currency to capture |
| `description` | no | Free-form note |

## 3. `RELEASE` body — `TransactionRequestBodyReleaseJsonV600`

```json
{
  "hold_transaction_request_id": "abc-123-...",
  "value": { "currency": "EUR", "amount": "250.00" },
  "description": "Offer offer-456 cancelled by user"
}
```

No `to` field. Two options for resolving the destination, depending on §6:

- **If we keep `hold_transaction_request_id`**: destination = the referenced HOLD's `from_account_id`.
- **If we drop it**: destination = the source account's `RELEASER_ACCOUNT_ID` attribute (only viable resolution path without the linkage).

If `value.amount` is omitted, the server releases the full remaining balance (definition of "remaining" depends on §6).

## 4. Response

`transactionRequestWithChargeJSON400` — same as every other transaction-request type. If we keep §6, attach two attributes to the resulting transaction request:

- `hold_transaction_request_id = abc-123-...`
- `hold_purpose = capture` *or* `hold_purpose = release`

## 5. Validation

Common to both types:

1. `{ACCOUNT_ID}` exists and the caller has the required view permission (same as every other transaction-request).
2. Standard body validation (positive amount, valid currency, etc.).
3. `value.amount` must not exceed the source account's available balance. (Already enforced by the underlying transfer machinery.)

§6-dependent (only if we keep `hold_transaction_request_id`):

4. The HOLD referenced exists, was a HOLD, has status `COMPLETED`.
5. The HOLD's destination = `{ACCOUNT_ID}` in the URL. (Otherwise `HoldDoesNotMatchAccount`.)
6. `value.currency` matches the HOLD's currency.
7. Sum of completed `CAPTURE` + `RELEASE` against this HOLD plus `value.amount` ≤ HOLD amount. (`CaptureExceedsHoldRemaining` / `ReleaseExceedsHoldRemaining`.)

## 6. Open question: do we need `hold_transaction_request_id`?

This is the question that's still under discussion. Two designs:

### Design A — keep the linkage

CAPTURE/RELEASE bodies carry `hold_transaction_request_id`. The resulting transaction stores it as an attribute. Per-HOLD "remaining balance" is computed on demand:

```
remaining(hold_id) = hold.amount
                   − Σ amount of CAPTURE txns linked to hold_id, status=COMPLETED
                   − Σ amount of RELEASE txns linked to hold_id, status=COMPLETED
```

**What this gives us**

- Server-enforced invariant: a HOLD cannot be over-captured or over-released; partial fills compose cleanly.
- The optional `GET .../transaction-requests/{HOLD_ID}/balance` helper makes sense.
- Audit/regulatory readers see "this transfer was the capture of HOLD X" without having to reason from context.
- Distinguishes CAPTURE/RELEASE from "ordinary transfer that happens to leave a HOLDING account" at the data-model level.

**What it costs**

- Each operation does one extra lookup (HOLD row + sum-of-related-attributes).
- Concurrent CAPTUREs against the same HOLD need transactional balance-check (already true for any debit transfer, but now the check is per-HOLD too).
- Schema-level additions: just two new transaction-request attributes (`hold_transaction_request_id`, `hold_purpose`) — no new table.

### Design B — drop the linkage

CAPTURE/RELEASE are typed by intent only. The HOLDING account is a fungible bucket; capture/release just transfer in or out of it. No per-HOLD tracking. RELEASE's destination has to come from the HOLDING account's `RELEASER_ACCOUNT_ID` attribute (so we'd need that attribute to be load-bearing again).

**What this gives us**

- Simpler API. Bodies match existing ACCOUNT-type transaction requests one-for-one.
- One source of truth for held funds: the HOLDING account's balance.
- Trading orchestrator (or any consumer) tracks per-HOLD bookkeeping itself if it cares.

**What it costs**

- The system can't tell you "how much of HOLD X is still held" — only "how much is in the HOLDING account in total." Multiple HOLDs against the same parent become indistinguishable post-hoc.
- The CAPTURE/RELEASE types reduce to "labelled transfers from a HOLDING account": almost no semantic gain over the existing ACCOUNT type beyond the label itself.
- `RELEASER_ACCOUNT_ID` attribute on the holding account becomes mandatory and load-bearing for RELEASE to work.

### Recommendation

**Design A** — keep `hold_transaction_request_id`. Without it, the new types add little over plain ACCOUNT transfers, and the per-HOLD invariant ("captured + released ≤ original HOLD amount") is exactly the kind of guarantee that belongs in the API, not in every consumer. Cost is two attributes and a sum-aggregation query — well-bounded.

**But** — if the view is that the trading orchestrator (or any consumer) is the rightful owner of per-HOLD bookkeeping and OBP-API should stay primitive, Design B is internally consistent and a smaller commitment.

## 7. Other open questions

1. **API version**: land in v6.0.0 (alongside HOLD) or v7.0.0 (gets idempotency middleware + new patterns automatically)? Mild preference for v7.0.0.
2. **Entitlements**: `canCaptureHoldAtOneBank` / `canReleaseHoldAtOneBank` (plus AnyBank variants), or piggy-back on existing transaction-request entitlements? Probably new ones for clarity.
3. **HOLD expiry**: should an unconsumed HOLD auto-release after a TTL, or stay parked until the orchestrator releases it? (Ties into §6: auto-release only really makes sense in Design A where "remaining" is a first-class concept.)
4. **`GET .../balance` helper**: pure read endpoint exposing the per-HOLD remaining computation. Only meaningful in Design A.
5. **Naming**: `hold_transaction_request_id` vs `original_transaction_request_id` — keep specific or go generic for future use?

---

## 8. How the trading orchestrator uses these

Per-trade settlement saga (one trade, two HOLDs — buyer's fiat HOLD, seller's token HOLD):

```
1. CAPTURE buyer's fiat HOLD,  to=seller's fiat account,  amount=trade.value
2. CAPTURE seller's token HOLD, to=buyer's token account, amount=trade.qty
   On any failure: release the unconsumed remainder, mark trade FAILED.
```

Cancel an unfilled offer:

```
1. RELEASE the HOLD (no amount → full remaining balance).
```

Partial fill:

```
1. CAPTURE the matched portion.
2. (Optional) RELEASE the unmatched portion if the offer is being closed.
```

Each step is an ordinary transaction-request-create call; idempotent via the `Idempotency-Key` header in the v7 idempotency middleware; auditable via the attached `hold_transaction_request_id` + `hold_purpose` attributes (Design A) or the source-account balance and type-on-the-record (Design B).
