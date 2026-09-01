#!/usr/bin/env python3
"""
Count the OBP endpoints reachable via /obp/v7.0.0/.

Statically reproduces Http4s700.allResourceDocs.

Inputs are derived from source — no hardcoded data tables:
  * Version files are discovered by globbing obp-api/src/main/scala/code/api/v*/
    for Http4s{NNN}.scala. All registrations live in Http4s files now — the
    older Lift APIMethods*.scala files have been deleted (their historical
    ResourceDoc text lives on in scripts/resource_doc_baseline/, see that
    directory's README).
  * excludeEndpoints lists are extracted from each version's OBPAPI{a}_{b}_{c}.scala
    (or OBPAPI{a}.{b}.{c}.scala for v1.2.1) — and from Http4s700.scala for v7
    which has no OBPAPI counterpart.

Aggregation chain (matches the runtime code in each OBPAPI{a}_{b}_{c}.scala):
  OBPAPI1_2_1.allResourceDocs = Http4s121.resourceDocs                    (chain root)
  OBPAPI{N}.allResourceDocs   = collectResourceDocs(OBPAPI{N-1}, Http4s{N})
                                  .filterNot(excludeEndpoints if any)
  Http4s700.allResourceDocs   = collectResourceDocs(OBPAPI6_0_0, Http4s700)
                                  .filterNot(v7 excludeEndpoints — currently Nil)

collectResourceDocs: concat, stable sort by version descending, dedup by (url, verb).
filterNot: drop docs whose partialFunctionName is in the excluded-names set.

Each run prints a self-check that flags any `\\w*resourceDocs += ResourceDoc`
line that wasn't parsed and isn't obviously commented out — i.e. a new buffer
name, a split-line registration, or a constructor-shape change that broke
parsing.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "obp-api" / "src" / "main" / "scala" / "code" / "api"

VERSION_DIR_RE = re.compile(r"^v(\d+)_(\d+)_(\d+)$")
REG_START      = re.compile(r"^\s*(?:static)?[Rr]esourceDocs\s*\+=\s*ResourceDoc\s*\(")
ANY_REG_REF    = re.compile(r"\w*[Rr]esourceDocs\s*\+=\s*ResourceDoc\b")
NAMEOF_RE      = re.compile(r"nameOf\s*\(\s*[\w.]*?(\w+)\s*\)")
EXCLUDE_DEF_RE = re.compile(r"(?:lazy\s+)?val\s+excludeEndpoints\b[^=]*=")

HTTP_VERBS = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}


class Doc:
    __slots__ = ("verb", "url", "func", "version", "src")

    def __init__(self, verb: str, url: str, func: str, version: tuple, src: str):
        self.verb, self.url, self.func = verb, url, func
        self.version, self.src = version, src

    def key(self):
        return (self.url, self.verb)


# -- auto-discovery of version files ------------------------------------

def discover_version_files() -> list[tuple[tuple, Path]]:
    """Find every v*/ directory and resolve its Http4s{NNN}.scala source file.

    Every version's contribution to the aggregation chain lives in its
    Http4s{NNN}.scala (the older Lift APIMethods*.scala files that used to
    carry registrations have been deleted).
    """
    found = []
    for entry in sorted(SRC.iterdir()):
        if not entry.is_dir():
            continue
        m = VERSION_DIR_RE.match(entry.name)
        if not m:
            continue
        version = tuple(int(g) for g in m.groups())
        nnn = "".join(str(p) for p in version)
        candidate = entry / f"Http4s{nnn}.scala"
        if candidate.is_file():
            found.append((version, candidate))
        else:
            print(
                f"WARN: directory {entry.name} has no {candidate.name} — skipped",
                file=sys.stderr,
            )
    return found


def find_obpapi_file(version: tuple) -> Path | None:
    """Locate the file that owns this version's excludeEndpoints list.

    Most versions: OBPAPI{a}_{b}_{c}.scala. v1.2.1 is an outlier
    (OBPAPI1.2.1.scala with dots). v7 has no OBPAPI counterpart — its
    excludeEndpoints lives in Http4s700.scala.
    """
    a, b, c = version
    vdir = SRC / f"v{a}_{b}_{c}"
    for candidate in (
        vdir / f"OBPAPI{a}_{b}_{c}.scala",
        vdir / f"OBPAPI{a}.{b}.{c}.scala",
        vdir / f"Http4s{a}{b}{c}.scala",
    ):
        if candidate.is_file():
            return candidate
    return None


# -- excludeEndpoints extraction ---------------------------------------

def extract_excludes(path: Path) -> set[str]:
    """Pull names from `(lazy )?val excludeEndpoints = nameOf(...) :: ... :: Nil`.

    Returns the empty set if the val is absent (e.g. v3.0.0, v5.0.0).
    """
    lines = path.read_text(encoding="utf-8").splitlines()
    start = next((i for i, line in enumerate(lines) if EXCLUDE_DEF_RE.search(line)), None)
    if start is None:
        return set()

    names: set[str] = set()
    in_block_comment = False
    for raw in lines[start:]:
        line = raw
        # close an open block comment
        if in_block_comment:
            end = line.find("*/")
            if end == -1:
                continue
            line = line[end + 2:]
            in_block_comment = False
        # open block comment that doesn't close on this line
        if "/*" in line and "*/" not in line[line.find("/*"):]:
            line = line[: line.find("/*")]
            in_block_comment = True
        # line comment
        if "//" in line:
            line = line[: line.find("//")]
        names.update(NAMEOF_RE.findall(line))
        if line.strip() in {"Nil", "Nil)"} or line.rstrip().endswith("Nil"):
            break
    return names


# -- registration parsing ----------------------------------------------

def split_top_level_args(text: str, want: int) -> list[str]:
    """Return the first `want` comma-separated args of a Scala call.

    `text` begins just after the opening '(' of ResourceDoc(...). Tracks single-
    and triple-quoted strings, nested brackets, // line comments and /* */ block
    comments so commas inside any of those aren't treated as separators.
    """
    args, buf = [], []
    depth = 0
    i, n = 0, len(text)
    while i < n and len(args) < want:
        c, two, three = text[i], text[i:i + 2], text[i:i + 3]
        if three == '"""':
            end = text.find('"""', i + 3)
            if end == -1:
                buf.append(text[i:]); break
            buf.append(text[i:end + 3]); i = end + 3; continue
        if c == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2; continue
                if text[j] == '"':
                    break
                j += 1
            buf.append(text[i:j + 1]); i = j + 1; continue
        if two == "//":
            end = text.find("\n", i)
            i = n if end == -1 else end
            continue
        if two == "/*":
            end = text.find("*/", i + 2)
            i = n if end == -1 else end + 2
            continue
        if c in "([{":
            depth += 1; buf.append(c); i += 1; continue
        if c in ")]}":
            if depth == 0:
                break
            depth -= 1; buf.append(c); i += 1; continue
        if c == "," and depth == 0:
            args.append("".join(buf)); buf = []; i += 1; continue
        buf.append(c); i += 1
    if buf and len(args) < want:
        args.append("".join(buf))
    return args


