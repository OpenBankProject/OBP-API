#!/bin/bash

################################################################################
# Run Specific Tests Script
#
# Simple script to run specific test classes for fast iteration.
# Reads test classes from test-results/failed_tests.txt (edit the file manually,
# one fully-qualified test class per line).
#
# Usage:
#   ./run_specific_tests.sh
#
# Configuration:
#   Option 1: Edit test-results/failed_tests.txt (recommended)
#   Option 2: Edit SPECIFIC_TESTS array in this script
#
# File format (test-results/failed_tests.txt):
#   One test class per line with full package path
#   Lines starting with # are comments
#   Example: code.api.v6_0_0.RateLimitsTest
#
# IMPORTANT: ScalaTest requires full package path!
#   - Must include: code.api.vX_X_X.TestClassName
#   - Do NOT use just "TestClassName"
#   - Do NOT include .scala extension
#
# How to find package path:
#   1. Find test file: obp-api/src/test/scala/code/api/v6_0_0/RateLimitsTest.scala
#   2. Package path: code.api.v6_0_0.RateLimitsTest
#
# Output:
#   - test-results/last_specific_run.log
#   - test-results/last_specific_run_summary.log
#
# Technical Note:
#   Uses Maven -Dsuites parameter (NOT -Dtest) because we use scalatest-maven-plugin
#   The -Dtest parameter is for surefire plugin and doesn't work with ScalaTest
################################################################################

set -eo pipefail

# Pin the JDK before any Maven work, exactly as the other runners do — the suite must
# run on the project's JDK, because a different one produces different results.
. "$(dirname "${BASH_SOURCE[0]}")/scripts/java_env.sh"

################################################################################
# CONFIGURATION
################################################################################

FAILED_TESTS_FILE="test-results/failed_tests.txt"

# Test class names - MUST include full package path for ScalaTest!
# This will be overridden if test-results/failed_tests.txt exists
# Format: "code.api.vX_X_X.TestClassName"
# Example: "code.api.v6_0_0.RateLimitsTest"
SPECIFIC_TESTS=(
  "code.api.v6_0_0.RateLimitsTest"
)

################################################################################
# Script Logic
################################################################################

LOG_DIR="test-results"
DETAIL_LOG="${LOG_DIR}/last_specific_run.log"
SUMMARY_LOG="${LOG_DIR}/last_specific_run_summary.log"

mkdir -p "${LOG_DIR}"

# Read tests from file if it exists, otherwise use SPECIFIC_TESTS array
if [ -f "${FAILED_TESTS_FILE}" ]; then
  echo "Reading test classes from: ${FAILED_TESTS_FILE}"
  # Read non-empty, non-comment lines from file into array (macOS compatible)
  SPECIFIC_TESTS=()
  while IFS= read -r line; do
    SPECIFIC_TESTS+=("$line")
  done < <(grep -v '^\s*#' "${FAILED_TESTS_FILE}" | grep -v '^\s*$')
  echo "Loaded ${#SPECIFIC_TESTS[@]} test(s) from file"
  echo ""
fi

