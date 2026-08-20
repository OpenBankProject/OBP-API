#!/usr/bin/env python3
"""Turn `liquibase generateChangeLog` output into a changelog fit to commit.

The baseline changelog is generated from a Postgres database built by the Flyway scripts, so that
it inherits Schemifier's exported DDL rather than somebody's idea of what the schema should be.
Generate it with:

    java -cp obp-api/target/classes:$(mvn -pl obp-api -am dependency:build-classpath \
             -Dmdep.outputFile=/dev/stdout -q) scripts/GenerateChangelog.java <db> <out.yaml>

The raw output is not committable as-is, for three reasons, and this fixes each one:

1. **Changeset ids are a wall-clock timestamp** (`1787090787246-1`). The id is the identity in
   DATABASECHANGELOG, so regenerating the file would give every changeset a new one and Liquibase
   would try to apply the whole schema again to a database that already has it. They become names
   derived from what the changeset creates, which regeneration reproduces exactly.

2. **The author is whoever ran the generator** (`zhanghongwei (generated)`). Also part of the
   identity, and it says nothing useful about a shared file.

3. **Three type names need rewriting.** Two are Postgres's internal spelling rather than a portable one. The snapshot
   reports what the catalogue holds, not what the script asked for: `DOUBLE` comes back as
   `FLOAT8` and `TIMESTAMP` as `TIMESTAMP WITHOUT TIME ZONE`. Neither belongs in a changelog whose
   entire purpose is that one description generates every vendor's dialect. The third, `TEXT`, has
   no portable spelling at all and becomes a property the master changelog defines per vendor -
   see the comment on TYPE_RENAMES.

What is deliberately NOT patched here: the absence of `defaultValue`. The Flyway scripts declare
no column defaults at all - the defaults live in the application (`isActive.getOrElse(true)` and
friends), matching the Mapper fields, which only ever applied through the Mapper API. Checked with
grep before concluding the generator had dropped them.
"""
import re
import sys
from pathlib import Path

# Postgres's catalogue spelling -> the portable name the scripts actually used.
TYPE_RENAMES = {
    "TIMESTAMP WITHOUT TIME ZONE": "TIMESTAMP",
    "FLOAT8": "DOUBLE",
    # Unbounded text has no single portable spelling, so it becomes a per-vendor property that the
    # master changelog defines. Liquibase's own TEXT maps to CHARACTER LARGE OBJECT on H2, and the
    # H2 scripts declare CHARACTER VARYING(1000000000) - a CLOB rather than a varchar, read through
    # a different JDBC path, on 36 columns of the database the whole test suite runs against.
    # Generating from Postgres is what loses the distinction: the length is already gone by then,
    # because the H2 -> Postgres translation had to turn it into TEXT to get under Postgres's
    # varchar ceiling.
    "TEXT": "${text.type}",
}

AUTHOR = "obp"


def stable_ids(lines):
    """Replace each timestamped id with one derived from the object the changeset creates.

    The name is read from the `tableName:`/`indexName:` a few lines below the id, which is where
    generateChangeLog puts it for both change types it emits.
    """
    out = []
    for i, line in enumerate(lines):
        m = re.match(r"^(\s*)id: \d+-\d+$", line)
        if not m:
            out.append(line)
            continue
        indent = m.group(1)
        name = None
        kind = None
        # Look ahead within this changeset for the object's name.
        for ahead in lines[i:i + 400]:
            if re.match(r"^\s*id: \d+-\d+$", ahead) and ahead is not line:
                break
            im = re.match(r"^\s*indexName: (\S+)$", ahead)
            if im:
                name, kind = im.group(1), "create-index"
                break
            tm = re.match(r"^\s*tableName: (\S+)$", ahead)
            if tm and kind is None:
                name, kind = tm.group(1), "create-table"
                break
        if name is None:
            raise SystemExit(f"could not name the changeset at line {i + 1}: {line!r}")
        out.append(f"{indent}id: {kind}-{name}\n")
    return out


def main():
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <generated.yaml> <out.yaml>")
    # .resolve() collapses any ".."/symlink segments in the CLI-supplied paths before either
    # is opened - src/dst are meant to be whatever generated.yaml/out.yaml the caller names, so
    # the fix is making sure the path actually opened is the one meant, not constraining them
    # to some fixed root.
    src, dst = str(Path(sys.argv[1]).resolve()), str(Path(sys.argv[2]).resolve())
    lines = open(src).readlines()

    lines = stable_ids(lines)

    out = []
    for line in lines:
        line = re.sub(r"^(\s*)author: .*$", rf"\1author: {AUTHOR}", line)
        for pg, portable in TYPE_RENAMES.items():
            line = line.replace(f"type: {pg}", f"type: {portable}")
        out.append(line)

    seen = set()
    for line in out:
        m = re.match(r"^\s*id: (\S+)$", line)
        if m:
            if m.group(1) in seen:
                raise SystemExit(f"duplicate changeset id after renaming: {m.group(1)}")
            seen.add(m.group(1))

    open(dst, "w").writelines(out)
    print(f"{len(seen)} changesets written to {dst}")


if __name__ == "__main__":
    main()
