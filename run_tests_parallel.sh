#!/bin/bash
# Local parallel test runner — mirrors CI's test coverage on a single machine.
# Pinned to JDK 25 (Scala 2.12.21+): the suite MUST run on JDK 25, because a
# different JDK produces different results. This script is portable — it does not
# hard-code any one developer's install path. It discovers a JDK 25 across macOS
# and Linux (see resolve_jdk25 below) and ABORTS with guidance if none is found,
# rather than silently falling back to whatever JDK happens to be active.
#
# Override order (first match wins):
#   1. $OBP_JDK25_HOME     — explicit escape hatch for non-standard installs
#   2. $JAVA_HOME          — but only if it already points at a JDK 25
#   3. macOS  /usr/libexec/java_home -v 25   (any vendor: Temurin/Zulu/Oracle/…)
#   4. SDKMAN ~/.sdkman/candidates/java/*25*
#   5. Linux  /usr/lib/jvm/*25*, /opt/java/*25*, etc.
#   6. a `java` already on PATH that reports version 25

# _java_is_25 <java-home-or-binary>: true iff it runs and reports Java 25.
_java_is_25() {
  local jb="$1"
  [[ -d "$jb" ]] && jb="$jb/bin/java"
  [[ -x "$jb" ]] || return 1
  "$jb" -version 2>&1 | grep -qE 'version "25(\.|")'
}

resolve_jdk25() {
  local c cand=()

  # 1. Respect an already-correct JAVA_HOME (explicit user override).
  if [[ -n "${JAVA_HOME:-}" ]] && _java_is_25 "$JAVA_HOME"; then
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
  fi

  # 2. Explicit escape hatch for odd install locations.
  [[ -n "${OBP_JDK25_HOME:-}" ]] && cand+=("$OBP_JDK25_HOME")

  # 3. macOS canonical resolver — vendor-agnostic.
  if [[ -x /usr/libexec/java_home ]]; then
    local mh; mh=$(/usr/libexec/java_home -v 25 2>/dev/null) && [[ -n "$mh" ]] && cand+=("$mh")
  fi

  # 4. SDKMAN-managed JDKs.
  if [[ -d "${HOME:-}/.sdkman/candidates/java" ]]; then
    for c in "$HOME/.sdkman/candidates/java"/*25*/; do [[ -d "$c" ]] && cand+=("${c%/}"); done
  fi

  # 5. Common Linux + macOS-bundle JVM locations (unmatched globs stay literal and
  #    are filtered out by the [[ -d ]] test below).
  for c in /usr/lib/jvm/*25* /usr/lib/jvm/*-25 /usr/lib/jvm/java-25* \
           /opt/java/*25* /Library/Java/JavaVirtualMachines/*25*/Contents/Home; do
    [[ -d "$c" ]] && cand+=("$c")
  done

  # 6. First candidate that actually reports Java 25 wins.
  for c in "${cand[@]}"; do
    if _java_is_25 "$c"; then
      export JAVA_HOME="$c"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  done

  # 7. Last resort: a `java` already on PATH that is version 25. Derive JAVA_HOME
  #    from it so child mvn/JVMs agree on the same JDK.
  if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -qE 'version "25(\.|")'; then
    local jbin; jbin=$(command -v java)
    command -v realpath >/dev/null 2>&1 && jbin=$(realpath "$jbin" 2>/dev/null || echo "$jbin")
    local jhome; jhome=$(cd "$(dirname "$jbin")/.." 2>/dev/null && pwd)
    if [[ -n "$jhome" ]] && _java_is_25 "$jhome"; then
      export JAVA_HOME="$jhome"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  fi

  return 1
}

if ! resolve_jdk25; then
  cat >&2 <<'EOF'
❌ JDK 25 not found. This suite is pinned to JDK 25 (Scala 2.12.21+); running on a
   different JDK produces different results, so the script refuses to continue.
   Install a JDK 25 and retry, e.g.:
     • SDKMAN:  sdk install java 25-tem
     • macOS:   brew install --cask temurin@25   (or download Zulu/Temurin 25)
     • Linux:   install a temurin-25 / java-25-openjdk package
   Or point the script at an existing install:
     OBP_JDK25_HOME=/path/to/jdk-25  ./run_tests_parallel.sh
