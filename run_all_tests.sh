#!/bin/bash
# Backward-compat shim: some docs/scripts still say "run ./run_all_tests.sh".
# The real runner is run_tests_parallel.sh — this just forwards to it.
# Usage: ./run_all_tests.sh [--shards=4|6]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/run_tests_parallel.sh" "$@"
