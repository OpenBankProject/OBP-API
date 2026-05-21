#!/usr/bin/env python3
"""
rehydrate_resource_docs.py
==========================

Tooling used during the v6.0.0 (and friends) liftweb → http4s migration to
rehydrate `ResourceDoc` registrations whose request/response example bodies
and descriptions were dropped during the port. The script reads the
commented-out liftweb predecessor blocks in `APIMethodsXYZ.scala`, parses
the positional args of each `staticResourceDocs += ResourceDoc(...)` block,
and writes the descriptions + request + response example bodies into the
matching `resourceDocs += ResourceDoc(...)` registrations in
`Http4sXYZ.scala` (matched by `nameOf(<endpointName>)`).

Two subcommands are provided:

  lift          Lift description (arg 7), request body (arg 8) and response
                body (arg 9) from each commented liftweb predecessor into the
                matching http4s registration. Idempotent; only patches an
                entry when at least one of the three differs.

  split-init    Split top-level `resourceDocs += ResourceDoc(...)` statements
                inside `object Implementations*` into batched
                `private def registerBatchK(): Unit = { ... }` methods so the
                object's `<init>` stays under the JVM's 64KB-per-method limit.
                Only needed once per Http4sXYZ.scala when post-lift `<init>`
                exceeds the bytecode limit (in practice: Http4s600.scala).

Usage
-----

  # Rehydrate v5.1.0 endpoints (then rebuild with mvn).
  python3 scripts/rehydrate_resource_docs.py lift \
      --api-methods obp-api/src/main/scala/code/api/v5_1_0/APIMethods510.scala \
      --http4s      obp-api/src/main/scala/code/api/v5_1_0/Http4s510.scala

  # After rehydrating v6.0.0, the object init blew past 64KB. Split it.
  python3 scripts/rehydrate_resource_docs.py split-init \
      --http4s      obp-api/src/main/scala/code/api/v6_0_0/Http4s600.scala \
      --batch-size 30

Notes
-----

* The script does not run `mvn`. After lifting you almost certainly need to
  add a few imports (helpers like `userAuthenticationMessage`, case classes
  like `ConsentChallengeJsonV310`, etc.) that the lifted bodies reference.
  `mvn -pl obp-api -am compile -DskipTests` will name them; add to the
  Http4sXYZ.scala import block and rebuild.
* The lift step only touches an entry when the lift would change something —
  it's safe to re-run after manual edits.
* Some Http4sXYZ files use `staticResourceDocs += ResourceDoc(...)` rather
  than `resourceDocs += ResourceDoc(...)`. The script auto-detects which
  prefix the file uses and operates on whichever is present.
* The parser is Scala-aware enough to handle:
    - balanced (), [], {} depth
    - regular "..." strings, including backslash-escapes
    - triple-quoted strings (incl. trailing `"` content quotes — Scala parses
      4+ trailing quotes greedily so the close is the first `<triple-quote>`
      not followed by another `"`)
    - s/f-interpolated strings with ${...} blocks containing nested strings
      and additional braces
    - // line comments

  It is *not* a full Scala parser. If a body expression uses syntax outside
  the above (e.g. `'x'` char literals with embedded special chars beyond
  the simple two-char case), the parser may mis-split args. In practice
  this hasn't bitten on the v6/v5 endpoint set, but worth knowing.

Authoring history
-----------------

Written 2026-05-21 while migrating the v6_0_0 Http4s registrations after the
liftweb retirement. Marko Milić authored most of the http4s migration; the
descriptions/bodies didn't carry over and this script rehydrates them in one
shot. ~370 endpoints across v5_0_0 / v5_1_0 / v6_0_0 picked up populated
schemas as a result.
"""

import argparse
import os
import re
import sys
from pathlib import Path


# ─────────────────────────────────────────────────────────────────────────
# Scala-aware top-level argument splitter
# ─────────────────────────────────────────────────────────────────────────


