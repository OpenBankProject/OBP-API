#!/usr/bin/env python3
"""
check_lift_http4s_resource_doc_parity.py

For each API version, compare ResourceDoc declarations from the Lift-era JSON
baseline (scripts/resource_doc_baseline/lift_resource_docs_vX_Y_Z.json — see
scripts/resource_doc_baseline/README.md) against those in the corresponding
live Http4s*.scala file. Report fields that differ (summary, description,
example request body, success response body, errors, tags).

Read-only. Produces a report to stdout. No files are modified.

Usage:
    python3 scripts/check_lift_http4s_resource_doc_parity.py              # all versions
    python3 scripts/check_lift_http4s_resource_doc_parity.py v6_0_0       # one version
    python3 scripts/check_lift_http4s_resource_doc_parity.py v5_1_0 v6_0_0
    python3 scripts/check_lift_http4s_resource_doc_parity.py --field=successResponseBody v6_0_0
    python3 scripts/check_lift_http4s_resource_doc_parity.py --report-stale-allowlist-entries

The Lift-side JSON baseline was exported once (scripts/resource_doc_baseline/export_and_verify.py
--write) from what used to be the original Lift ResourceDoc text, preserved as
commented-out lines inside the 12 APIMethodsXYZ.scala files before their
deletion — see git history for those files. Known, reviewed differences
between the two sides are recorded in scripts/resource_doc_baseline/parity_allowlist.json
and do not fail this script; anything not in that allowlist, or whose content
has drifted since it was allowlisted, does.

The Http4s-side parsing (collect_resourcedocs, uncomment, etc.) is unchanged
from before this script read JSON — it still reads live Http4s*.scala source,
using the same string/paren-aware parser this file has always used, because
that side is still live code.

The 8th/9th positional ResourceDoc fields are exampleRequestBody/successResponseBody
(see APIUtil.scala:1589 case class ResourceDoc). When http4s has `EmptyBody` and
the Lift baseline has a populated case class, that surfaces in the report.
"""

import argparse
import hashlib
import json
import os
import re
import sys
from collections import OrderedDict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = REPO_ROOT / "scripts" / "resource_doc_baseline"
DEFAULT_ALLOWLIST_PATH = BASELINE_DIR / "parity_allowlist.json"
API_ROOT = REPO_ROOT / "obp-api" / "src" / "main" / "scala" / "code" / "api"

VERSION_RE = re.compile(r"^v\d+_\d+_\d+$")


def resolve_inside(path: Path, root: Path, what: str) -> Path:
    """Resolve a caller-supplied path and require it to stay under root."""
    resolved = Path(path).resolve()
    if resolved != root and root not in resolved.parents:
        sys.exit(f"ERROR: {what} must be inside {root}, got: {resolved}")
    return resolved

# Positional fields in the pre-teardown ResourceDoc(...) signature, which is what
# the commented-out Lift baselines still use — the endpoint partial function was
# the first parameter.
POSITIONAL_FIELDS = [
    "partialFunction",         # 0
    "implementedInApiVersion", # 1
    "partialFunctionName",     # 2 — endpoint identifier
    "requestVerb",             # 3
    "requestUrl",              # 4
    "summary",                 # 5
    "description",             # 6
    "exampleRequestBody",      # 7
    "successResponseBody",     # 8
    "errorResponseBodies",     # 9
    "tags",                    # 10
]

# Current signature (the Lift teardown removed the leading partialFunction
# parameter — see CLAUDE.md Rule 1); used by all active Http4s*.scala files.
CURRENT_POSITIONAL_FIELDS = POSITIONAL_FIELDS[1:]

# The verb literal is the discriminator between the two signatures: it sits at
# positional index 3 in the old signature and index 2 in the current one.
HTTP_VERB_LITERALS = {'"%s"' % v for v in
                      ("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")}

# Default fields included in the diff report.
DEFAULT_DIFF_FIELDS = [
    "requestVerb",
    "requestUrl",
    "summary",
    "description",
    "exampleRequestBody",
    "successResponseBody",
    "errorResponseBodies",
    "tags",
]

# Named-arg fields that can follow the positional list.
NAMED_ARG_FIELDS = {
    "roles",
    "http4sPartialFunction",
    "isFeatured",
    "specialInstructions",
    "createdByBankId",
    "authMode",
}

