# ResourceDoc baseline

This directory is the source of truth for the original Lift-era `ResourceDoc`
documentation (summary, description, example bodies, error lists, tags) that
used to be preserved as commented-out text inside the 12 `APIMethodsXYZ.scala`
files. Those files have been deleted (see git history — `git log --follow --
obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`, etc., from
whichever commit removed them); this JSON is what
`scripts/check_lift_http4s_resource_doc_parity.py` now reads instead, to keep
auditing the live http4s implementation for accidental documentation drift
from the original Lift design.

## Files

- `lift_resource_docs_vX_Y_Z.json` (one per API version, 12 total) — every
  endpoint's Lift-era `ResourceDoc` fields, each stored as the **literal,
  unevaluated Scala source snippet** it always was (e.g. an interpolated
  triple-quoted description with `.stripMargin`, a `List(...)` tags
  expression, an `X :: Y :: Nil` error list) — not a parsed or evaluated
  value. This is not optional: `rehydrate_resource_docs.py` and
  `restore_resource_doc_bodies.py` splice this text verbatim into a live
  `.scala` file, so anything stored here must still be valid, reproducible
  Scala source text.
- `digest_manifest.json` — a signature proving the JSON above was exported
  losslessly from the original `.scala` sources (see "Digest verification"
  below). Only ever written by `export_and_verify.py --write`.
- `parity_allowlist.json` — known, human-reviewed differences between the
  Lift baseline and the current http4s implementation, so
  `check_lift_http4s_resource_doc_parity.py` only fails on *new* drift, not
  on this already-reviewed backlog. See "Maintaining the allowlist" below.
- `export_and_verify.py` — the one-time (and re-runnable) export tool. Only
  needed again if the JSON baseline itself is ever regenerated from a
  different source (it shouldn't be, in the ordinary course of things — the
  `.scala` files it reads from are gone).
- `allowlist_helper.py` — prints one ready-to-paste allowlist entry with
  correctly-computed digests. Use this instead of hand-computing sha256
  hashes when adding to `parity_allowlist.json`.

## Why JSON instead of keeping the `.scala` files around

The 12 `APIMethodsXYZ.scala` files were themselves already dead code — every
one had shrunk to a thin runtime shim (`object APIMethodsNNN { val
ImplementationsX = Http4sNNN.ImplementationsX }`) plus thousands of lines of
commented-out original Lift source kept only so the parity script could read
it. Moving that text into JSON removes ~60,000 lines of dead Scala from the
build (a `.scala` file the compiler does nothing with is still a `.scala`
file every IDE/search/lint tool indexes) while keeping the actual
information — the Lift design's documentation text — intact and just as
readable as a diff of the parity report.

## Digest verification (why you can trust the JSON matches what was deleted)

Before the `.scala` files were deleted, `export_and_verify.py --write`:

1. Extracted every `ResourceDoc` field from each live `.scala` file (the same
   parser `check_lift_http4s_resource_doc_parity.py` already used) and
   computed a sha256 digest of each `(version, endpoint, field)` value.
2. Wrote the 12 JSON files.
3. Re-read those JSON files back and computed the *same* digests from the
   JSON content.
4. Asserted the two digest sets were **identical — same keys, same values**,
   not just the same count (a byte-for-byte round-trip proof, not a
   plausibility check). Only on success did it write `digest_manifest.json`.

If you ever need to re-verify the checked-in JSON hasn't drifted (e.g. after
a hand-edit, or as a periodic sanity check), run:

```sh
python3 scripts/resource_doc_baseline/export_and_verify.py --check-only
```

This confirms the JSON still matches `digest_manifest.json`'s stored
aggregate digest. (Its check against the original `.scala` sources is
automatically skipped now that those files are gone — there's nothing left
to compare against on that side.)

## Maintaining the allowlist

`parity_allowlist.json` has four sections:

- **`rename_pairs`** — a Lift endpoint and an http4s endpoint that are the
  same real endpoint (same HTTP verb + URL), just renamed during the http4s
  migration. Each pair is bound to both sides' `(verb, url)` identity digest
  — if either side's verb/url ever changes, the pair stops matching and the
  two endpoints fall back to showing as ordinary only-lift/only-http4s
  differences (which will then also need a decision).
- **`only_in_lift`** / **`only_in_http4s`** — an endpoint that only exists on
  one side, already reviewed as intentional (a Lift endpoint that was
  retired, or a genuinely new http4s endpoint with no Lift predecessor).
  Bound to that endpoint's `(verb, url)` identity digest.
- **`field_mismatches`** — one field (`requestVerb`, `requestUrl`, `summary`,
  `description`, `exampleRequestBody`, `successResponseBody`,
  `errorResponseBodies`, or `tags`) on a shared endpoint whose Lift and
  http4s text differ, already reviewed as an intentional improvement (a
  fixed Lift bug, a clarified description, an updated example) rather than a
  regression. Bound to both sides' normalized-value digest — reformatting
  never counts as drift, but any real content change does.

**Every entry is bound to a digest, not a name.** If the underlying value
changes after being allowlisted, the digest no longer matches, the entry
stops suppressing it, and the parity check goes red again on that specific
item — the allowlist can't silently rot into "yes to everything forever."

To add a new entry, don't hand-compute the digests — use the helper, which
computes them from the current live data so they're guaranteed to match what
the parity script itself will see:

```sh
python3 scripts/resource_doc_baseline/allowlist_helper.py rename-pair \
    v3_1_0 someOldName someNewName "why this is the same endpoint, renamed"
python3 scripts/resource_doc_baseline/allowlist_helper.py only-lift \
    v2_1_0 someRetiredEndpoint "why this was intentionally retired"
python3 scripts/resource_doc_baseline/allowlist_helper.py only-http4s \
    v6_0_0 someNewEndpoint "why this has no Lift predecessor"
python3 scripts/resource_doc_baseline/allowlist_helper.py field-mismatch \
    v6_0_0 someEndpoint description "why the http4s text is fine as-is"
```

Paste the printed JSON object into the matching array in
`parity_allowlist.json`, then re-run the parity check and confirm the entry
is now consulted:

```sh
python3 scripts/check_lift_http4s_resource_doc_parity.py --report-stale-allowlist-entries
```

An allowlist entry that never matches anything (typo'd version/endpoint/
field, or the difference it was covering no longer exists) shows up under
"STALE ALLOWLIST ENTRIES" — that's a prompt to fix or remove it, not a
failure by itself.

**Never allowlist a real regression to make the check pass.** If a
difference turns out to be a genuine bug (http4s dropped an error code Lift
declared, a description now omits a real caveat, a URL changed meaning
rather than just a template variable name), fix the http4s side instead, or
leave the parity check red for that item and track the fix separately — the
whole point of this tool is to catch exactly that class of drift.

## Regenerating everything from scratch

This should not normally be necessary — the 12 source `.scala` files are
gone, so there is nothing left to re-export from. It would only apply if,
hypothetically, a future change needed to rebuild the JSON from some other
source of the original Lift text (e.g. checking out the pre-deletion commit).
In that case: point `export_and_verify.py` at that source the same way it
originally read the `.scala` files, run `--write`, confirm the digest gate
passes, and then re-review `parity_allowlist.json` from scratch — a
regenerated baseline's digests won't match the old allowlist's stored
digests unless the extraction is byte-for-byte identical.
