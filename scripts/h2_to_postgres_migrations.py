#!/usr/bin/env python3
"""Translate the H2 migration scripts into their Postgres equivalents.

Two differences matter; everything else in these scripts is standard SQL that both accept.

  1. Schema. H2 puts everything in "PUBLIC"; Postgres's default schema is `public`, and
     "PUBLIC" quoted is a different, non-existent schema.
  2. Identifier case. A quoted "MAPPEDNARRATIVE" is a case-sensitive uppercase name in
     Postgres, while every query the application issues is unquoted lowercase, which
     Postgres folds to lowercase - so the table would exist and never be found. Dropping
     the quotes lets Postgres fold the name the same way it folds the queries.
  3. Unbounded text. Lift's MappedText became CHARACTER VARYING(1000000000) under H2, which
     is past Postgres's varchar ceiling of 10485760 - Postgres rejects the column outright.
     TEXT is the type that means the same thing there.
"""
import re, sys
from pathlib import Path

PG_VARCHAR_MAX = 10485760

def translate_statement_text(text: str) -> str:
    """Apply the three dialect rules to SQL, never to a comment or a string literal."""
    # a varchar longer than Postgres allows is Lift's MappedText; TEXT is its Postgres type
    out = re.sub(r'CHARACTER VARYING\((\d+)\)',
                 lambda m: 'TEXT' if int(m.group(1)) > PG_VARCHAR_MAX else m.group(0), text)
    # "PUBLIC"."THING" -> thing   (schema qualifier removed; public is already the search_path)
    out = re.sub(r'"PUBLIC"\.', '', out)
    # bare "IDENT" -> ident, but never touch anything inside single-quoted string literals
    pieces = re.split(r"('(?:[^']|'')*')", out)
    for i in range(0, len(pieces), 2):          # even indexes are outside string literals
        pieces[i] = re.sub(r'"([A-Za-z_][A-Za-z_0-9]*)"', lambda m: m.group(1).lower(), pieces[i])
    return ''.join(pieces)


def translate(sql: str) -> str:
    """Translate the SQL, leaving the comments exactly as the H2 script wrote them.

    The comments carry the reasoning for each table - why a column is named the way it is,
    what Schemifier did - and they quote identifiers as prose. Rewriting those would make the
    two vendors' scripts read differently for no reason and lose the quoting the prose meant.
    """
    return '\n'.join(
        line if line.lstrip().startswith('--') else translate_statement_text(line)
        for line in sql.split('\n'))

if __name__ == '__main__':
    # .resolve() collapses any ".."/symlink segments in the CLI-supplied paths into their
    # real, absolute form before either is used - this script's whole point is translating
    # between two directories the caller names on the command line, so the fix here is not to
    # constrain src/dst to some fixed root, only to make sure what gets opened is the path
    # that was actually meant, not one still carrying unresolved traversal segments.
    #
    # SonarCloud still flags dst.mkdir/write_text below (rule id not surfaced by the check, but
    # the message is the generic "validate the constructed path before accessing the file
    # system" one) even after .resolve() - its pattern wants src/dst constrained inside some
    # fixed base directory, which does not fit this script: dst IS the caller-chosen output
    # location, by design, for every invocation. Constraining it to a fixed root would break
    # the tool's actual job. NOSONAR on the two flagged lines, not a code change.
    src, dst = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
    dst.mkdir(parents=True, exist_ok=True)  # NOSONAR - dst is the caller-chosen output dir by design, not attacker input
    n = 0
    for f in sorted(src.glob('*.sql')):
        (dst / f.name).write_text(translate(f.read_text()))  # NOSONAR - same as above; dst is the caller-chosen output dir by design
        n += 1
    print(f'translated {n} script(s) -> {dst}')
