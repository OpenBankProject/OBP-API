#!/usr/bin/env python3
"""Rewrite net.liftweb.json imports/references to json4s equivalents.

Member classification (lift-json package -> json4s location):
  METHODS  parse/parseOpt/compactRender/prettyRender -> com.openbankproject.commons.util.JsonAliases
  NATIVE   Serialization, JsonParser                 -> org.json4s.native
  DROP     Meta (private[json] internal; no public json4s equivalent)
  ROOT     everything else                           -> org.json4s

Every touched file additionally gets `import org.json4s._` (provides the
implicit conversions json4s needs for `.extract`, `\\`, `parse(String)` etc.
that lift-json had as instance methods).

Idempotent: re-running on migrated files is a no-op.
"""
import re
import sys
from pathlib import Path

ROOTS = ["obp-api/src", "obp-commons/src"]

ALIASES = "com.openbankproject.commons.util.JsonAliases"
SHIM = "com.openbankproject.commons.util.json"

METHOD_MEMBERS = {"parse", "parseOpt", "compactRender", "prettyRender"}
NATIVE_MEMBERS = {"Serialization", "JsonParser"}
DROP_MEMBERS = {"Meta"}

IMPORT_RE = re.compile(r"^(\s*\|?\s*)import net\.liftweb\.json(\.[\w.{}, =>_]+)?\s*$")
BRACE_RE = re.compile(r"^\.\{(.*)\}$")


def classify(member: str):
    """member may be 'X' or 'X => Alias' or '_'."""
    base = member.split("=>")[0].strip()
    if base == "_":
        return "wildcard"
    if base in METHOD_MEMBERS:
        return "method"
    if base in NATIVE_MEMBERS:
        return "native"
    if base in DROP_MEMBERS:
        return "drop"
    return "root"


def rewrite_import(prefix: str, tail: str):
    """Return list of replacement import lines (without trailing newline)."""
    if tail is None or tail == "":
        # bare `import net.liftweb.json` -> shim object
        return [f"{prefix}import {SHIM}"]

    m = BRACE_RE.match(tail)
    if m:
        members = [s.strip() for s in m.group(1).split(",") if s.strip()]
        groups = {"root": [], "method": [], "native": [], "wildcard": []}
        for mem in members:
            kind = classify(mem)
            if kind == "drop":
                continue
            groups[kind].append(mem)
        lines = []
        root = groups["root"]
        if groups["wildcard"]:
            root = root + ["_"] if root else ["_"]
        if root:
            inner = ", ".join(root)
            if len(root) == 1 and "=>" not in inner:
                lines.append(f"{prefix}import org.json4s.{inner}")
            else:
                lines.append(f"{prefix}import org.json4s.{{{inner}}}")
        if groups["wildcard"]:
            lines.append(f"{prefix}import {ALIASES}._")
        if groups["method"]:
            inner = ", ".join(groups["method"])
            if len(groups["method"]) == 1:
                lines.append(f"{prefix}import {ALIASES}.{inner}")
            else:
                lines.append(f"{prefix}import {ALIASES}.{{{inner}}}")
        if groups["native"]:
            inner = ", ".join(groups["native"])
            if len(groups["native"]) == 1:
                lines.append(f"{prefix}import org.json4s.native.{inner}")
            else:
                lines.append(f"{prefix}import org.json4s.native.{{{inner}}}")
        return lines or [f"{prefix}import org.json4s._"]

    # dotted path: .X or .X.Y or .X._
    path = tail[1:]  # drop leading dot
    head = path.split(".")[0]
    rest = path[len(head):]  # includes leading dot or empty

    if head == "_":
        return [f"{prefix}import org.json4s._", f"{prefix}import {ALIASES}._"]
    if head == "Serialization":
        return [f"{prefix}import org.json4s.native.Serialization{rest}"]
    if head == "JsonParser":
        if rest == ".parse":
            return [f"{prefix}import {ALIASES}.parse"]
        if rest == ".ParseException":
            return [f"{prefix}import org.json4s.ParserUtil.ParseException"]
        return [f"{prefix}import org.json4s.native.JsonParser{rest}"]
    if head in METHOD_MEMBERS and not rest:
        return [f"{prefix}import {ALIASES}.{head}"]
    if head in DROP_MEMBERS and not rest:
        return []
    return [f"{prefix}import org.json4s.{path}"]


# Non-import textual references (qualified usages, incl. inside string literals
# used by the dynamic-code compiler in DynamicUtil.scala)
TEXT_RULES = [
    (re.compile(r"\bnet\.liftweb\.json\.Serialization\b"), "org.json4s.native.Serialization"),
    (re.compile(r"\bnet\.liftweb\.json\.JsonParser\.ParseException\b"), "org.json4s.ParserUtil.ParseException"),
    (re.compile(r"\bnet\.liftweb\.json\.JsonParser\b"), "org.json4s.native.JsonParser"),
    (re.compile(r"\bnet\.liftweb\.json\.parse\b"), f"{ALIASES}.parse"),
    (re.compile(r"\bnet\.liftweb\.json\.parseOpt\b"), f"{ALIASES}.parseOpt"),
    (re.compile(r"\bnet\.liftweb\.json\.compactRender\b"), f"{ALIASES}.compactRender"),
    (re.compile(r"\bnet\.liftweb\.json\.prettyRender\b"), f"{ALIASES}.prettyRender"),
    (re.compile(r"\bnet\.liftweb\.json\."), "org.json4s."),
]

J4S_WILDCARD = re.compile(r"^\s*import org\.json4s\._\s*$", re.M)


def migrate(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    if "net.liftweb.json" not in txt:
        return False

    out_lines = []
    for line in txt.splitlines():
        m = IMPORT_RE.match(line)
        if m:
            out_lines.extend(rewrite_import(m.group(1), m.group(2)))
        else:
            out_lines.append(line)
    new = "\n".join(out_lines)
    if txt.endswith("\n"):
        new += "\n"

    for pat, rep in TEXT_RULES:
        new = pat.sub(rep, new)

    # ensure `import org.json4s._` for the implicit conversions
    if not J4S_WILDCARD.search(new):
        lines = new.splitlines()
        for i, line in enumerate(lines):
            if line.startswith("import "):
                lines.insert(i, "import org.json4s._")
                break
        new = "\n".join(lines)
        if txt.endswith("\n"):
            new += "\n"

    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False


def main():
    changed = 0
    for root in ROOTS:
        for path in Path(root).rglob("*.scala"):
            if migrate(path):
                changed += 1
    print(f"migrated {changed} files")
    survivors = []
    for root in ROOTS:
        for path in Path(root).rglob("*.scala"):
            if "net.liftweb.json" in path.read_text(encoding="utf-8"):
                survivors.append(str(path))
    if survivors:
        print("files still containing net.liftweb.json:")
        for s in survivors:
            print("  " + s)
        sys.exit(1)


if __name__ == "__main__":
    main()
