#!/usr/bin/env python3
"""Every baseline changeset must decide for itself whether its object is already there.

`LiquibaseSchemaSetup.bringUpToDate` is a plain `update`, for every state a database can be in when
the application boots - empty, or brought by an existing deployment with tables and no Liquibase
record, or left half-way by a start that was killed. What makes one code path right for all three is
that each changeset in the baseline carries `not tableExists` / `not indexExists` with
`onFail: MARK_RAN`: it records itself without running when its object exists, and runs when it does
not.

Take those away and both of the failures they replaced come back. A blanket `changeLogSync` marks
the de-duplications and the unique indexes they clear the way for as applied on the strength of the
tables being present - and Schemifier never created those indexes, which is why V057 and V116
existed, so the databases that needed them were exactly the ones that skipped them. And a sync
commits row by row, so a start killed during one leaves a partial DATABASECHANGELOG that sent the
next start into a plain `update` over objects that already existed:
`MigrationFailedException ... Index "METRIC_CONSUMERID" already exists`, on that start and every one
after it.

The baseline is generated (scripts/GenerateChangelog.java) and normalised
(scripts/normalise_generated_changelog.py, which inserts these). A regeneration that lost the
insertion step would produce a changelog that looks right, passes every fresh-database test in the
suite, and breaks only on the upgrades this exists to serve - so the invariant is checked here
rather than left to be noticed later.

Preconditions are not part of a changeset's checksum - ChangeSet.generateCheckSum reads only the
changes and the sql visitors - so this costs nothing on databases that have already run them.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHANGELOG_DIR = ROOT / "obp-api/src/main/resources/db/changelog"

# Every changelog that creates or alters schema. The view changelogs are deliberately absent:
# they use createView with replaceIfExists under runOnChange, which is idempotent by
# construction, and db.changelog-dedup.yaml has its own guard
# (check_changelog_data_migrations.py) because its DELETEs cannot be expressed this way.
# Listing them rather than globbing so that adding a changelog is a decision, not an accident -
# a new schema file has to be named here, which is the moment to ask which guard it needs.
SCHEMA_CHANGELOGS = [
    CHANGELOG_DIR / "db.changelog-baseline.yaml",
    CHANGELOG_DIR / "db.changelog-provenance.yaml",
    CHANGELOG_DIR / "db.changelog-develop-merge.yaml",
]


def changesets(text):
    """Each changeset as (id, body), split on the `- changeSet:` marker.

    The indentation is not fixed: the generated baseline puts `- changeSet:` at column 0, while a
    hand-written changelog nests it under `databaseChangeLog:`. Anchoring on column 0, as this did,
    silently matched nothing in the second shape - the guard reported success over a file it had
    not read. Accept either.
    """
    parts = re.split(r"(?m)^[ \t]*- changeSet:\n", text)[1:]
    for body in parts:
        m = re.search(r"^\s*id: (\S+)$", body, re.M)
        yield (m.group(1) if m else "<unnamed>"), body


MASTER_CHANGELOG = CHANGELOG_DIR / "db.changelog-master.yaml"


def unreferenced_changelogs():
    """Every schema changelog must be included by the master changelog, exactly once.

    Liquibase only ever loads what the master includes, so a changelog that is complete, correct
    and guarded by every check above still does nothing at all if its `- include:` is missing --
    and nothing here would have said so: the other checks read each file on its own.

    The failure it produced was a merge that folded two includes into one YAML mapping::

        - include:
            file: db/changelog/db.changelog-provenance.yaml
            file: db/changelog/db.changelog-develop-merge.yaml

    Duplicate keys in a mapping are not an error; the last one wins, so the provenance changelog
    was silently dropped and the tables it creates were never made. That surfaced far away, as
    `Table "CHAT_EMAIL_DIGEST_STATE" not found` from a DELETE in the per-class test reset, with
    every shard aborting before it ran a test.

    Hence also the count check: one `- include:` per `file:` line is what distinguishes the two
    shapes, and it is the shape, not the file list, that went wrong.
    """
    text = MASTER_CHANGELOG.read_text()
    includes = len(re.findall(r"(?m)^\s*- include:$", text))
    files = re.findall(r"(?m)^\s*file: (\S+)$", text)
    problems = []
    if includes != len(files):
        problems.append(f"{MASTER_CHANGELOG.name}: {includes} `- include:` entries but "
                        f"{len(files)} `file:` lines -- two includes sharing one mapping means "
                        f"all but the last are silently ignored")
    for changelog in SCHEMA_CHANGELOGS:
        want = f"db/changelog/{changelog.name}"
        n = files.count(want)
        if n == 0:
            problems.append(f"{changelog.name} is not included by {MASTER_CHANGELOG.name} -- "
                            f"Liquibase will never load it")
        elif n > 1:
            problems.append(f"{changelog.name} is included {n} times by {MASTER_CHANGELOG.name}")
    return problems


def main():
    missing = [c for c in SCHEMA_CHANGELOGS if not c.exists()]
    if missing:
        for c in missing:
            print(f"check_changelog_preconditions: {c} not found", file=sys.stderr)
        return 1

    problems = unreferenced_changelogs()
    checked = 0

    for changelog in SCHEMA_CHANGELOGS:
      where = changelog.name
      for cs_id, body in changesets(changelog.read_text()):
        cs_id = f"{where}::{cs_id}"
        checked += 1
        creates_table = "- createTable:" in body
        creates_index = "- createIndex:" in body
        adds_column = "- addColumn:" in body
        widens_column = "- modifyDataType:" in body
        if not (creates_table or creates_index or adds_column or widens_column):
            problems.append(f"{cs_id}: none of createTable / createIndex / addColumn - this check "
                            f"does not know what precondition it needs; teach it, do not skip it")
            continue

        if "preConditions:" not in body:
            problems.append(f"{cs_id}: no preConditions block")
            continue
        if "onFail: MARK_RAN" not in body:
            problems.append(f"{cs_id}: precondition does not say onFail: MARK_RAN")
        if not re.search(r"^\s*- not:$", body, re.M):
            problems.append(f"{cs_id}: precondition is not negated - it must fire when the object "
                            f"is ABSENT")

        table = re.search(r"^\s*tableName: (\S+)$", body, re.M)
        if creates_index:
            index = re.search(r"^\s*indexName: (\S+)$", body, re.M)
            if not re.search(r"^\s*- indexExists:$", body, re.M):
                problems.append(f"{cs_id}: createIndex needs an indexExists precondition")
            elif index is None:
                problems.append(f"{cs_id}: createIndex has no indexName to check")
            elif body.count(f"indexName: {index.group(1)}") < 2:
                problems.append(f"{cs_id}: the precondition names an index other than "
                                f"{index.group(1)}")
        elif widens_column:
            # Neither tableExists nor columnExists can express "is this column still the old
            # type": both are true before and after. The question is the width, which lives in
            # information_schema.columns.character_maximum_length - NULL once the type is
            # unbounded - so a sqlCheck is the only precondition that can answer it. Require one
            # rather than accepting the changeset unguarded: re-running modifyDataType on a
            # database that has already been widened is what MARK_RAN exists to avoid.
            if not re.search(r"^\s*- sqlCheck:$", body, re.M):
                problems.append(f"{cs_id}: modifyDataType needs a sqlCheck precondition that "
                                f"detects the OLD type (tableExists/columnExists cannot - the "
                                f"column is there either way)")
            elif "information_schema" not in body:
                problems.append(f"{cs_id}: the sqlCheck does not read information_schema, so it "
                                f"cannot be testing the column's current type")
        elif adds_column:
            # A column cannot be checked with tableExists - the table is there either way. The
            # precondition has to name one of the columns the changeset adds, so a database that
            # already went through this change marks it run instead of failing on a duplicate.
            if not re.search(r"^\s*- columnExists:$", body, re.M):
                problems.append(f"{cs_id}: addColumn needs a columnExists precondition")
            else:
                guarded = re.search(r"^\s*columnName: (\S+)$", body, re.M)
                added = set(re.findall(r"\{name: ([a-z_]+),", body))
                if guarded is None:
                    problems.append(f"{cs_id}: columnExists has no columnName to check")
                elif added and guarded.group(1) not in added:
                    problems.append(f"{cs_id}: the precondition checks {guarded.group(1)}, which "
                                    f"is not among the columns this changeset adds")
        else:
            if not re.search(r"^\s*- tableExists:$", body, re.M):
                problems.append(f"{cs_id}: createTable needs a tableExists precondition")
            elif table is None:
                problems.append(f"{cs_id}: createTable has no tableName to check")
            elif body.count(f"tableName: {table.group(1)}") < 2:
                problems.append(f"{cs_id}: the precondition names a table other than "
                                f"{table.group(1)}")

    print(f"check_changelog_preconditions: {checked} schema changeset(s) checked, "
          f"{len(problems)} without a usable existence precondition")
    if problems:
        print("", file=sys.stderr)
        for p in problems[:40]:
            print(f"  {p}", file=sys.stderr)
        if len(problems) > 40:
            print(f"  ... and {len(problems) - 40} more", file=sys.stderr)
        print("", file=sys.stderr)
        print("Each schema changeset needs `preConditions: [onFail: MARK_RAN, not: "
              "[tableExists|indexExists: <the object it creates>]]`. "
              "scripts/normalise_generated_changelog.py inserts them; if the changelog was "
              "regenerated without it, re-run the normaliser rather than adding them by hand.",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