EOF
  exit 1
fi
echo "JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)  (JAVA_HOME=$JAVA_HOME)"
# CI (build_pull_request.yml / build_container.yml) uses 9 shards across 9 VMs;
# this script uses 4 coarser shards that achieve identical coverage via the
# catch-all mechanism, without exhausting the single local DB connection pool
# (> 4 shards causes connection-pool contention and spurious failures).
# Catch-all logic (build_s4) is a direct port of CI's shard-8 catch-all.
# Usage: ./run_tests_parallel.sh [--shards=4|6]
#
# ── CI step → local equivalent (how cross-machine machinery is replaced) ───
#   CI (multi-machine)                            Local (single machine)
#   ───────────────────────────────────────────  ──────────────────────────────
#   lint: check_test_isolation.py                same (run before tests; abort on fail)
#   compile job: mvn clean install -Pprod        pre-compile once: install obp-commons
#     + upload-artifact(target/)                   into shared ~/.m2 + test-compile
#   test job: download-artifact + touch +          obp-api into shared target/ — a
#     install-file(obp-commons, parentPom)         single machine shares ~/.m2 and
#                                                  target/ natively, so no artifact
#                                                  upload/download/touch is needed
#   each shard on its own VM → mvn test           two dynamic free ports per shard
#     (port/DB isolation for free)                 (OBP_TESTS_PORT + OBP_HTTP4S_TEST_PORT)
#                                                  + scalatest:test (see port block)
#   "Setup props" step writes test.default.props  missing critical props injected via
#                                                  OBP_* env vars (see run_shard)
#   report job: test_speed_report.py             run best-effort after all shards
#
# Notes:
#   * scalatest:test is the correct local stand-in for CI's `mvn test`: CI can
#     run the full Maven lifecycle safely because each shard has its own VM and
#     its own target/. Locally the 4 processes share one target/, so we must
#     pre-compile once and then run scalatest:test (tests only) — otherwise the
#     shards race on copying resources into target/test-classes.
#   * Do NOT use 6 shards: they contend over the single local DB connection pool
#     and produce spurious failures.

mkdir -p test-results/parallel

MVN_OPTS="-Xmx3G -Xss2m -XX:MaxMetaspaceSize=1G"

# Portable `timeout`: GNU coreutils ships it as `timeout` (Linux) but Homebrew on
# macOS installs it prefixed as `gtimeout`. Pick whichever exists.
if command -v timeout >/dev/null 2>&1; then
    TIMEOUT_BIN="timeout"
elif command -v gtimeout >/dev/null 2>&1; then
    TIMEOUT_BIN="gtimeout"
else
    echo "ERROR: neither 'timeout' nor 'gtimeout' found on PATH" >&2
    exit 1
fi

# Maven is required (every dev has it via the project README's setup).
command -v mvn >/dev/null 2>&1 || { echo "ERROR: 'mvn' (Maven) not found on PATH" >&2; exit 1; }

# python3 is used ONLY for the (non-authoritative) test-isolation lint and the
# per-test speed report. The pass/fail verdict itself is computed in pure shell
# (see the surefire audit near the end), so a missing or broken python3 never turns
# a green run red — those two extras just skip with a visible warning.
if command -v python3 >/dev/null 2>&1 && python3 -c 'import sys' >/dev/null 2>&1; then
    HAVE_PY3=1
else
    HAVE_PY3=0
fi

# Cross-checkout mutex: the obp-commons `mvn install` writes to the shared ~/.m2.
# Multiple checkouts starting this script simultaneously race on that write and can
# corrupt each other's JARs (torn ZipFile).  We use an atomic mkdir lock to serialise
# ~/.m2 writes across processes.  The lock is released immediately after the install
# and cleaned up on exit (including crashes) via the EXIT trap.
OBC_LOCK="/tmp/obp-commons-m2-install.lock"
trap 'rm -rf "$OBC_LOCK"' EXIT

SHARDS=4
for arg in "$@"; do
  case $arg in
    --shards=*) SHARDS="${arg#*=}" ;;
  esac
