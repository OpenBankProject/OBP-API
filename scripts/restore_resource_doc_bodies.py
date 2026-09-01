#!/usr/bin/env python3
"""
restore_resource_doc_bodies.py

Companion to scripts/rehydrate_resource_docs.py — that script lifts
description / exampleRequestBody / successResponseBody. This script lifts the
remaining "metadata" fields that public docs consumers also see:
  - summary
  - errorResponseBodies
  - tags

Reads the Lift-era predecessor data from the JSON baseline at
scripts/resource_doc_baseline/lift_resource_docs_vX_Y_Z.json (see that
directory's README) — originally commented-out ResourceDocs in
APIMethodsXYZ.scala, now JSON since those .scala files were deleted once the
baseline was verified to reproduce them losslessly — and overwrites the
matching field in Http4sXYZ.scala. Endpoints are matched by `nameOf(...)`.

Usage:
    python3 scripts/restore_resource_doc_bodies.py v6_0_0
    python3 scripts/restore_resource_doc_bodies.py v6_0_0 --dry-run
    python3 scripts/restore_resource_doc_bodies.py v6_0_0 --fields=tags
    python3 scripts/restore_resource_doc_bodies.py v6_0_0 --only=getXyz

Read+writes the http4s file in place. Does NOT touch the JSON baseline.
"""

import argparse
import re
import sys
from collections import OrderedDict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
API_ROOT = REPO_ROOT / "obp-api" / "src" / "main" / "scala" / "code" / "api"

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
import check_lift_http4s_resource_doc_parity as parity  # noqa: E402

# Kept as the canonical field-name list for --fields validation and iteration —
# both ResourceDoc constructor signatures (pre- and post-Lift-teardown) share
# these same field NAMES, just at different positional indices. See
# detect_fields() below for the index-detection logic, reused from
# check_lift_http4s_resource_doc_parity.py's own POSITIONAL_FIELDS /
# CURRENT_POSITIONAL_FIELDS split (the same split this file used to duplicate
# with a single hardcoded — and pre-teardown-only — index list, which had
# silently stopped matching any current Http4sXYZ.scala file).
POSITIONAL_FIELDS = parity.POSITIONAL_FIELDS
NAMED_ARG_FIELDS = parity.NAMED_ARG_FIELDS

DEFAULT_FIELDS_TO_RESTORE = [
    "summary",
    "errorResponseBodies",
    "tags",
]


def _skip_string_or_comment(source: str, i: int):
    n = len(source)
    c = source[i]
    # Scala's lexer is greedy about trailing `"` in `"""..."""` — consume
    # any extra `"` chars after the closer to keep string boundaries
    # aligned through the rest of the source.
    if source.startswith('"""', i):
        j = source.find('"""', i + 3)
        if j == -1:
            return n
        k = j + 3
        while k < n and source[k] == '"':
            k += 1
        return k
    if c in ("s", "f") and source.startswith('"""', i + 1):
        j = source.find('"""', i + 4)
        if j == -1:
            return n
        k = j + 3
        while k < n and source[k] == '"':
            k += 1
        return k
    if c == '"':
        j = i + 1
        while j < n:
            if source[j] == "\\":
                j += 2
                continue
            if source[j] == '"':
                return j + 1
            j += 1
        return n
    if c == "/" and i + 1 < n and source[i + 1] == "/":
        j = source.find("\n", i)
        return n if j == -1 else j + 1
    if c == "/" and i + 1 < n and source[i + 1] == "*":
        j = source.find("*/", i + 2)
        return n if j == -1 else j + 2
    return None


def scan_matching_close(source: str, start: int, open_ch: str, close_ch: str):
    depth = 1
    i = start
    n = len(source)
    while i < n:
        skipped = _skip_string_or_comment(source, i)
        if skipped is not None:
            i = skipped
            continue
        c = source[i]
        if c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return None


