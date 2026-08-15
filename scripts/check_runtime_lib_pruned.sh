#!/bin/bash
# Fails when target/lib holds a jar that is no longer a runtime dependency, or holds the same
# artifact at two versions.
#
# Why: the thin jar's manifest puts every jar in lib/ on the runtime classpath, and
# maven-dependency-plugin's copy-dependencies only ever ADDS to that directory. On an
# incremental build a dropped dependency therefore keeps being loaded - which silently undoes
# dependency removals, including ones done for a CVE. Observed on this branch: avro-1.11.4.jar
# stayed in lib/ after avro was removed (CVE-2024-47561, CVSS 9.8 RCE), and scalameta 4.1.12
# and 4.13.6 sat there together after the upgrade.
#
# maven-clean-plugin's prune-runtime-lib execution in obp-api/pom.xml is the fix; this is the
# check that it is still working.
#
# Usage: scripts/check_runtime_lib_pruned.sh   (after a package build)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

LIB="obp-api/target/lib"
if [ ! -d "$LIB" ]; then
  echo "SKIP: $LIB does not exist - run a package build first"
  exit 0
fi

TREE=$(mktemp)
trap 'rm -f "$TREE"' EXIT
mvn -q -pl obp-api dependency:list -DincludeScope=runtime -DoutputFile="$TREE" -DappendOutput=false >/dev/null

# dependency:list prints "group:artifact:jar:version:scope", or with a classifier
# "group:artifact:jar:classifier:version:scope" - and classifiers ARE in use here
# (org.jline:jline:jdk8, com.github.jnr:jffi:native), so both arities must be handled or those
# two are reported as stale every run. copy-dependencies names the file artifact-version.jar
# and artifact-version-classifier.jar respectively.
expected=$(sed -E 's/\x1b\[[0-9;]*m//g; s/ --.*$//' "$TREE" \
  | grep -oE '^ +[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:jar:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+(:[a-z]+)?' \
  | awk -F: '{
      if (NF >= 6) print $2 "-" $5 "-" $4 ".jar";   # classified
      else print $2 "-" $4 ".jar";                  # plain
    }' | sort -u)

fail=0
for jar in "$LIB"/*.jar; do
  [ -e "$jar" ] || continue
  name=$(basename "$jar")
  if ! grep -qxF "$name" <<<"$expected"; then
    echo "FAIL: $name is in $LIB but is not a runtime dependency (stale - would still be on the classpath)"
    fail=1
  fi
done

# No separate "same artifact twice" check: Maven resolves exactly one version per
# groupId:artifactId, so a pruned-and-refilled lib/ cannot contain two versions of the same
# artifact - if two are there, at least one is stale and the check above already named it.
# A filename-based duplicate heuristic is worse than useless here: it cannot see groupIds, so
# it reads legitimately-coexisting lines such as io.swagger:swagger-parser 1.x and the v3
# swagger-parser as one artifact at two versions.

[ "$fail" = 0 ] && echo "OK: every jar in runtime lib/ is a current runtime dependency"
exit $fail