done

# ── Dynamic free-port allocation ──────────────────────────────────────────
# Each shard is its own `mvn scalatest:test` JVM that binds TWO sockets:
#   tests.port       (OBP_TESTS_PORT,       TestServer,       default 8000)
#   http4s.test.port (OBP_HTTP4S_TEST_PORT, Http4sTestServer, default 8087)
# Hardcoded ports collide when several project checkouts run this script at the
# same time. The un-injected 8087 even collides WITHIN one run, because the
# suites that start Http4sTestServer are split across shards (v5_0_0 in shard 2;
# http4sbridge/v7 in shard 4) — both JVMs would bind the default 8087.
# So we pick random high free ports per shard. A fixed base + upward scan can't
# solve simultaneous launches: at fork time no shard has bound yet, so every
# concurrent run picks the same base. Random high range + lsof skip (catches
# ports other checkouts already bound) + in-run dedup avoids that.
PORT_MIN=20000
PORT_MAX=55000
ASSIGNED_PORTS=()   # ports already handed out in THIS run (prevents shard clashes)
ALLOC_PORT=""       # alloc_free_port returns its result here (no subshell — see below)

# alloc_free_port: pick a random free port into the global ALLOC_PORT.
# Returns via a global, NOT stdout, so it must be called WITHOUT $(...): a command
# substitution runs in a subshell, and the ASSIGNED_PORTS append would be lost,
# breaking the in-run dedup. Call as: `alloc_free_port || exit 1; X=$ALLOC_PORT`.
alloc_free_port() {
    local tries=0 p
    while [[ $tries -lt 500 ]]; do
        p=$(( PORT_MIN + RANDOM % (PORT_MAX - PORT_MIN) ))
        if [[ " ${ASSIGNED_PORTS[*]} " != *" $p "* ]] && ! lsof -i :"$p" >/dev/null 2>&1; then
            ASSIGNED_PORTS+=("$p")
            ALLOC_PORT="$p"
            return 0
        fi
        tries=$((tries + 1))
    done
    echo "[FATAL] no free port found in ${PORT_MIN}-${PORT_MAX} after 500 tries" >&2
    return 1
}

# ── Shard definitions ─────────────────────────────────────────────────────
# Deliberately coarser than CI's 9 shards: CI splits each package onto its own
# VM; locally we merge packages to stay within the shared DB connection pool.
# Coverage is identical: the catch-all (build_s4) picks up any package not
# named here, same as CI's shard-8 catch-all.
S1="code.api.v4_0_0"

S2="code.api.v6_0_0,code.api.v5_0_0,code.api.v3_0_0,code.api.v2_1_0,\
code.api.v2_2_0,code.api.v2_0_0,code.api.v1_4_0,code.api.v1_3_0,\
code.api.UKOpenBanking,code.atms,code.branches,code.products,code.crm,\
code.accountHolder,code.entitlement,code.bankaccountcreation,code.bankconnectors,code.container"

S3="code.api.v1_2_1,code.api.ResourceDocs1_4_0,code.api.util,code.api.berlin,\
code.management,code.metrics,code.model,code.views,code.usercustomerlinks,\
code.customer,code.errormessages"

# Shard 4 base — auth/login/connector/util plus any packages not in shards 1-3
S4_BASE="code.api.v5_1_0,code.api.v3_1_0,code.api.http4sbridge,code.api.v7_0_0,\
code.api.Authentication,code.api.dauthTest,code.api.DirectLoginTest,\
code.api.gateWayloginTest,code.api.OBPRestHelperTest,code.util,code.connector"