def split_top_level(text: str) -> list[str]:
    """Split the top-level positional args of a parenthesised expression.

    `text` is the content between the outer `(` and `)` of an
    `<...>ResourceDoc(...)` call (already stripped of any comment markers).
    Returns a list of trimmed arg expressions in positional order.

    Handles:
      - nested () [] {} depth
      - normal "..." string literals (with backslash escapes)
      - triple-quoted "..." strings (incl. trailing `"` content quotes)
      - simple 'x' char literals
      - line // comments
      - Scala s/f-interpolated strings with `${...}` containing nested
        strings and braces
    """
    args: list[str] = []
    current: list[str] = []
    depth = 0
    i = 0
    n = len(text)

    while i < n:
        c = text[i]
        nxt3 = text[i : i + 3]

        # Triple-quoted strings. Scala rule: the closing `"""` is the first
        # run of 3+ quotes whose 4th char (if any) is NOT another `"`.
        # Embedded `"` and `""` are content; `""""` means content-`"` + closer.
        if nxt3 == '"""':
            j = i + 3
            while j < n:
                idx = text.find('"""', j)
                if idx == -1:
                    j = n
                    break
                if idx + 3 < n and text[idx + 3] == '"':
                    j = idx + 1
                    continue
                j = idx + 3
                break
            current.append(text[i:j])
            i = j
            continue

        # Regular single/double-quoted string literals — with s/f
        # interpolation awareness: a `${...}` block inside the string may
        # itself contain nested strings, and we must not let those nested
        # `"` close the outer string.
        if c == '"':
            current.append(c)
            i += 1
            while i < n:
                if (
                    text[i] == "$"
                    and i + 1 < n
                    and text[i + 1] == "{"
                ):
                    current.append("$")
                    current.append("{")
                    i += 2
                    brace_depth = 1
                    while i < n and brace_depth > 0:
                        cc2 = text[i]
                        if text[i : i + 3] == '"""':
                            kk = i + 3
                            while kk < n:
                                idx2 = text.find('"""', kk)
                                if idx2 == -1:
                                    kk = n
                                    break
                                if idx2 + 3 < n and text[idx2 + 3] == '"':
                                    kk = idx2 + 1
                                    continue
                                kk = idx2 + 3
                                break
                            current.append(text[i:kk])
                            i = kk
                            continue
                        if cc2 == '"':
                            current.append(cc2)
                            i += 1
                            while i < n:
                                nch = text[i]
                                current.append(nch)
                                i += 1
                                if nch == "\\" and i < n:
                                    current.append(text[i])
                                    i += 1
                                elif nch == '"':
                                    break
                            continue
                        if cc2 == "{":
                            brace_depth += 1
                        elif cc2 == "}":
                            brace_depth -= 1
                        current.append(cc2)
                        i += 1
                    continue
                ch = text[i]
                current.append(ch)
                i += 1
                if ch == "\\" and i < n:
                    current.append(text[i])
                    i += 1
                elif ch == '"':
                    break
            continue

        # Char literal: 'x' or '\n' — best-effort, only the simple two-char
        # forms. Anything fancier passes through as regular tokens.
        if (
            c == "'"
            and i + 2 < n
            and (text[i + 2] == "'" or text[i + 1] == "\\")
        ):
            j = text.find("'", i + 1)
            if j == -1:
                j = n
            else:
                j += 1
            current.append(text[i:j])
            i = j
            continue

        # Line comment.
        if nxt3[:2] == "//":
            j = text.find("\n", i)
            if j == -1:
                j = n
            current.append(text[i:j])
            i = j
            continue

        if c in "([{":
            depth += 1
            current.append(c)
            i += 1
            continue
        if c in ")]}":
            depth -= 1
            current.append(c)
            i += 1
            continue

        if c == "," and depth == 0:
            args.append("".join(current).strip())
            current = []
            i += 1
            continue

        current.append(c)
        i += 1

    tail = "".join(current).strip()
    if tail:
        args.append(tail)
    return args


