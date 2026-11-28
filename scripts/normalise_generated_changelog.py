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


def existence_preconditions(lines):
    """Give each changeset the precondition that lets it decide whether its object is already there.

    `not tableExists` / `not indexExists` with `onFail: MARK_RAN`, so a changeset whose object
    exists records itself without running. That is what lets `bringUpToDate` be a plain `update`
    for a fresh database, for one an existing deployment brings with tables and no Liquibase
    record, and for one left half-way by a start that was killed - see LiquibaseSchemaSetup.

    Preconditions are not part of a changeset's checksum (ChangeSet.generateCheckSum reads only
    the changes and the sql visitors), so adding them to changesets a deployment has already run
    does not invalidate anything.

    Runs after stable_ids, so the id already names the kind, and the object's own name is read from
    the change body rather than from that derived slug.
    """
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        m = re.match(r"^(\s*)id: create-(table|index)-(\S+)$", line)
        if not m:
            i += 1
            continue
        indent, kind = m.group(1), m.group(2)
        if not re.match(r"^\s*author: ", lines[i + 1]):
            raise SystemExit(f"expected an author line after {line!r}")
        out.append(lines[i + 1])
        body = "".join(lines[i:i + 400]).split("\n- changeSet:")[0]
        table = re.search(r"^\s*tableName: (\S+)$", body, re.M)
        if table is None:
            raise SystemExit(f"no tableName in the changeset at {line!r}")
        out.append(f"{indent}preConditions:\n")
        out.append(f"{indent}  - onFail: MARK_RAN\n")
        out.append(f"{indent}  - not:\n")
        if kind == "table":
            out.append(f"{indent}      - tableExists:\n")
            out.append(f"{indent}          tableName: {table.group(1)}\n")
        else:
            index = re.search(r"^\s*indexName: (\S+)$", body, re.M)
            if index is None:
                raise SystemExit(f"no indexName in the changeset at {line!r}")
            out.append(f"{indent}      - indexExists:\n")
            out.append(f"{indent}          indexName: {index.group(1)}\n")
            out.append(f"{indent}          tableName: {table.group(1)}\n")
        i += 2
    return out


def main():
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <generated.yaml> <out.yaml>")
    # .resolve() collapses any ".."/symlink segments in the CLI-supplied paths before either
    # is opened - src/dst are meant to be whatever generated.yaml/out.yaml the caller names, so
    # the fix is making sure the path actually opened is the one meant, not constraining them
    # to some fixed root.
    #
    # SonarCloud still flags the two opens below even after .resolve() - its pattern wants
    # src/dst constrained inside some fixed base directory, which doesn't fit this script: both
    # ARE the caller-chosen locations, by design, for every invocation (see the usage message
    # above). Constraining them to a fixed root would break the tool's actual job. NOSONAR on
    # the two flagged lines, not a code change.
    src, dst = str(Path(sys.argv[1]).resolve()), str(Path(sys.argv[2]).resolve())
    lines = open(src).readlines()  # NOSONAR - src is the caller-chosen input file by design, not attacker input

    lines = stable_ids(lines)
    lines = existence_preconditions(lines)

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

    open(dst, "w").writelines(out)  # NOSONAR - dst is the caller-chosen output file by design, not attacker input
    print(f"{len(seen)} changesets written to {dst}")


if __name__ == "__main__":
    main()