# ── Shard 4 catch-all: discover every package not covered by shards 1–3 ───
#    (same logic as CI shard-8 catch-all — ensures no new package is silently skipped)
build_s4() {
  local ASSIGNED="$S1 $(echo "$S2" | tr ',' ' ') $(echo "$S3" | tr ',' ' ') $(echo "$S4_BASE" | tr ',' ' ')"
  local ALL_PKGS
  ALL_PKGS=$(find obp-api/src/test/scala obp-commons/src/test/scala \
               -name "*.scala" 2>/dev/null \
             | sed 's|.*/test/scala/||; s|/[^/]*\.scala$||; s|/|.|g' \
             | sort -u)
  local EXTRAS=""
  for pkg in $ALL_PKGS; do
    local covered=false
    for prefix in $ASSIGNED; do
      if [[ "$pkg" == "$prefix" || "$pkg" == "$prefix."* || "$prefix" == "$pkg."* ]]; then
        covered=true; break
      fi
    done
    [[ "$covered" = "false" ]] && EXTRAS="${EXTRAS},${pkg}"
  done
  if [[ -n "$EXTRAS" ]]; then
    echo "  [Shard 4] Catch-all extras: $EXTRAS" >&2
  fi
  echo "${S4_BASE}${EXTRAS}"
}

S4=$(build_s4)

# ── 6-shard definitions (split the original shards 3 and 4; no catch-all) ──
S3_6="code.api.v1_2_1"

S4_6="code.api.ResourceDocs1_4_0,code.api.util,code.api.berlin,\
code.management,code.metrics,code.model,code.views,code.usercustomerlinks,\
code.customer,code.errormessages"

S5_6="code.api.v5_1_0,code.api.v3_1_0,code.api.http4sbridge,code.api.v7_0_0"

S6_6="code.api.Authentication,code.api.dauthTest,code.api.DirectLoginTest,\
code.api.gateWayloginTest,code.api.OBPRestHelperTest,code.util,code.connector"

