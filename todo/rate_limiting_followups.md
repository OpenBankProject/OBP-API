# TODO — rate limiting follow-ups

From `docs/API_PRODUCT_SUBSCRIPTION_PLAN.md` (2026-09-02). Re-checked against the tree 2026-09-14 —
all three still open: `RateLimitingUtil.scala` has no `apiName` / `apiVersion` / `bankId` matching,
and there is no expiry job anywhere.

Value semantics are settled and not in question: `0` blocks, `-1` is unlimited, no record falls back
to the `rate_limiting_per_*` props, and overlapping records sum by design.

- [ ] **Per-endpoint limits keyed by `operationId`** (not URL). A `RateLimiting` record's `apiName`
  and `apiVersion` are stored and reported but never matched; enforcement is per consumer across all
  endpoints. Needed before an API Product's collection can be rate-limited on its own.

- [ ] **Per-bank matching** using the record's `bankId`. Same situation — stored, reported, not
  matched — with a sharper consequence: a record created for a product at bank A silently changes
  that consumer's limits at bank B.

- [ ] **Subscription expiry job.** When an API Product Subscription's `end_date` passes, its
  rate-limit record stops counting for free (same `toDate`), but its derived Scopes stay until
  something sets the status to `cancelled`. A scheduled job should do that.