def strip_str_literal(token: str) -> str:
    s = token.strip()
    if s.startswith("s"):
        s = s[1:].strip()
    if s.startswith('"""') and s.endswith('"""'):
        return s[3:-3]
    if s.startswith('"') and s.endswith('"'):
        return s[1:-1]
    return s


def parse_file(path: Path, version: tuple) -> tuple[list[Doc], set[int]]:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    docs, parsed_lines = [], set()
    for idx, line in enumerate(lines):
        if not REG_START.match(line):
            continue
        parsed_lines.add(idx)
        chunk = "".join(lines[idx:idx + 60])
        chunk = chunk[chunk.index("ResourceDoc") + len("ResourceDoc"):]
        chunk = chunk[chunk.index("(") + 1:]
        args = split_top_level_args(chunk, want=5)
        if len(args) < 5:
            print(f"  WARN: incomplete args at {path.name}:{idx + 1}", file=sys.stderr)
            continue
        m = NAMEOF_RE.search(args[2])
        func = m.group(1) if m else strip_str_literal(args[2])
        verb = strip_str_literal(args[3]).upper()
        url  = strip_str_literal(args[4])
        if verb not in HTTP_VERBS:
            print(f"  WARN: unexpected verb {verb!r} at {path.name}:{idx + 1}",
                  file=sys.stderr)
            continue
        docs.append(Doc(verb, url, func, version, f"{path.name}:{idx + 1}"))
    return docs, parsed_lines


# -- self-check --------------------------------------------------------

