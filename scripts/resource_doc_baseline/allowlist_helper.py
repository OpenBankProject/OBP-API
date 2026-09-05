#!/usr/bin/env python3
"""
allowlist_helper.py

Prints a ready-to-paste JSON object for one new entry in
scripts/resource_doc_baseline/parity_allowlist.json, with digests computed
from the CURRENT live data (the JSON baseline + the current Http4s*.scala) —
so the digests you paste in are guaranteed to match what
check_lift_http4s_resource_doc_parity.py will compute the next time it runs.

This tool only PRINTS; it never edits parity_allowlist.json itself. Paste the
printed object into the right array, add your own "reason", and re-run
check_lift_http4s_resource_doc_parity.py to confirm the entry is now consulted
(look for "○ ... (allowlisted)" / "[allowlisted]" in its output, or pass
--report-stale-allowlist-entries to catch a typo'd version/endpoint/field that
never matches anything).

Usage:
    # A Lift endpoint and an http4s endpoint that are the same thing, renamed:
    python3 scripts/resource_doc_baseline/allowlist_helper.py rename-pair \\
        v3_1_0 addCardsForBank addCardForBank "Renamed during http4s migration"

    # An endpoint that exists only on one side (already reviewed as intentional):
    python3 scripts/resource_doc_baseline/allowlist_helper.py only-lift \\
        v2_1_0 createTransactionRequestSepa "SEPA transaction-request type retired"
    python3 scripts/resource_doc_baseline/allowlist_helper.py only-http4s \\
        v6_0_0 createWebUiProps "New in http4s, no Lift predecessor"

    # A field whose Lift vs http4s text differs, already reviewed as intentional:
    python3 scripts/resource_doc_baseline/allowlist_helper.py field-mismatch \\
        v6_0_0 getMetrics description "http4s description is more complete"
"""

import argparse
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
sys.path.insert(0, str(REPO_ROOT / "scripts"))
import check_lift_http4s_resource_doc_parity as parity  # noqa: E402


def get_docs(version: str):
    lift_docs = parity.load_baseline_docs(version)
    if lift_docs is None:
        print(f"No JSON baseline for {version} — run export_and_verify.py --write first.", file=sys.stderr)
        sys.exit(1)
    http_path = parity.find_http4s_path(version)
    if http_path is None:
        print(f"No Http4s*.scala found for {version}.", file=sys.stderr)
        sys.exit(1)
    http_docs = parity.collect_resourcedocs(http_path)
    return lift_docs, http_docs


def cmd_rename_pair(args):
    lift_docs, http_docs = get_docs(args.version)
    if args.lift_name not in lift_docs:
        print(f"'{args.lift_name}' not found in the Lift baseline for {args.version}.", file=sys.stderr)
        sys.exit(1)
    if args.http4s_name not in http_docs:
        print(f"'{args.http4s_name}' not found in http4s for {args.version}.", file=sys.stderr)
        sys.exit(1)
    l = lift_docs[args.lift_name]
    h = http_docs[args.http4s_name]
    entry = {
        "version": args.version,
        "lift_name": args.lift_name,
        "http4s_name": args.http4s_name,
        "reason": args.reason,
        "lift_identity_digest": parity.identity_digest(l.get("requestVerb", ""), l.get("requestUrl", "")),
        "http4s_identity_digest": parity.identity_digest(h.get("requestVerb", ""), h.get("requestUrl", "")),
    }
    print(json.dumps(entry, indent=2))


def cmd_only_one_side(args, docs_key: str):
    lift_docs, http_docs = get_docs(args.version)
    docs = lift_docs if docs_key == "lift" else http_docs
    if args.endpoint not in docs:
        print(f"'{args.endpoint}' not found on the {docs_key} side for {args.version}.", file=sys.stderr)
        sys.exit(1)
    d = docs[args.endpoint]
    entry = {
        "version": args.version,
        "endpoint": args.endpoint,
        "reason": args.reason,
        "identity_digest": parity.identity_digest(d.get("requestVerb", ""), d.get("requestUrl", "")),
    }
    print(json.dumps(entry, indent=2))


VALID_FIELDS = sorted(set(parity.POSITIONAL_FIELDS) | parity.NAMED_ARG_FIELDS)


def cmd_field_mismatch(args):
    # Without this an unknown field yields digests over the empty string for BOTH
    # sides — a well-formed entry that silently matches nothing, leaving the
    # reviewer to wonder why the audit is still red.
    if args.field not in VALID_FIELDS:
        print(f"Unknown field '{args.field}'. Valid fields: {', '.join(VALID_FIELDS)}",
              file=sys.stderr)
        sys.exit(2)
    lift_docs, http_docs = get_docs(args.version)
    if args.endpoint not in lift_docs or args.endpoint not in http_docs:
        print(f"'{args.endpoint}' is not shared between Lift and http4s for {args.version} "
              f"— use only-lift/only-http4s instead if it's missing on one side.", file=sys.stderr)
        sys.exit(1)
    lv = lift_docs[args.endpoint].get(args.field)
    hv = http_docs[args.endpoint].get(args.field)
    entry = {
        "version": args.version,
        "endpoint": args.endpoint,
        "field": args.field,
        "reason": args.reason,
        "lift_digest": parity.field_digest(lv, args.field),
        "http4s_digest": parity.field_digest(hv, args.field),
    }
    print(json.dumps(entry, indent=2))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("rename-pair")
    p.add_argument("version")
    p.add_argument("lift_name")
    p.add_argument("http4s_name")
    p.add_argument("reason")

    p = sub.add_parser("only-lift")
    p.add_argument("version")
    p.add_argument("endpoint")
    p.add_argument("reason")

    p = sub.add_parser("only-http4s")
    p.add_argument("version")
    p.add_argument("endpoint")
    p.add_argument("reason")

    p = sub.add_parser("field-mismatch")
    p.add_argument("version")
    p.add_argument("endpoint")
    p.add_argument("field")
    p.add_argument("reason")

    args = ap.parse_args()
    if args.cmd == "rename-pair":
        cmd_rename_pair(args)
    elif args.cmd == "only-lift":
        cmd_only_one_side(args, "lift")
    elif args.cmd == "only-http4s":
        cmd_only_one_side(args, "http4s")
    elif args.cmd == "field-mismatch":
        cmd_field_mismatch(args)


if __name__ == "__main__":
    main()