# Check if tests are configured
if [ ${#SPECIFIC_TESTS[@]} -eq 0 ]; then
  echo "ERROR: No tests configured!"
  echo "Either:"
  echo "  1. Create ${FAILED_TESTS_FILE} with one fully-qualified test class per line"
  echo "  2. Create ${FAILED_TESTS_FILE} manually with test class names"
  echo "  3. Edit this script and add test names to SPECIFIC_TESTS array"
  exit 1
fi

echo "=========================================="
echo "Running Specific Tests"
echo "=========================================="
echo ""
echo "Tests to run:"
for test in "${SPECIFIC_TESTS[@]}"; do
  echo "  - $test"
done
echo ""
echo "Logs: ${DETAIL_LOG}"
echo ""

# Set Maven options
export MAVEN_OPTS="-Xss128m -Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"

# Build test list (space-separated for ScalaTest -Dsuites)
TEST_ARG="${SPECIFIC_TESTS[*]}"

# Start time
START_TIME=$(date +%s)

# Run tests individually (running multiple tests together doesn't work with scalatest:test)
# We use mvn test with -T 4 for parallel compilation
echo "Running ${#SPECIFIC_TESTS[@]} test(s) individually..."
echo ""

TOTAL_TESTS=0
TOTAL_PASSED=0
TOTAL_FAILED=0
FAILED_TEST_NAMES=()

# Clear the detail log
> "${DETAIL_LOG}"

for test_class in "${SPECIFIC_TESTS[@]}"; do
  echo "=========================================="
  echo "Running: $test_class"
  echo "=========================================="
  
  # Run test and capture output
  if mvn -pl obp-api test -T 4 -Dsuites="$test_class" 2>&1 | tee -a "${DETAIL_LOG}"; then
    echo "✓ $test_class completed"
  else
    echo "✗ $test_class FAILED"
    FAILED_TEST_NAMES+=("$test_class")
  fi
  echo ""
done

# Parse results from log
TOTAL_TESTS=$(grep -c "Total number of tests run:" "${DETAIL_LOG}" || echo 0)
if [ "$TOTAL_TESTS" -gt 0 ]; then
  # Sum up all test counts
  TOTAL_PASSED=$(grep "Tests: succeeded" "${DETAIL_LOG}" | sed -E 's/.*succeeded ([0-9]+).*/\1/' | awk '{s+=$1} END {print s}')
  TOTAL_FAILED=$(grep "Tests: succeeded" "${DETAIL_LOG}" | sed -E 's/.*failed ([0-9]+).*/\1/' | awk '{s+=$1} END {print s}')
fi

# Determine overall result based on parsed test output
if [ -n "$TOTAL_FAILED" ] && [ "$TOTAL_FAILED" -gt 0 ] 2>/dev/null; then
  TEST_RESULT="FAILURE"
elif [ ${#FAILED_TEST_NAMES[@]} -gt 0 ]; then
  # Maven failed but no test failures parsed — likely compilation error
  TEST_RESULT="FAILURE"
else
  TEST_RESULT="SUCCESS"
fi

# End time
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
DURATION_MIN=$((DURATION / 60))
DURATION_SEC=$((DURATION % 60))

# Write summary
{
  echo "=========================================="
  echo "Test Run Summary"
  echo "=========================================="
  echo "Result:   ${TEST_RESULT}"
  echo "Duration: ${DURATION_MIN}m ${DURATION_SEC}s"
  echo ""
  echo "Test Classes Run: ${#SPECIFIC_TESTS[@]}"
  if [ -n "$TOTAL_PASSED" ] && [ "$TOTAL_PASSED" != "0" ]; then
    echo "Tests Passed: $TOTAL_PASSED"
  fi
  if [ -n "$TOTAL_FAILED" ] && [ "$TOTAL_FAILED" != "0" ]; then
    echo "Tests Failed: $TOTAL_FAILED"
  fi
  echo ""
  if [ ${#FAILED_TEST_NAMES[@]} -gt 0 ]; then
    echo "Failed Test Classes:"
    for failed_test in "${FAILED_TEST_NAMES[@]}"; do
      echo "  ✗ $failed_test"
    done
    echo ""
  fi
  echo "Tests Run:"
  for test in "${SPECIFIC_TESTS[@]}"; do
    if [[ " ${FAILED_TEST_NAMES[@]} " =~ " ${test} " ]]; then
      echo "  ✗ $test"
    else
      echo "  ✓ $test"
    fi
  done
  echo ""
  echo "Logs:"
  echo "  ${DETAIL_LOG}"
  echo "  ${SUMMARY_LOG}"
} | tee "${SUMMARY_LOG}"

# Update failed_tests.txt with only the tests that still fail
# so the next run doesn't re-run tests that have been fixed
if [ -f "${FAILED_TESTS_FILE}" ]; then
  if [ ${#FAILED_TEST_NAMES[@]} -gt 0 ]; then
    {
      echo "# Failed test classes from last specific run"
      echo "# Updated: $(date)"
      for failed_test in "${FAILED_TEST_NAMES[@]}"; do
        echo "$failed_test"
      done
    } > "${FAILED_TESTS_FILE}"
    echo "Updated ${FAILED_TESTS_FILE} with ${#FAILED_TEST_NAMES[@]} still-failing test(s)"
  else
    {
      echo "# All tests passed on last specific run"
      echo "# Updated: $(date)"
      echo "# Add test classes here (one fully-qualified class per line)"
    } > "${FAILED_TESTS_FILE}"
    echo "Cleared ${FAILED_TESTS_FILE} — all tests passed"
  fi
fi

echo ""
echo "=========================================="
echo "Done!"
echo "=========================================="

# Exit with test result
if [ "$TEST_RESULT" = "FAILURE" ]; then
  exit 1
fi