# ─────────────────────────────────────────────────────────────────────────
# Liftweb predecessor extraction (commented `staticResourceDocs += …` blocks)
# ─────────────────────────────────────────────────────────────────────────


def collect_liftweb_full(source: str) -> dict[str, tuple[str, str, str]]:
    """Walk source; return { endpointName -> (description, req, resp) } for
    every commented-out `staticResourceDocs += ResourceDoc(...)` block."""
    out: dict[str, tuple[str, str, str]] = {}
    lines = source.splitlines()
    n = len(lines)
    i = 0
    while i < n:
        line = lines[i]
        stripped = line.lstrip()
        if stripped.startswith("//") and "staticResourceDocs += ResourceDoc(" in stripped:
            depth = 0
            collected: list[str] = []
            end = i
            for j in range(i, n):
                cleaned = lines[j].lstrip()
                if not cleaned.startswith("//"):
                    break
                content = cleaned[2:]
                if content.startswith(" "):
                    content = content[1:]
                collected.append(content)
                depth += content.count("(") - content.count(")")
                if depth == 0 and "(" in "".join(collected):
                    end = j
                    break
            else:
                end = n - 1
            block = "\n".join(collected)
            open_idx = block.find("(")
            close_idx = block.rfind(")")
            if open_idx >= 0 and close_idx > open_idx:
                inner = block[open_idx + 1 : close_idx]
                try:
                    args = split_top_level(inner)
                except Exception:
                    args = []
                if len(args) >= 9:
                    name = args[0].strip()
                    desc = args[6].strip()
                    req = args[7].strip()
                    resp = args[8].strip()
                    out[name] = (desc, req, resp)
            i = end + 1
        else:
            i += 1
    return out


# ─────────────────────────────────────────────────────────────────────────
# Http4s file scanning — find each `resourceDocs += ResourceDoc(...)` block
# ─────────────────────────────────────────────────────────────────────────


def find_resourcedoc_spans(source: str, marker: str = "resourceDocs += ResourceDoc("):
    """Yield (start_idx, end_idx_after_close_paren, endpoint_name, args_list)
    for every non-commented occurrence of `marker` in `source`."""
    i = 0
    n = len(source)
    while True:
        j = source.find(marker, i)
        if j == -1:
            return
        line_start = source.rfind("\n", 0, j) + 1
        if source[line_start:j].lstrip().startswith("//"):
            i = j + len(marker)
            continue
        if marker == "resourceDocs += ResourceDoc(" and source[
            max(0, j - len("static")) : j
        ].endswith("static"):
            # We were asked for `resourceDocs += …` but this is actually
            # `staticResourceDocs += …` — skip.
            i = j + len(marker)
            continue
        paren_pos = j + len(marker) - 1
        depth = 1
        k = paren_pos + 1
        while k < n and depth > 0:
            c = source[k]
            nxt3 = source[k : k + 3]
            if nxt3 == '"""':
                kk = k + 3
                while kk < n:
                    idx = source.find('"""', kk)
                    if idx == -1:
                        kk = n
                        break
                    if idx + 3 < n and source[idx + 3] == '"':
                        kk = idx + 1
                        continue
                    kk = idx + 3
                    break
                k = kk
                continue
            if c == '"':
                k += 1
                while k < n:
                    if (
                        source[k] == "$"
                        and k + 1 < n
                        and source[k + 1] == "{"
                    ):
                        k += 2
                        brace_depth = 1
                        while k < n and brace_depth > 0:
                            if source[k : k + 3] == '"""':
                                kk = k + 3
                                while kk < n:
                                    idx2 = source.find('"""', kk)
                                    if idx2 == -1:
                                        kk = n
                                        break
                                    if idx2 + 3 < n and source[idx2 + 3] == '"':
                                        kk = idx2 + 1
                                        continue
                                    kk = idx2 + 3
                                    break
                                k = kk
                                continue
                            if source[k] == '"':
                                k += 1
                                while k < n:
                                    if source[k] == "\\" and k + 1 < n:
                                        k += 2
                                        continue
                                    if source[k] == '"':
                                        k += 1
                                        break
                                    k += 1
                                continue
                            if source[k] == "{":
                                brace_depth += 1
                            elif source[k] == "}":
                                brace_depth -= 1
                            k += 1
                        continue
                    if source[k] == "\\" and k + 1 < n:
                        k += 2
                        continue
                    if source[k] == '"':
                        k += 1
                        break
                    k += 1
                continue
            if nxt3[:2] == "//":
                k = source.find("\n", k)
                if k == -1:
                    k = n
                continue
            if c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
            k += 1
        inner = source[paren_pos + 1 : k - 1]
        try:
            args = split_top_level(inner)
        except Exception:
            args = []
        name = None
        if len(args) >= 3:
            m = re.search(r"nameOf\(([a-zA-Z_][a-zA-Z0-9_]*)\)", args[2])
            if m:
                name = m.group(1)
        yield (j, k, name, args)
        i = k


