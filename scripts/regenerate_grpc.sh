#!/usr/bin/env bash
# Regenerate the gRPC/protobuf Scala sources under obp-api/src/main/scala/code/obp/grpc.
#
# Those files are generated but checked in, and the build has no protoc plugin, so without this
# script their provenance is folklore: nobody can say which protoc or which scalapb produced them.
# Both are pinned here and cached under target/, so two runs on two machines produce the same bytes.
#
#   ./scripts/regenerate_grpc.sh          regenerate in place
#   ./scripts/regenerate_grpc.sh --check  regenerate into a temp dir and diff, changing nothing
#
# READ THIS BEFORE REGENERATING. The checked-in sources are NOT a clean output of this script, and
# --check reports a large diff on purpose:
#
#   * They sit in different packages. api.proto declares `package code.obp.grpc` and chat.proto
#     declares `code.obp.grpc.chat.g1`, but the checked-in code is under code.obp.grpc.api and
#     code.obp.grpc.chat.api. Regenerating adds a parallel set of packages (41 files becomes 71)
#     instead of updating the existing ones.
#   * They have been edited by hand. ObpServiceGrpc.scala and ApiProto.scala carry a
#     "Temporarily disabled ... javaDescriptor filter" change, with matching edits in Client.scala
#     and ObpGrpcServer.scala. Regenerating discards all of it.
#
# So regenerating is a deliberate piece of work - reconciling packages and re-applying those edits -
# not a routine refresh. The scalapb 0.9.0 upgrade did not do it: the 41 companion signatures that
# 0.9.0 narrowed were patched in place instead, which keeps the hand edits intact. Use this script
# to see what upstream generation would produce, and to make a real regeneration reproducible when
# somebody takes that work on.
#
# After regenerating, read the diff. Generated code is still code that ships: a scalapb upgrade can
# change the shape of repeated fields, the companion objects, or the service stubs, and looking is
# the only way to know what moved. Then run the smoke test, which drives the result over a socket:
#   mvn -pl obp-api test -DwildcardSuites=code.obp.grpc.ObpGrpcServerSmokeTest

set -euo pipefail

# Must match scalapb-runtime-grpc in obp-api/pom.xml. The generated code and its runtime are one
# unit: 0.8.4-generated sources do not compile against the 0.9.0 runtime at all (the
# GeneratedMessageCompanion signatures changed), so upgrading one without the other does not work.
# 0.11.17, not 0.11.20+: it is the last version with a scalapbc zip on GitHub releases, which is
# what this script fetches; later 0.11.x are Maven-only. Same 0.11 line, publishes _3 artifacts.
SCALAPB_VERSION="0.11.17"

# scalapbc bundles protoc-jar, which pins protoc 3.7.1 - a release with no osx-aarch_64 binary, so
# it cannot run on Apple Silicon at all. protoc is therefore fetched directly and handed the
# scalapb code generator as a plugin. 3.19.6 is exactly the protobuf-java version in the
# scalapbc 0.11.17 lib/ - do not reach for a much newer protoc, whose descriptors this
# scalapb was never built against.
PROTOC_VERSION="3.19.6"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTO_DIR="$REPO_ROOT/obp-api/src/main/protobuf"
OUT_DIR="$REPO_ROOT/obp-api/src/main/scala"
CACHE_DIR="$REPO_ROOT/target/grpc-codegen"

CHECK_ONLY=false
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=true

mkdir -p "$CACHE_DIR"

# --- platform ----------------------------------------------------------------------------
case "$(uname -s)" in
  Darwin) OS="osx" ;;
  Linux)  OS="linux" ;;
  *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64|aarch64) ARCH="aarch_64" ;;
  x86_64|amd64)  ARCH="x86_64" ;;
  *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac
CLASSIFIER="$OS-$ARCH"

# --- protoc ------------------------------------------------------------------------------
PROTOC="$CACHE_DIR/protoc-$PROTOC_VERSION-$CLASSIFIER"
if [[ ! -x "$PROTOC" ]]; then
  echo "Fetching protoc $PROTOC_VERSION ($CLASSIFIER) ..."
  curl --proto '=https' --tlsv1.2 -fsSL -o "$PROTOC" \
    "https://repo1.maven.org/maven2/com/google/protobuf/protoc/$PROTOC_VERSION/protoc-$PROTOC_VERSION-$CLASSIFIER.exe"
  chmod +x "$PROTOC"
