# Signing baskets: operating notes

For whoever runs an OBP-API instance that offers the Berlin Group signing basket. The behaviour of the API
itself is in the ResourceDocs; this covers what an operator does and sees.

## Turning authorisation on

`signing_basket_authorisation_enabled` is `false` by default. While it is, creating, reading, deleting a
basket and starting an authorisation work, and answering the authorisation (the PUT) answers 403
`SERVICE_BLOCKED`. Roll it out in stages: first the ownership guard (this change with the property off),
then the property, with the recovery rehearsal below in between.

Related properties:

| Property | Default | Meaning |
|---|---|---|
| `signing_basket_member_max_attempts` | 3 | Times a payment that failed to book is claimed again. Mapped connector only. |
| `signing_basket_resume_interval_in_seconds` | 593 | How often stopped executions are resumed. 0 switches it off. |
| `signing_basket_execution_lease_in_seconds` | 300 | How long a basket or member may sit without moving before it counts as stopped. |

## What the stored status means

A basket reports `RCVD`, `PATC`, `ACTC`, `CANC` or `RJCT`. Two more are stored and reported as `RCVD`:

* `AUTHORISING`: the authorisation was answered correctly and the basket was claimed; its members are being
  executed.
* `EXECUTION_INCOMPLETE`: execution stopped with a member that is not `DONE`.

`ACTC` means every member is `DONE`. Each member has its own state in `SigningBasketMemberExecution`, and the
creating TPP reads it from `GET /signing-baskets/{basketId}/execution`.

| Member state | Meaning |
|---|---|
| `PENDING` | Not started. |
| `EXECUTING` | Claimed by an executor. Past the lease it becomes `UNKNOWN`. |
| `DONE` | Payment booked, or consent activated. |
| `FAILED` | Refused before it took effect. Claimed again automatically, on the mapped connector, up to the attempts allowed. |
| `UNKNOWN` | The executor stopped without recording an outcome, or a connector other than the mapped one failed after it may have booked. |

Several payments in one basket are not one transaction. A failure leaves the earlier payments booked.

## Looking at baskets that did not complete

```sql
-- Baskets that did not reach ACTC after their authorisation was answered
SELECT basketid, status, consumerid, psuuserid, updatedat
FROM signingbasket
WHERE status IN ('AUTHORISING', 'EXECUTION_INCOMPLETE')
ORDER BY updatedat;

-- What happened to each member of one basket
SELECT membertype, memberid, position, state, detail, attempts, updatedat
FROM SigningBasketMemberExecution
WHERE basketid = '<basket id>'
ORDER BY position;
```

## Members left UNKNOWN

The resumption reconciles an `UNKNOWN` payment by its transaction id: if the payment carries one it was
booked, and the member becomes `DONE`. Without one, nothing proves whether it was booked, so it is left for you.

1. Find the payment's debit in the ledger (the debtor account, the amount, the time of the execution).
2. If it was booked, set the payment's transaction id and mark the member `DONE`; the next resumption completes
   the basket. If it was not, mark the member `FAILED`; it is then claimed again, up to the attempts allowed.

On a connector other than the mapped one, a failure is always recorded as `UNKNOWN`, because that connector
may have booked before it failed, and automatic retry is off.

## Baskets created before ownership was recorded

Baskets created before this change have no creating TPP and no PSU. They cannot be attributed safely, so every
operation answers them as unknown (403 `RESOURCE_UNKNOWN`), they are never authorised, and their rows are kept.
Nothing assigns one to whoever asks first, and no property re-opens them.

To find them:

```sql
SELECT basketid, status, createdat
FROM signingbasket
WHERE consumerid IS NULL OR consumerid = '';
```

If a particular one has to be revived, assign it explicitly, after establishing who created it:

```sql
UPDATE signingbasket
SET consumerid = '<consumer id of the TPP that created it>'
WHERE basketid = '<basket id>' AND (consumerid IS NULL OR consumerid = '');
```

Its payments and consents are not held by any claim. If the basket is to be used, add the claims:

```sql
INSERT INTO SigningBasketMemberClaim (memberkey, basketid, createdat, updatedat)
VALUES ('payment:<payment id>', '<basket id>', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

(one row per payment, `consent:<consent id>` for consents). Without them another basket could take the same
payment. A basket that is only to be closed needs none of this; set its status to `CANC`.

## Things that can still surprise

* A payment waiting in a basket is still a payment waiting for SCA: if
  `berlin_group_outdated_transactions_interval_in_seconds` is set, the outdated-payment task rejects it after
  `berlin_group_outdated_transactions_time_in_seconds`, and the basket's member then fails as not waiting for
  authorisation.
* A consent in a basket is `received` until activated, and the consent scheduler rejects a Berlin Group consent
  that stays `received` for `berlin_group_outdated_consents_time_in_seconds`.
* The recurrence of a periodic payment is not stored, so a periodic payment cannot be recognised and refused when
  it is put in a basket; it is treated as a single payment.
