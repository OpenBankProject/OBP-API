#!/bin/bash
# Local parallel test runner — mirrors CI's test coverage on a single machine.
# Pinned to the project's JDK: the suite MUST run on it, because a different JDK
# produces different results. JDK selection lives in scripts/java_env.sh, which is
# shared with the build/run scripts so all three agree, reads the required version
# from pom.xml's <java.version>, and ABORTS with guidance if it is missing rather
# than silently falling back to whatever JDK happens to be active. See that file
# for the full override order (JAVA_HOME, $OBP_JDK_HOME, java_home, SDKMAN, …).

# Shared with flushall_build_and_run.sh / flushall_fast_build_and_run.sh so the
# build, the server and the tests can never run on different JDKs. Aborts if the
# required JDK is absent.
. "$(dirname "${BASH_SOURCE[0]}")/scripts/java_env.sh"
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
# and cleaned up on exit (including crashes) via an ownership-checked EXIT trap.
# This checkout's absolute path, used to keep the orphan reaper below to test JVMs from here.
CHECKOUT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OBC_LOCK="/tmp/obp-commons-m2-install.lock"
# Armed here, and ownership-checked: it removes the directory only when the pid recorded inside is
# this process. Armed unconditionally it would delete a lock another run holds - while waiting for
# one, or in the instant after releasing ours and before a disarm. Armed only after the mkdir it
# would miss a signal in between. Checking the pid is what makes both ends safe.
trap '[[ "$(cat "$OBC_LOCK/pid" 2>/dev/null)" == "$$" ]] && rm -rf "$OBC_LOCK"' EXIT

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
    # OBP_BERLIN_GROUP_V1_3_ALIAS_PATH mirrors CI's berlin_group_v1_3_alias_path=0.6/v1
    # (injected by both workflows' "Setup props" step): without it, OBP_BERLIN_GROUP_1_3_Alias
    # reports an empty ScannedApiVersion("","","") and ScannedApis.isAddressable filters it
    # out of versionMapScannedApis entirely, so ApiVersionUtilsTest's `versions.length shouldBe(21)`
    # sees only 20 (CI green, local red) -- confirmed by reproducing the failure on a clean
    # checkout with this var unset, then reproducing the pass with it set.
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
    OBP_DYNAMIC_CODE_SANDBOX_PERMISSIONS='[new java.net.NetPermission("specifyStreamHandler"), new java.lang.reflect.ReflectPermission("suppressAccessChecks"), new java.lang.RuntimePermission("getenv.*"), new java.lang.RuntimePermission("accessDeclaredMembers"), new java.lang.RuntimePermission("getClassLoader")]' \
    OBP_ALLOW_USER_GENERATED_SCALA_CODE="true" \
    OBP_BERLIN_GROUP_V1_3_ALIAS_PATH="0.6/v1" \
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

