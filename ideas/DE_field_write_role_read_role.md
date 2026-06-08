# RFC: Field-level write/read role permissions for OBP Dynamic Entities

**Target:** OBP-API (upstream)
**Status:** Draft for discussion
**Author:** (OGCR team)
**Date:** 2026-06-05

> Companion: per-field provenance is specified separately in
> `DE_field_provenance.md` (provenance). The two are **orthogonal** and
> compose, but each ships independently. This doc covers **access control only**.

---

## 1. Summary

A small, generic addition to Dynamic Entities (DEs): **per-field `writeRole` / `readRole`** so individual
fields can be restricted independently of the entity-level roles. No blockchain vocabulary; useful across
OBP deployments. Opt-in per field, fully backward compatible.

## 2. Motivation

DEs today have only **entity-level** roles (`CanGet/CanCreate/CanUpdate/CanDelete<Entity>`), **row-level**
scoping (`hasPersonalEntity` + `userId`), and a fixed set of server-injected audit fields. There is no way
to make *one field* writable or readable only by a specific role.

Generic use-cases (no blockchain required):

- A `verification_status` field only a **certifier/verifier** role may set; everyone reads it.
- **Admin-only** fields (`internal_notes`) — restricted read *and* write.
- A moderation/`featured` flag only platform staff may toggle.
- Registry-mirrored fields a consumer app must display but never edit.
- A projection field written only by a privileged service (e.g. an indexer), read by everyone.

## 3. Current behaviour (baseline)

- Entity-level roles gate whole operations.
- Row-level scoping via `hasPersonalEntity` / `personalRequiresRole` + `userId`.
- Per-property schema in the definition (`metadataJson`): `type`, `required`, `minLength`, `maxLength`,
  `reference:`.
- DE data is a JSON blob (`dataJson`) in a generic `DynamicData` table; reads are get-by-id or get-all
  (field filtering/sorting/pagination is a separate concern — see §9).

---

## 4. Schema additions (per property)

All keywords are camelCase, consistent with existing schema keywords (`minLength`, `maxLength`). They are
always read in the context of a field, so the names need no "field-level" prefix.

| keyword | type | meaning |
|---|---|---|
| `writeRoleRequired` | boolean (default `false`) | field is **write-restricted**: not writable via PUT/CREATE; only via the role-gated PATCH path |
| `writeRole` | string (optional) | the role permitted to write; if omitted, auto-generate `CanWriteDynamicEntityField_<Entity>__<Field>` |
| `readRoleRequired` | boolean (default `false`) | field is **read-restricted**: omitted from GET unless the caller holds the read role |
| `readRole` | string (optional) | the role permitted to read; if omitted, auto-generate `CanGetDynamicEntityField_<Entity>__<Field>` |

**Restriction-on rule:** write restriction is on if *either* `writeRoleRequired: true` *or* an explicit
`writeRole` is named (and symmetrically for read). So you never specify both — `writeRoleRequired: true`
gives an auto role, `writeRole: "…"` gives an explicit (shareable) role and implies the restriction.

Read and write are independent — a field can be (a) write-restricted but world-readable (the common case:
verifier/indexer writes, everyone reads), (b) read-restricted but writable, or (c) both.

**Example definition:**
```json
{
  "activity_listing": {
    "required": ["title"],
    "properties": {
      "title":               { "type": "string" },
      "price_per_credit":    { "type": "string" },
      "chain_owner":         { "type": "string", "writeRoleRequired": true },
      "verification_status": { "type": "string", "writeRole": "CanSetVerificationStatus_activity_listing" },
      "internal_notes":      { "type": "string", "writeRole": "CanEditInternal_activity_listing",
                                                 "readRole":  "CanReadInternal_activity_listing" }
    }
  }
}
```

## 5. Write semantics — PUT/CREATE never write restricted fields

- **PUT / CREATE** operate on **unrestricted fields only**. Any field with `writeRoleRequired`/`writeRole`
  in the body is **ignored and preserved** (existing value kept) — no value comparison, no error. This
  removes the stale-echo problem (a consumer echoing an out-of-date restricted value can never block or
  clobber).
