#!/usr/bin/env python3
"""Fix remaining compile errors after json4s migration."""
import re
from pathlib import Path

RICH_JFIELD_IMPORT = "import com.openbankproject.commons.util.JsonAliases.RichJField"
ALIASES_WILDCARD = "import com.openbankproject.commons.util.JsonAliases._"
JSON4S_WILDCARD = "import org.json4s._"

def insert_after_last_import(text: str, new_import: str) -> str:
    """Insert a new import after the last top-level import line."""
    lines = text.splitlines()
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import_idx = i
    if last_import_idx >= 0:
        lines.insert(last_import_idx + 1, new_import)
    return "\n".join(lines) + ("\n" if text.endswith("\n") else "")

def has_import(text: str, import_str: str) -> bool:
    return import_str in text

# ─── Fix 1: OBPRestHelper.scala – shadow org.json4s.ThreadLocal ────────────────
def fix_obpresthelper(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    needle = "import java.lang.ThreadLocal"
    if needle in txt:
        return False
    # Insert after `import org.json4s._`
    new = txt.replace(
        "import org.json4s._\n",
        "import org.json4s._\nimport java.lang.ThreadLocal\n",
        1
    )
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False

# ─── Fix 2: Add RichJField import where .name/.value on JField ────────────────
def add_rich_jfield(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    if RICH_JFIELD_IMPORT in txt or ALIASES_WILDCARD in txt:
        return False
    new = insert_after_last_import(txt, RICH_JFIELD_IMPORT)
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False

# ─── Fix 3: Replace JsonAST.compactRender/prettyRender calls ──────────────────
# In json4s, JsonAST object has no compactRender/prettyRender.
# Files that already have `import com.openbankproject.commons.util.JsonAliases._`
# or specific imports for compactRender/prettyRender just need the call fixed.
JSONAST_COMPACT = re.compile(r'\borg\.json4s\.JsonAST\.compactRender\b')
JSONAST_PRETTY  = re.compile(r'\borg\.json4s\.JsonAST\.prettyRender\b')
JSONAST_COMPACT2 = re.compile(r'\bJsonAST\.compactRender\b')
JSONAST_PRETTY2  = re.compile(r'\bJsonAST\.prettyRender\b')

def fix_jsonast_render_calls(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    new = JSONAST_COMPACT.sub("com.openbankproject.commons.util.JsonAliases.compactRender", txt)
    new = JSONAST_PRETTY.sub("com.openbankproject.commons.util.JsonAliases.prettyRender", new)
    new = JSONAST_COMPACT2.sub("com.openbankproject.commons.util.JsonAliases.compactRender", new)
    new = JSONAST_PRETTY2.sub("com.openbankproject.commons.util.JsonAliases.prettyRender", new)
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False

# ─── Fix 4: JSONFactory6.0.0.scala – add top-level imports ───────────────────
def fix_json_factory6(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    changed = False
    if JSON4S_WILDCARD not in txt:
        txt = insert_after_last_import(txt, JSON4S_WILDCARD)
        changed = True
    if ALIASES_WILDCARD not in txt and RICH_JFIELD_IMPORT not in txt:
        txt = insert_after_last_import(txt, ALIASES_WILDCARD)
        changed = True
    if changed:
        path.write_text(txt, encoding="utf-8")
    return changed

# ─── Fix 5: Helper.scala – add prettyRender import ───────────────────────────
def fix_helper(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    needle = "import com.openbankproject.commons.util.JsonAliases.prettyRender"
    if needle in txt or ALIASES_WILDCARD in txt:
        return False
    new = insert_after_last_import(txt, needle)
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False


def main():
    base = Path("/Users/zhanghongwei/Documents/GitHub-Tower/OBP-API")

    changes = []

    # Fix 1
    p = base / "obp-api/src/main/scala/code/api/OBPRestHelper.scala"
    if fix_obpresthelper(p): changes.append(str(p))

    # Fix 2 – add RichJField where .value/.name used on JField
    for rel in [
        "obp-api/src/main/scala/code/api/dynamic/endpoint/helper/DynamicEndpointHelper.scala",
        "obp-api/src/main/scala/code/api/v1_4_0/JSONFactory1_4_0.scala",
        "obp-api/src/main/scala/code/api/v3_0_0/Http4s300.scala",
    ]:
        p = base / rel
        if add_rich_jfield(p): changes.append(str(p))

    # Fix 3 – replace JsonAST.compactRender/prettyRender calls
    for rel in [
        "obp-api/src/main/scala/code/api/util/APIUtil.scala",
        "obp-api/src/main/scala/code/api/util/ErrorMessages.scala",
        "obp-api/src/main/scala/code/api/v2_1_0/Http4s210.scala",
        "obp-api/src/main/scala/code/api/v4_0_0/Http4s400.scala",
    ]:
        p = base / rel
        if fix_jsonast_render_calls(p): changes.append(str(p))

    # Fix 4 – JSONFactory6.0.0.scala top-level imports
    p = base / "obp-api/src/main/scala/code/api/v6_0_0/JSONFactory6.0.0.scala"
    if fix_json_factory6(p): changes.append(str(p))

    # Fix 5 – Helper.scala prettyRender
    p = base / "obp-api/src/main/scala/code/util/Helper.scala"
    if fix_helper(p): changes.append(str(p))

    print(f"Fixed {len(changes)} files:")
    for c in changes:
        print(f"  {c}")


if __name__ == "__main__":
    main()
