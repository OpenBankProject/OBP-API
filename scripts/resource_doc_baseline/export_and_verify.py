#!/usr/bin/env python3
"""
export_and_verify.py

One-time (and re-runnable) tool that exports the Lift-era ResourceDoc metadata
currently living as commented-out text inside the 12 APIMethods*.scala files
into a JSON baseline under scripts/resource_doc_baseline/, and proves the
export is lossless before anything downstream — most importantly, deletion of
those .scala files — is allowed to trust it.

Every field is stored as the literal, unevaluated Scala source snippet
check_lift_http4s_resource_doc_parity.py already extracts (e.g. an
interpolated triple-quoted description with .stripMargin, a List(...) tags
expression, an X :: Y :: Nil error list) — not a parsed or evaluated value.
rehydrate_resource_docs.py and restore_resource_doc_bodies.py splice this
text verbatim into a live .scala file; a JSON baseline that stored evaluated
values would break that.

Modes:
  --write        Export .scala -> JSON, verify the round-trip digest set
                 matches EXACTLY (same keys, same values — not just the same
                 count), and only then write digest_manifest.json. On any
                 mismatch, nothing is written past the JSON files themselves
                 and the exit code is 1 — treat that as "do not proceed to
                 deleting the .scala files".
  --check-only   Re-verify an already-exported baseline: the checked-in JSON
                 must still match digest_manifest.json (catches hand-edits),
                 and — while the APIMethods*.scala files still exist — the
                 .scala sources must still match too (catches the JSON going
                 stale before the deletion commit lands). Once those files are
                 deleted, the .scala-side half of this check is skipped.

Read-only towards the .scala sources in both modes; only ever writes into
scripts/resource_doc_baseline/.

Usage:
    python3 scripts/resource_doc_baseline/export_and_verify.py --write
    python3 scripts/resource_doc_baseline/export_and_verify.py --check-only
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
from collections import OrderedDict
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
BASELINE_DIR = SCRIPT_DIR

sys.path.insert(0, str(REPO_ROOT / "scripts"))
import check_lift_http4s_resource_doc_parity as parity  # noqa: E402

# Full set of positional fields persisted per endpoint — broader than parity's
# own DEFAULT_DIFF_FIELDS, since this check is about lossless *export*, not
# comparison against http4s.
PERSISTED_FIELDS = [
    "partialFunctionName",
    "requestVerb",
    "requestUrl",
    "summary",
    "description",
    "exampleRequestBody",
    "successResponseBody",
    "errorResponseBodies",
    "tags",
]

FIELD_JSON_KEYS = {
    "partialFunctionName": "partial_function_name_raw",
    "requestVerb": "request_verb",
    "requestUrl": "request_url",
    "summary": "summary",
    "description": "description",
    "exampleRequestBody": "example_request_body",
    "successResponseBody": "success_response_body",
    "errorResponseBodies": "error_response_bodies",
    "tags": "tags",
}
JSON_KEY_TO_FIELD = {v: k for k, v in FIELD_JSON_KEYS.items()}


def sha256_hex(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def current_git_commit() -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=REPO_ROOT, capture_output=True, text=True, check=True,
        )
        return result.stdout.strip()
    except Exception:
        return "unknown"


def discover_versions():
    return sorted(
        d.name for d in parity.API_ROOT.iterdir()
        if d.is_dir() and parity.VERSION_RE.match(d.name)
    )


def find_lift_path(version: str):
    """Return the canonical APIMethodsNNN.scala path for a version, or None.

    Reuses parity.find_pair_for_version (which also requires a Http4s* file to
    exist in the same directory) and rejects decorator files like
    APIMethodsCustom300.scala the same way parity.py's own `pick()` does.
    """
    vdir = parity.API_ROOT / version
    if not vdir.is_dir():
        return None
    pair = parity.find_pair_for_version(vdir)
    if not pair:
        return None
    lift_path, _http_path = pair
    if not re.match(r"^APIMethods\d+\.scala$", lift_path.name):
        return None
    return lift_path


def digest_tuples(version: str, docs: "OrderedDict[str, OrderedDict]"):
    """Yield (version, endpoint, field, digest) for every persisted field.

    Uses the RAW (unnormalized) string content's sha256 — this proves
    byte-exact round-tripping through JSON, not semantic equivalence (that
    tolerance belongs to the parity script's allowlist, not here).
    """
    tuples = []
    for name, args in docs.items():
        for field in PERSISTED_FIELDS:
            if field in args:
                tuples.append((version, name, field, sha256_hex(args[field])))
        for named_field in sorted(parity.NAMED_ARG_FIELDS):
            if named_field in args:
                tuples.append((version, name, f"extra:{named_field}", sha256_hex(args[named_field])))
    return tuples


def to_digest_map(tuples):
    d = {}
    for version, endpoint, field, digest in tuples:
        d[(version, endpoint, field)] = digest
    return d


def build_version_baseline_json(version: str, source_file: Path, docs, git_commit: str):
    endpoints = OrderedDict()
    for name, args in docs.items():
        entry = OrderedDict()
        for field in PERSISTED_FIELDS:
            if field in args:
                entry[FIELD_JSON_KEYS[field]] = args[field]
        extra = OrderedDict()
        for named_field in sorted(parity.NAMED_ARG_FIELDS):
            if named_field in args:
                extra[named_field] = args[named_field]
        if extra:
            entry["extra_named_args"] = extra
        endpoints[name] = entry
    return OrderedDict([
        ("schema_version", 1),
        ("version", version),
        ("source_file", str(source_file.relative_to(REPO_ROOT))),
        ("exported_from_git_commit", git_commit),
        ("endpoint_count", len(endpoints)),
        ("endpoints", endpoints),
    ])


def docs_from_baseline_json(baseline: dict):
    """Inverse of build_version_baseline_json: JSON -> OrderedDict[name -> args],
    using the ORIGINAL Scala field names (partialFunctionName, requestVerb, ...)
    so digest_tuples() can be applied uniformly to both the .scala-derived and
    JSON-derived docs.
    """
    docs = OrderedDict()
    for name, entry in baseline["endpoints"].items():
        args = OrderedDict()
        for json_key, value in entry.items():
            if json_key == "extra_named_args":
                for k, v in value.items():
                    args[k] = v
                continue
            field = JSON_KEY_TO_FIELD.get(json_key)
            if field:
                args[field] = value
        docs[name] = args
    return docs


def aggregate_digest(digest_map: dict) -> str:
    serializable = sorted(
        [list(k) + [v] for k, v in digest_map.items()]
    )
    return hashlib.sha256(
        json.dumps(serializable, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def report_mismatch(map_a, map_b, label_a: str, label_b: str):
    keys_a, keys_b = set(map_a), set(map_b)
    missing_in_b = sorted(keys_a - keys_b)
    extra_in_b = sorted(keys_b - keys_a)
    changed = sorted(k for k in (keys_a & keys_b) if map_a[k] != map_b[k])
    if not (missing_in_b or extra_in_b or changed):
        return True
    print(f"DIGEST MISMATCH between {label_a} and {label_b}:", file=sys.stderr)
    for k in missing_in_b[:50]:
        print(f"  in {label_a} but missing from {label_b}: {k}", file=sys.stderr)
    for k in extra_in_b[:50]:
        print(f"  in {label_b} but missing from {label_a}: {k}", file=sys.stderr)
    for k in changed[:50]:
        print(f"  value differs: {k}", file=sys.stderr)
    total = len(missing_in_b) + len(extra_in_b) + len(changed)
    if total > 150:
        print(f"  ... and {total - 150} more", file=sys.stderr)
    return False


def cmd_write() -> int:
    versions = discover_versions()
    git_commit = current_git_commit()
    scala_digests = []
    json_digests = []
    per_version_endpoint_counts = OrderedDict()
    BASELINE_DIR.mkdir(parents=True, exist_ok=True)

    exported_versions = []
    coverage_gaps = []
    for v in versions:
        lift_path = find_lift_path(v)
        if lift_path is None:
            continue
        docs, stats = parity.collect_resourcedocs_with_stats(lift_path)
        if not docs:
            continue
        # Coverage, not just fidelity: the digest comparison below only proves the
        # JSON stores what the parser produced. It cannot see a registration the
        # parser never produced, which is exactly the loss that would matter once
        # these .scala files are deleted. So account for every block found.
        if stats["unparsed"] or stats["duplicates"]:
            coverage_gaps.append((v, lift_path, stats))
        scala_digests.extend(digest_tuples(v, docs))
        baseline = build_version_baseline_json(v, lift_path, docs, git_commit)
        out_path = BASELINE_DIR / f"lift_resource_docs_{v}.json"
        out_path.write_text(
            json.dumps(baseline, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        per_version_endpoint_counts[v] = len(docs)
        exported_versions.append(v)

        reloaded = json.loads(out_path.read_text(encoding="utf-8"))
        reloaded_docs = docs_from_baseline_json(reloaded)
        json_digests.extend(digest_tuples(v, reloaded_docs))

    if coverage_gaps:
        print(
            "\nEXPORT IS NOT COMPLETE — some ResourceDoc registrations were found in the "
            "source but not stored, so the digest check below cannot vouch for them:",
            file=sys.stderr,
        )
        for v, path, st in coverage_gaps:
            print(
                f"  {v} ({path.relative_to(REPO_ROOT)}): {st['blocks']} registrations found, "
                f"{st['unparsed']} unparsed, {st['duplicates']} dropped as duplicate names"
                + (f" ({', '.join(st['duplicate_names'])})" if st["duplicate_names"] else ""),
                file=sys.stderr,
            )
        print(
            "digest_manifest.json was NOT written. Do not delete the .scala sources.",
            file=sys.stderr,
        )
        return 1

    map_scala = to_digest_map(scala_digests)
    map_json = to_digest_map(json_digests)
    if not report_mismatch(map_scala, map_json, ".scala extraction", "JSON round-trip"):
        print(
            "\nExport is NOT lossless — digest_manifest.json was NOT written. "
            "Do not proceed to deleting the .scala files.",
            file=sys.stderr,
        )
        return 1

    agg = aggregate_digest(map_scala)
    manifest = OrderedDict([
        ("schema_version", 1),
        ("generated_at", datetime.now(timezone.utc).isoformat()),
        ("aggregate_digest", f"sha256:{agg}"),
        ("per_field_count", len(map_scala)),
        ("per_version_endpoint_counts", per_version_endpoint_counts),
        ("source_git_commit", git_commit),
    ])
    (BASELINE_DIR / "digest_manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    total_endpoints = sum(per_version_endpoint_counts.values())
    print(
        f"OK: {len(map_scala)} field digests match exactly across "
        f"{len(exported_versions)} versions ({total_endpoints} endpoints)."
    )
    print(f"Wrote {len(exported_versions)} baseline files + digest_manifest.json to {BASELINE_DIR}")
    return 0


def cmd_check_only() -> int:
    manifest_path = BASELINE_DIR / "digest_manifest.json"
    if not manifest_path.exists():
        print("No digest_manifest.json found — run --write first.", file=sys.stderr)
        return 1
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    versions = discover_versions()
    json_digests = []
    scala_digests = []
    any_scala_present = False
    any_scala_missing = False

    for v in versions:
        baseline_path = BASELINE_DIR / f"lift_resource_docs_{v}.json"
        if not baseline_path.exists():
            continue
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
        docs_json = docs_from_baseline_json(baseline)
        json_digests.extend(digest_tuples(v, docs_json))

        lift_path = find_lift_path(v)
        if lift_path is not None:
            any_scala_present = True
            docs_scala = parity.collect_resourcedocs(lift_path)
            scala_digests.extend(digest_tuples(v, docs_scala))
        else:
            any_scala_missing = True

    map_json = to_digest_map(json_digests)
    agg_json = aggregate_digest(map_json)
    ok = True
    if f"sha256:{agg_json}" != manifest["aggregate_digest"]:
        print(
            "Checked-in JSON baseline no longer matches digest_manifest.json "
            "(was it hand-edited without re-running --write?).",
            file=sys.stderr,
        )
        ok = False
    else:
        print("OK: checked-in JSON baseline matches digest_manifest.json.")

    if any_scala_present:
        map_scala = to_digest_map(scala_digests)
        if not report_mismatch(map_scala, map_json, ".scala sources (current)", "checked-in JSON baseline"):
            print(
                "\n.scala sources have drifted from the checked-in JSON baseline "
                "since it was exported — re-run --write.",
                file=sys.stderr,
            )
            ok = False
        else:
            print("OK: current .scala sources still match the checked-in JSON baseline.")
    if any_scala_missing and not any_scala_present:
        print(
            "APIMethods*.scala files are gone (already deleted) — skipped the "
            ".scala-side comparison; only JSON-vs-manifest was checked."
        )
    elif any_scala_missing:
        print(
            "Some but not all APIMethods*.scala files are present — unexpected "
            "partial deletion state; treat this as a red flag even though the "
            "digests above may have matched for the files that do still exist.",
            file=sys.stderr,
        )
        ok = False

    return 0 if ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true", help="Export .scala -> JSON and verify losslessness")
    mode.add_argument("--check-only", action="store_true", help="Re-verify an existing export")
    args = ap.parse_args()

    if args.write:
        return cmd_write()
    return cmd_check_only()


if __name__ == "__main__":
    sys.exit(main())