# scalatest-maven-plugin runs with forkMode=once, so a shard is two JVMs: mvn, and the test JVM it
# forks. Pekko's non-daemon threads keep that fork alive after the tests finish; mvn exits anyway,
# the fork is reparented and nothing has owned it since. The `timeout` above never sees this - it
# fires only when mvn itself overruns, and on the ordinary path mvn returns 0.
#
# Left alone they accumulate: five were found alive six to ten hours after their runs, one of them
# holding port 8080, where it answered a verification probe with a build eight commits old and no
# error anywhere. Matching on both -Drun.mode=test (the plugin's own argLine) and this checkout's
# basedir keeps this to test JVMs from this worktree - a dev server started by hand has no
# run.mode=test, and another checkout has another basedir.
# Called once, after every shard has been waited on - never from run_shard. The shards run in
# parallel and share this matcher, so reaping from inside one of them would kill the test JVMs the
# others are still using.
reap_orphaned_test_jvms() {
    local pids pid
    # These select what to kill, so neither half may treat a dot as "any character". grep takes -F.
    # pgrep has no fixed-string option - its pattern is an extended regex either way, which
    # `pgrep -f "sleep 3.0"` matching a running `sleep 300` demonstrates - so the dots are escaped.
    pids=$(pgrep -f -- "-Drun\.mode=test" 2>/dev/null | while read -r pid; do
        ps -o command= -p "$pid" 2>/dev/null | grep -qF -- "$CHECKOUT_ROOT" && echo "$pid"
    done)
    [[ -z "$pids" ]] && return 0
    echo "Reaping orphaned test JVM(s) left by this run: $(echo $pids | tr '\n' ' ')"
    # shellcheck disable=SC2086
    kill $pids 2>/dev/null
    sleep 3
    for pid in $pids; do kill -9 "$pid" 2>/dev/null; done
    return 0
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
echo "Pre-compile 1/2: install obp-parent + obp-commons -> ~/.m2 ..."
# The lock records its holder's PID so a run killed before it could clean up does not wedge every
# later one. Two ways it can be stale: the recorded PID is gone, or there is no PID at all - the
# holder died between the mkdir and the write below, which is the case that used to be unreclaimable.
# The second gets a grace period, since a live holder is only momentarily in that state. Every path
# through the loop sleeps and advances the counter, so a removal that does not take cannot spin.
OBC_LOCK_WAITED=0
OBC_LOCK_NO_PID_GRACE=30
until mkdir "$OBC_LOCK" 2>/dev/null; do
  OBC_LOCK_PID="$(cat "$OBC_LOCK/pid" 2>/dev/null || true)"
  OBC_LOCK_STALE=""
  if [[ -n "$OBC_LOCK_PID" ]]; then
    # ps -p, not kill -0: kill -0 fails with EPERM for a live process owned by another user as
    # well as with ESRCH for one that is gone, so it reads somebody else's running build as dead.
    ps -p "$OBC_LOCK_PID" >/dev/null 2>&1 || OBC_LOCK_STALE="held by dead PID $OBC_LOCK_PID"
  elif (( OBC_LOCK_WAITED >= OBC_LOCK_NO_PID_GRACE )); then
    OBC_LOCK_STALE="has recorded no holder for ${OBC_LOCK_NO_PID_GRACE}s"
  fi

  if [[ -n "$OBC_LOCK_STALE" ]]; then
    echo "  Lock $OBC_LOCK_STALE; removing it."
    rm -rf "$OBC_LOCK" 2>/dev/null || true
    if [[ -d "$OBC_LOCK" ]]; then
      echo "Cannot remove stale $OBC_LOCK - check its owner and permissions." >&2
      exit 1
    fi
  fi

  if (( OBC_LOCK_WAITED >= 600 )); then
    echo "Timed out after 10m waiting for $OBC_LOCK (held by PID ${OBC_LOCK_PID:-unknown})." >&2
    exit 1
  fi
  sleep 2
  OBC_LOCK_WAITED=$(( OBC_LOCK_WAITED + 2 ))
done
echo $$ > "$OBC_LOCK/pid"
# -am so the parent pom is installed alongside obp-commons. Installing the module alone leaves
# whatever obp-parent is already in ~/.m2, and obp-api resolves its dependencies through that pom -
# scala.version, lift.version and the rest live there. A stale parent therefore pulls _2.12
# artifacts onto the classpath next to a freshly built obp-commons, and because com.tesobe:obp-commons
# carries no Scala suffix nothing detects the mismatch: the build succeeds and the tests die at run
# time with ClassNotFoundException: scala.Serializable.
MAVEN_OPTS="$MVN_OPTS" \
  mvn install -DskipTests -pl obp-commons -am -q > test-results/parallel/precompile.log 2>&1
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

# PropGatedPublicEndpoint: JsonSchemaValidationPublicPropTrueTest /
# AuthenticationTypeValidationPublicPropTrueTest need
# read_json_schema_validation_requires_role / read_authentication_type_validation_requires_role
# forced true. That value is baked into Http4s400's ResourceDoc error list at object-init time, so
# it needs its own JVM (pom.xml's default tagsToExclude skips this tag in every shard above, which
# all boot with the props unset i.e. false). Run sequentially, after the main shards, so it shares
# no DB connection pool contention with them; its surefire XML lands in the same
# obp-api/target/surefire-reports directory the audit below already scans, so no separate
# reporting path is needed.
# Shard wall-clock is taken before the sequential step below, so the figure printed
# next to the per-shard timings covers the same work they do.
SHARDS_END=$(date +%s)

echo ""
echo "Running PropGatedPublicEndpoint tests (prop=true JVM)..."
# Port allocation failure records a failing RC instead of exiting: the four shards
# have already run by this point, and a hard exit here would skip the summary,
# the per-shard failure diagnostics and the Surefire audit below — throwing away
# their results over an unrelated transient.
if ! alloc_free_port; then
    echo "  ✗ PropGatedPublicEndpoint tests skipped — could not allocate a test port"
    PROP_RC=1
else
    PROP_PORT=$ALLOC_PORT
    if ! alloc_free_port; then
        echo "  ✗ PropGatedPublicEndpoint tests skipped — could not allocate an http4s port"
        PROP_RC=1
    else
        PROP_HTTP4S_PORT=$ALLOC_PORT
        MAVEN_OPTS="$MVN_OPTS" \
        OBP_TESTS_PORT="${PROP_PORT}" \
        OBP_HOSTNAME="http://localhost:${PROP_PORT}" \
        OBP_HTTP4S_TEST_PORT="${PROP_HTTP4S_PORT}" \
        OBP_MAIL_TEST_MODE="true" \
        OBP_DYNAMIC_CODE_SANDBOX_PERMISSIONS='[new java.net.NetPermission("specifyStreamHandler"), new java.lang.reflect.ReflectPermission("suppressAccessChecks"), new java.lang.RuntimePermission("getenv.*"), new java.lang.RuntimePermission("accessDeclaredMembers"), new java.lang.RuntimePermission("getClassLoader")]' \
        OBP_ALLOW_USER_GENERATED_SCALA_CODE="true" \
        OBP_BERLIN_GROUP_V1_3_ALIAS_PATH="0.6/v1" \
        OBP_API_INSTANCE_ID="prop_gated_${PROP_PORT}" \
        OBP_READ_JSON_SCHEMA_VALIDATION_REQUIRES_ROLE="true" \
        OBP_READ_AUTHENTICATION_TYPE_VALIDATION_REQUIRES_ROLE="true" \
        "$TIMEOUT_BIN" 300 mvn scalatest:test -pl obp-api -DfailIfNoTests=false \
            "-DwildcardSuites=code.api.v4_0_0.JsonSchemaValidationPublicPropTrueTest,code.api.v4_0_0.AuthenticationTypeValidationPublicPropTrueTest" \
            -DtagsToInclude=PropGatedPublicEndpoint -Dtest.tagsToExclude= \
            > test-results/parallel/prop_gated_public_endpoint.log 2>&1
        PROP_RC=$?
        if [[ $PROP_RC -ne 0 ]]; then
            echo "  ✗ PropGatedPublicEndpoint tests failed — see test-results/parallel/prop_gated_public_endpoint.log"
        else
            echo "  ✓ PropGatedPublicEndpoint tests passed"
        fi
    fi
fi
RCS+=($PROP_RC)

END=$(date +%s)
ELAPSED=$(( (SHARDS_END - START) / 60 ))
SEC=$(( (SHARDS_END - START) % 60 ))
TOTAL_ELAPSED=$(( (END - START) / 60 ))
TOTAL_SEC=$(( (END - START) % 60 ))

echo ""
echo "══════════════════════════════════════"
echo "All ${TOTAL_SHARDS} shards done in ${ELAPSED}m ${SEC}s (whole run ${TOTAL_ELAPSED}m ${TOTAL_SEC}s incl. the prop-gated step)"
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
    # ScalaTest's JUnit XML reporter does NOT put a skipped="N" attribute on <testsuite>;
    # it emits a <skipped/> child inside each cancelled <testcase>. Reading the attribute
    # therefore always yielded 0, so this line reported "0 skipped/canceled" for a run in
    # which DynamicUtilTest cancelled three of its nine -- and would have reported the same
    # for a suite that cancelled every one of its tests. That is the number somebody checks
    # precisely when they suspect tests are not running, so it has to be counted from what
    # is actually in the file. Attribute first for reporters that do emit it, child elements
    # otherwise.
    _sk=$(_sf_attr "$_head" skipped)
    if [[ -z "$_sk" ]]; then _sk=$(grep -c "<skipped" "$_f" 2>/dev/null); fi
    _sk=${_sk:-0}
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
# and "passes". A total far below the real one means shards ran near-empty — fail instead of
# reporting a hollow green.
#
# 3200 is 90% of the 3571 measured on develop-obp (2026-08-25, --shards=4). The previous
# figure, 2000, was set against a suite the header called "~2900" and had drifted far enough
# that a run losing a fifth of its tests would still have passed it. Re-measure and re-set
# both numbers when the suite grows: a floor that is only half the real count is barely a
# floor at all.
if [[ "${SF_TOTAL:-0}" -lt 3200 ]]; then
    echo "  ✗ suspicious total: only ${SF_TOTAL:-0} tests ran (< 3200 floor) — filter/discovery regression?"
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

# Reaped here, not straight after the shards: every shard has been waited on since then, but both
# the surefire audit and the speed report read those XMLs above, and this script already treats a
# report truncated by a JVM killed mid-write as a broken suite. Killing before either read could
# manufacture exactly that failure. By this line nothing left alive can change the verdict.
reap_orphaned_test_jvms

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