# Maps CURRENT_POSITIONAL_FIELDS names (Scala-side, camelCase) to the JSON baseline's
# snake_case keys (scripts/resource_doc_baseline/lift_resource_docs_vX_Y_Z.json). Shared
# with scripts/resource_doc_baseline/export_and_verify.py, which imports this module
# rather than redefining the mapping — the two must stay in lockstep, or the export
# and this script's read-back would silently disagree on field names.
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


def uncomment(source: str) -> str:
    """Strip leading `//` (with optional single space) from every line.

    Used for fully-commented Lift files (v5.0.0 / v5.1.0 / v6.0.0).
    """
    out = []
    for line in source.splitlines():
        m = re.match(r"^(\s*)//\s?(.*)$", line)
        if m:
            out.append(m.group(1) + m.group(2))
        else:
            out.append(line)
    return "\n".join(out)


def strip_inline_comments(source: str) -> str:
    """Remove `//` line and `/* */` block comments outside string literals.

    Run AFTER uncomment(). This stops trailing comments (like the `// TODO`
    after `nameOf(getCurrentUser),`) from being split into the next positional
    argument.
    """
    out = []
    i = 0
    n = len(source)
    while i < n:
        skipped = _skip_string_or_comment(source, i)
        if skipped is not None:
            # If the skip was for a string, copy it through; if it was a
            # comment, drop it.
            c = source[i]
            is_string = (
                source.startswith('"""', i)
                or (c in ("s", "f") and source.startswith('"""', i + 1))
                or c == '"'
            )
            if is_string:
                out.append(source[i:skipped])
            # else: comment — discard, but preserve a single newline if the
            # line comment ate one, so line numbering/structure stays sane.
            elif c == "/" and i + 1 < n and source[i + 1] == "/":
                # Line comment: emit the newline if present (skipped includes it).
                if skipped <= n and source[skipped - 1:skipped] == "\n":
                    out.append("\n")
            i = skipped
            continue
        out.append(source[i])
        i += 1
    return "".join(out)


def _skip_string_or_comment(source: str, i: int):
    """If position i starts a string/comment, return the index after it.
    Otherwise return None. Handles triple-quoted, interpolated triple-quoted,
    single-quoted, line and block comments.
    """
    n = len(source)
    c = source[i]
    # triple-quoted plain. Scala's lexer is greedy about trailing `"` —
    # e.g. `"""foo "bar""""` has content `foo "bar"` and closer `"""`, with
    # the 4 quotes at the end being content-quote + 3 closer quotes.
    # A non-greedy `find('"""', i+3)` would split at the wrong boundary,
    # leaving a stray `"` that misaligns every subsequent string in the file.
    if source.startswith('"""', i):
        j = source.find('"""', i + 3)
        if j == -1:
            return n
        k = j + 3
        while k < n and source[k] == '"':
            k += 1
        return k
    # interpolated triple-quote: s"""..."""  or f"""..."""
    if c in ("s", "f") and source.startswith('"""', i + 1):
        j = source.find('"""', i + 4)
        if j == -1:
            return n
        k = j + 3
        while k < n and source[k] == '"':
            k += 1
        return k
    # plain double-quoted string (handle escapes)
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
    # line comment
    if c == "/" and i + 1 < n and source[i + 1] == "/":
        j = source.find("\n", i)
        return n if j == -1 else j + 1
    # block comment
    if c == "/" and i + 1 < n and source[i + 1] == "*":
        j = source.find("*/", i + 2)
        return n if j == -1 else j + 2
    return None


def scan_matching_close(source: str, start: int, open_ch: str, close_ch: str):
    """Return index of the matching close char (depth-aware, string-aware)."""
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
    """Yield (start, end, body) for each `(static)?[rR]esourceDocs += ResourceDoc(...)`.

    If a block cannot be closed (e.g. because its inner contents are doubly
    commented after a mass-comment pass — these are dead in both code paths
    anyway), skip it and continue searching after the opening paren.
    """
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
            # Couldn't find matching close — skip this match and advance.
            i = body_start
            continue
        yield m.start(), close + 1, source[body_start:close]
        i = close + 1


def split_top_level_args(body: str):
    """Split on commas at brace/paren depth 0, respecting strings/comments."""
    args = []
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
            args.append(body[cur:i].strip())
            cur = i + 1
        i += 1
    tail = body[cur:].strip()
    if tail:
        args.append(tail)
    return args


