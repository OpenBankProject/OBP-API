#!/usr/bin/env python3
"""The de-duplications the changelog carries by hand must stay in it.

`liquibase generateChangeLog` takes a snapshot of a schema, and a statement that ran once and left
no trace in the catalogue is invisible to it. Two of the Flyway scripts deleted duplicate rows
before creating a unique index, and reverse-generating the changelog kept the indexes and dropped
the DELETEs that made them creatable. They were written back into
db/changelog/db.changelog-dedup.yaml by hand.

Nothing else would notice them going missing again. The equivalence checks that guarded the
generation compared schemas, and both built empty databases, where a de-duplication is a no-op
either way; the loss only shows up on a real database that holds duplicates, at the moment the
unique index fails to build. So the statements are frozen here, verbatim as they stood in the
Flyway scripts that are now deleted - V057 (the three internal id-mapping tables) and V116 (five
tables whose unique index the earliest migrations left out).

Freezing rather than reading them from somewhere is the point: there is no longer another copy to
compare against, and a check that derives its expectation from the file it is checking would pass
whatever that file said.

DedupChangesetsTest is the other half - it runs the changesets against a table that actually holds
duplicates. This one only asserts they are present.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHANGELOG_DIR = ROOT / "obp-api/src/main/resources/db/changelog"

EXPECTED = [
    # Boot's own de-duplication, moved into the changelog: it ran after the index it existed to
    # precede, and named a table that does not exist. Unlike the eight below these carry no dbms
    # restriction, so they use the ROW_NUMBER() derived-table form MySQL's ERROR 1093 permits -
    # see the changelog file's header.
    "delete from mappedentitlement where id in ( select id from ( select id, row_number() over ( partition by mbankid, muserid, mrolename order by id asc) as rn from mappedentitlement where mbankid is not null and muserid is not null and mrolename is not null ) tmp where rn > 1 )",
    "delete from mapperaccountholders where id in ( select id from ( select id, row_number() over ( partition by user_c, accountbankpermalink, accountpermalink order by id asc) as rn from mapperaccountholders where user_c is not null and accountbankpermalink is not null and accountpermalink is not null ) tmp where rn > 1 )",
    # V057: accountidmapping
    "delete from accountidmapping where maccountplaintextreference is not null and id not in ( select min(id) from accountidmapping where maccountplaintextreference is not null group by maccountplaintextreference )",
    # V057: mappedcustomeridmapping
    "delete from mappedcustomeridmapping where mcustomerplaintextreference is not null and id not in ( select min(id) from mappedcustomeridmapping where mcustomerplaintextreference is not null group by mcustomerplaintextreference )",
    # V057: transactionidmapping
    "delete from transactionidmapping where transactionplaintextreference is not null and id not in ( select min(id) from transactionidmapping where transactionplaintextreference is not null group by transactionplaintextreference )",
    # V116: mappedatm
    "delete from mappedatm where mbankid is not null and matmid is not null and id not in ( select min(id) from mappedatm where mbankid is not null and matmid is not null group by mbankid, matmid )",
    # V116: mappedcomment
    "delete from mappedcomment where apiid is not null and id not in (select min(id) from mappedcomment where apiid is not null group by apiid)",
    # V116: mappedtag
    "delete from mappedtag where tagid is not null and id not in (select min(id) from mappedtag where tagid is not null group by tagid)",
    # V116: mappedtransactionimage
    "delete from mappedtransactionimage where imageid is not null and id not in ( select min(id) from mappedtransactionimage where imageid is not null group by imageid )",
    # V116: consent_item
    "delete from consent_item where consent_item_id is not null and id not in ( select min(id) from consent_item where consent_item_id is not null group by consent_item_id )",
]


def normalise(sql: str) -> str:
    """Collapse whitespace, so YAML indentation does not count as a difference."""
    return re.sub(r"\s+", " ", sql).strip().rstrip(";").lower()


def main() -> int:
    if not CHANGELOG_DIR.is_dir():
        print(f"check_changelog_data_migrations: {CHANGELOG_DIR} does not exist", file=sys.stderr)
        return 1

    changelog = "\n".join(p.read_text() for p in sorted(CHANGELOG_DIR.rglob("*.yaml")))
    haystack = normalise(changelog)

    missing = [stmt for stmt in EXPECTED if stmt not in haystack]

    print(f"check_changelog_data_migrations: {len(EXPECTED)} de-duplication statement(s) expected, "
          f"{len(EXPECTED) - len(missing)} present in the changelog")
    if missing:
        print("", file=sys.stderr)
        print("These statements are missing from the changelog:", file=sys.stderr)
        for stmt in missing:
            print(f"  {stmt[:150]}", file=sys.stderr)
        print("", file=sys.stderr)
        print("They belong in db/changelog/db.changelog-dedup.yaml as `sql` changesets, ordered "
              "before the createIndex they clear the way for. Without them a unique index cannot "
              "be built on a database that already holds duplicate rows - which is every existing "
              "deployment whose constraint was missing.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