def detect_marker(source: str) -> str:
    """Return whichever marker prefix the file actually uses."""
    if "resourceDocs += ResourceDoc(" in source:
        # Could also have staticResourceDocs +=, but the strict pattern
        # below (not preceded by "static") is what find_resourcedoc_spans
        # filters on.
        # Prefer the non-static form when it's present at all.
        for line in source.splitlines():
            s = line.lstrip()
            if s.startswith("resourceDocs += ResourceDoc("):
                return "resourceDocs += ResourceDoc("
    if "staticResourceDocs += ResourceDoc(" in source:
        return "staticResourceDocs += ResourceDoc("
    return "resourceDocs += ResourceDoc("


# ─────────────────────────────────────────────────────────────────────────
# Lift subcommand
# ─────────────────────────────────────────────────────────────────────────


def _render_arg_block(args: list[str], block_indent: str) -> str:
    """Render arg list as one-arg-per-line inside the parens, re-indenting
    multi-line arg expressions so continuation lines align."""
    arg_indent = block_indent + "  "

    def reindent(expr: str) -> str:
        lines = expr.splitlines()
        if not lines:
            return ""
        if len(lines) == 1:
            return lines[0].strip()
        first = lines[0].strip()
        rest = lines[1:]
        common = None
        for line in rest:
            if not line.strip():
                continue
            n_lead = len(line) - len(line.lstrip())
            if common is None or n_lead < common:
                common = n_lead
        common = common or 0
        rebuilt = [first]
        for line in rest:
            if not line.strip():
                rebuilt.append("")
            else:
                rebuilt.append(arg_indent + line[common:].rstrip())
        return "\n".join(rebuilt)

    body = ",\n".join(arg_indent + reindent(a) for a in args)
    return f"\n{body}\n{block_indent}"


def cmd_lift(api_methods: Path, http4s: Path) -> int:
    api_src = api_methods.read_text()
    h4_src = http4s.read_text()

    extracted = collect_liftweb_full(api_src)
    print(f"liftweb predecessors collected: {len(extracted)}")

    marker = detect_marker(h4_src)
    spans = list(find_resourcedoc_spans(h4_src, marker=marker))
    # Patch from bottom up so earlier offsets stay valid.
    spans.sort(key=lambda s: s[0], reverse=True)

    new_src = h4_src
    patched = 0
    skipped_missing = 0
    skipped_unchanged = 0
    skipped_short_args = 0
    for j, k, name, args in spans:
        if not name or name not in extracted or len(args) < 9:
            if not name or name not in extracted:
                skipped_missing += 1
            else:
                skipped_short_args += 1
            continue
        desc_l, req_l, resp_l = extracted[name]
        if (
            args[6].strip() == desc_l
            and args[7].strip() == req_l
            and args[8].strip() == resp_l
        ):
            skipped_unchanged += 1
            continue
        new_args = args.copy()
        new_args[6] = desc_l
        new_args[7] = req_l
        new_args[8] = resp_l
        line_start = new_src.rfind("\n", 0, j) + 1
        prefix = new_src[line_start:j]
        block_indent = re.match(r"\s*", prefix).group(0)
        block = _render_arg_block(new_args, block_indent)
        paren_pos = j + len(marker) - 1
        new_src = new_src[: paren_pos + 1] + block + new_src[k - 1 :]
        patched += 1

    http4s.write_text(new_src)
    print(f"Patched: {patched}")
    print(f"Skipped (already up-to-date): {skipped_unchanged}")
    print(f"Skipped (no liftweb match): {skipped_missing}")
    print(f"Skipped (parse / arg-count): {skipped_short_args}")
    return 0