def split_named(arg: str):
    """If `arg` is `name = value` at top-level, return (name, value). Else (None, arg)."""
    m = re.match(r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)$", arg, re.S)
    if m and m.group(1) in NAMED_ARG_FIELDS:
        return m.group(1), m.group(2).strip()
    return None, arg


def parse_resourcedoc(body: str):
    raw = split_top_level_args(body)
    positional = []
    named = OrderedDict()
    for a in raw:
        n, v = split_named(a)
        if n is None:
            positional.append(a)
        else:
            named[n] = v
    out = OrderedDict()
    # Detect which constructor signature this doc uses by where the verb
    # literal sits: index 2 → current signature (no leading partialFunction),
    # index 3 → old signature (Lift baseline comments).
    fields = POSITIONAL_FIELDS
    if len(positional) > 2 and positional[2].strip() in HTTP_VERB_LITERALS:
        fields = CURRENT_POSITIONAL_FIELDS
    for fname, val in zip(fields, positional):
        out[fname] = val
    if len(positional) > len(fields):
        # Next positional after tags is roles (Option[List[ApiRole]]).
        extra = positional[len(fields):]
        if "roles" not in named and extra:
            named["roles"] = extra[0]
    out.update(named)
    return out


def endpoint_name(part_fn_name: str) -> str:
    """`nameOf(getBanks)` -> `getBanks`. Literal `"root"` -> `root`.

    Also evaluates chained `.replace("a", "b")` calls so e.g.
    `nameOf(createConsentByConsentRequestId).replace("Id", "IdEmail")`
    becomes `createConsentByConsentRequestIdEmail` — matches what runs at
    runtime when http4s derives names for related ResourceDoc entries.
    """
    s = (part_fn_name or "").strip()
    # First extract the base name from `nameOf(...)` or a string literal.
    rest = s
    m = re.match(r"^nameOf\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", s)
    if m:
        base = m.group(1)
        rest = s[m.end():]
    elif s.startswith('"'):
        m2 = re.match(r'^"([^"]*)"', s)
        if not m2:
            return s
        base = m2.group(1)
        rest = s[m2.end():]
    else:
        return s
    # Apply any trailing `.replace("a", "b")` calls in order.
    rep_re = re.compile(r'^\s*\.\s*replace\s*\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)')
    while True:
        m = rep_re.match(rest)
        if not m:
            break
        base = base.replace(m.group(1), m.group(2))
        rest = rest[m.end():]
    return base


def normalize(s: str) -> str:
    """Compare semantically — strip whitespace and equivalent syntactic forms."""
    if s is None:
        return None
    t = s
    # `s"""..."""` and `"""..."""` are equivalent when there's no `${...}` —
    # we still keep `s` if there are interpolations because content differs.
    # For comparison, drop a bare leading `s` before a triple quote.
    t = re.sub(r"\bs(\"\"\")", r"\1", t)
    # `.stripMargin` calls are formatting-only — same content with margins stripped
    # also matches the inner content. Drop `.stripMargin` and the leading `|` markers
    # so descriptions compare on their actual text.
    t = re.sub(r"\.stripMargin\b", "", t)
    t = re.sub(r"^\s*\|", "", t, flags=re.M)
    # Collapse whitespace.
    t = re.sub(r"\s+", "", t)
    # `X :: Y :: Nil` -> `List(X,Y)`-style normalization is tricky; instead, treat
    # both `::Nil` and `List(...)` ends the same way by stripping both.
    return t


def normalize_list(s: str) -> str:
    """Normalize list expressions like `X :: Y :: Nil` vs `List(X, Y)`."""
    if s is None:
        return None
    items = []
    # Try `List(...)` form
    m = re.match(r"^\s*List\s*\(\s*(.*?)\s*\)\s*$", s, re.S)
    if m:
        items = [x.strip() for x in split_top_level_args(m.group(1))]
    else:
        # Try `X :: Y :: ... :: Nil`
        # Replace `Nil` with empty and split on `::`
        parts = [p.strip() for p in re.split(r"::", s)]
        if parts and parts[-1] == "Nil":
            items = [p for p in parts[:-1] if p]
        else:
            items = parts
    return "[" + ",".join(re.sub(r"\s+", "", x) for x in items) + "]"


def short(s, max_len: int = 200):
    if s is None:
        return "(absent)"
    one_line = re.sub(r"\s+", " ", s).strip()
    if len(one_line) > max_len:
        return one_line[:max_len] + f" …({len(one_line)} chars)"
    return one_line


