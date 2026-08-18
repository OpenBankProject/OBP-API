#!/usr/bin/env python3
"""Every data statement in the Flyway scripts must survive into the Liquibase changelog.

`liquibase generateChangeLog` takes a snapshot of a schema. A snapshot has no way to see a
statement that ran once and left no trace in the catalogue, so the two Flyway scripts that delete
duplicate rows before creating a unique index are invisible to it - the indexes come through, the
DELETEs that made them creatable do not.

That loss is silent and the schema comparison cannot catch it: SchemaEquivalenceTest and
H2SchemaEquivalenceTest both build empty databases, where a de-duplication is a no-op either way.
It only shows up on a real database that holds duplicates, at the moment the unique index fails to
build. Hence a separate check, comparing text rather than schema.

The statements are matched verbatim modulo whitespace, against the Postgres scripts - the changelog
is generated from Postgres and its SQL is written in that dialect.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "obp-api/src/main/resources/db/migration/postgres"
CHANGELOG_DIR = ROOT / "obp-api/src/main/resources/db/changelog"

DML = re.compile(r"^\s*(DELETE|UPDATE|INSERT|MERGE)\b", re.IGNORECASE)


def strip_comments(text: str) -> str:
    return "\n".join(l for l in text.splitlines() if not l.lstrip().startswith("--"))


def normalise(sql: str) -> str:
    """Collapse whitespace so indentation in the YAML does not count as a difference."""
    return re.sub(r"\s+", " ", sql).strip().rstrip(";").lower()


def statements_of(text: str):
    """Split on `;` and keep the ones that change data."""
    for raw in strip_comments(text).split(";"):
        if DML.match(raw):
            yield normalise(raw)


def main() -> int:
    expected = []
    for script in sorted(SCRIPTS.glob("*.sql")):
        for stmt in statements_of(script.read_text()):
            expected.append((script.name, stmt))

    if not expected:
        print("check_changelog_data_migrations: found no data statements in the Flyway scripts - "
              "either they are gone or this script is looking in the wrong place", file=sys.stderr)
        return 1

    changelog = "\n".join(
        p.read_text() for p in sorted(CHANGELOG_DIR.rglob("*.yaml"))
    )
    haystack = normalise(changelog)

    missing = [(name, stmt) for name, stmt in expected if stmt not in haystack]

    print(f"check_changelog_data_migrations: {len(expected)} data statement(s) in the Flyway "
          f"scripts, {len(expected) - len(missing)} present in the changelog")
    if missing:
        print("", file=sys.stderr)
        print("These statements exist in the Flyway scripts and have no counterpart in the "
              "changelog:", file=sys.stderr)
        for name, stmt in missing:
            print(f"  {name}: {stmt[:150]}", file=sys.stderr)
        print("", file=sys.stderr)
        print("Add them as `sql` changesets, ordered before the createIndex they clear the way "
              "for. generateChangeLog cannot see them - it snapshots the schema, and a DELETE "
              "leaves nothing in the catalogue to snapshot.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
