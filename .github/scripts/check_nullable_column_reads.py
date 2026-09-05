#!/usr/bin/env python3
"""
Static check: catch a store reading a nullable column into a non-nullable Scala type.

The bug:
    private type Row = (String, Boolean, java.sql.Timestamp)   // column is nullable
    ...
    (selectColumns ++ condition).query[Row].to[List]

Doobie's Get for a non-nullable type throws NonNullableColumnRead on a SQL NULL, and it fails
the whole query rather than the one row - so a single row with a NULL turns a listing endpoint
into a 500. Lift Mapper never failed a read: MappedString handed back null, MappedDateTime
handed back null, MappedBoolean handed back false, MappedLong/Int handed back the field's
declared defaultValue.

Rows holding NULL are not hypothetical. Schemifier added a new field to an existing table with
ALTER TABLE ADD COLUMN and no backfill, so every row written before the field existed has one,
and several stores bind their own writes through Option - they can write a NULL they cannot
then read.

The fix is to bind the column as Option and collapse it the way Mapper's reader did:

    String              -> Option[String]              .orNull
    java.sql.Timestamp  -> Option[java.sql.Timestamp]  read through a `new Date(t.getTime)`
                                                       helper, never handed to json4s as-is
    java.sql.Date       -> Option[java.sql.Date]       same
    Boolean             -> Option[Boolean]             .getOrElse(false)
    Int / Long / BigDecimal -> Option[...]             .getOrElse(<the Lift field's default>)

A column declared NOT NULL in the changelog is fine bound bare, and that is what this check uses
to tell the two apart.

Run from the repo root:
    python3 .github/scripts/check_nullable_column_reads.py

Exits 0 if clean, 1 if violations found.
"""
import collections
import re
import sys
from pathlib import Path

SCALA_ROOT = Path("obp-api/src/main/scala")
CHANGELOG_ROOT = Path("obp-api/src/main/resources/db/changelog")

# Scala types that cannot hold a SQL NULL through Doobie's Get.
NON_NULLABLE = ("String", "Boolean", "Int", "Long", "Double", "BigDecimal",
                "java.sql.Timestamp", "java.sql.Date")


def read_ddl(changelog_root):
    """table -> {column: is_nullable}, from the changelog's createTable changesets.

    Read from the changelog rather than from the H2 CREATE TABLE scripts, and not only because the
    scripts are on their way out. The regex that parsed them matched a column's type with the
    character class `[A-Z0-9_ ()]`, which has no comma in it, so `NUMERIC(16, 10)` never matched
    and five columns - productfee.amount and four of counterpartylimit's - were absent from the map
    entirely. A column that is not in the map cannot be reported, so those five were exempt from
    this check without anything saying so; productfee.amount was in fact bound as a bare BigDecimal
    the whole time, which is a 500 on any row holding a NULL. Structured data does not have that
    failure mode: a column is either declared or it is not.

    Parsed line by line rather than with a YAML library because the workflows run a bare python3
    with no pip install step, and the file is machine-generated with fixed indentation by
    Liquibase's own writer - the shape does not vary. `tableName` follows the column list, since
    the writer emits keys alphabetically.
    """
    tables = {}
    for path in sorted(changelog_root.rglob("*.yaml")):
        lines = path.read_text().splitlines()
        i = 0
        while i < len(lines):
            if lines[i].strip() != "- createTable:":
                i += 1
                continue
            cols = []
            j = i + 1
            while j < len(lines) and not lines[j].strip().startswith("- changeSet:"):
                stripped = lines[j].strip()
                if stripped == "- column:":
                    name, nullable = None, True
                    k = j + 1
                    while k < len(lines):
                        t = lines[k].strip()
                        if t == "- column:" or t.startswith("tableName:"):
                            break
                        if t.startswith("name: "):
                            name = t[len("name: "):].strip()
                        elif t == "nullable: false":
                            nullable = False
                        k += 1
                    if name:
                        cols.append((name.lower(), nullable))
                    j = k
                    continue
                if stripped.startswith("tableName: "):
                    table = stripped[len("tableName: "):].strip().lower()
                    tables.setdefault(table, {}).update(dict(cols))
                    break
                j += 1
            i = j + 1
    return tables


def split_top_level(text):
    """Split on commas that are not inside brackets."""
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def find_violations(path, tables):
    """Yield (line, table, column, scala_type) for each nullable column bound bare."""
    src = path.read_text(errors="ignore")
    if "DoobieUtil" not in src:
        return
    for rm in re.finditer(r"(?:private\s+)?type\s+\w*Row\w*\s*=\s*\(", src):
        start = src.index("(", rm.end() - 1)
        depth, i = 0, start
        while i < len(src):
            if src[i] in "([":
                depth += 1
            elif src[i] in ")]":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        components = split_top_level(src[start + 1:i])
        # The SELECT this Row type reads: the closest one above it.
        select = None
        for sm in re.finditer(r"SELECT\s+(.*?)\s+FROM\s+\"?(\w+)\"?", src[:rm.start()], re.S | re.I):
            select = sm
        if select is None:
            continue
        columns = [c.strip().split()[-1].strip('"').lower()
                   for c in re.sub(r"\s+", " ", select.group(1)).split(",")]
        table = select.group(2).lower()
        # A shape this check cannot read (a join, a computed column, an aliased select) is
        # skipped rather than guessed at - it would only produce noise.
        if table not in tables or len(columns) != len(components):
            continue
        line = src[:rm.start()].count("\n") + 1
        for column, component in zip(columns, components):
            if component.startswith("Option["):
                continue
            if component not in NON_NULLABLE:
                continue
            if tables[table].get(column):
                yield line, table, column, component


def main():
    repo_root = Path(__file__).resolve().parents[2]
    scala_root = repo_root / SCALA_ROOT
    changelog_root = repo_root / CHANGELOG_ROOT
    if not scala_root.exists() or not changelog_root.exists():
        print("ERROR: run this from the repository root", file=sys.stderr)
        return 2

    tables = read_ddl(changelog_root)
    if not tables:
        print(f"ERROR: no createTable changeset found under {CHANGELOG_ROOT}", file=sys.stderr)
        return 2

    by_file = collections.OrderedDict()
    for path in sorted(scala_root.rglob("*.scala")):
        found = list(find_violations(path, tables))
        if found:
            by_file[path.relative_to(repo_root).as_posix()] = found

    total = sum(len(v) for v in by_file.values())
    for rel, found in by_file.items():
        for line, table, column, component in found:
            print(f"{rel}:{line}: {table}.{column} is nullable but is read as {component}")

    if total > 0:
        print(
            f"\n{total} nullable column(s) read into a non-nullable type, in {len(by_file)} "
            f"store(s).\nDoobie throws NonNullableColumnRead on a NULL and fails the whole "
            f"query; Mapper returned the field's default. Bind the column as Option and "
            f"collapse it the way Mapper's reader did - see the module docstring.",
            file=sys.stderr,
        )
        return 1
    print("OK: every nullable column is read through Option.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