# ─────────────────────────────────────────────────────────────────────────
# split-init subcommand
# ─────────────────────────────────────────────────────────────────────────


def _find_object_close(source: str, obj_name: str) -> tuple[int, int]:
    """Return (open_idx, close_idx) of the named object's braces."""
    needle = f"object {obj_name} {{"
    obj_open = source.find(needle)
    if obj_open == -1:
        raise RuntimeError(f"couldn't find `{needle}` in source")
    n = len(source)
    k = obj_open + len(needle)
    depth = 1
    while k < n and depth > 0:
        c = source[k]
        nxt3 = source[k : k + 3]
        if nxt3 == '"""':
            kk = k + 3
            while kk < n:
                idx = source.find('"""', kk)
                if idx == -1:
                    kk = n
                    break
                if idx + 3 < n and source[idx + 3] == '"':
                    kk = idx + 1
                    continue
                kk = idx + 3
                break
            k = kk
            continue
        if c == '"':
            k += 1
            while k < n:
                if source[k] == "\\" and k + 1 < n:
                    k += 2
                    continue
                if source[k] == '"':
                    k += 1
                    break
                k += 1
            continue
        if nxt3[:2] == "//":
            k = source.find("\n", k)
            if k == -1:
                k = n
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                break
        k += 1
    return obj_open, k


def _find_statement_spans(source: str, marker: str):
    """Yield (start, end) spans of each non-commented `<marker>...)` block."""
    i = 0
    n = len(source)
    while True:
        j = source.find(marker, i)
        if j == -1:
            return
        line_start = source.rfind("\n", 0, j) + 1
        if source[line_start:j].lstrip().startswith("//"):
            i = j + len(marker)
            continue
        if marker == "resourceDocs += ResourceDoc(" and source[
            max(0, j - len("static")) : j
        ].endswith("static"):
            i = j + len(marker)
            continue
        paren_pos = j + len(marker) - 1
        depth = 1
        k = paren_pos + 1
        while k < n and depth > 0:
            c = source[k]
            nxt3 = source[k : k + 3]
            if nxt3 == '"""':
                kk = k + 3
                while kk < n:
                    idx = source.find('"""', kk)
                    if idx == -1:
                        kk = n
                        break
                    if idx + 3 < n and source[idx + 3] == '"':
                        kk = idx + 1
                        continue
                    kk = idx + 3
                    break
                k = kk
                continue
            if c == '"':
                k += 1
                while k < n:
                    if source[k] == "\\" and k + 1 < n:
                        k += 2
                        continue
                    if source[k] == '"':
                        k += 1
                        break
                    k += 1
                continue
            if nxt3[:2] == "//":
                k = source.find("\n", k)
                if k == -1:
                    k = n
                continue
            if c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
            k += 1
        yield (j, k)
        i = k