def find_pair_for_version(version_dir: Path):
    api_methods = list(version_dir.glob("APIMethods*.scala"))
    http4s = list(version_dir.glob("Http4s*.scala"))
    if not api_methods or not http4s:
        return None
    # Prefer the canonical filename (APIMethods600 / Http4s600); reject decorators
    # like APIMethodsCustom300 by preferring shorter names that start with the
    # exact prefix.
    def pick(files, prefix):
        ranked = sorted(
            files,
            key=lambda p: (
                0 if re.match(rf"^{prefix}\d+\.scala$", p.name) else 1,
                len(p.name),
            ),
        )
        return ranked[0]
    return pick(api_methods, "APIMethods"), pick(http4s, "Http4s")


def collect_resourcedocs_with_stats(path: Path):
    """Parse a file's ResourceDoc registrations.

    Returns (docs, stats) where stats records what was seen but NOT stored:
      blocks       — registrations found after comment stripping
      unparsed     — blocks with no extractable partialFunctionName
      duplicates   — blocks whose endpoint name was already taken (first wins)

    Callers that are about to treat `docs` as a faithful copy of the file (the
    baseline export, which then authorises deleting it) need those counts: a
    digest over `docs` alone cannot reveal a registration that never made it
    into `docs` in the first place.
    """
    source = path.read_text(encoding="utf-8")
    active = re.search(r"^\s*(?:static)?[rR]esourceDocs\s*\+=\s*ResourceDoc\s*\(", source, re.M)
    commented = re.search(r"^\s*//\s*(?:static)?[rR]esourceDocs\s*\+=\s*ResourceDoc\s*\(", source, re.M)
    if not active and commented:
        source = uncomment(source)
    source = strip_inline_comments(source)
    docs = OrderedDict()
    stats = {"blocks": 0, "unparsed": 0, "duplicates": 0, "duplicate_names": []}
    for _, _, body in find_resourcedoc_blocks(source):
        stats["blocks"] += 1
        args = parse_resourcedoc(body)
        if "partialFunctionName" not in args:
            stats["unparsed"] += 1
            continue
        name = endpoint_name(args["partialFunctionName"])
        # Key by endpoint name only — URL/verb get compared as fields so
        # intentional renames during migration show up as field diffs rather
        # than "missing" entries.
        if name in docs:
            stats["duplicates"] += 1
            stats["duplicate_names"].append(name)
            continue
        docs[name] = args
    return docs, stats


def collect_resourcedocs(path: Path):
    docs, _ = collect_resourcedocs_with_stats(path)
    return docs


def find_http4s_path(version: str):
    """Locate the canonical Http4sNNN.scala for a version (no longer paired
    against an APIMethods*.scala file — that side now comes from the JSON
    baseline via load_baseline_docs). Same "prefer the canonical short name"
    ranking find_pair_for_version used to apply to both sides.
    """
    vdir = API_ROOT / version
    if not vdir.is_dir():
        return None
    candidates = list(vdir.glob("Http4s*.scala"))
    if not candidates:
        return None
    ranked = sorted(
        candidates,
        key=lambda p: (0 if re.match(r"^Http4s\d+\.scala$", p.name) else 1, len(p.name)),
    )
    return ranked[0]


def load_baseline_docs(version: str):
    """Read scripts/resource_doc_baseline/lift_resource_docs_vX_Y_Z.json and
    return the same OrderedDict[name -> args] shape collect_resourcedocs()
    produces from .scala source, using the ORIGINAL Scala field names
    (requestVerb, requestUrl, ...) via JSON_KEY_TO_FIELD, so every function
    below this point (normalize, normalize_list, the diff loop) can treat
    JSON-sourced and .scala-sourced docs identically. Returns None if no
    baseline file exists for this version.
    """
    if not VERSION_RE.match(version):
        sys.exit(f"ERROR: not a valid version token: {version!r} (expected e.g. v6_0_0)")
    path = BASELINE_DIR / f"lift_resource_docs_{version}.json"
    if not path.exists():
        return None
    data = json.loads(path.read_text(encoding="utf-8"))
    docs = OrderedDict()
    for name, entry in data.get("endpoints", {}).items():
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


def load_allowlist(path: Path):
    path = resolve_inside(path, REPO_ROOT, "--allowlist")
    if not path.exists():
        return {"rename_pairs": [], "only_in_lift": [], "only_in_http4s": [], "field_mismatches": []}
    data = json.loads(path.read_text(encoding="utf-8"))
    data.setdefault("rename_pairs", [])
    data.setdefault("only_in_lift", [])
    data.setdefault("only_in_http4s", [])
    data.setdefault("field_mismatches", [])
    return data


