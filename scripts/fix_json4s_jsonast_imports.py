#!/usr/bin/env python3
"""Post-pass after migrate_liftjson_to_json4s.py.

1. `import org.json4s.JsonAST._` duplicates the ensured `import org.json4s._`
   (the json4s package object aliases every AST type) and makes JField/JArray
   ambiguous -> drop the line.
2. lift-json's JsonAST object carried the render helpers; json4s's does not.
   Remap parse/parseOpt/compactRender/prettyRender members of JsonAST imports
   to JsonAliases, keep real AST members.
3. Qualified `liftweb.json.X` call sites (used where a local `val json` shadows
   the package) -> JsonAliases / org.json4s equivalents; drop the then-unused
   bare `import net.liftweb` lines.
"""
import re
from pathlib import Path

ROOTS = ["obp-api/src", "obp-commons/src"]
ALIASES = "com.openbankproject.commons.util.JsonAliases"
METHOD_MEMBERS = {"parse", "parseOpt", "compactRender", "prettyRender"}

AST_IMPORT_RE = re.compile(r"^(\s*\|?\s*)import org\.json4s\.JsonAST\.(.+?)\s*$")

TEXT_RULES = [
    (re.compile(r"(?<![.\w])liftweb\.json\.compactRender\b"), f"{ALIASES}.compactRender"),
    (re.compile(r"(?<![.\w])liftweb\.json\.prettyRender\b"), f"{ALIASES}.prettyRender"),
    (re.compile(r"(?<![.\w])liftweb\.json\.parse\b"), f"{ALIASES}.parse"),
    (re.compile(r"(?<![.\w])liftweb\.json\.JValue\b"), "org.json4s.JValue"),
]

BARE_LIFTWEB_IMPORT_RE = re.compile(r"^import net\.liftweb\s*$")
LIVE_LIFTWEB_REF_RE = re.compile(r"(?<![.\w])liftweb\.")


def rewrite_ast_import(prefix: str, tail: str):
    tail = tail.strip()
    if tail == "_":
        return []  # covered by ensured org.json4s._
    m = re.match(r"^\{(.*)\}$", tail)
    members = [s.strip() for s in m.group(1).split(",")] if m else [tail]
    ast, methods = [], []
    for mem in members:
        base = mem.split("=>")[0].strip()
        if base == "_":
            return []  # brace wildcard: fall back to org.json4s._
        (methods if base in METHOD_MEMBERS else ast).append(mem)
    lines = []
    if ast:
        inner = ", ".join(ast)
        if len(ast) == 1 and "=>" not in inner:
            lines.append(f"{prefix}import org.json4s.JsonAST.{inner}")
        else:
            lines.append(f"{prefix}import org.json4s.JsonAST.{{{inner}}}")
    if methods:
        inner = ", ".join(methods)
        if len(methods) == 1:
            lines.append(f"{prefix}import {ALIASES}.{inner}")
        else:
            lines.append(f"{prefix}import {ALIASES}.{{{inner}}}")
    return lines


def migrate(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    if "org.json4s.JsonAST." not in txt and "liftweb." not in txt:
        return False

    out = []
    for line in txt.splitlines():
        m = AST_IMPORT_RE.match(line)
        if m:
            out.extend(rewrite_ast_import(m.group(1), m.group(2)))
        else:
            out.append(line)
    new = "\n".join(out)
    if txt.endswith("\n"):
        new += "\n"

    for pat, rep in TEXT_RULES:
        new = pat.sub(rep, new)

    # drop bare `import net.liftweb` when nothing live references `liftweb.` anymore
    lines = new.splitlines()
    live_ref = any(
        LIVE_LIFTWEB_REF_RE.search(l) and not l.lstrip().startswith("//")
        for l in lines
        if not BARE_LIFTWEB_IMPORT_RE.match(l)
    )
    if not live_ref:
        lines = [l for l in lines if not BARE_LIFTWEB_IMPORT_RE.match(l)]
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
    print(f"fixed {changed} files")


if __name__ == "__main__":
    main()
