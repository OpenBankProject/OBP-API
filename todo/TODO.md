# TODO — duplicate-create 409s

Reference for the rule, the per-version state, and the open decision:
**`docs/http_status_conventions.md`**. Only the parts with an agreed direction are listed here.

- [ ] **`Http4s700.scala:4697`** — `AccountIdAlreadyExists` in `createAccountCommon` (shared by
  `createAccountV700` POST and `createAccountWithIdV700` PUT) still returns 400. v7.0.0 is
  BLEEDING_EDGE, so the "safe to fix in place" reasoning that cleared all of v6.0.0 applies
  directly. No decision needed — this one was simply missed. Add `failCode = 409` and a
  duplicate-create scenario in `Http4s700RoutesTest`.

- [ ] **`ConsumerKeyAlreadyExists` is a dead constant.** Defined in `ErrorMessages.scala`,
  referenced by nothing in the tree. Either wire it up at the consumer-create site or delete it.
  Error-code numbers are stable once committed, so deleting frees nothing — but a constant no code
  can produce is worse than a gap, because it appears in no response and yet reads as supported.

The 29 sites on v1.4.0 – v5.1.0 are **not** listed here. Whether to change them is an open contract
decision (see the doc above), so they are inventory, not work.