def identity_digest(verb: str, url: str) -> str:
    """Digest an endpoint's (verb, url) identity for only_in_lift/only_in_http4s
    allowlist entries — bound to VALUE, not name: if the endpoint's verb/url
    changes after being allowlisted, this digest changes and the entry stops
    matching (drift), rather than silently continuing to suppress a now-different
    endpoint that happens to share the old name.
    """
    key = f"{normalize(verb)}|{normalize(url)}"
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


def field_digest(value, field: str) -> str:
    """Digest one field's normalized value for field_mismatches allowlist entries.
    Uses the same normalize()/normalize_list() the diff loop itself uses for
    equality, so pure reformatting never counts as drift — but any real content
    change does.
    """
    if field in ("errorResponseBodies", "tags"):
        norm = normalize_list(value)
    else:
        norm = normalize(value)
    return hashlib.sha256((norm or "").encode("utf-8")).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("versions", nargs="*", help="Versions like v6_0_0 (omit = all auto-discovered)")
    ap.add_argument("--field", action="append", default=None,
                    help=f"Restrict diff to these fields. Repeatable. Default: {','.join(DEFAULT_DIFF_FIELDS)}")
    ap.add_argument("--list-only", action="store_true",
                    help="Just list endpoints; do not print diffs")
    ap.add_argument("--verbose-bodies", action="store_true",
                    help="Print full lift/http4s field values (default: truncated to 200 chars)")
    ap.add_argument("--allowlist", type=Path, default=DEFAULT_ALLOWLIST_PATH,
                    help=f"Path to the allowlist JSON (default: {DEFAULT_ALLOWLIST_PATH})")
    ap.add_argument("--report-stale-allowlist-entries", action="store_true",
                    help="List allowlist entries that matched nothing this run "
                         "(informational; does not affect exit code)")
    args = ap.parse_args()

    fields = args.field or DEFAULT_DIFF_FIELDS
    truncate_len = 100000 if args.verbose_bodies else 200
    allowlist = load_allowlist(args.allowlist)
    used = {"rename_pairs": set(), "only_in_lift": set(), "only_in_http4s": set(), "field_mismatches": set()}

    versions = args.versions
    if not versions:
        versions = sorted(
            d.name for d in API_ROOT.iterdir()
            if d.is_dir() and VERSION_RE.match(d.name)
        )

    total_endpoints = 0
    total_mismatches = 0            # unallowlisted or drifted field mismatches
    total_allowlisted_mismatches = 0
    only_in_lift = []               # unallowlisted or drifted
    only_in_http4s = []
    allowlisted_only_lift_count = 0
    allowlisted_only_http4s_count = 0
    per_field_counts = {f: 0 for f in fields}
    per_version_summary = []

    for v in versions:
        lift_docs = load_baseline_docs(v)
        if lift_docs is None:
            print(f"[{v}] no JSON baseline found "
                  f"(scripts/resource_doc_baseline/lift_resource_docs_{v}.json) — skipping", file=sys.stderr)
            continue
        http_path = find_http4s_path(v)
        if http_path is None:
            print(f"[{v}] no Http4s*.scala found — skipping", file=sys.stderr)
            continue
        http_docs = collect_resourcedocs(http_path)

        # Resolve rename pairs first: fold a known Lift-name/http4s-name pair into
        # one shared entry (keyed by the http4s name) before computing only-lift/
        # only-http4s, so an intentional rename never shows up as "missing" on
        # either side. If the pair's (verb, url) identity has drifted since it was
        # allowlisted, leave it unresolved — it falls through to the ordinary
        # only-lift/only-http4s path below and (absent a separate allowlist entry)
        # correctly shows up as a new, unallowlisted difference.
        for idx, p in enumerate(allowlist["rename_pairs"]):
            if p.get("version") != v:
                continue
            lift_name, http4s_name = p.get("lift_name"), p.get("http4s_name")
            if lift_name in lift_docs and http4s_name in lift_docs:
                # Remapping would overwrite a real Lift entry and drop it from the
                # comparison entirely, so any drift inside it would pass unseen.
                print(f"  ✗ CONFLICTING rename pair {lift_name} -> {http4s_name} ({v}): "
                      f"both names exist on the Lift side; refusing to remap", file=sys.stderr)
            elif lift_name in lift_docs and http4s_name in http_docs:
                lift_entry = lift_docs[lift_name]
                http_entry = http_docs[http4s_name]
                lift_id = identity_digest(lift_entry.get("requestVerb", ""), lift_entry.get("requestUrl", ""))
                http_id = identity_digest(http_entry.get("requestVerb", ""), http_entry.get("requestUrl", ""))
                if lift_id == p.get("lift_identity_digest") and http_id == p.get("http4s_identity_digest"):
                    lift_docs[http4s_name] = lift_docs.pop(lift_name)
                    used["rename_pairs"].add(idx)
                else:
                    print(f"  ✗ DRIFTED rename pair {lift_name} -> {http4s_name} ({v}): "
                          f"identity changed since allowlisted, treating as a normal miss", file=sys.stderr)

        lift_keys = set(lift_docs.keys())
        http_keys = set(http_docs.keys())

        print(f"\n=== {v} ===")
        print(f"  lift:   scripts/resource_doc_baseline/lift_resource_docs_{v}.json   ({len(lift_docs)} docs)")
        print(f"  http4s: {http_path.relative_to(REPO_ROOT)} ({len(http_docs)} docs)")

        def partition_only(names, docs, allowlist_key):
            """Split names present on only one side into (new, allowlisted),
            checking each candidate against the matching allowlist category by
            (version, endpoint) and confirming its identity digest still matches."""
            new_ones, allowed_ones = [], []
            for name in names:
                d = docs[name]
                verb = d.get("requestVerb", "").strip().strip('"')
                url = d.get("requestUrl", "").strip().strip('"')
                digest = identity_digest(d.get("requestVerb", ""), d.get("requestUrl", ""))
                match_idx, match_entry = None, None
                for idx, e in enumerate(allowlist[allowlist_key]):
                    if e.get("version") == v and e.get("endpoint") == name:
                        match_idx, match_entry = idx, e
                        break
                if match_entry is not None and match_entry.get("identity_digest") == digest:
                    used[allowlist_key].add(match_idx)
                    allowed_ones.append((verb, url, name))
                else:
                    if match_entry is not None:
                        print(f"      ✗ DRIFTED {verb:6} {url}  ({name}) [{v}] — "
                              f"identity changed since allowlisted", file=sys.stderr)
                    new_ones.append((verb, url, name))
            return new_ones, allowed_ones

        miss_http = sorted(lift_keys - http_keys)   # only in lift
        miss_lift = sorted(http_keys - lift_keys)   # only in http4s
        new_only_lift, allowed_only_lift = partition_only(miss_http, lift_docs, "only_in_lift")
        new_only_http4s, allowed_only_http4s = partition_only(miss_lift, http_docs, "only_in_http4s")
        only_in_lift.extend((v, name) for _, _, name in new_only_lift)
        only_in_http4s.extend((v, name) for _, _, name in new_only_http4s)
        allowlisted_only_lift_count += len(allowed_only_lift)
        allowlisted_only_http4s_count += len(allowed_only_http4s)

        if new_only_lift:
            print(f"  ⚠ {len(new_only_lift)} endpoint(s) in Lift but NOT in Http4s (not allowlisted):")
            for verb, url, name in new_only_lift:
                print(f"      - {verb:6} {url}  ({name})")
        if allowed_only_lift:
            print(f"  ○ {len(allowed_only_lift)} endpoint(s) in Lift but NOT in Http4s (allowlisted):")
            for verb, url, name in allowed_only_lift:
                print(f"      - {verb:6} {url}  ({name})")
        if new_only_http4s:
            print(f"  ⚠ {len(new_only_http4s)} endpoint(s) in Http4s but NOT in Lift (not allowlisted):")
            for verb, url, name in new_only_http4s:
                print(f"      - {verb:6} {url}  ({name})")
        if allowed_only_http4s:
            print(f"  ○ {len(allowed_only_http4s)} endpoint(s) in Http4s but NOT in Lift (allowlisted):")
            for verb, url, name in allowed_only_http4s:
                print(f"      - {verb:6} {url}  ({name})")

        shared = sorted(lift_keys & http_keys)
        version_mismatches = 0
        version_endpoints = len(shared)
        total_endpoints += version_endpoints

        if not args.list_only:
            for name in shared:
                l = lift_docs[name]
                h = http_docs[name]
                diffs = []
                allowlisted_diffs = []
                for f in fields:
                    lv = l.get(f)
                    hv = h.get(f)
                    if f in ("errorResponseBodies", "tags"):
                        eq = normalize_list(lv) == normalize_list(hv)
                    else:
                        eq = normalize(lv) == normalize(hv)
                    if eq:
                        continue
                    lift_dig = field_digest(lv, f)
                    http_dig = field_digest(hv, f)
                    match_idx, match_entry = None, None
                    for idx, e in enumerate(allowlist["field_mismatches"]):
                        if e.get("version") == v and e.get("endpoint") == name and e.get("field") == f:
                            match_idx, match_entry = idx, e
                            break
                    if (match_entry is not None
                            and match_entry.get("lift_digest") == lift_dig
                            and match_entry.get("http4s_digest") == http_dig):
                        used["field_mismatches"].add(match_idx)
                        allowlisted_diffs.append((f, lv, hv))
                        total_allowlisted_mismatches += 1
                    else:
                        if match_entry is not None:
                            print(f"      ✗ DRIFTED allowlisted field mismatch {name}.{f} ({v}) — "
                                  f"value changed since allowlisted", file=sys.stderr)
                        diffs.append((f, lv, hv))
                        per_field_counts[f] += 1
                if diffs:
                    version_mismatches += 1
                    total_mismatches += 1
                    verb = h.get("requestVerb", l.get("requestVerb", "")).strip().strip('"')
                    url = h.get("requestUrl", l.get("requestUrl", "")).strip().strip('"')
                    print(f"\n  ✗ {verb:6} {url}  ({name})  [not allowlisted]")
                    for fname, lv, hv in diffs:
                        print(f"      [{fname}]")
                        print(f"        lift:   {short(lv, truncate_len)}")
                        print(f"        http4s: {short(hv, truncate_len)}")
                if allowlisted_diffs and args.verbose_bodies:
                    verb = h.get("requestVerb", l.get("requestVerb", "")).strip().strip('"')
                    url = h.get("requestUrl", l.get("requestUrl", "")).strip().strip('"')
                    print(f"\n  ○ {verb:6} {url}  ({name})  [allowlisted]")
                    for fname, lv, hv in allowlisted_diffs:
                        print(f"      [{fname}]")
                        print(f"        lift:   {short(lv, truncate_len)}")
                        print(f"        http4s: {short(hv, truncate_len)}")
            if version_mismatches == 0 and not new_only_lift and not new_only_http4s:
                print("  ✓ all shared endpoints match (or all differences are allowlisted)")
        per_version_summary.append((v, version_endpoints, version_mismatches,
                                   len(new_only_lift), len(new_only_http4s)))

    print("\n=== SUMMARY ===")
    print(f"{'version':10} {'shared':>8} {'mismatch':>10} {'only-lift':>10} {'only-http4s':>12}")
    for v, sh, mm, ml, mh in per_version_summary:
        print(f"{v:10} {sh:>8} {mm:>10} {ml:>10} {mh:>12}")
    print(f"\nTotal endpoints compared: {total_endpoints}")
    print(f"Total mismatches (unallowlisted or drifted): {total_mismatches}")
    print(f"Total mismatches (allowlisted, informational): {total_allowlisted_mismatches}")
    print(f"Per-field counts (unallowlisted or drifted):")
    for f, c in per_field_counts.items():
        print(f"  {f:25} {c}")
    print(f"only-in-lift (unallowlisted or drifted): {len(only_in_lift)}   "
          f"(allowlisted: {allowlisted_only_lift_count})")
    print(f"only-in-http4s (unallowlisted or drifted): {len(only_in_http4s)}   "
          f"(allowlisted: {allowlisted_only_http4s_count})")

    if args.report_stale_allowlist_entries:
        print("\n=== STALE ALLOWLIST ENTRIES (matched nothing this run) ===")
        stale_found = False
        for category in ("rename_pairs", "only_in_lift", "only_in_http4s", "field_mismatches"):
            for idx, entry in enumerate(allowlist[category]):
                if idx not in used[category]:
                    stale_found = True
                    print(f"  [{category}] {json.dumps(entry, sort_keys=True)}")
        if not stale_found:
            print("  (none)")

    return 0 if total_mismatches == 0 and not only_in_lift and not only_in_http4s else 1


if __name__ == "__main__":
    sys.exit(main())
