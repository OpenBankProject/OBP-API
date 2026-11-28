#!/usr/bin/env python3
"""
Static check: catch setPropsValues calls placed outside test scenarios.

The bug:
    feature("...") {
      setPropsValues(...)   // runs at class-init time -> pollutes global Props
      scenario("...") { ... }
    }

The fix is to put setPropsValues inside `scenario { ... }`, `beforeEach()`,
`beforeAll()`, `afterEach()`, or `afterAll()` — blocks that run at test-execution
time, not class-instantiation time.

See docs/testing/TEST_ISOLATION_ADVISE.md.

Run from the repo root:
    python3 .github/scripts/check_test_isolation.py

Exits 0 if clean, 1 if violations found.
"""
import re
import sys
from pathlib import Path


TEST_ROOT = Path("obp-api/src/test/scala")

# Files exempted from the check. ServerSetup.scala is the shared base trait;
# its trait-body `setPropsValues` calls MUST run before `val server = TestServer`
# triggers `Boot.boot()`, because `Migration.scala` captures
# `migration_scripts.execute_all` in a `private val` at class-load time. Moving
# those props into `beforeEach` makes migrations skip and breaks the H2 schema.
ALLOWLIST = {
    "obp-api/src/test/scala/code/setup/ServerSetup.scala",
}

# The watched call sites. Add more here if you want to extend the check
# (e.g. "LiftRules.statelessDispatch.append").
WATCH_CALLS = ("setPropsValues(",)

# Block-opener keywords whose body is allowed to call setPropsValues.
# - scenario:      ScalaTest test method.
# - beforeEach/afterEach/beforeAll/afterAll: ScalaTest hooks run at test time.
# - def <name>:    helper methods, assumed safe (called from scenarios).
# Scenario/Feature are capitalised since the scalatest 3.2 migration (AnyFeatureSpec's DSL);
# the lowercase spellings stay listed so this keeps working on any not-yet-migrated file.
GOOD_KEYWORD_RE = re.compile(
    r"\b(Scenario|scenario|beforeEach|afterEach|beforeAll|afterAll|def\s+\w+)\b"
)
# "BAD" — feature body. setPropsValues at this scope runs at class-init.
BAD_KEYWORD_RE = re.compile(r"\b(Feature|feature)\b")


def strip_strings_and_comments(src: str) -> str:
    """Replace string-literal and comment contents with spaces (preserving
    newlines and overall offsets) so brace tracking isn't confused by `"`
    or `//` inside them."""
    out = []
    i, n = 0, len(src)
    in_block_comment = False
    in_string = False
    while i < n:
        c = src[i]
        if in_block_comment:
            if c == "*" and i + 1 < n and src[i + 1] == "/":
                out.append("  ")
                in_block_comment = False
                i += 2
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if in_string:
            if c == "\\" and i + 1 < n:
                out.append("  ")
                i += 2
                continue
            if c == '"':
                in_string = False
                out.append(" ")
                i += 1
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            in_block_comment = True
            out.append("  ")
            i += 2
            continue
        if c == '"':
            # Triple-quoted strings — preserve newlines, blank everything else
            if i + 2 < n and src[i + 1] == '"' and src[i + 2] == '"':
                out.append("   ")
                i += 3
                while i + 2 < n and not (src[i] == '"' and src[i + 1] == '"' and src[i + 2] == '"'):
                    out.append("\n" if src[i] == "\n" else " ")
                    i += 1
                if i + 2 < n:
                    out.append("   ")
                    i += 3
                continue
            in_string = True
            out.append(" ")
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def classify_opener(text_before_brace: str) -> str:
    """Look at the text immediately preceding a `{` and decide which block
    is opening. Returns 'good', 'bad', or 'neutral'."""
    snippet = text_before_brace[-300:]
    last_good = -1
    for m in GOOD_KEYWORD_RE.finditer(snippet):
        last_good = max(last_good, m.end())
    last_bad = -1
    for m in BAD_KEYWORD_RE.finditer(snippet):
        last_bad = max(last_bad, m.end())
    if last_good > last_bad and last_good >= 0:
        return "good"
    if last_bad > last_good and last_bad >= 0:
        return "bad"
    return "neutral"


def line_of(cleaned: str, idx: int) -> int:
    return cleaned.count("\n", 0, idx) + 1


def find_violations(path: Path):
    src = path.read_text()
    cleaned = strip_strings_and_comments(src)

    violations = []
    stack = []      # list of (kind, depth_when_opened)
    depth = 0
    i, n = 0, len(cleaned)

    while i < n:
        c = cleaned[i]
        if c == "{":
            kind = classify_opener(cleaned[:i])
            depth += 1
            stack.append((kind, depth))
            i += 1
            continue
        if c == "}":
            if stack and stack[-1][1] == depth:
                stack.pop()
            depth = max(0, depth - 1)
            i += 1
            continue
        matched = False
        for call in WATCH_CALLS:
            if cleaned.startswith(call, i):
                # Avoid matching a method *definition* like `def setPropsValues(...)`.
                # Look back ~30 chars for `def `.
                pre = cleaned[max(0, i - 30):i]
                if re.search(r"\bdef\s+$", pre):
                    i += len(call)
                    matched = True
                    break

                # Walk the stack from top: the closest annotated frame decides.
                verdict = "neutral"
                for kind, _ in reversed(stack):
                    if kind == "good":
                        verdict = "good"
                        break
                    if kind == "bad":
                        verdict = "bad"
                        break

                if verdict == "bad":
                    violations.append((
                        line_of(cleaned, i),
                        f"{call} inside feature(...) body but outside any scenario/before*/after*",
                    ))
                elif verdict == "neutral":
                    # Inside class body but no enclosing test scope — also bad.
                    violations.append((
                        line_of(cleaned, i),
                        f"{call} at class body level (runs at class instantiation)",
                    ))
                i += len(call)
                matched = True
                break
        if not matched:
            i += 1

    return violations


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    test_root = repo_root / TEST_ROOT
    if not test_root.exists():
        print(f"ERROR: test root {test_root} not found", file=sys.stderr)
        return 2

    total = 0
    for path in sorted(test_root.rglob("*.scala")):
        rel = path.relative_to(repo_root).as_posix()
        if rel in ALLOWLIST:
            continue
        for line, reason in find_violations(path):
            print(f"{rel}:{line}: {reason}")
            total += 1

    if total > 0:
        print(
            f"\n{total} test-isolation violation(s) found.\n"
            "Wrap setPropsValues inside scenario {{ ... }} or "
            "beforeEach()/beforeAll()/afterEach()/afterAll().\n"
            "See docs/testing/TEST_ISOLATION_ADVISE.md.",
            file=sys.stderr,
        )
        return 1
    print("OK: no test-isolation violations found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
