# RFC: Per-field provenance for OBP Dynamic Entities

**Target:** OBP-API (upstream)
**Status:** Draft for discussion
**Author:** (OGCR team)
**Date:** 2026-06-05

> Companion: field-level write/read role permissions are specified separately in
> `DE_field_write_role_read_role.md`. The two are **orthogonal** and compose, but each ships
> independently. This doc covers **provenance only**.

---

## 1. Summary

A small, optional addition to Dynamic Entities (DEs): on write, the server stamps **who** and **when**
for a field, and the writer may attach an **opaque** metadata object. No blockchain vocabulary — a chain
projection is just one worked example (§5). Opt-in per field, fully backward compatible.

## 2. Motivation

DEs today have only a fixed set of server-injected audit fields (`userId`, `consentId`) at the **record**
level. There is no way to record *who set a particular field, and when* — useful for any sensitive or
externally-sourced field:

- "Who last changed this field, and when" auditing on any field.
- Externally-sourced fields (registry imports, indexer projections) that should carry their source/stamp.

Kept blockchain-agnostic so it's reusable across OBP deployments.

## 3. Current behaviour (baseline)

- Server-injected **record-level** audit fields (`userId`, `consentId`) only.
- No per-field "who/when" or per-field metadata.
- DE data is a JSON blob (`dataJson`) in a generic `DynamicData` table.

---

## 4. Schema addition (per property)

| keyword | type | meaning |
|---|---|---|
| `trackProvenance` | boolean (default `false`) | record who/when on each write of this field, plus optional writer-supplied metadata |

Entity-level convenience (optional): `trackProvenanceAllFields: true` enables provenance for every field.

`trackProvenance` is **independent of access control** — any field can opt in, restricted or not.

## 5. Provenance structure

When a `trackProvenance` field is written, the server records an entry in a reserved `_provenance` object
on the record:

```jsonc
"_provenance": {
  "<field>": {
    // server-stamped (authoritative, cannot be forged):
    "written_by_user_id": "…",
    "written_by_role":    "…",      // role that authorised the write (if any)
    "updated_at":         "2026-06-05T12:00:00Z",
    // writer-supplied (optional, opaque to OBP):
    "metadata": { /* any JSON the writer attaches */ }
  }
}
```

- `written_by_user_id`, `written_by_role`, `updated_at` are **always** set by the server.
- `metadata` is an **opaque** object OBP stores and returns verbatim — OBP attaches no meaning to it.
- Stores the **latest** write per field (full history out of scope — §9).
- `_provenance` is a reserved property name (definitions may not declare a field called that).

## 6. Interaction with field-level permissions

Provenance is a **side-effect of an accepted write**, so it inherits the field's authorisation from the
companion permissions RFC — no separate permission is needed:

- **Provenance inherits write authorisation.** A field's provenance can only be created/changed by a write
  that already passed its `writeRole` check. So a write-restricted field's provenance can only come from the
  keyholder; ordinary consumers can only generate provenance on fields they're allowed to write.
- **Restamp only on a real authorised write.** When PUT/CREATE preserves a restricted field (value not
  actually written by the caller), its provenance is **preserved**, not restamped to that caller/now. No-op
  echoes don't update `updated_at`.
- **Visibility follows `readRole`.** If a field is read-restricted, its `_provenance[field]` entry is
  **omitted** for callers lacking the read role (who/when/metadata can leak information).
- **Writer-supplied `metadata`** is accepted only for fields the caller is authorised to write; server-
  stamped keys always override client-supplied ones.

### 6.1 Writer supplies metadata via a parallel block

```http
PATCH /obp/dynamic-entity/<entity>/{id}
{
  "<field>": "<value>",
  "_provenance": { "<field>": { "metadata": { /* arbitrary */ } } }
}
```

## 7. Worked example — blockchain projection (one example, not a special case)

A `chain_owner` field with `writeRole = CanWriteChainProjection` (the indexer's shared key, per the
permissions RFC) and `trackProvenance: true`, where the writer's opaque `metadata` happens to be
`{ "source": "chain:ogcr:2025", "tx_hash": "0x…", "block_number": 1234567, "confirmations": 20, "finalized": true }`.
OBP neither knows nor cares that this is chain data — it's just a restricted, provenance-tracked field.

## 8. Backward compatibility

- Definitions without `trackProvenance` behave exactly as today.
- `_provenance` only appears when at least one field opts in.
- Only new validation on definitions: reserving the `_provenance` property name.

## 9. Out of scope / future

- **Provenance history** (append-only) — this RFC keeps latest-only.
- **Field-level permissions** — companion RFC `DE_field_write_role_read_role.md`.
- **Queryable DE GET** — see `dynamic_entity_indexing.md`.

## 10. Implementation touch points (OBP-API)

- `code/dynamicEntity/DynamicEntityProvider.scala` — recognise `trackProvenance` /
  `trackProvenanceAllFields`; reserve `_provenance`.
- `code/api/dynamic/entity/APIMethodsDynamicEntity.scala` & `Http4sDynamicEntity.scala` — stamp provenance
  on accepted writes; preserve provenance on no-op/preserved fields; omit provenance per `readRole`.
- `MapppedDynamicDataProvider.scala` — persist & return `_provenance`.
- `JSONFactory6.0.0` / `ExampleValue.scala` / resource docs — document keyword + examples.

## 11. Open questions

- Latest-only (proposed) vs append-only history.
- Should `trackProvenanceAllFields` be allowed, given storage/write cost, or per-field only?
- Sequencing: ship after the permissions RFC (recommended), since provenance leans on its auth model.