fi

# --- scalapbc (for its protoc-gen-scala plugin) -------------------------------------------
SCALAPBC_HOME="$CACHE_DIR/scalapbc-$SCALAPB_VERSION"
if [[ ! -d "$SCALAPBC_HOME" ]]; then
  echo "Fetching scalapbc $SCALAPB_VERSION ..."
  curl --proto '=https' --tlsv1.2 -fsSL -o "$CACHE_DIR/scalapbc.zip" \
    "https://github.com/scalapb/ScalaPB/releases/download/v$SCALAPB_VERSION/scalapbc-$SCALAPB_VERSION.zip"
  unzip -q -d "$CACHE_DIR" "$CACHE_DIR/scalapbc.zip"
  rm -f "$CACHE_DIR/scalapbc.zip"
fi
# The 0.9.x zips shipped bin/protoc-gen-scala; 0.11.x zips only ship the scalapbc
# launcher. The plugin entry point still exists as scalapb.ScalaPbCodeGenerator (a
# standard stdin/stdout protoc plugin), so synthesize the shim the old zips contained.
PLUGIN="$SCALAPBC_HOME/bin/protoc-gen-scala"
if [[ ! -f "$PLUGIN" ]]; then
  # The shim locates its own lib/ at run time rather than having this machine's absolute path
  # baked in. Both forms work where they were written, but a baked-in path silently points at
  # another checkout's jars if target/grpc-codegen is ever copied or the worktree is moved -
  # and this repository is routinely checked out into several worktrees at once.
  cat > "$PLUGIN" <<'SHIM'
#!/bin/sh
DIR="$(cd "$(dirname "$0")/.." && pwd)"
exec java -cp "$DIR/lib/*" scalapb.ScalaPbCodeGenerator "$@"
SHIM
fi
chmod +x "$PLUGIN"

# --- destination --------------------------------------------------------------------------
if $CHECK_ONLY; then
  DEST="$(mktemp -d)"
  trap 'rm -rf "$DEST"' EXIT
else
  DEST="$OUT_DIR"
fi

# --- generate -------------------------------------------------------------------------------
# grpc=true emits the service stubs alongside the messages. The protobuf directory doubles as the
# include path so the google/ well-known types vendored beside the .proto files resolve.
# Not mapfile: that is bash 4, and macOS still ships 3.2 as /bin/bash.
# connector.proto is excluded along with the vendored google/ types. It declares `package
# code.bankconnectors.grpc` and scalapb appends the file name, so generating from it writes
# code.bankconnectors.grpc.connector - a second copy of the connector service that nothing imports.
# GrpcUtils takes those types from code.bankconnectors.grpc.api, which is hand-written and predates
# this script. The generated copy was committed by accident with the scalapb upgrade (335ace483,
# 360 lines over 4 files) and removed again; without this exclusion the next regeneration restores
# it. Reconcile connector.proto with the .api package before dropping the exclusion.
# signal.proto is excluded too: it has no checked-in Scala and no Scala consumer (the
# signal channel's server side is external); generating it would only add dead files.
# scalapb/ is the vendored scalapb.proto for the file-level options - an import, not a
# generation source.
PROTOS=()
while IFS= read -r proto; do PROTOS+=("$proto"); done \
  < <(find "$PROTO_DIR" -name '*.proto' -not -path '*/google/*' -not -path '*/scalapb/*' -not -name 'connector.proto' -not -name 'signal.proto' | sort)

echo "Generating from ${#PROTOS[@]} proto files (protoc $PROTOC_VERSION, scalapb $SCALAPB_VERSION)"
"$PROTOC" \
  --plugin=protoc-gen-scala="$PLUGIN" \
  --proto_path="$PROTO_DIR" \
  --scala_out=grpc:"$DEST" \
  "${PROTOS[@]}"

if $CHECK_ONLY; then
  echo
  echo "--- diff against the checked-in sources (empty means they are reproducible) ---"
  diff -ru "$OUT_DIR/code/obp/grpc" "$DEST/code/obp/grpc" || true
else
  echo "Regenerated into $DEST - review the diff before committing."
fi
