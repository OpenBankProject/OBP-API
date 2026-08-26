#!/usr/bin/env python3
"""
check_lift_http4s_resource_doc_parity.py

For each API version, compare ResourceDoc declarations in the Lift APIMethods*.scala
file against those in the corresponding Http4s*.scala file. Report fields that
differ (summary, description, example request body, success response body,
errors, tags).

Read-only. Produces a report to stdout. No files are modified.

Usage:
    python3 scripts/check_lift_http4s_resource_doc_parity.py              # all versions
    python3 scripts/check_lift_http4s_resource_doc_parity.py v6_0_0       # one version
    python3 scripts/check_lift_http4s_resource_doc_parity.py v5_1_0 v6_0_0
    python3 scripts/check_lift_http4s_resource_doc_parity.py --field=successResponseBody v6_0_0

The "Lift file" can be either:
  - an active APIMethodsXYZ.scala (live Lift code, as in v1..v4)
  - a stubbed APIMethodsXYZ.scala where the original Lift code is preserved as
    line comments (as in v5.0.0 / v5.1.0 / v6.0.0). The script strips leading
    `// ` from such files before parsing.

The 8th/9th positional ResourceDoc fields are exampleRequestBody/successResponseBody
(see APIUtil.scala:1589 case class ResourceDoc). When http4s has `EmptyBody` and
Lift has a populated case class, that surfaces in the report.
"""

import argparse
import os
import re
import sys
from collections import OrderedDict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
API_ROOT = REPO_ROOT / "obp-api" / "src" / "main" / "scala" / "code" / "api"

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


def collect_resourcedocs(path: Path):
    source = path.read_text(encoding="utf-8")
    active = re.search(r"^\s*(?:static)?[rR]esourceDocs\s*\+=\s*ResourceDoc\s*\(", source, re.M)
    commented = re.search(r"^\s*//\s*(?:static)?[rR]esourceDocs\s*\+=\s*ResourceDoc\s*\(", source, re.M)
    if not active and commented:
        source = uncomment(source)
    source = strip_inline_comments(source)
    docs = OrderedDict()
    for _, _, body in find_resourcedoc_blocks(source):
        args = parse_resourcedoc(body)
        if "partialFunctionName" not in args:
            continue
        name = endpoint_name(args["partialFunctionName"])
        # Key by endpoint name only — URL/verb get compared as fields so
        # intentional renames during migration show up as field diffs rather
        # than "missing" entries.
        docs.setdefault(name, args)
    return docs


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("versions", nargs="*", help="Versions like v6_0_0 (omit = all auto-discovered)")
    ap.add_argument("--field", action="append", default=None,
                    help=f"Restrict diff to these fields. Repeatable. Default: {','.join(DEFAULT_DIFF_FIELDS)}")
    ap.add_argument("--list-only", action="store_true",
                    help="Just list endpoints; do not print diffs")
    ap.add_argument("--verbose-bodies", action="store_true",
                    help="Print full lift/http4s field values (default: truncated to 200 chars)")
    args = ap.parse_args()

    fields = args.field or DEFAULT_DIFF_FIELDS
    truncate_len = 100000 if args.verbose_bodies else 200

    versions = args.versions
    if not versions:
        versions = sorted(
            d.name for d in API_ROOT.iterdir()
            if d.is_dir() and re.match(r"^v\d+_\d+_\d+$", d.name)
        )

    total_endpoints = 0
    total_mismatches = 0
    only_in_lift = []
    only_in_http4s = []
    per_field_counts = {f: 0 for f in fields}
    per_version_summary = []

    for v in versions:
        vdir = API_ROOT / v
        pair = find_pair_for_version(vdir)
        if not pair:
            print(f"[{v}] no APIMethods/Http4s pair found — skipping", file=sys.stderr)
            continue
        lift_path, http_path = pair
        lift_docs = collect_resourcedocs(lift_path)
        http_docs = collect_resourcedocs(http_path)

        lift_keys = set(lift_docs.keys())
        http_keys = set(http_docs.keys())

        print(f"\n=== {v} ===")
        print(f"  lift:   {lift_path.relative_to(REPO_ROOT)}   ({len(lift_docs)} docs)")
        print(f"  http4s: {http_path.relative_to(REPO_ROOT)} ({len(http_docs)} docs)")

        miss_http = sorted(lift_keys - http_keys)
        miss_lift = sorted(http_keys - lift_keys)
        if miss_http:
            print(f"  ⚠ {len(miss_http)} endpoint(s) in Lift but NOT in Http4s:")
            for name in miss_http:
                d = lift_docs[name]
                verb = d.get("requestVerb", "").strip().strip('"')
                url = d.get("requestUrl", "").strip().strip('"')
                print(f"      - {verb:6} {url}  ({name})")
            only_in_lift.extend((v, name) for name in miss_http)
        if miss_lift:
            print(f"  ⚠ {len(miss_lift)} endpoint(s) in Http4s but NOT in Lift:")
            for name in miss_lift:
                d = http_docs[name]
                verb = d.get("requestVerb", "").strip().strip('"')
                url = d.get("requestUrl", "").strip().strip('"')
                print(f"      - {verb:6} {url}  ({name})")
            only_in_http4s.extend((v, name) for name in miss_lift)

        shared = sorted(lift_keys & http_keys)
        version_mismatches = 0
        version_endpoints = len(shared)
        total_endpoints += version_endpoints

        if not args.list_only:
            for name in shared:
                l = lift_docs[name]
                h = http_docs[name]
                diffs = []
                for f in fields:
                    lv = l.get(f)
                    hv = h.get(f)
                    if f in ("errorResponseBodies", "tags"):
                        eq = normalize_list(lv) == normalize_list(hv)
                    else:
                        eq = normalize(lv) == normalize(hv)
                    if not eq:
                        diffs.append((f, lv, hv))
                        per_field_counts[f] += 1
                if diffs:
                    version_mismatches += 1
                    total_mismatches += 1
                    verb = h.get("requestVerb", l.get("requestVerb", "")).strip().strip('"')
                    url = h.get("requestUrl", l.get("requestUrl", "")).strip().strip('"')
                    print(f"\n  ✗ {verb:6} {url}  ({name})")
                    for fname, lv, hv in diffs:
                        print(f"      [{fname}]")
                        print(f"        lift:   {short(lv, truncate_len)}")
                        print(f"        http4s: {short(hv, truncate_len)}")
            if version_mismatches == 0 and not miss_http and not miss_lift:
                print("  ✓ all shared endpoints match")
        per_version_summary.append((v, version_endpoints, version_mismatches,
                                   len(miss_http), len(miss_lift)))

    print("\n=== SUMMARY ===")
    print(f"{'version':10} {'shared':>8} {'mismatch':>10} {'only-lift':>10} {'only-http4s':>12}")
    for v, sh, mm, ml, mh in per_version_summary:
        print(f"{v:10} {sh:>8} {mm:>10} {ml:>10} {mh:>12}")
    print(f"\nTotal endpoints compared: {total_endpoints}")
    print(f"Total mismatches:         {total_mismatches}")
    print(f"Per-field counts:")
    for f, c in per_field_counts.items():
        print(f"  {f:25} {c}")

    return 0 if total_mismatches == 0 and not only_in_lift and not only_in_http4s else 1


if __name__ == "__main__":
    sys.exit(main())
