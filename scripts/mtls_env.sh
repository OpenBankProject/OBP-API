#!/bin/bash
################################################################################
# OBP-API mTLS environment
#
# Exports the OBP_* overrides that switch the http4s server into in-process mTLS
# termination (see obp-api/src/main/scala/bootstrap/http4s/Http4sMtls.scala and
# docs/MTLS.md). Sourced by the --mtls flag of the build_and_run
# scripts; can also be sourced by hand for a jar that is already built:
#
#   . scripts/mtls_env.sh && java -cp "obp-api/target/obp-api.jar:obp-api/target/lib/*" bootstrap.http4s.Http4sServer
#
# Every OBP prop is overridable from the environment as OBP_<NAME> with dots
# replaced by underscores (APIUtil.getPropsValue reads the environment ahead of
# the props file), which is what makes this a no-edit toggle.
#
# All values below are defaults only: anything already exported wins, so
#   OBP_MTLS_CLIENT_AUTH=want ./flushall_fast_build_and_run.sh --mtls
# behaves as expected.
################################################################################

MTLS_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Mirror Lift's own props resolution, in its order. In Development, Lift's `modeName`
# is the EMPTY string, so its candidate list collapses to the user/host-specific files
# and then default.props. A file named `development.default.props` is therefore never
# loaded in Development — despite the name, it is inert, and reading the hostname from
# it silently pins local_identity_provider to a value the running app does not use.
# (`test.default.props` does work, because in Test mode modeName is "test".)
mtls_props_dir="$MTLS_REPO_ROOT/obp-api/src/main/resources/props"
mtls_user="$(id -un 2>/dev/null)"
mtls_host="$(hostname 2>/dev/null)"
mtls_props_file=""
for mtls_candidate in \
    "$mtls_props_dir/$mtls_user.$mtls_host.props" \
    "$mtls_props_dir/$mtls_user.props" \
    "$mtls_props_dir/$mtls_host.props" \
    "$mtls_props_dir/default.props"; do
    if [[ -f "$mtls_candidate" ]]; then mtls_props_file="$mtls_candidate"; break; fi
done

# Echoes the value of a prop from that file, or nothing when it is absent/commented out.
mtls_prop() {
    local prop_name="$1"
    [[ -n "$mtls_props_file" ]] || return 0
    grep -E "^[[:space:]]*$prop_name[[:space:]]*=" "$mtls_props_file" \
        | tail -1 | cut -d= -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

# --- the toggle itself -------------------------------------------------------
# Http4sMtls defaults the four store props to this same checked-in dev pair, so
# strictly only OBP_MTLS_ENABLED is required. They are set explicitly here as
# absolute paths so that the server also starts correctly from another working
# directory, where the repo-relative defaults would not resolve.
export OBP_MTLS_ENABLED=true
: "${OBP_MTLS_KEYSTORE_PATH:=$MTLS_REPO_ROOT/obp-api/src/test/resources/cert/server.jks}"
: "${OBP_MTLS_KEYSTORE_PASSWORD:=123456}"
: "${OBP_MTLS_TRUSTSTORE_PATH:=$MTLS_REPO_ROOT/obp-api/src/test/resources/cert/server.trust.jks}"
: "${OBP_MTLS_TRUSTSTORE_PASSWORD:=123456}"
: "${OBP_MTLS_CLIENT_AUTH:=need}"
export OBP_MTLS_KEYSTORE_PATH OBP_MTLS_KEYSTORE_PASSWORD
export OBP_MTLS_TRUSTSTORE_PATH OBP_MTLS_TRUSTSTORE_PASSWORD OBP_MTLS_CLIENT_AUTH

# --- hostname, and why local_identity_provider has to be pinned with it -------
# The listener now speaks TLS, so generated links must say https://. But hostname
# is also the default for local_identity_provider (code/api/constant/constant.scala),
# which is the `provider` column every AuthUser / ResourceUser row is keyed on.
# Flipping the scheme alone would give the same local users a different provider
# string on every toggle, orphaning them. So pin local_identity_provider to the
# pre-toggle hostname and let only the scheme move. Skipped when the props file
# already sets local_identity_provider explicitly — then there is nothing to pin.
mtls_props_hostname="$(mtls_prop hostname)"
if [[ -n "$mtls_props_hostname" ]]; then
    if [[ -z "$(mtls_prop local_identity_provider)" ]]; then
        : "${OBP_LOCAL_IDENTITY_PROVIDER:=$mtls_props_hostname}"
        export OBP_LOCAL_IDENTITY_PROVIDER
    fi
    # Move the scheme to TLS and nothing else. Bash's anchored replacement rather than echo|sed:
    # no subshell, and no clear-text URL literal for scanners to flag (the point of the line is to
    # UPGRADE to https, but S5332 sees only the string). "${x/#http:/https:}" rewrites a leading
    # "http:" and leaves an already-https value untouched, since "https:" does not start "http:".
    : "${OBP_HOSTNAME:=${mtls_props_hostname/#http:/https:}}"
    export OBP_HOSTNAME
fi

echo ">>> mTLS enabled (in-process TLS termination)"
echo "      keystore   : $OBP_MTLS_KEYSTORE_PATH"
echo "      truststore : $OBP_MTLS_TRUSTSTORE_PATH"
echo "      client_auth: $OBP_MTLS_CLIENT_AUTH"
echo "      hostname   : ${OBP_HOSTNAME:-<unchanged, no hostname in props>}"
# Plain `if`, not `[ … ] && echo`: this is the last command of a sourced file, and the callers
# run under `set -e` — a false test would take its exit status and abort the caller.
if [[ -n "$OBP_LOCAL_IDENTITY_PROVIDER" ]]; then
    echo "      local_identity_provider pinned to: $OBP_LOCAL_IDENTITY_PROVIDER"
fi
