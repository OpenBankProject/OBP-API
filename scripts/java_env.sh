#!/bin/bash
################################################################################
# OBP-API JDK selection
#
# The build compiles with `-release 17`. A JDK older than 17 fails the build with
#
#   ERROR '17' is not a valid choice for '-release'
#   ERROR bad option: '-release'
#
# which is easy to misread as a source error. Distributions where the default
# `java` is still 11 hit this on every build, so the run scripts source this file
# to select a >= 17 JDK before invoking Maven.
#
# An existing JAVA_HOME is honoured whenever it is new enough; it is only replaced
# when it would fail the build, and the replacement is announced. Sets both
# JAVA_HOME and PATH, because the run scripts invoke a bare `java` for the server.
################################################################################

# Echoes the feature version (e.g. 17) of the java binary passed in, or nothing.
java_major_of() {
    local java_bin="$1"
    [ -x "$java_bin" ] || return 0
    "$java_bin" -version 2>&1 \
        | head -1 \
        | sed -nE 's/.*version "([0-9]+)(\.[0-9]+)*.*/\1/p'
}

java_env_required=17
java_env_selected=""

# 1. An adequate JAVA_HOME already in the environment wins — do not second-guess it.
if [ -n "$JAVA_HOME" ]; then
    java_env_current="$(java_major_of "$JAVA_HOME/bin/java")"
    if [ -n "$java_env_current" ] && [ "$java_env_current" -ge "$java_env_required" ] 2>/dev/null; then
        java_env_selected="$JAVA_HOME"
    else
        echo ">>> JAVA_HOME points at Java ${java_env_current:-unknown}, but the build needs >= $java_env_required — looking for another JDK"
    fi
fi

# 2. Otherwise search the usual locations (Linux distro paths, then macOS).
if [ -z "$java_env_selected" ]; then
    for java_env_candidate in \
        /usr/lib/jvm/java-17-openjdk-amd64 \
        /usr/lib/jvm/java-17-openjdk \
        /usr/lib/jvm/java-21-openjdk-amd64 \
        /usr/lib/jvm/java-21-openjdk \
        "$(/usr/libexec/java_home -v 17 2>/dev/null)" \
        "$(/usr/libexec/java_home -v 21 2>/dev/null)"; do
        [ -n "$java_env_candidate" ] || continue
        java_env_candidate_major="$(java_major_of "$java_env_candidate/bin/java")"
        if [ -n "$java_env_candidate_major" ] && [ "$java_env_candidate_major" -ge "$java_env_required" ] 2>/dev/null; then
            java_env_selected="$java_env_candidate"
            break
        fi
    done
fi

# 3. Fall back to whatever `java` is on PATH, warning if it cannot build.
if [ -n "$java_env_selected" ]; then
    export JAVA_HOME="$java_env_selected"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo ">>> Using JDK $(java_major_of "$JAVA_HOME/bin/java") at $JAVA_HOME"
else
    java_env_path_major="$(java_major_of "$(command -v java 2>/dev/null)")"
    echo ">>> WARNING: no JDK >= $java_env_required found (java on PATH is ${java_env_path_major:-missing})."
    echo "    The build will fail with \"'17' is not a valid choice for '-release'\"."
    echo "    Install a JDK $java_env_required or set JAVA_HOME to one."
fi

# Sourced under `set -e`: end on a command that always succeeds so a false test
# in the block above can never take down the caller.
true
