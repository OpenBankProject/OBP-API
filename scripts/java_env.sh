#!/bin/bash
################################################################################
# OBP-API JDK selection (sourced by the build, run and test scripts)
#
# The build and the shipped artifacts are pinned to one JDK: <java.version> in
# pom.xml, which is also what every CI job and every Dockerfile uses. That one
# version is the supported configuration - not "that version or newer" - so this
# file selects exactly it and refuses to continue if it is missing. A build or a
# test run on a different JDK is not evidence about what CI will do.
#
# Two distinct failure modes it prevents:
#
#   1. Too old for scalac. scala-maven-plugin passes -release ${scalac.release}
#      to scalac, and scalac only accepts a -release up to the spec version of
#      the JDK running it. On an older JDK the build dies with
#        ERROR 'NN' is not a valid choice for '-release'
#      which reads like a Scala-version limit rather than a stale build host, and
#      has been misread that way before. (pom.xml now also enforces a lower bound
#      via maven-enforcer-plugin; this check fails earlier and is exact.)
#   2. Silent drift. A bare `mvn`/`java` picks up whatever the shell or the
#      package manager supplies, which is easily a different JDK than CI used.
#      Homebrew's `mvn` wrapper, for instance, points JAVA_HOME at its own JDK
#      whenever the environment does not already carry one.
#
# The required version is read from pom.xml rather than hardcoded, because a
# hardcoded copy here already went stale once: it still required >= 17 after the
# project had moved to 25, so an unset JAVA_HOME would select a JDK 17 that could
# no longer build.
#
# Order (first match wins):
#   1. $JAVA_HOME            - honoured when it already is the required version
#   2. $OBP_JDK_HOME         - escape hatch for non-standard install locations
#   3. macOS  /usr/libexec/java_home -v <req>   (any vendor)
#   4. SDKMAN ~/.sdkman/candidates/java/*<req>*
#   5. Linux  /usr/lib/jvm/*<req>*, /opt/java/*<req>*, macOS JVM bundles
#   6. a `java` already on PATH that reports the required version
#
# Sets both JAVA_HOME and PATH, because the run scripts invoke a bare `java` for
# the server after Maven has produced the jar.
################################################################################

# Feature version required, taken from pom.xml's <java.version>. The fallback
# only applies if the pom cannot be read (e.g. sourced from an unexpected cwd).
#
# The `|| true` is load-bearing: callers source this under `set -e` (and
# run_specific_tests.sh adds `pipefail`), and `var=$(cmd)` takes the exit status of
# the substitution, so an unreadable pom would abort the caller here instead of
# reaching the fallback on the next line.
java_env_pom="$(dirname "${BASH_SOURCE[0]}")/../pom.xml"
java_env_required="$(sed -nE 's/.*<java\.version>([0-9]+)<\/java\.version>.*/\1/p' "$java_env_pom" 2>/dev/null | head -1 || true)"
[[ -n "$java_env_required" ]] || java_env_required=25

# java_env_major_of <java-home-or-binary>: echoes the feature version, or nothing.
java_env_major_of() {
    local jb="$1"
    [[ -n "$jb" ]] || return 0
    [[ -d "$jb" ]] && jb="$jb/bin/java"
    [[ -x "$jb" ]] || return 0
    "$jb" -version 2>&1 | head -1 | sed -nE 's/.*version "([0-9]+)(\.[0-9]+)*.*/\1/p'
}

# java_env_is_required <java-home-or-binary>: true iff it reports exactly
# $java_env_required. Exact, not a minimum: a newer JDK is not a supported
# configuration here, and accepting one would let local runs diverge from CI.
java_env_is_required() {
    local m; m="$(java_env_major_of "$1")"
    [[ -n "$m" && "$m" -eq "$java_env_required" ]]
}