run_shard() {
    local n=$1
    local filter=$2
    local port=$3          # tests.port       — TestServer       (OBP_TESTS_PORT)
    local http4s_port=$4   # http4s.test.port — Http4sTestServer (OBP_HTTP4S_TEST_PORT)
    local log="test-results/parallel/shard${n}.log"
    echo "[Shard $n] Starting... (tests.port=$port, http4s.test.port=$http4s_port)"
    # OBP_* env vars take priority over the props file (see APIUtil.getPropsValue:
    # property name . -> _, uppercased, prefixed with OBP_). This is the local
    # equivalent of CI's "Setup props" step: the local test.default.props lacks
    # mail.test.mode (CI has it); without it, flows like consent actually open an
    # SMTP socket -> 500 (CI green, local red). We inject OBP_MAIL_TEST_MODE
    # instead of editing props so we don't clobber the user's local DB settings.
    # This env var always outranks a test's setPropsValues("mail.test.mode" -> "false", ...)
    # (see APIUtil.getPropsValue precedence above), so PasswordResetTest's real-SMTP-failure
    # scenario mutates this specific env var for its own scope via code.setup.EnvVarOverride
    # rather than relying on setPropsValues alone.
    # OBP_DYNAMIC_CODE_SANDBOX_PERMISSIONS mirrors CI's dynamic_code_sandbox_permissions
    # props line: without it the dynamic-code sandbox denies reflection/getenv and
    # DynamicResourceDocTest's native-execution scenarios fail locally (CI green, local red).
    # OBP_ALLOW_USER_GENERATED_SCALA_CODE mirrors CI's allow_user_generated_scala_code=true:
    # the kill-switch defaults to false everywhere (including test/dev) with no run-mode
    # fallback, so DynamicUtilTest / ConnectorMethodTest / AbacRuleTests /
    # DynamicResourceDocTest / DynamicMessageDocTest / DynamicCodeKillSwitchTest's ON
    # scenarios need this set explicitly or they fail locally with OBP-50020.
    # -pl obp-commons,obp-api mirrors CI: obp-commons' own util suites run on whichever
    # shard's filter matches com.openbankproject.* (the shard-4 catch-all); on every other
    # shard the filter matches nothing in obp-commons -> 0 tests there.
    # OBP_TESTS_PORT + OBP_HTTP4S_TEST_PORT carry the two dynamically-allocated free
    # ports (both test servers bind a real socket; see the port-allocation block).
    # Tests only, no recompile (the compile already happened in the pre-compile step).
    # ${TIMEOUT_BIN} 1200: hard-kill after 20 min to prevent Pekko non-daemon threads from hanging.
    # OBP_API_INSTANCE_ID feeds Constant.getGlobalCacheNamespacePrefix, which prefixes every
    # Redis cache key with "{api_instance_id}_{runmode}_". Ports and the H2 database are already
    # isolated per run, but Redis is not: every checkout on this machine talks to the same
    # 127.0.0.1:6379. A plain "shard_${n}" is therefore identical in every checkout, so two
    # concurrent runs share one key namespace -- and LocalMappedConnectorTestSetup.wipeTestData
    # deletes that whole namespace after EVERY test. Run A's teardown was wiping run B's live
    # rate-limit counters, once per test. Nothing failed because the rate-limit suites seed their
    # counters immediately before asserting, but that is luck, not isolation. Mixing in the
    # already-allocated random port makes the namespace unique per run, so a teardown only ever
    # deletes its own keys. Keys are still cleaned up: wipeTestData removes the whole prefix at
    # the end of every test, so unique namespaces do not accumulate garbage in the shared Redis.
    MAVEN_OPTS="$MVN_OPTS" \
    OBP_TESTS_PORT="${port}" \
    OBP_HOSTNAME="http://localhost:${port}" \
    OBP_HTTP4S_TEST_PORT="${http4s_port}" \
    OBP_MAIL_TEST_MODE="true" \
    OBP_DYNAMIC_CODE_SANDBOX_PERMISSIONS='[new java.net.NetPermission("specifyStreamHandler"), new java.lang.reflect.ReflectPermission("suppressAccessChecks"), new java.lang.RuntimePermission("getenv.*"), new java.util.PropertyPermission("cglib.useCache", "read"), new java.util.PropertyPermission("net.sf.cglib.test.stressHashCodes", "read"), new java.util.PropertyPermission("cglib.debugLocation", "read"), new java.lang.RuntimePermission("accessDeclaredMembers"), new java.lang.RuntimePermission("getClassLoader")]' \
    OBP_ALLOW_USER_GENERATED_SCALA_CODE="true" \
    OBP_API_INSTANCE_ID="shard_${n}_${port}" \
    "$TIMEOUT_BIN" 1200 mvn scalatest:test -pl obp-commons,obp-api -DfailIfNoTests=false \
        "-DwildcardSuites=${filter}" \
        > "$log" 2>&1
    local rc=$?
    # timeout returns 124 when the JVM was killed. That is only benign when the tests had
    # already finished green and only the JVM shutdown hung (Pekko non-daemon threads) —
    # require proof from the log instead of blindly converting 124 to success.
    if [[ $rc -eq 124 ]]; then
        if grep -q "BUILD SUCCESS" "$log" 2>/dev/null; then
            rc=0
        else
            echo "[Shard $n] ⏱ timeout: JVM killed BEFORE tests completed — counted as failure"
        fi
    fi
    # maven.test.failure.ignore=true (root pom) makes mvn exit 0 even when suites
    # abort or tests fail — the exit code alone is not trustworthy. Scan the log for
    # scalatest's own failure markers (RUN ABORTED / SUITE ABORTED / failed N).
    if [[ $rc -eq 0 ]] && grep -qE '\*\*\* RUN ABORTED \*\*\*|SUITE(S)? ABORTED|Tests: succeeded [0-9]+, failed [1-9]' "$log"; then
        rc=1
    fi
    if [[ $rc -eq 0 ]]; then
        echo "[Shard $n] ✅ BUILD SUCCESS"
    else
        echo "[Shard $n] ❌ BUILD FAILURE — see $log"
    fi
    return $rc
}

START=$(date +%s)

# ── Lint (CI compile job's first step): test-isolation static check; abort on fail ──
# CI always has python3. Locally, if python3 is unavailable we SKIP the lint with a
# visible warning rather than fail — the authoritative test verdict below does not
# depend on it (so a missing tool never masquerades as a lint/test failure).
if [[ "$HAVE_PY3" = "1" ]]; then
  echo "Lint: test-isolation check..."
  if ! python3 .github/scripts/check_test_isolation.py; then
    echo "❌ Lint failed (setPropsValues at class/feature body). Fix before running." >&2
    exit 1
  fi
else
  echo "⚠ Lint SKIPPED: python3 not available (test-isolation static check not run)." >&2