def cmd_split_init(http4s: Path, obj_name: str, batch_size: int) -> int:
    src = http4s.read_text()
    marker = detect_marker(src)
    stmts = list(_find_statement_spans(src, marker))
    print(f"Found {len(stmts)} `{marker}...)` statements.")

    obj_open, obj_close = _find_object_close(src, obj_name)
    stmts_in_obj = [(s, e) for (s, e) in stmts if obj_open < s and e <= obj_close]
    print(
        f"  …of which {len(stmts_in_obj)} live inside `object {obj_name}`."
    )

    # Pull each statement's full source (anchored at its line start).
    contents: list[str] = []
    for s, e in stmts_in_obj:
        line_start = src.rfind("\n", 0, s) + 1
        contents.append(src[line_start:e])

    batches = [
        contents[i : i + batch_size] for i in range(0, len(contents), batch_size)
    ]
    print(f"Grouping into {len(batches)} batches of {batch_size}.")

    indent4 = "    "

    def reindent_for_def(stmt: str) -> str:
        lines = stmt.splitlines()
        return "\n".join("  " + line if line.strip() else line for line in lines)

    tail = ["\n\n"]
    tail.append(
        indent4
        + "// ─────────────────────────────────────────────────────────────────────\n"
        + indent4
        + "// ResourceDoc registrations are split into batched private defs so the\n"
        + indent4
        + "// object's <init> stays under the JVM's 64KB-per-method limit. Each\n"
        + indent4
        + "// batch is invoked once during object initialisation.\n"
        + indent4
        + "// ─────────────────────────────────────────────────────────────────────\n"
    )
    for k in range(len(batches)):
        tail.append(indent4 + f"registerBatch{k + 1}()\n")
    for k, batch in enumerate(batches):
        tail.append("\n")
        tail.append(indent4 + f"private def registerBatch{k + 1}(): Unit = {{\n")
        for stmt in batch:
            tail.append(reindent_for_def(stmt))
            tail.append("\n")
        tail.append(indent4 + "}\n")

    # Strip the original statements (bottom-up).
    new_src = src
    for s, e in sorted(stmts_in_obj, reverse=True):
        line_start = new_src.rfind("\n", 0, s) + 1
        line_end = new_src.find("\n", e)
        if line_end == -1:
            line_end = len(new_src)
        new_src = new_src[:line_start] + new_src[line_end + 1 :]

    # Re-find the object's close brace in the shrunken text, then insert the
    # tail just before it.
    _, obj_close_new = _find_object_close(new_src, obj_name)
    out = new_src[:obj_close_new] + "".join(tail) + new_src[obj_close_new:]
    http4s.write_text(out)
    print(f"Wrote {http4s}")
    print(
        f"Removed {len(stmts_in_obj)} inline registrations, added "
        f"{len(batches)} batched private defs."
    )
    return 0


# ─────────────────────────────────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────────────────────────────────


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    lift_p = sub.add_parser("lift", help="Lift description/req/resp from commented liftweb predecessors.")
    lift_p.add_argument("--api-methods", required=True, type=Path)
    lift_p.add_argument("--http4s", required=True, type=Path)

    split_p = sub.add_parser(
        "split-init",
        help="Split top-level ResourceDoc registrations into batched private defs.",
    )
    split_p.add_argument("--http4s", required=True, type=Path)
    split_p.add_argument(
        "--object-name",
        default=None,
        help="Object name. Defaults to `Implementations<n>_<n>_<n>` derived from the filename.",
    )
    split_p.add_argument("--batch-size", type=int, default=30)

    args = parser.parse_args(argv)

    if args.cmd == "lift":
        return cmd_lift(args.api_methods, args.http4s)
    if args.cmd == "split-init":
        obj_name = args.object_name
        if obj_name is None:
            # Derive from filename: Http4s600.scala → Implementations6_0_0
            stem = args.http4s.stem  # e.g. Http4s600
            m = re.match(r"Http4s(\d)(\d)(\d)", stem)
            if not m:
                print(
                    f"--object-name not provided and filename {stem!r} is not "
                    "in the Http4sNNN convention; pass it explicitly.",
                    file=sys.stderr,
                )
                return 2
            obj_name = f"Implementations{m.group(1)}_{m.group(2)}_{m.group(3)}"
        return cmd_split_init(args.http4s, obj_name, args.batch_size)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
