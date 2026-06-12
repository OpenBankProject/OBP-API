#!/usr/bin/env python3
"""Fix test-compile errors after json4s migration."""
import re
from pathlib import Path

RICH_JFIELD_IMPORT = "import com.openbankproject.commons.util.JsonAliases.RichJField"
ALIASES_WILDCARD = "import com.openbankproject.commons.util.JsonAliases._"
COMPACT_IMPORT = "import com.openbankproject.commons.util.JsonAliases.compactRender"

def has_top_level_import(text: str, import_str: str) -> bool:
    """Check if import exists as a top-level (non-indented) import."""
    for line in text.splitlines():
        if line.strip() == import_str.strip() and (line.startswith("import ") or line.startswith("  import ")):
            return True
    return False

def insert_after_last_top_import(text: str, new_import: str) -> str:
    lines = text.splitlines()
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import_idx = i
    if last_import_idx >= 0:
        lines.insert(last_import_idx + 1, new_import)
    return "\n".join(lines) + ("\n" if text.endswith("\n") else "")


# ─── Fix 1: SandboxDataLoadingTest.scala – add native Serialization object ───
def fix_sandbox_serialization(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    # The file imports `Serialization.write` but not Serialization object itself.
    # Add `import org.json4s.native.Serialization` so `Serialization.formats(...)` works.
    needle = "import org.json4s.native.Serialization"
    if needle + "\n" in txt or needle + " " in txt:
        return False
    new = txt.replace(
        "import org.json4s.native.Serialization.write",
        "import org.json4s.native.Serialization\nimport org.json4s.native.Serialization.write",
        1
    )
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False


# ─── Fix 2: Add RichJField import to test files ───────────────────────────────
def add_rich_jfield(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    if RICH_JFIELD_IMPORT in txt or ALIASES_WILDCARD in txt:
        return False
    new = insert_after_last_top_import(txt, RICH_JFIELD_IMPORT)
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False


# ─── Fix 3: Add compactRender import to UserTest.scala ────────────────────────
def add_compact_render(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    if COMPACT_IMPORT in txt or ALIASES_WILDCARD in txt:
        return False
    new = insert_after_last_top_import(txt, COMPACT_IMPORT)
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False


# ─── Fix 4: DynamicendPointsTest.scala – fix .values.get("x").head pattern ───
WRONG_VALUES_GET = re.compile(
    r'(\.\s*\\\\?\s*\("([\w_]+)"\))\s*\.values\s*\.get\s*\(\s*"([\w_]+)"\s*\)\s*\.head\s*\.toString'
)

def fix_dynamic_endpoints_values(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    # In json4s, \\("field") returns the value directly, not a Map.
    # Replace .values.get("field").head.toString with .values.toString
    # (since \\("field") already gives us the JValue, .values gives native representation)
    new = WRONG_VALUES_GET.sub(r'\1.values.toString', txt)
    if new != txt:
        path.write_text(new, encoding="utf-8")
        return True
    return False


def main():
    base = Path("/Users/zhanghongwei/Documents/GitHub-Tower/OBP-API")
    test_src = "obp-api/src/test/scala"
    changes = []

    # Fix 1 – SandboxDataLoadingTest Serialization
    p = base / test_src / "code/api/v2_1_0/SandboxDataLoadingTest.scala"
    if fix_sandbox_serialization(p): changes.append(str(p))

    # Fix 2 – RichJField imports
    for rel in [
        "code/api/v5_0_0/Http4s500SystemViewsTest.scala",
        "code/api/v7_0_0/Http4s700RoutesTest.scala",
        "code/api/v7_0_0/Http4s700TransactionTest.scala",
        "code/api/v7_0_0/V7ResourceDocsAggregationTest.scala",
    ]:
        p = base / test_src / rel
        if add_rich_jfield(p): changes.append(str(p))

    # Fix 3 – compactRender in UserTest
    p = base / test_src / "code/api/v3_0_0/UserTest.scala"
    if add_compact_render(p): changes.append(str(p))

    # Fix 4 – DynamicendPointsTest values.get pattern
    p = base / test_src / "code/api/v4_0_0/DynamicendPointsTest.scala"
    if fix_dynamic_endpoints_values(p): changes.append(str(p))

    print(f"Fixed {len(changes)} files:")
    for c in changes:
        print(f"  {c}")


if __name__ == "__main__":
    main()