def find_resourcedoc_blocks(source: str):
    """Yield (header_start, body_start, body_end, body_text) for each
    `(static)?[rR]esourceDocs += ResourceDoc(...)` call."""
    pattern = re.compile(
        r"(?:static)?[rR]esourceDocs\s*\+=\s*ResourceDoc\s*\("
    )
    i = 0
    while True:
        m = pattern.search(source, i)
        if not m:
            return
        body_start = m.end()
        close = scan_matching_close(source, body_start, "(", ")")
        if close is None:
            i = body_start
            continue
        yield m.start(), body_start, close, source[body_start:close]
        i = close + 1


def split_top_level_args(body: str):
    """Return list of (start, end, text)."""
    out = []
    depth = 0
    cur = 0
    i = 0
    n = len(body)
    while i < n:
        skipped = _skip_string_or_comment(body, i)
        if skipped is not None:
            i = skipped
            continue
        c = body[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            out.append((cur, i, body[cur:i]))
            cur = i + 1
        i += 1
    tail = body[cur:]
    if tail.strip():
        out.append((cur, n, tail))
    return out


def split_named(arg_text: str):
    """Return (name, value_text_with_leading_ws_stripped) for named args, or
    (None, arg_text) for positional."""
    m = re.match(r"^(\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*=\s*)", arg_text)
    if m and m.group(2) in NAMED_ARG_FIELDS:
        return m.group(2), arg_text[m.end():]
    return None, arg_text


def parse_args(body: str):
    """Return (positional, named). Each positional entry is {'start','end','text'};
    named entries map name -> value_text."""
    raw = split_top_level_args(body)
    positional = []
    named = OrderedDict()
    for start, end, text in raw:
        name, val = split_named(text)
        if name is None:
            positional.append({"start": start, "end": end, "text": text.strip()})
        else:
            named[name] = val.strip()
    return positional, named


def detect_fields(positional):
    """Return the field-name list matching THIS particular call's constructor
    signature, or None when neither shape is recognisable.

    Pre-teardown calls carry a leading `partialFunction` (verb literal at index
    3); current ones do not (verb literal at index 2). The verb literal is the
    discriminator, so exactly one of those two positions must hold one —
    neither, or both, means this is not a shape the tool understands.

    None is returned rather than defaulting to either layout because the caller
    rewrites the file: guessing wrong shifts every field by one and would write
    a field's text into its neighbour's slot. Reuses
    check_lift_http4s_resource_doc_parity.py's own constants instead of a second
    hardcoded guess — this file's old fixed index-2 name lookup had silently
    stopped matching any current-signature Http4sXYZ.scala file.
    """
    def verb_at(i):
        return len(positional) > i and positional[i]["text"].strip() in parity.HTTP_VERB_LITERALS

    current, legacy = verb_at(2), verb_at(3)
    if current and not legacy:
        return parity.CURRENT_POSITIONAL_FIELDS
    if legacy and not current:
        return parity.POSITIONAL_FIELDS
    return None


def collect_lift_docs(version: str):
    """Return {endpoint_name -> {field_name -> raw_text}} from the JSON baseline."""
    docs = parity.load_baseline_docs(version)
    if docs is None:
        raise FileNotFoundError(
            f"no baseline at {parity.BASELINE_DIR / f'lift_resource_docs_{version}.json'} "
            f"— see scripts/resource_doc_baseline/README.md"
        )
    return docs


def reindent_value(value_text: str, target_indent: str) -> str:
    """Re-indent multi-line value so subsequent lines start at target_indent."""
    if "\n" not in value_text:
        return value_text
    lines = value_text.split("\n")
    min_indent = None
    for line in lines[1:]:
        if line.strip():
            il = len(line) - len(line.lstrip())
            if min_indent is None or il < min_indent:
                min_indent = il
    if min_indent is None:
        min_indent = 0
    out = [lines[0]]
    for line in lines[1:]:
        if not line.strip():
            out.append("")
            continue
        stripped = line[min_indent:] if len(line) >= min_indent else line.lstrip()
        out.append(target_indent + stripped)
    return "\n".join(out)


def normalize_for_diff(s: str, fname: str) -> str:
    """Normalize a value just enough to detect 'no semantic change'. Mirrors
    what scripts/check_lift_http4s_resource_doc_parity.py does so we don't
    rewrite when the audit would already consider them equal."""
    if s is None:
        return ""
    t = s
    if fname in ("errorResponseBodies", "tags"):
        # `X :: Y :: Nil` vs `List(X, Y)`: extract items and sort/join.
        m = re.match(r"^\s*List\s*\(\s*(.*?)\s*\)\s*$", t, re.S)
        if m:
            items = [x.strip() for _, _, x in split_top_level_args(m.group(1))]
        else:
            parts = [p.strip() for p in re.split(r"::", t)]
            items = [p for p in parts if p and p != "Nil"]
        return "[" + ",".join(re.sub(r"\s+", "", x) for x in items) + "]"
    # Drop leading `s` of `s"""..."""` and the `.stripMargin` postfix; strip
    # leading `|` markers; collapse whitespace.
    t = re.sub(r"\bs(\"\"\")", r"\1", t)
    t = re.sub(r"\.stripMargin\b", "", t)
    t = re.sub(r"^\s*\|", "", t, flags=re.M)
    t = re.sub(r"\s+", "", t)
    return t


def render_new_body(positional, named, new_field_text, fname_to_replace,
                    field_indent):
    """Render the body with the named field replaced by new_field_text.

    Other positional args keep their original text exactly; named args also
    kept verbatim. Layout: positional 0..3 on one line, 4..5 on next, 6+ one
    per line.
    """
    pos_texts = []
    for idx, fname in enumerate(POSITIONAL_FIELDS):
        if idx >= len(positional):
            break
        if fname == fname_to_replace:
            pos_texts.append(new_field_text)
        else:
            pos_texts.append(positional[idx]["text"])
    for idx in range(len(POSITIONAL_FIELDS), len(positional)):
        pos_texts.append(positional[idx]["text"])

    named_texts = [f"{n} = {v}" for n, v in named.items()]

    lines = []
    if len(pos_texts) >= 5:
        lines.append(", ".join(pos_texts[0:4]) + ",")
        line5 = pos_texts[4] + ("," if len(pos_texts) > 5 or named_texts else "")
        if len(pos_texts) > 5:
            line5 = pos_texts[4] + ", " + pos_texts[5] + ","
            for v in pos_texts[6:]:
                lines.append(line5)
                line5 = v + ","
        lines.append(line5)
    else:
        for v in pos_texts:
            lines.append(v + ",")
    for nt in named_texts:
        lines.append(nt + ",")
    if lines:
        last = lines[-1]
        if last.endswith(","):
            lines[-1] = last[:-1]

    body = ("\n" + field_indent).join(lines)
    return "\n" + field_indent + body + ")"


def process_file(http4s_path: Path, lift_docs: dict, dry_run: bool,
                 fields_to_restore, only_names=None):
    """Surgical per-field replacement: for each field that needs to change,
    locate its substring in the body and swap it in-place. Other fields are
    untouched (preserves existing layout & minimises diff)."""
    source = http4s_path.read_text(encoding="utf-8")
    # (abs_start_in_file, abs_end_in_file, new_substring)
    edits = []
    matched = 0
    skipped_no_lift = 0
    unknown_signature = 0
    changed_endpoints = 0
    per_field_changes = {f: 0 for f in fields_to_restore}

    for header_start, body_start, body_end, body in find_resourcedoc_blocks(source):
        positional, _ = parse_args(body)
        if len(positional) < 3:
            continue
        fields = detect_fields(positional)
        if fields is None:
            # Never guess at an unclassifiable signature — writing to guessed
            # offsets would move each field's text into its neighbour's slot.
            unknown_signature += 1
            line_no = source.count("\n", 0, header_start) + 1
            print(
                f"  WARN: unrecognised ResourceDoc signature at "
                f"{http4s_path.name}:{line_no} — no verb literal at positional "
                f"index 2 or 3; skipped",
                file=sys.stderr,
            )
            continue
        name_idx = fields.index("partialFunctionName")
        if name_idx >= len(positional):
            continue
        name = parity.endpoint_name(positional[name_idx]["text"])
        if only_names and name not in only_names:
            continue
        lift = lift_docs.get(name)
        if lift is None:
            skipped_no_lift += 1
            continue
        matched += 1

        # Determine field indent from the FIRST line break we find before the
        # current text of any positional field — fall back to "        ".
        # (Field indent only matters when the new value has embedded newlines.)
        endpoint_changed = False
        for fname in fields_to_restore:
            idx = fields.index(fname)
            if idx >= len(positional):
                continue
            if fname not in lift:
                continue
            cur_text = positional[idx]["text"]
            new_text = lift[fname].strip()
            if normalize_for_diff(cur_text, fname) == normalize_for_diff(new_text, fname):
                continue

            # Compute absolute file offsets of the substring to replace —
            # preserve leading whitespace from the original arg slot.
            arg_slice = body[positional[idx]["start"]:positional[idx]["end"]]
            leading_ws_len = len(arg_slice) - len(arg_slice.lstrip())
            value_start_abs = body_start + positional[idx]["start"] + leading_ws_len
            value_end_abs   = body_start + positional[idx]["end"]

            # Re-indent multi-line new value to align with the leading
            # whitespace context — derive target_indent from the line on
            # which the original value starts.
            line_start = source.rfind("\n", 0, value_start_abs) + 1
            target_indent = source[line_start:value_start_abs]
            # If target_indent is all whitespace, use it verbatim. Else fall
            # back to a sensible default (8 spaces).
            if target_indent.strip():
                target_indent = "        "
            new_text_reindented = reindent_value(new_text, target_indent)

            edits.append((value_start_abs, value_end_abs, new_text_reindented))
            per_field_changes[fname] += 1
            endpoint_changed = True

        if endpoint_changed:
            changed_endpoints += 1

    new_source = source
    for start, end, txt in sorted(edits, key=lambda e: -e[0]):
        new_source = new_source[:start] + txt + new_source[end:]

    prefix = "[dry-run] " if dry_run else ""
    print(f"  {prefix}endpoints matched: {matched}  changed: {changed_endpoints}  "
          f"skipped (no lift match): {skipped_no_lift}  "
          f"skipped (unrecognised signature): {unknown_signature}")
    for f in fields_to_restore:
        print(f"    {f}: {per_field_changes[f]} fields rewritten")

    if not dry_run and changed_endpoints:
        http4s_path.write_text(new_source, encoding="utf-8")
        print(f"  wrote {http4s_path}")

    return unknown_signature


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("versions", nargs="+", help="Versions like v6_0_0")
    ap.add_argument("--dry-run", action="store_true",
                    help="Report intended changes without writing files")
    ap.add_argument("--only", action="append", default=None,
                    help="Restrict to endpoint name(s). Repeatable.")
    ap.add_argument("--fields", default=None,
                    help=f"Comma-separated list of fields to restore. "
                         f"Default: {','.join(DEFAULT_FIELDS_TO_RESTORE)}")
    args = ap.parse_args()

    fields_to_restore = (args.fields.split(",") if args.fields
                         else DEFAULT_FIELDS_TO_RESTORE)
    invalid = [f for f in fields_to_restore if f not in POSITIONAL_FIELDS]
    if invalid:
        print(f"ERROR: unknown fields: {invalid}", file=sys.stderr)
        sys.exit(2)

    unrecognised = 0
    for v in args.versions:
        http4s_path = parity.find_http4s_path(v)
        if http4s_path is None:
            print(f"[skip] {v}: no Http4s*.scala found under {API_ROOT / v}",
                  file=sys.stderr)
            continue
        try:
            lift_docs = collect_lift_docs(v)
        except FileNotFoundError as e:
            print(f"[skip] {v}: {e}", file=sys.stderr)
            continue
        print(f"\n=== {v} ===")
        print(f"  lift:   scripts/resource_doc_baseline/lift_resource_docs_{v}.json")
        print(f"  http4s: {http4s_path.relative_to(REPO_ROOT)}")
        print(f"  fields: {fields_to_restore}")
        unrecognised += process_file(
            http4s_path, lift_docs, args.dry_run,
            fields_to_restore, set(args.only) if args.only else None)

    # Non-zero when any registration could not be classified: the tool's
    # assumption about the constructor shape no longer holds everywhere, and
    # quietly patching the rest would hide that.
    if unrecognised:
        print(f"\nERROR: {unrecognised} ResourceDoc registration(s) had an "
              f"unrecognised constructor signature and were skipped.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