def self_check(path: Path, parsed_lines: set[int]) -> list[str]:
    """Every `\\w*resourceDocs += ResourceDoc` line must be either parsed by
    the script, a `// resourceDocs += ResourceDoc` commented-out registration,
    or an inline-comment mention (// comes before the pattern)."""
    warnings = []
    for idx, line in enumerate(path.read_text(encoding="utf-8").splitlines()):
        if not ANY_REG_REF.search(line):
            continue
        if idx in parsed_lines:
            continue
        stripped = line.lstrip()
        if stripped.startswith("//"):
            continue
        comment_at = line.find("//")
        ref_at = line.find("resourceDocs")
        # `resourceDocs` may appear after `static`; fall back to a robust check
        if ref_at == -1:
            ref_at = line.find("ResourceDocs")
        if comment_at != -1 and comment_at < ref_at:
            continue
        warnings.append(f"  {path.name}:{idx + 1}: {line.rstrip()}")
    return warnings


# -- aggregation -------------------------------------------------------

def collect(*buckets: list[Doc]) -> list[Doc]:
    merged = [d for bucket in buckets for d in bucket]
    merged.sort(key=lambda d: d.version, reverse=True)  # stable
    seen, out = set(), []
    for d in merged:
        if d.key() not in seen:
            seen.add(d.key())
            out.append(d)
    return out


# -- main --------------------------------------------------------------

def main() -> None:
    version_files = discover_version_files()
    if not version_files:
        sys.exit("ERROR: no v*/Http4s*.scala files found")

    by_version: dict[tuple, list[Doc]] = {}
    self_check_warnings: list[str] = []

    print("Parsing ResourceDoc registrations (auto-discovered):")
    for version, path in version_files:
        docs, parsed = parse_file(path, version)
        by_version[version] = docs
        self_check_warnings += self_check(path, parsed)
        print(f"  v{'.'.join(map(str, version)):<8s}  "
              f"{path.relative_to(REPO)}  {len(docs):4d} docs")
    print()

    excludes: dict[tuple, set[str]] = {}
    print("Extracting excludeEndpoints lists from source:")
    for version, _ in version_files:
        owner = find_obpapi_file(version)
        if owner is None:
            continue
        ex = extract_excludes(owner)
        if ex:
            excludes[version] = ex
            print(f"  v{'.'.join(map(str, version))}: "
                  f"{len(ex)} excludes ({owner.relative_to(REPO)})")
    print()

    # Chain root: OBPAPI1_2_1.allResourceDocs = Http4s121.resourceDocs (no concat).
    # Every later version: collectResourceDocs(prev, this) [.filterNot(excludes)].
    versions_asc = sorted(by_version.keys())
    if not versions_asc:
        sys.exit("ERROR: no parseable version files")
    level: list[Doc] = list(by_version[versions_asc[0]])
    excluded_log: list[tuple[tuple, Doc]] = []
    for version in versions_asc[1:]:
        level = collect(level, by_version[version])
        if version in excludes:
            removed = [d for d in level if d.func in excludes[version]]
            excluded_log += [(version, d) for d in removed]
            level = [d for d in level if d.func not in excludes[version]]

    final = level
    own_v7 = len(by_version.get((7, 0, 0), []))

    print("=" * 66)
    print("OBP endpoints reachable via /obp/v7.0.0/")
    print("=" * 66)
    print(f"v7.0.0 native (http4s) endpoints       : {own_v7:4d}")
    print(f"Total reachable (aggregated + deduped) : {len(final):4d}")
    print()

    wins: dict[tuple, int] = {}
    for d in final:
        wins[d.version] = wins.get(d.version, 0) + 1
    print("Owned by version (newest wins on URL+verb clash):")
    for v in sorted(wins, reverse=True):
        print(f"  v{'.'.join(map(str, v)):<14s} {wins[v]:4d}")
    print()

    verbs: dict[str, int] = {}
    for d in final:
        verbs[d.verb] = verbs.get(d.verb, 0) + 1
    print("By HTTP method:")
    for verb in sorted(verbs, key=lambda k: -verbs[k]):
        print(f"  {verb:<8s} {verbs[verb]:4d}")
    print()

    if excluded_log:
        final_keys = {d.key() for d in final}
        print(f"Endpoints removed by excludeEndpoints filters: {len(excluded_log)}")
        for version, d in excluded_log:
            tag = " (key re-added by a later version)" if d.key() in final_keys else ""
            print(f"  v{'.'.join(map(str, version))} drops "
                  f"{d.verb:6s} {d.url}  ({d.func}){tag}")
        print()

    if self_check_warnings:
        print("Self-check FAILED — unaccounted registration lines "
              "(possible new buffer name, split-line registration, or "
              "constructor-shape change):")
        for w in self_check_warnings:
            print(w)
        sys.exit(1)
    print("Self-check: every `resourceDocs += ResourceDoc` reference "
          "is parsed or visibly commented.")


if __name__ == "__main__":
    main()
