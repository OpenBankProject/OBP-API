#!/usr/bin/env python3
"""A connector result must be converted to its Commons type, never cast to it.

`list.asInstanceOf[List[XCommons]]` compiles and does nothing at runtime - the element type is
erased, so the cast never checks anything. What it does is give the compiler licence to insert a
checkcast at the first element access, and to serialize whatever the elements actually are. The
premise it rests on - "the provider only ever constructs XCommons" - stopped being true when the
providers moved to Doobie and started returning their own row types implementing the same trait
(`ProductAttributeRow`, `CardAttributeRow`, ...).

That is not hypothetical. Four sites failed exactly this way and were fixed in
`fix: convert Commons list responses instead of casting them`: management/method_routings,
management/endpoint-mappings, management/banks/BANK_ID/cards/CARD_ID and management/webui_props
all threw ClassCastException instead of serving a response.

Every XCommons companion extends Converter/ConverterWithType, whose `toCommonsList` does the
conversion this cast pretends to do. Use it:

    data = XCommons.toCommonsList(response)          // converts
    data = response.asInstanceOf[List[XCommons]]     // lies

Scope is deliberately `List[...Commons]`. A cast to a list of something that is not a Commons type
is a different question - it has no Converter to reach for and no trait/implementation split behind
it - so it is left alone rather than swept in here.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOTS = [ROOT / "obp-api/src/main/scala", ROOT / "obp-commons/src/main/scala"]

CAST = re.compile(r"asInstanceOf\[List\[\s*([A-Za-z0-9_.]*Commons)\s*\]\]")


def code_of(line):
    """The line with comment content removed - the pattern is about code, and the comment
    explaining why the pattern is banned necessarily contains it.

    String-aware: a `//` inside a string literal ("http://...") must not truncate the line, or a
    cast written after such a literal is never seen. Block-comment interiors (` * ...`) are dropped
    wholesale; a `/*` opener keeps what precedes it. Line-local by design - a cast inside a
    multi-line block comment would be flagged, which errs on the side the lint should err on.
    """
    stripped = line.lstrip()
    if stripped.startswith("*") or stripped.startswith("/*"):
        return ""
    out = []
    in_string = False
    i, n = 0, len(line)
    while i < n:
        c = line[i]
        if in_string:
            if c == "\\" and i + 1 < n:
                out.append("  ")
                i += 2
                continue
            if c == '"':
                in_string = False
            out.append(c)
            i += 1
            continue
        if c == '"':
            in_string = True
            out.append(c)
            i += 1
            continue
        if c == "/" and i + 1 < n and line[i + 1] in "/*":
            break
        out.append(c)
        i += 1
    return "".join(out)


def main():
    offenders = []
    scanned = 0
    for root in SOURCE_ROOTS:
        for path in sorted(root.rglob("*.scala")):
            scanned += 1
            for n, line in enumerate(path.read_text().splitlines(), 1):
                m = CAST.search(code_of(line))
                if m:
                    offenders.append((path.relative_to(ROOT), n, m.group(1), line.strip()))

    print(f"check_no_blind_commons_casts: {scanned} source file(s) scanned, "
          f"{len(offenders)} blind cast(s) to a Commons list")
    if offenders:
        print("", file=sys.stderr)
        for path, n, kind, line in offenders:
            print(f"  {path}:{n}", file=sys.stderr)
            print(f"      {line}", file=sys.stderr)
            print(f"      -> {kind}.toCommonsList(...)", file=sys.stderr)
        print("", file=sys.stderr)
        print("Each of these casts a provider result to a Commons list without converting it. The "
              "cast is erased, so it checks nothing and defers the failure to the first element "
              "access or to serialization. Use the companion's toCommonsList instead.",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