fi
echo ""

# ── Pre-compile (done once, so the 4 shards don't race over a shared target/) ──
# In CI the compile job runs `clean install` to install artifacts into ~/.m2 and
# uploads them; the test job downloads them and re-installs obp-commons / the
# parent POM into the new machine's ~/.m2 via install-file. A single local machine
# shares one ~/.m2, so we only install once — dropping upload/download/touch.
# Key point: each shard runs `scalatest:test -pl obp-api` (no -am), so obp-commons
# is resolved from ~/.m2, not from the reactor. We must install the CURRENT
# obp-commons into ~/.m2, otherwise shards test against a stale obp-commons (the
# old `test-compile -am` only built it in the reactor and never refreshed ~/.m2).
# The obp-commons install holds OBC_LOCK (see top) so concurrent checkouts don't
# race on the shared ~/.m2 write.  The subsequent test-compile writes only to this
# checkout's own target/ and is safe to run in parallel across checkouts.
echo "Pre-compile 1/2: install obp-commons -> ~/.m2 ..."
until mkdir "$OBC_LOCK" 2>/dev/null; do sleep 2; done
MAVEN_OPTS="$MVN_OPTS" \
  mvn install -DskipTests -pl obp-commons -q > test-results/parallel/precompile.log 2>&1
PRECOMPILE_RC=$?
rm -rf "$OBC_LOCK"
if [[ $PRECOMPILE_RC -eq 0 ]]; then
  echo "Pre-compile 2/2: test-compile obp-api -> shared target/ ..."
  MAVEN_OPTS="$MVN_OPTS" \
    mvn test-compile -pl obp-api -q >> test-results/parallel/precompile.log 2>&1
  PRECOMPILE_RC=$?
fi
if [[ $PRECOMPILE_RC -ne 0 ]]; then
  echo "❌ Pre-compile failed — see test-results/parallel/precompile.log" >&2
  tail -25 test-results/parallel/precompile.log >&2
  exit 1
fi
# Fresh verdict basis: stale surefire XMLs from earlier runs would poison both the
# surefire audit below and the speed report (observed: test counts drifting across runs).
rm -rf obp-api/target/surefire-reports obp-commons/target/surefire-reports

echo "Pre-compile done, starting shards..." 
echo ""

if [[ "$SHARDS" = "6" ]]; then
    echo "Starting 6 shards in parallel..."
    echo ""
    # Allocate two free ports per shard BEFORE forking. Sequential calls (not in a
    # subshell) so ASSIGNED_PORTS dedup carries across allocations.
    alloc_free_port || exit 1; P1=$ALLOC_PORT; alloc_free_port || exit 1; H1=$ALLOC_PORT
    alloc_free_port || exit 1; P2=$ALLOC_PORT; alloc_free_port || exit 1; H2=$ALLOC_PORT
    alloc_free_port || exit 1; P3=$ALLOC_PORT; alloc_free_port || exit 1; H3=$ALLOC_PORT
    alloc_free_port || exit 1; P4=$ALLOC_PORT; alloc_free_port || exit 1; H4=$ALLOC_PORT
    alloc_free_port || exit 1; P5=$ALLOC_PORT; alloc_free_port || exit 1; H5=$ALLOC_PORT
    alloc_free_port || exit 1; P6=$ALLOC_PORT; alloc_free_port || exit 1; H6=$ALLOC_PORT
    run_shard 1 "$S1"   "$P1" "$H1" & PID1=$!
    run_shard 2 "$S2"   "$P2" "$H2" & PID2=$!
    run_shard 3 "$S3_6" "$P3" "$H3" & PID3=$!
    run_shard 4 "$S4_6" "$P4" "$H4" & PID4=$!
    run_shard 5 "$S5_6" "$P5" "$H5" & PID5=$!
    run_shard 6 "$S6_6" "$P6" "$H6" & PID6=$!
    wait $PID1; RC1=$?
    wait $PID2; RC2=$?
    wait $PID3; RC3=$?
    wait $PID4; RC4=$?
    wait $PID5; RC5=$?
    wait $PID6; RC6=$?
    RCS=($RC1 $RC2 $RC3 $RC4 $RC5 $RC6)
    TOTAL_SHARDS=6