- **PATCH** (role-gated) is the **only path that writes restricted fields**. The caller must hold the
  field's `writeRole`, else `403`. (PATCH also writes unrestricted fields a caller is allowed to write.)
- **`required` is validated against the merged object** (request body + preserved restricted fields), so
  a consumer create/update never fails because a restricted field is "missing". Restricted fields are
  **optional-at-create** (the keyholder fills them afterward via PATCH).

## 6. Read semantics

- A field with `readRoleRequired`/`readRole` is **omitted** from GET responses for callers without the
  read role (applied consistently on GET_ONE and GET_ALL).
- Fields without read restriction behave exactly as today.

## 7. Roles & "the key"

- Restricted fields **generate** the implied roles by default:
  `CanWriteDynamicEntityField_<Entity>__<Field>` and `CanGetDynamicEntityField_<Entity>__<Field>`
  (double-underscore delimiter to disambiguate snake_case entity/field names; bank-scoped variants where
  the entity is bank-scoped). They appear in the dynamic role registry and are grantable via the existing
  entitlement endpoints.
- **Explicit shared role override:** naming an explicit `writeRole`/`readRole` lets many fields (across many
  entities) point at **one** role — essential to avoid role-explosion for a service like an indexer that
  writes lots of fields (grant it one `CanWriteChainProjection`-style role once).
- One write role per field covers both create-time and PATCH writes; the read role is **additive** on top
  of the entity-level `CanGet…` (you need both to see a read-restricted field).
- "The key" = an entitlement granted to a user. A service (verifier, importer, indexer) authenticates as a
  user holding the role; ordinary consumers don't and are read-only on those fields.

## 8. Operation-aware resource docs

OBP auto-generates DE resource docs (request/response schemas + examples) from the entity definition. Today
it derives one body shape from the full definition; this enhancement makes generation **operation-aware** so
the docs stop advertising fields a caller can't actually set on that endpoint.