java_env_resolve() {
    local c cand=()

    # 1. An already-correct JAVA_HOME wins - do not second-guess an explicit choice.
    #    Re-export it even though it is already set: it may be a plain shell
    #    variable rather than an environment one, and then the child mvn would not
    #    see it and would fall back to its own JDK, which is the drift this file
    #    exists to prevent.
    if [[ -n "${JAVA_HOME:-}" ]] && java_env_is_required "$JAVA_HOME"; then
        export JAVA_HOME
        export PATH="$JAVA_HOME/bin:$PATH"
        return 0
    fi
    if [[ -n "${JAVA_HOME:-}" ]]; then
        echo ">>> JAVA_HOME points at Java $(java_env_major_of "$JAVA_HOME"), but this project is pinned to $java_env_required - looking for another JDK"
    fi

    # 2. Explicit escape hatch for odd install locations.
    [[ -n "${OBP_JDK_HOME:-}" ]] && cand+=("$OBP_JDK_HOME")

    # 3. macOS canonical resolver - vendor-agnostic.
    if [[ -x /usr/libexec/java_home ]]; then
        local mh; mh=$(/usr/libexec/java_home -v "$java_env_required" 2>/dev/null) && [[ -n "$mh" ]] && cand+=("$mh")
    fi

    # 4. SDKMAN-managed JDKs.
    if [[ -d "${HOME:-}/.sdkman/candidates/java" ]]; then
        for c in "$HOME/.sdkman/candidates/java"/*"$java_env_required"*/; do
            [[ -d "$c" ]] && cand+=("${c%/}")
        done
    fi

    # 5. Common Linux + macOS-bundle JVM locations (unmatched globs stay literal
    #    and are filtered out by the [[ -d ]] test).
    for c in /usr/lib/jvm/*"$java_env_required"* /usr/lib/jvm/*-"$java_env_required" \
             /usr/lib/jvm/java-"$java_env_required"* /opt/java/*"$java_env_required"* \
             "/Library/Java/JavaVirtualMachines/"*"$java_env_required"*"/Contents/Home" \
             "$HOME/Library/Java/JavaVirtualMachines/"*"$java_env_required"*"/Contents/Home"; do
        [[ -d "$c" ]] && cand+=("$c")
    done

    # 6. First candidate that actually reports the required version wins.
    for c in ${cand[@]+"${cand[@]}"}; do
        if java_env_is_required "$c"; then
            export JAVA_HOME="$c"
            export PATH="$JAVA_HOME/bin:$PATH"
            return 0
        fi
    done

    # 7. Last resort: a `java` already on PATH. Derive JAVA_HOME from it so the
    #    child mvn and the server JVM agree on one JDK.
    if command -v java >/dev/null 2>&1 && java_env_is_required "$(command -v java)"; then
        local jbin; jbin=$(command -v java)
        command -v realpath >/dev/null 2>&1 && jbin=$(realpath "$jbin" 2>/dev/null || echo "$jbin")
        local jhome; jhome=$(cd "$(dirname "$jbin")/.." 2>/dev/null && pwd)
        if [[ -n "$jhome" ]] && java_env_is_required "$jhome"; then
            export JAVA_HOME="$jhome"
            export PATH="$JAVA_HOME/bin:$PATH"
            return 0
        fi
    fi

    return 1
}

if java_env_resolve; then
    echo ">>> Using JDK $(java_env_major_of "$JAVA_HOME") at $JAVA_HOME"
else
    cat >&2 <<EOF
>>> ERROR: JDK $java_env_required not found.
    This project is pinned to JDK $java_env_required (pom.xml <java.version>); CI and every
    Dockerfile use it too, and a different JDK is not a supported configuration.
    Install one and retry, e.g.:
      - SDKMAN:  sdk install java $java_env_required-tem
      - macOS:   brew install --cask temurin@$java_env_required   (or download Zulu/Temurin $java_env_required)
      - Linux:   install a temurin-$java_env_required / java-$java_env_required-openjdk package
    Or point the scripts at an existing install:
      OBP_JDK_HOME=/path/to/jdk-$java_env_required  ./<script>.sh
EOF
    exit 1
fi

# Sourced under `set -e`: end on a command that always succeeds so a false test
# above can never take down the caller.
true