else
    echo "Starting 4 shards in parallel..."
    echo ""
    # Allocate two free ports per shard BEFORE forking. Sequential calls (not in a
    # subshell) so ASSIGNED_PORTS dedup carries across allocations.
    alloc_free_port || exit 1; P1=$ALLOC_PORT; alloc_free_port || exit 1; H1=$ALLOC_PORT
    alloc_free_port || exit 1; P2=$ALLOC_PORT; alloc_free_port || exit 1; H2=$ALLOC_PORT
    alloc_free_port || exit 1; P3=$ALLOC_PORT; alloc_free_port || exit 1; H3=$ALLOC_PORT
    alloc_free_port || exit 1; P4=$ALLOC_PORT; alloc_free_port || exit 1; H4=$ALLOC_PORT
    run_shard 1 "$S1" "$P1" "$H1" & PID1=$!
    run_shard 2 "$S2" "$P2" "$H2" & PID2=$!
    run_shard 3 "$S3" "$P3" "$H3" & PID3=$!
    run_shard 4 "$S4" "$P4" "$H4" & PID4=$!
    wait $PID1; RC1=$?
    wait $PID2; RC2=$?
    wait $PID3; RC3=$?
    wait $PID4; RC4=$?
    RCS=($RC1 $RC2 $RC3 $RC4)
    TOTAL_SHARDS=4
fi

END=$(date +%s)
ELAPSED=$(( (END - START) / 60 ))
SEC=$(( (END - START) % 60 ))

echo ""
echo "══════════════════════════════════════"
echo "All ${TOTAL_SHARDS} shards done in ${ELAPSED}m ${SEC}s"
echo ""

for (( n=1; n<=TOTAL_SHARDS; n++ )); do
    log="test-results/parallel/shard${n}.log"
    total_time=$(grep "Total time:" "$log" 2>/dev/null | tail -1 | sed 's/.*Total time: *//')
    # CI parity ("RECOMPILATION CHECK"): after pre-compile, shards should not
    # recompile; if they do, the artifacts weren't reused — warn.
    if grep -q "Compiling " "$log" 2>/dev/null; then
        echo "  Shard $n: $total_time  ⚠ recompilation detected (artifacts not reused)"
    else
        echo "  Shard $n: $total_time"
    fi
done

OVERALL_RC=0
for rc in "${RCS[@]}"; do
    [[ $rc -ne 0 ]] && OVERALL_RC=1
done

# ── CI parity ("Report failing tests" step): extract failures for failed shards ──
if [[ $OVERALL_RC -ne 0 ]]; then
    echo ""
    echo "── Failure diagnostics (CI-style report) ───────────"
    for (( n=1; n<=TOTAL_SHARDS; n++ )); do
        [[ "${RCS[$((n-1))]}" -eq 0 ]] && continue
        log="test-results/parallel/shard${n}.log"
        echo ""
        echo "### Shard $n ($log) ###"
        echo "  -- bridge / uncaught exceptions --"
        grep -n "\[BRIDGE\] Exception\|Uncaught exception in dispatch\|requestScopeProxy=" \
            "$log" 2>/dev/null | head -20 || true
        echo "  -- failing scenarios (*** FAILED ***) --"
        grep -n "\*\*\* FAILED \*\*\*" "$log" 2>/dev/null | head -40 || true
    done
fi

# ── Authoritative verdict: audit the surefire XMLs in PURE SHELL (awk/grep) so the
#    pass/fail verdict does NOT depend on python3 or its XML library being healthy —
#    that dependency previously caused a false FAIL on a machine whose python3/expat
#    was broken. Per-shard mvn exit codes can lie (timeout-killed JVMs, plugins
#    swallowing failures), so any failure/error recorded in the reports fails the run
#    regardless of what the shards returned. A file with no parseable
#    <testsuite tests="…"> root (truncated: JVM killed mid-write) counts as broken.
_SF_DIGITS='[0-9]+'   # shared pattern so the digit-match regex isn't repeated per attribute