| Endpoint | Write-restricted fields in **request** body | In **response** body |
|---|---|---|
| **POST (CREATE)** | **omitted** from `typed_request_body` + `example_request_body` (they'd be ignored) | included |
| **PUT (UPDATE)** | **omitted** from the request body | included |
| **PATCH** (role-gated write path) | **included** — annotated with the required `writeRole` | included |
| **GET** | n/a | included; read-restricted fields annotated "only returned if you hold `readRole`" |

Consequences:
- A developer reading **POST/PUT** docs sees only settable fields — no misleading restricted fields in the
  example body. This also reflects that restricted fields are **optional-at-create**.
- **PATCH** docs are where restricted fields appear, each annotated with the role needed to write it.
- **Responses** include restricted fields (they're readable); read-restricted ones are annotated.

Because OBP resource docs are **static, not per-caller**, role requirements are *documented* (the endpoint
already lists its required roles) rather than dynamically hidden per viewer. A read-restricted field
therefore still appears in the response *schema* with a "requires `readRole`" annotation, but is omitted from
the actual JSON at runtime for callers without the role.

Implementation: the generator (`DynamicEntityHelper.operationToResourceDoc` / `APIMethodsDynamicEntity`)
filters the request-body schema by operation — strip `writeRoleRequired` fields from CREATE/PUT, keep them in
PATCH, keep readable fields in responses with annotations.

## 9. Backward compatibility

- Definitions without the new keywords behave exactly as today.
- Restricted fields are simply omitted (read) or ignored (PUT/CREATE write) for callers without roles.
- No new reserved property names; no migration.

## 10. Security considerations

- Authorisation uses the existing entitlement check (`hasEntitlement`).
- `readRole` omission must be applied consistently on GET_ONE and GET_ALL.
- Orthogonal to personal-entity (`userId`) row scoping.
- Entitlements are user-level; a service "key" is a service user's credential.

## 11. Out of scope / related

- **Per-field provenance** (who/when stamping) — companion RFC `…-permissions.md`. Composes with this.
- **Queryable DE GET** (field filtering/sorting/pagination) — separate enhancement; see
  `dynamic_entity_indexing.md`.
- A new **PATCH** verb for DEs (the write path for restricted fields) — assumed by this RFC; if OBP has no
  DE PATCH today, adding it is part of this work.

## 12. Implementation touch points (OBP-API)

- `code/dynamicEntity/DynamicEntityProvider.scala` — recognise `writeRole` / `readRole` /
  `writeRoleRequired` / `readRoleRequired`; helper to list restricted fields; skip `required` for
  role-restricted fields at create.
- `code/api/dynamic/entity/APIMethodsDynamicEntity.scala` & `Http4sDynamicEntity.scala` —
  PUT/CREATE ignore-and-preserve of restricted fields; role-gated PATCH write path; `readRole` omission on
  GET; operation-aware resource-doc generation.
- `DynamicEntityInfo` + `code/entitlement/*` — generate/register the implied roles.
- `JSONFactory6.0.0` / `ExampleValue.scala` / resource docs — document keywords + examples.

## 13. Tests

**Conventions (match existing OBP suites):** ScalaTest feature scenarios
(`scenario("x.y: …", VersionOfApi)` with Given/When/Then); grant roles via
`Entitlement.entitlement.vend.addEntitlement("", userId, role)`; drive endpoints with
`makePostRequest` / `makeGetRequest` / `makePutRequest` (add a `makePatchRequest` helper);
multiple `resourceUser`s for privileged vs ordinary callers.
**Home:** a new `obp-api/src/test/scala/code/api/v6_0_0/DynamicEntityFieldRolesTest.scala`, plus
regression additions to the existing `DynamicEntityTest`, `DynamicEntityAccessFlagsTest`,
`DynamicEntityFilterAndBankAccessTest`.

**A. Definition & role generation**
- A.1 A definition with `writeRole`/`readRole`/`writeRoleRequired`/`readRoleRequired` parses and persists.
- A.2 `writeRoleRequired: true` (no explicit role) auto-generates `CanWriteDynamicEntityField_<Entity>__<Field>`, and it is grantable via the entitlement endpoints.
- A.3 Explicit `writeRole` is used verbatim; several fields (and entities) sharing one role all enforce it.
- A.4 Bank-scoped entity → bank-scoped field-role variant is generated and enforced.
- A.5 Restriction-on rule: boolean `true` **or** an explicit role each enables restriction; neither = unrestricted (today's behaviour).

**B. Write — POST/CREATE (never writes restricted fields)**
- B.1 Ordinary consumer POSTs with a restricted field in the body → field ignored; record created (201); restricted field unset.
- B.2 Unrestricted fields in the same POST are written normally.
- B.3 A restricted field listed in `required` → consumer create still succeeds (`required` validated post-merge; restricted = optional-at-create), not rejected as "missing".
- B.4 Even a keyholder's POST does not set restricted fields (confirms PATCH is the only write path).

**C. Write — PUT (ignore + preserve)**
- C.1 Consumer PUT omitting a restricted field → existing value preserved, not blanked.
- C.2 Consumer PUT echoing a **stale** restricted value → ignored; current value preserved; no error, no clobber (the stale-echo case).
- C.3 Consumer PUT changing only unrestricted fields → those update; restricted fields untouched.

**D. Write — PATCH (the role-gated write path)**
- D.1 Caller **with** the field's `writeRole` PATCHes a restricted field → updated (200).
- D.2 Caller **without** the role PATCHes a restricted field → 403; value unchanged.
- D.3 Allowed caller PATCHes an unrestricted field → updated.
- D.4 Shared-role: one service user holding a single shared `writeRole` PATCHes restricted fields across multiple entities.

**E. Read — GET one/all**
- E.1 Caller **without** `readRole` → read-restricted field omitted from GET_ONE **and** GET_ALL.
- E.2 Caller **with** `readRole` (and entity `CanGet`) → field present.
- E.3 Additive rule: holding the field `readRole` but **not** the entity `CanGet` → still cannot read the entity.

**F. Operation-aware resource docs**
- F.1 POST/PUT resource docs: restricted fields absent from `typed_request_body` + `example_request_body`.
- F.2 PATCH resource docs: restricted fields present, annotated with the required role.
- F.3 GET resource docs: restricted fields present in the response schema; read-restricted ones annotated.

**G. Backward compatibility**
- G.1 A definition with none of the new keywords behaves exactly as today (existing DE suites pass unchanged).
- G.2 Existing endpoints/behaviour unaffected when no field is restricted.

**H. Security / negative**
- H.1 Tampering via PUT (changed restricted value) is silently ignored — no privilege escalation.
- H.2 Tampering via PATCH without the role → 403, value unchanged.
- H.3 Revoking the entitlement: previously-allowed PATCH now 403; read-restricted field now omitted on GET.

**I. Personal-entity interaction**
- I.1 Field-level roles compose with personal (`userId`) row scoping — both enforced, orthogonally.

**Harness note:** OBP DE tests today exercise GET/POST/PUT/DELETE; a **`makePatchRequest`** helper (and PATCH routing for DEs) is a prerequisite, since PATCH is new for Dynamic Entities.

## 14. Open questions

- Multiple write roles per field (OR semantics) — needed, or one role per field enough?
- PATCH semantics confirmation (partial update verb) vs reusing PUT with a flag.
- Naming of the auto-generated role delimiter (`__`) — final convention.

## 15. Implementation plan (v7.0.0)

Branch: `feature/de-field-level-permissions`. Locked decisions: target **v7.0.0**; introduce **PATCH** as the
restricted-field write path; enforce in the **handler layer** (`Http4sDynamicEntity`, which has `callContext`/user);
use the recommended `handleEntitlementsAndScopes` for new checks (boolean `APIUtil.hasEntitlement` for per-field loops).

Grounding (from code recon):
- CRUD: `Http4sDynamicEntity.scala` — `genericPost/genericGet/genericPut/genericDelete`; role checks via
  `NewStyle.function.hasEntitlement(... DynamicEntityInfo.canXRole(entityName, bankId) ...)`; data + body-validation via
  `NewStyle.function.invokeDynamicConnector(op, ...)`. **No PATCH route** (dispatch match on `(req.method, rest)`).
- Definition validation: `DynamicEntityProvider.scala` — `validateEntityJson` (runtime body) and
  `DynamicEntityCommons.apply` (definition-time schema). Per-field foreach validates type/example/minLength.
- Roles: `DynamicEntityHelper.scala` → `DynamicEntityInfo.canCreateRole/...` (`CanCreateDynamicEntity_System<entity>`
  or bank variant) via `ApiRole.getOrCreateDynamicApiRole`; registered through `dynamicEntityRoles`/`roleNames`.
- Data provider: `MapppedDynamicDataProvider.scala` — `save/update/get/getAll/getAllDataJson` (stores `dataJson`).
- Docs: `DynamicEntityHelper.createDocs` + `DynamicEntityInfo.getSingleExampleWithoutId/getSingleExample`.

**Phase 1 — schema keywords (DynamicEntityProvider.scala).** Recognise per-property `writeRole`/`readRole` (string)
and `writeRoleRequired`/`readRoleRequired` (boolean) in `DynamicEntityCommons.apply`; add `writeRestrictedFields`/
`readRestrictedFields` + `explicitWriteRole`/`explicitReadRole` helpers on `DynamicEntityT`; make `validateEntityJson`
skip `required` for write-restricted fields. Backward compatible (absence ⇒ unrestricted).
**Phase 2 — roles (DynamicEntityHelper `DynamicEntityInfo` + ApiRole).** `fieldWriteRole/fieldReadRole` auto-names
(`CanWriteDynamicEntityField_<Entity>__<Field>`); register explicit role strings; extend `roleNames`/`dynamicEntityRoles`.
**Phase 3 — POST/PUT enforcement (Http4sDynamicEntity).** POST strips restricted fields; PUT strips + merges existing
restricted values back before `invokeDynamicConnector(UPDATE)`.
**Phase 4 — PATCH (Http4sDynamicEntity).** Add `Method.PATCH => genericPatch`; per-field `writeRole` check (403 else),
merge authorised fields into existing record; register route + resource doc.
**Phase 5 — GET read omission (Http4sDynamicEntity `genericGet`/`publicGet`/`communityGet`).** Omit fields the caller
lacks `readRole` for (GET_ONE + GET_ALL); public/community omit all read-restricted.
**Phase 6 — operation-aware resource docs (DynamicEntityHelper).** CREATE/UPDATE request examples exclude restricted
fields; add PATCH docs; annotate read-restricted in responses.
**Phase 7 — tests.** New `obp-api/src/test/scala/code/api/v7_0_0/DynamicEntityFieldRolesTest.scala` + `makePatchRequest`
helper, covering §13 A–I.
**Phase 8 — docs/changelog.**

Status: Phase 1 DONE (compiled, runs). Phase 2 DONE — pending compile:
- `DynamicEntityHelper.scala`: `DynamicEntityInfo.fieldWriteRole`/`fieldReadRole` (explicit shared role, or auto
  `CanWriteDynamicEntityField_[System]<entity>__<field>` / `CanGetDynamicEntityField_...`); `dynamicEntityRoles`
  now also emits per-field roles (`.distinct`), so they're grantable via the existing `ApiRole.valueOf` path.
Phase 2b DONE — v6.0.0 create-DE docs (`Http4s600.scala`) now advertise the keywords (markdown + structured request/response examples + Note bullet).
Phase 3 DONE — pending compile: `Http4sDynamicEntity.scala` — `genericPost` strips write-restricted fields; `genericPut` strips + re-injects existing restricted values (preserve); helpers `writeRestrictedFieldsOf`/`stripFields`/`preserveRestrictedOnPut`; restricted-field helpers added to `DynamicEntityInfo`.
Phase 4 DONE — pending compile: `Http4sDynamicEntity.scala` — added `Method.PATCH` route + `genericPatch`
(baseline `canUpdateRole`; per-field `writeRole` check → 403 via `missingFieldWriteRoleNames`; partial-update
`mergePatch` of incoming over existing, bounded to schema fields); `propertyNames` added to `DynamicEntityInfo`.
Note: PATCH route works at runtime but has no resource doc yet (Phase 6) — test via curl/Postman, not API Explorer.
Phase 5 DONE — pending compile: `Http4sDynamicEntity.scala` — `genericGet`/`publicGet`/`communityGet` omit
read-restricted fields via `applyReadRestrictions`/`omitFields` (authenticated → per-user role check; public →
omit all read-restricted). Internal merge fetches in PUT/PATCH stay unfiltered.
Phase 6 PARTIAL DONE — pending compile: `DynamicEntityHelper.scala` — added `getSingleExampleWithoutIdWritable`;
CREATE/UPDATE request-body examples now exclude write-restricted fields (responses keep all).
Phase 6 REMAINING: PATCH resource doc (needs a new `DynamicEntityOperation.PATCH` enum value in obp-commons +
a createDocs branch + doc-pipeline wiring; PATCH already works at runtime), and read-restricted annotations in
response-body docs.
Phase 6 COMPLETE — pending compile: PATCH resource doc (generic + my/) via new `DynamicEntityOperation.PATCH`
enum value (obp-commons) + `buildPatchFunctionName` + createDocs branches; restriction notes appended to
`fieldsDescription`. (The connector match in LocalMappedConnector casts to `Any`, so the new enum value is safe.)
Phase 7 DONE — pending compile: added `makePatchRequest` to `SendServerRequests.scala`; new test
`obp-api/src/test/scala/code/api/v6_0_0/DynamicEntityFieldRolesTest.scala` (placed in v6_0_0 because DE creation is
v6.0.0 + harness lives there) covering: definition-create, POST-drops-write-restricted, PUT-can't-set, PATCH 403→grant→200,
GET read-omission→grant→visible. NOT yet run — may need iteration (role-string exactness, dispatch `.PATCH`).
Phase 8 DONE — brief `release_notes.md` entry (2026-06-05) + field-level permissions section added to the
"Dynamic-Entities" glossary item in `Glossary.scala`.

ALL PHASES 1–8 IMPLEMENTED on branch `feature/de-field-level-permissions` (Phases 1–4 compiled+spot-tested by user;
5–8 pending compile/test). Provenance (companion RFC `DE_field_provenance.md`) is a separate later PR.
