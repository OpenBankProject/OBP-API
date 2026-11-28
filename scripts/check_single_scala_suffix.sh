#!/bin/bash
# Fails when the runtime classpath mixes Scala binary suffixes.
#
# Why this exists: Maven does not unify Scala binary suffixes the way sbt does.
# Once obp-api moves to Scala 3 while obp-commons and lift-persistence stay on
# _2.13 (the for3Use2_13 consumption pattern), nothing stops a transitive
# dependency from dragging a second binary version of the SAME library onto the
# classpath (e.g. scala-xml_2.13 next to scala-xml_3). The JVM then loads
# whichever class it finds first - a LinkageError at best, a silently wrong
# class at worst. That is a correctness and security problem (two versions of a
# validation class = undefined which one runs), so it is checked on every
# commit, not just at release time.
#
# What is allowed:
#   - any number of _2.13 artifacts                      (the permanent keep-list)
#   - any number of _3 artifacts                         (after the Scala 3 flip)
#   - scala-library + scala3-library coexisting          (scala3-library_3
#     depends on scala-library 2.13 by design - that pair is the ONE sanctioned
#     dual entry and is exactly how for3Use2_13 works)
# What fails:
#   - any _2.11 or _2.12 artifact                        (dead binary versions)
#   - the same groupId:base-artifact appearing with BOTH _2.13 and _3 suffixes
#
# Usage: scripts/check_single_scala_suffix.sh   (run from the repo root)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

TREE=$(mktemp)
trap 'rm -f "$TREE"' EXIT

# One absolute outputFile + appendOutput: every reactor module appends its tree
# into the same file. Test scope is included on purpose - a mixed suffix that
# only bites the test classpath still invalidates every test result.
: > "$TREE"
mvn -q dependency:tree -DoutputFile="$TREE" -DappendOutput=true >/dev/null

fail=0

# 1) Dead binary versions must not appear at all.
if grep -E '_2\.1[12]:' "$TREE" | grep -v '^\s*#' > /dev/null; then
  echo "FAIL: _2.11/_2.12 artifacts on the classpath:"
  grep -E '_2\.1[12]:' "$TREE" | sort -u
  fail=1
fi

# 2) No base artifact may appear with both _2.13 and _3.
# Extract "group:artifact-without-suffix" for every suffixed artifact, count suffix variants.
dupes=$(grep -oE '[A-Za-z0-9_.:-]+_(2\.13|3):[a-z]+:' "$TREE" \
  | sed -E 's/_(2\.13|3):[a-z]+:$/ \1/' \
  | sort -u \
  | awk '{print $1}' \
  | sort | uniq -d)
if [ -n "$dupes" ]; then
  echo "FAIL: artifacts present with BOTH _2.13 and _3 suffixes:"
  for d in $dupes; do
    grep -E "${d}_(2\.13|3):" "$TREE" | sort -u
  done
  fail=1
fi

if [ "$fail" = 0 ]; then
  echo "OK: single-suffix audit passed (no _2.11/_2.12; no _2.13/_3 duplicates)"
fi
exit $fail