# _sf_attr <head> <attr-name>: print the integer value of the first attr="N" match, or
# nothing if the attribute isn't present.
_sf_attr() {
    local head="$1" attr="$2"
    printf '%s' "$head" | grep -oE "${attr}=\"${_SF_DIGITS}\"" | head -1 | grep -oE "$_SF_DIGITS"
    return $?
}

SF_TOTAL=0; SF_FAIL=0; SF_ERR=0; SF_SKIP=0; SF_BROKEN=0
SF_BAD=()
_sf_files=$(find obp-api/target/surefire-reports obp-commons/target/surefire-reports \
              -name 'TEST-*.xml' 2>/dev/null)
while IFS= read -r _f; do
    [[ -z "$_f" ]] && continue
    # The <testsuite …> root tag (carrying tests/failures/errors/skipped) sits at the
    # very top of the file, before <properties>. Read only the head so we never match
    # the same-looking text inside <system-out> CDATA. Attributes may be split across
    # lines, so grab the first match of each independently (attribute-order-agnostic).
    _head=$(head -c 8000 "$_f" 2>/dev/null)
    _t=$(_sf_attr "$_head" tests)
    if [[ -z "$_t" ]]; then
        SF_BROKEN=$((SF_BROKEN+1))
        SF_BAD+=("$(basename "$_f"): UNPARSEABLE report (JVM killed mid-write?)")
        continue
    fi
    _fa=$(_sf_attr "$_head" failures); _fa=${_fa:-0}
    _e=$(_sf_attr "$_head" errors);    _e=${_e:-0}
    _sk=$(_sf_attr "$_head" skipped);  _sk=${_sk:-0}
    SF_TOTAL=$((SF_TOTAL+_t)); SF_FAIL=$((SF_FAIL+_fa)); SF_ERR=$((SF_ERR+_e)); SF_SKIP=$((SF_SKIP+_sk))
    if [[ $_fa -ne 0 || $_e -ne 0 ]]; then
        SF_BAD+=("$(basename "$_f" | sed 's/^TEST-//; s/\.xml$//'): $_fa failed, $_e errors")
    fi
done <<< "$_sf_files"
echo ""
echo "Surefire audit: ${SF_TOTAL} tests, ${SF_FAIL} failures, ${SF_ERR} errors, ${SF_SKIP} skipped/canceled"
if [[ "$SF_FAIL" != "0" ]] || [[ "$SF_ERR" != "0" ]] || [[ "$SF_BROKEN" != "0" ]]; then
    [[ ${#SF_BAD[@]} -gt 0 ]] && printf '  ✗ %s\n' "${SF_BAD[@]}"
    OVERALL_RC=1
fi
# Zero-test floor: -DfailIfNoTests=false means a broken wildcardSuites filter runs nothing
# and "passes". The suite has ~2900 tests; a total far below that means shards ran
# near-empty — fail instead of reporting a hollow green.
if [[ "${SF_TOTAL:-0}" -lt 2000 ]]; then
    echo "  ✗ suspicious total: only ${SF_TOTAL:-0} tests ran (< 2000 floor) — filter/discovery regression?"
    OVERALL_RC=1
fi

# ── CI parity (report job): http4s vs Lift per-test speed table; best-effort, ──
#    does not affect the exit code.
REPORTS_DIR="obp-api/target/surefire-reports"
if [[ "$HAVE_PY3" = "1" ]] && ls "$REPORTS_DIR"/*.xml >/dev/null 2>&1; then
    echo ""
    echo "── Per-test speed (CI report-job equivalent) ───────"
    python3 .github/scripts/test_speed_report.py "$REPORTS_DIR" 2>/dev/null \
        || echo "  (speed report skipped)"
fi

# Final verdict LAST so `tail -N` always captures it, plus a machine-readable file
# that survives any piping of stdout (`./run.sh | tail` reports tail's exit code).
echo ""
if [[ $OVERALL_RC -eq 0 ]]; then
    echo "✅ ALL SHARDS PASSED"
    echo "PASS" > test-results/parallel/RESULT
else
    echo "❌ SOME SHARDS FAILED — check test-results/parallel/shardN.log"
    echo "FAIL" > test-results/parallel/RESULT
fi

exit $OVERALL_RC