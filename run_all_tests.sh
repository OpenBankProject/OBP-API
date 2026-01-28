#!/bin/bash

################################################################################
# OBP-API Test Runner Script
#
# What it does:
#   1. Changes terminal to blue background with "Tests Running" in title
#   2. Runs: mvn clean test
#   3. Shows all test output in real-time
#   4. Updates title bar with: phase, time elapsed, pass/fail counts
#   5. Saves detailed log and summary to test-results/
#   6. Restores terminal to normal when done
#
# Usage:
#   ./run_all_tests.sh              - Run full test suite
#   ./run_all_tests.sh --summary-only - Regenerate summary from existing log
#   ./run_all_tests.sh --timeout=60 - Run with 60 minute timeout
################################################################################

# Don't use set -e globally - it causes issues with grep returning 1 when no match
# Instead, we handle errors explicitly where needed

################################################################################
# PARSE COMMAND LINE ARGUMENTS
################################################################################

SUMMARY_ONLY=false
TIMEOUT_MINUTES=0  # 0 means no timeout

for arg in "$@"; do
    case $arg in
        --summary-only)
            SUMMARY_ONLY=true
            ;;
        --timeout=*)
            TIMEOUT_MINUTES="${arg#*=}"
            ;;
    esac
done

################################################################################
# TERMINAL STYLING FUNCTIONS
################################################################################

# Set terminal to "test mode" - different colors for different phases
set_terminal_style() {
    local phase="${1:-Running}"
    
    # Set different background colors for different phases
    case "$phase" in
        "Starting")
            echo -ne "\033]11;#4a4a4a\007"  # Dark gray background
            echo -ne "\033]10;#ffffff\007"  # White text
            ;;
        "Building") 
            echo -ne "\033]11;#ff6b35\007"  # Orange background
            echo -ne "\033]10;#ffffff\007"  # White text
            ;;
        "Testing")
            echo -ne "\033]11;#001f3f\007"  # Dark blue background  
            echo -ne "\033]10;#ffffff\007"  # White text
            ;;
        "Complete")
            echo -ne "\033]11;#2ecc40\007"  # Green background
            echo -ne "\033]10;#ffffff\007"  # White text
            ;;
        *)
            echo -ne "\033]11;#001f3f\007"  # Default blue background
            echo -ne "\033]10;#ffffff\007"  # White text
            ;;
    esac
    
    # Set window title
    echo -ne "\033]0;OBP-API Tests ${phase}...\007"
    
    # Print header bar with phase-specific styling
    printf "\033[44m\033[1;37m%-$(tput cols)s\r  OBP-API TEST RUNNER ACTIVE - ${phase}  \n%-$(tput cols)s\033[0m\n" " " " "
}

# Update title bar with progress: "Testing: DynamicEntityTest - Scenario name [5m 23s]"
update_terminal_title() {
    local phase="$1"           # Starting, Building, Testing, Complete
    local elapsed="${2:-}"     # Time elapsed (e.g. "5m 23s")
    local counts="${3:-}"      # Module counts (e.g. "obp-commons:+38 obp-api:+245")
    local suite="${4:-}"       # Current test suite name
    local scenario="${5:-}"    # Current scenario name

    local title="OBP-API ${phase}"
    [ -n "$suite" ] && title="${title}: ${suite}"
    [ -n "$scenario" ] && title="${title} - ${scenario}"
    title="${title}..."
    [ -n "$elapsed" ] && title="${title} [${elapsed}]"
    [ -n "$counts" ] && title="${title} ${counts}"

    echo -ne "\033]0;${title}\007"
}

# Restore terminal to normal (black background, default title)
restore_terminal_style() {
    echo -ne "\033]0;Terminal\007\033]11;#000000\007\033]10;#ffffff\007\033[0m"
}

# Cleanup function: stop monitor, restore terminal, remove flag files
cleanup_on_exit() {
    # Stop background monitor if running
    if [ -n "${MONITOR_PID:-}" ]; then
        kill $MONITOR_PID 2>/dev/null || true
        wait $MONITOR_PID 2>/dev/null || true
    fi

    # Remove monitor flag file
    rm -f "${LOG_DIR}/monitor.flag" 2>/dev/null || true

    # Restore terminal
    restore_terminal_style
}

# Always cleanup on exit (Ctrl+C, errors, or normal completion)
trap cleanup_on_exit EXIT INT TERM

################################################################################
# CONFIGURATION
################################################################################

LOG_DIR="test-results"
DETAIL_LOG="${LOG_DIR}/last_run.log"        # Full Maven output
SUMMARY_LOG="${LOG_DIR}/last_run_summary.log"  # Summary only
FAILED_TESTS_FILE="${LOG_DIR}/failed_tests.txt"  # Failed test list for run_specific_tests.sh

# Phase timing variables (stored in temporary file)
PHASE_START_TIME=0

mkdir -p "${LOG_DIR}"

# Function to get current time in milliseconds
get_time_ms() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        python3 -c "import time; print(int(time.time() * 1000))"
    else
        # Linux
        date +%s%3N
    fi
}

# Function to record phase timing
record_phase_time() {
    local phase="$1"
    local current_time=$(get_time_ms)
    local timing_file="${LOG_DIR}/phase_timing.tmp"
    
    case "$phase" in
        "starting")
            echo "PHASE_START_TIME=$current_time" > "$timing_file"
            ;;
        "building")
            if [ -f "$timing_file" ]; then
                local phase_start=$(grep "PHASE_START_TIME=" "$timing_file" | cut -d= -f2)
                if [ "$phase_start" -gt 0 ]; then
                    local starting_time=$((current_time - phase_start))
                    echo "STARTING_TIME=$starting_time" >> "$timing_file"
                fi
            fi
            echo "PHASE_START_TIME=$current_time" >> "$timing_file"
            ;;
        "testing")
            if [ -f "$timing_file" ]; then
                local phase_start=$(grep "PHASE_START_TIME=" "$timing_file" | tail -1 | cut -d= -f2)
                if [ "$phase_start" -gt 0 ]; then
                    local building_time=$((current_time - phase_start))
                    echo "BUILDING_TIME=$building_time" >> "$timing_file"
                fi
            fi
            echo "PHASE_START_TIME=$current_time" >> "$timing_file"
            ;;
        "complete")
            if [ -f "$timing_file" ]; then
                local phase_start=$(grep "PHASE_START_TIME=" "$timing_file" | tail -1 | cut -d= -f2)
                if [ "$phase_start" -gt 0 ]; then
                    local testing_time=$((current_time - phase_start))
                    echo "TESTING_TIME=$testing_time" >> "$timing_file"
                fi
            fi
            echo "PHASE_START_TIME=$current_time" >> "$timing_file"
            ;;
        "end")
            if [ -f "$timing_file" ]; then
                local phase_start=$(grep "PHASE_START_TIME=" "$timing_file" | tail -1 | cut -d= -f2)
                if [ "$phase_start" -gt 0 ]; then
                    local complete_time=$((current_time - phase_start))
                    echo "COMPLETE_TIME=$complete_time" >> "$timing_file"
                fi
            fi
            ;;
    esac
}

# If summary-only mode, skip to summary generation
if [ "$SUMMARY_ONLY" = true ]; then
    if [ ! -f "${DETAIL_LOG}" ]; then
        echo "ERROR: No log file found at ${DETAIL_LOG}"
        echo "Please run tests first without --summary-only flag"
        exit 1
    fi
    echo "Regenerating summary from existing log: ${DETAIL_LOG}"
    # Skip cleanup and jump to summary generation
    START_TIME=0
    END_TIME=0
    DURATION=0
    DURATION_MIN=0
    DURATION_SEC=0
else
    # Delete old log files and stale flag files from previous run
    echo "Cleaning up old files..."
    if [ -f "${DETAIL_LOG}" ]; then
        rm -f "${DETAIL_LOG}"
        echo "  - Removed old detail log"
    fi
    if [ -f "${SUMMARY_LOG}" ]; then
        rm -f "${SUMMARY_LOG}"
        echo "  - Removed old summary log"
    fi
if [ -f "${LOG_DIR}/monitor.flag" ]; then
    rm -f "${LOG_DIR}/monitor.flag"
    echo "  - Removed stale monitor flag"
fi
    if [ -f "${LOG_DIR}/warning_analysis.tmp" ]; then
        rm -f "${LOG_DIR}/warning_analysis.tmp"
        echo "  - Removed stale warning analysis"
    fi
    if [ -f "${LOG_DIR}/recent_lines.tmp" ]; then
        rm -f "${LOG_DIR}/recent_lines.tmp"
        echo "  - Removed stale temp file"
    fi
    if [ -f "${LOG_DIR}/phase_timing.tmp" ]; then
        rm -f "${LOG_DIR}/phase_timing.tmp"
        echo "  - Removed stale timing file"
    fi
fi  # End of if [ "$SUMMARY_ONLY" = true ]

################################################################################
# HELPER FUNCTIONS
################################################################################

# Log message to terminal and both log files
log_message() {
    echo "$1"
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1" >> "${SUMMARY_LOG}"
    echo "$1" >> "${DETAIL_LOG}"
}

# Print section header
print_header() {
    echo ""
    echo "================================================================================"
    echo "$1"
    echo "================================================================================"
    echo ""
}

# Analyze warnings and return top contributors
analyze_warnings() {
    local log_file="$1"
    local temp_file="${LOG_DIR}/warning_analysis.tmp"

    # Extract and categorize warnings from last 5000 lines (for performance)
    # This gives good coverage without scanning entire multi-MB log file
    tail -n 5000 "${log_file}" 2>/dev/null | grep -i "warning" | \
        # Normalize patterns to group similar warnings
        sed -E 's/line [0-9]+/line XXX/g' | \
        sed -E 's/[0-9]+ warnings?/N warnings/g' | \
        sed -E 's/\[WARNING\] .*(src|test)\/[^ ]+/[WARNING] <source-file>/g' | \
        sed -E 's/version [0-9]+\.[0-9]+(\.[0-9]+)?/version X.X/g' | \
        # Extract the core warning message
        sed -E 's/^.*\[WARNING\] *//' | \
        sort | uniq -c | sort -rn > "${temp_file}"

    # Return the temp file path for further processing
    echo "${temp_file}"
}

# Format and display top warning factors
display_warning_factors() {
    local analysis_file="$1"
    local max_display="${2:-10}"

    if [ ! -f "${analysis_file}" ] || [ ! -s "${analysis_file}" ]; then
        log_message "  No detailed warning analysis available"
        return
    fi

    local total_warning_types=$(wc -l < "${analysis_file}")
    local displayed=0

    log_message "Top Warning Factors:"
    log_message "-------------------"

    while IFS= read -r line && [ $displayed -lt $max_display ]; do
        # Extract count and message
        local count=$(echo "$line" | awk '{print $1}')
        local message=$(echo "$line" | sed -E 's/^[[:space:]]*[0-9]+[[:space:]]*//')

        # Truncate long messages
        if [ ${#message} -gt 80 ]; then
            message="${message:0:77}..."
        fi

        # Format with count prominence
        printf "  %4d x %s\n" "$count" "$message" | tee -a "${SUMMARY_LOG}" > /dev/tty

        displayed=$((displayed + 1))
    done < "${analysis_file}"

    if [ $total_warning_types -gt $max_display ]; then
        local remaining=$((total_warning_types - max_display))
        log_message "  ... and ${remaining} more warning type(s)"
    fi

    # Clean up temp file
    rm -f "${analysis_file}"
}

################################################################################
# GENERATE SUMMARY FUNCTION (DRY)
################################################################################

generate_summary() {
    local detail_log="$1"
    local summary_log="$2"
    local start_time="${3:-0}"
    local end_time="${4:-0}"

    # Calculate duration
    local duration=$((end_time - start_time))
    local duration_min=$((duration / 60))
    local duration_sec=$((duration % 60))

    # If no timing info (summary-only mode), extract from log
    if [ $duration -eq 0 ] && grep -q "Total time:" "$detail_log"; then
        local time_str=$(grep "Total time:" "$detail_log" | tail -1)
        duration_min=$(echo "$time_str" | sed 's/.*: //' | sed 's/ min.*//' | grep -o '[0-9]*' | head -1)
        [ -z "$duration_min" ] && duration_min="0"
        duration_sec=$(echo "$time_str" | sed 's/.* min //' | sed 's/\..*//' | grep -o '[0-9]*' | head -1)
        [ -z "$duration_sec" ] && duration_sec="0"
    fi

    print_header "Test Results Summary"

    # Extract test statistics from ScalaTest output (with UNKNOWN fallback if extraction fails)
    # ScalaTest outputs across multiple lines:
    #   Run completed in X seconds.
    #   Total number of tests run: N
    #   Suites: completed M, aborted 0
    #   Tests: succeeded N, failed 0, canceled 0, ignored 0, pending 0
    #   All tests passed.
    # We need to sum stats from ALL test runs (multiple modules: obp-commons, obp-api, etc.)
    
    # Sum up all "Total number of tests run" values (macOS compatible - no grep -P)
    TOTAL_TESTS=$(grep "Total number of tests run:" "${detail_log}" 2>/dev/null | sed 's/.*Total number of tests run: //' | awk '{sum+=$1} END {print sum}' || echo "0")
    [ -z "$TOTAL_TESTS" ] || [ "$TOTAL_TESTS" = "0" ] && TOTAL_TESTS="UNKNOWN"
    
    # Sum up all succeeded from "Tests: succeeded N, ..." lines
    SUCCEEDED=$(grep "Tests: succeeded" "${detail_log}" 2>/dev/null | sed 's/.*succeeded //' | sed 's/,.*//' | awk '{sum+=$1} END {print sum}' || echo "0")
    [ -z "$SUCCEEDED" ] && SUCCEEDED="UNKNOWN"
    
    # Sum up all failed from "Tests: ... failed N, ..." lines
    FAILED=$(grep "Tests:.*failed" "${detail_log}" 2>/dev/null | sed 's/.*failed //' | sed 's/,.*//' | awk '{sum+=$1} END {print sum}' || echo "0")
    [ -z "$FAILED" ] && FAILED="0"
    
    # Sum up all ignored from "Tests: ... ignored N, ..." lines
    IGNORED=$(grep "Tests:.*ignored" "${detail_log}" 2>/dev/null | sed 's/.*ignored //' | sed 's/,.*//' | awk '{sum+=$1} END {print sum}' || echo "0")
    [ -z "$IGNORED" ] && IGNORED="0"
    
    # Sum up errors (if any)
    ERRORS=$(grep "errors" "${detail_log}" 2>/dev/null | grep -v "ERROR" | sed 's/.*errors //' | sed 's/[^0-9].*//' | awk '{sum+=$1} END {print sum}' || echo "0")
    [ -z "$ERRORS" ] && ERRORS="0"
    
    # Calculate total including ignored (like IntelliJ does)
    if [ "$TOTAL_TESTS" != "UNKNOWN" ] && [ "$IGNORED" != "0" ]; then
        TOTAL_WITH_IGNORED=$((TOTAL_TESTS + IGNORED))
    else
        TOTAL_WITH_IGNORED="$TOTAL_TESTS"
    fi
    
    WARNINGS=$(grep -c "WARNING" "${detail_log}" 2>/dev/null || echo "0")

    # Determine build status
    if grep -q "BUILD SUCCESS" "${detail_log}"; then
        BUILD_STATUS="SUCCESS"
        BUILD_COLOR=""
    elif grep -q "BUILD FAILURE" "${detail_log}"; then
        BUILD_STATUS="FAILURE"
        BUILD_COLOR=""
    else
        BUILD_STATUS="UNKNOWN"
        BUILD_COLOR=""
    fi

    # Print summary
    log_message "Test Run Summary"
    log_message "================"
    
    # Extract Maven timestamps and calculate Terminal timestamps
    local maven_start_timestamp=""
    local maven_end_timestamp=""
    local terminal_start_timestamp=""
    local terminal_end_timestamp=$(date)
    
    if [ "$start_time" -gt 0 ] && [ "$end_time" -gt 0 ]; then
        # Use actual terminal start/end times if available
        terminal_start_timestamp=$(date -r "$start_time" 2>/dev/null || date -d "@$start_time" 2>/dev/null || echo "Unknown")
        terminal_end_timestamp=$(date -r "$end_time" 2>/dev/null || date -d "@$end_time" 2>/dev/null || echo "Unknown")
    else
        # Calculate terminal start time by subtracting duration from current time
        if [ "$duration_min" -gt 0 -o "$duration_sec" -gt 0 ]; then
            local total_seconds=$((duration_min * 60 + duration_sec))
            local approx_start_epoch=$(($(date "+%s") - total_seconds))
            terminal_start_timestamp=$(date -r "$approx_start_epoch" 2>/dev/null || echo "Approx. ${duration_min}m ${duration_sec}s ago")
        else
            terminal_start_timestamp="Unknown"
        fi
    fi
    
    # Extract Maven timestamps from log
    maven_end_timestamp=$(grep "Finished at:" "${detail_log}" | tail -1 | sed 's/.*Finished at: //' | sed 's/T/ /' | sed 's/+.*//' || echo "Unknown")
    
    # Calculate Maven start time from Maven's "Total time" if available
    local maven_total_time=$(grep "Total time:" "${detail_log}" | tail -1 | sed 's/.*Total time: *//' | sed 's/ .*//' || echo "")
    if [ -n "$maven_total_time" ] && [ "$maven_end_timestamp" != "Unknown" ]; then
        # Parse Maven duration (e.g., "02:06" for "02:06 min" or "43.653" for "43.653 s")
        local maven_seconds=0
        if echo "$maven_total_time" | grep -q ":"; then
            # Format like "02:06" (minutes:seconds)
            local maven_min=$(echo "$maven_total_time" | sed 's/:.*//')
            local maven_sec=$(echo "$maven_total_time" | sed 's/.*://')
            # Remove leading zeros to avoid octal interpretation
            maven_min=$(echo "$maven_min" | sed 's/^0*//' | sed 's/^$/0/')
            maven_sec=$(echo "$maven_sec" | sed 's/^0*//' | sed 's/^$/0/')
            maven_seconds=$((maven_min * 60 + maven_sec))
        else
            # Format like "43.653" (seconds)
            maven_seconds=$(echo "$maven_total_time" | sed 's/\..*//')
        fi
        
        # Calculate Maven start time
        if [ "$maven_seconds" -gt 0 ]; then
            local maven_end_epoch=$(date -j -f "%Y-%m-%d %H:%M:%S" "$maven_end_timestamp" "+%s" 2>/dev/null || echo "0")
            if [ "$maven_end_epoch" -gt 0 ]; then
                local maven_start_epoch=$((maven_end_epoch - maven_seconds))
                maven_start_timestamp=$(date -r "$maven_start_epoch" 2>/dev/null || echo "Unknown")
            else
                maven_start_timestamp="Unknown"
            fi
        else
            maven_start_timestamp="Unknown"
        fi
    else
        maven_start_timestamp="Unknown"
    fi
    
    # Format Maven end timestamp nicely
    if [ "$maven_end_timestamp" != "Unknown" ]; then
        maven_end_timestamp=$(date -j -f "%Y-%m-%d %H:%M:%S" "$maven_end_timestamp" "+%a %b %d %H:%M:%S %Z %Y" 2>/dev/null || echo "$maven_end_timestamp")
    fi
    
    # Display both timelines
    log_message "Terminal Timeline:"
    log_message "  Started:       ${terminal_start_timestamp}"
    log_message "  Completed:     ${terminal_end_timestamp}"
    log_message "  Duration:      ${duration_min}m ${duration_sec}s"
    log_message ""
    log_message "Maven Timeline:"
    log_message "  Started:       ${maven_start_timestamp}"
    log_message "  Completed:     ${maven_end_timestamp}"
    if [ -n "$maven_total_time" ]; then
        local maven_duration_display=$(grep "Total time:" "${detail_log}" | tail -1 | sed 's/.*Total time: *//' || echo "Unknown")
        log_message "  Duration:      ${maven_duration_display}"
    fi
    log_message ""
    log_message "Build Status:  ${BUILD_STATUS}"
    log_message ""
    
    # Phase timing breakdown (if available)
    local timing_file="${LOG_DIR}/phase_timing.tmp"
    if [ -f "$timing_file" ]; then
        # Read timing values from file
        local start_ms=$(grep "STARTING_TIME=" "$timing_file" | cut -d= -f2 2>/dev/null || echo "0")
        local build_ms=$(grep "BUILDING_TIME=" "$timing_file" | cut -d= -f2 2>/dev/null || echo "0")
        local test_ms=$(grep "TESTING_TIME=" "$timing_file" | cut -d= -f2 2>/dev/null || echo "0")
        local complete_ms=$(grep "COMPLETE_TIME=" "$timing_file" | cut -d= -f2 2>/dev/null || echo "0")
        
        # Ensure we have numeric values (default to 0 if empty)
        [ -z "$start_ms" ] && start_ms=0
        [ -z "$build_ms" ] && build_ms=0
        [ -z "$test_ms" ] && test_ms=0
        [ -z "$complete_ms" ] && complete_ms=0
        
        # Clean up timing file
        rm -f "$timing_file"
        
        if [ "$start_ms" -gt 0 ] 2>/dev/null || [ "$build_ms" -gt 0 ] 2>/dev/null || [ "$test_ms" -gt 0 ] 2>/dev/null || [ "$complete_ms" -gt 0 ] 2>/dev/null; then
            log_message "Phase Timing Breakdown:"
            
            if [ "$start_ms" -gt 0 ] 2>/dev/null; then
                log_message "  Starting:    ${start_ms}ms ($(printf "%.2f" $(echo "scale=2; $start_ms/1000" | bc))s)"
            fi
            if [ "$build_ms" -gt 0 ] 2>/dev/null; then
                log_message "  Building:    ${build_ms}ms ($(printf "%.2f" $(echo "scale=2; $build_ms/1000" | bc))s)"
            fi
            if [ "$test_ms" -gt 0 ] 2>/dev/null; then
                log_message "  Testing:     ${test_ms}ms ($(printf "%.2f" $(echo "scale=2; $test_ms/1000" | bc))s)"
            fi
            if [ "$complete_ms" -gt 0 ] 2>/dev/null; then
                log_message "  Complete:    ${complete_ms}ms ($(printf "%.2f" $(echo "scale=2; $complete_ms/1000" | bc))s)"
            fi
            
            # Calculate percentages
            local total_phase_time=$((start_ms + build_ms + test_ms + complete_ms))
            if [ "$total_phase_time" -gt 0 ]; then
                log_message ""
                log_message "Phase Distribution:"
                if [ "$start_ms" -gt 0 ] 2>/dev/null; then
                    local starting_pct=$(echo "scale=1; $start_ms * 100 / $total_phase_time" | bc)
                    log_message "  Starting:    ${starting_pct}%"
                fi
                if [ "$build_ms" -gt 0 ] 2>/dev/null; then
                    local building_pct=$(echo "scale=1; $build_ms * 100 / $total_phase_time" | bc)
                    log_message "  Building:    ${building_pct}%"
                fi
                if [ "$test_ms" -gt 0 ] 2>/dev/null; then
                    local testing_pct=$(echo "scale=1; $test_ms * 100 / $total_phase_time" | bc)
                    log_message "  Testing:     ${testing_pct}%"
                fi
                if [ "$complete_ms" -gt 0 ] 2>/dev/null; then
                    local complete_pct=$(echo "scale=1; $complete_ms * 100 / $total_phase_time" | bc)
                    log_message "  Complete:    ${complete_pct}%"
                fi
            fi
            log_message ""
        fi
    fi
    
    log_message "Test Statistics:"
    log_message "  Total:       ${TOTAL_WITH_IGNORED} (${TOTAL_TESTS} run + ${IGNORED} ignored)"
    log_message "  Succeeded:   ${SUCCEEDED}"
    log_message "  Failed:      ${FAILED}"
    log_message "  Ignored:     ${IGNORED}"
    log_message "  Errors:      ${ERRORS}"
    log_message "  Warnings:    ${WARNINGS}"
    log_message ""

    # Analyze and display warning factors if warnings exist
    if [ "${WARNINGS}" != "0" ] && [ "${WARNINGS}" != "UNKNOWN" ]; then
        warning_analysis=$(analyze_warnings "${detail_log}")
        display_warning_factors "${warning_analysis}" 10
        log_message ""
    fi

    # Show failed tests if any (only actual test failures, not application ERROR logs)
    if [ "${FAILED}" != "0" ] && [ "${FAILED}" != "UNKNOWN" ]; then
        log_message "Failed Tests:"
        # Look for ScalaTest failure markers, not application ERROR logs
        grep -E "\*\*\* FAILED \*\*\*|\*\*\* RUN ABORTED \*\*\*" "${detail_log}" | head -50 >> "${summary_log}"
        log_message ""

        # Extract failed test class names and save to file for run_specific_tests.sh
        # Look backwards from "*** FAILED ***" to find the test class name
        # ScalaTest prints: "TestClassName:" before scenarios
        > "${FAILED_TESTS_FILE}"  # Clear/create file
        echo "# Failed test classes from last run" >> "${FAILED_TESTS_FILE}"
        echo "# Auto-generated by run_all_tests.sh - you can edit this file manually" >> "${FAILED_TESTS_FILE}"
        echo "#" >> "${FAILED_TESTS_FILE}"
        echo "# Format: One test class per line with full package path" >> "${FAILED_TESTS_FILE}"
        echo "# Example: code.api.v6_0_0.RateLimitsTest" >> "${FAILED_TESTS_FILE}"
        echo "#" >> "${FAILED_TESTS_FILE}"
        echo "# Usage: ./run_specific_tests.sh will read this file and run only these tests" >> "${FAILED_TESTS_FILE}"
        echo "#" >> "${FAILED_TESTS_FILE}"
        echo "# Lines starting with # are ignored (comments)" >> "${FAILED_TESTS_FILE}"
        echo "" >> "${FAILED_TESTS_FILE}"

        # Extract test class names from failures
        grep -B 20 "\*\*\* FAILED \*\*\*" "${detail_log}" | \
          grep -E "^[A-Z][a-zA-Z0-9_]+:" | sed 's/:$//' | \
          sort -u | \
          while read test_class; do
            # Try to find package by searching for the class in test files
            package=$(find obp-api/src/test/scala -name "${test_class}.scala" | \
                      sed 's|obp-api/src/test/scala/||' | \
                      sed 's|/|.|g' | \
                      sed 's|.scala$||' | \
                      head -1)
            if [ -n "$package" ]; then
              echo "$package" >> "${FAILED_TESTS_FILE}"
            fi
          done

        log_message "Failed test classes saved to: ${FAILED_TESTS_FILE}"
        log_message ""
    elif [ "${ERRORS}" != "0" ] && [ "${ERRORS}" != "UNKNOWN" ]; then
        log_message "Test Errors:"
        grep -E "\*\*\* FAILED \*\*\*|\*\*\* RUN ABORTED \*\*\*" "${detail_log}" | head -50 >> "${summary_log}"
        log_message ""
    fi

    # Final result
    print_header "Test Run Complete"

    if [ "${BUILD_STATUS}" = "SUCCESS" ] && [ "${FAILED}" = "0" ] && [ "${ERRORS}" = "0" ]; then
        log_message "[PASS] All tests passed!"
        return 0
    else
        log_message "[FAIL] Tests failed"
        return 1
    fi
}

################################################################################
# SUMMARY-ONLY MODE
################################################################################

if [ "$SUMMARY_ONLY" = true ]; then
    # Just regenerate the summary and exit
    rm -f "${SUMMARY_LOG}"
    if generate_summary "${DETAIL_LOG}" "${SUMMARY_LOG}" 0 0; then
        log_message ""
        log_message "Summary regenerated:"
        log_message "  ${SUMMARY_LOG}"
        exit 0
    else
        exit 1
    fi
fi

################################################################################
# START TEST RUN
################################################################################

# Record starting phase
record_phase_time "starting"
set_terminal_style "Starting"

# Start the test run
print_header "OBP-API Test Suite"
log_message "Starting test run at $(date)"
log_message "Detail log: ${DETAIL_LOG}"
log_message "Summary log: ${SUMMARY_LOG}"
echo ""

# Set Maven options for tests
# The --add-opens flags tell Java 17 to allow Kryo serialization library to access
# the internal java.lang.invoke and java.lang modules, which fixes the InaccessibleObjectException
export MAVEN_OPTS="-Xss128m -Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
log_message "Maven Options: ${MAVEN_OPTS}"
echo ""

# Ensure test properties file exists
PROPS_FILE="obp-api/src/main/resources/props/test.default.props"
PROPS_TEMPLATE="${PROPS_FILE}.template"

if [ -f "${PROPS_FILE}" ]; then
    log_message "[OK] Found test.default.props"
else
    log_message "[WARNING] test.default.props not found - creating from template"
    if [ -f "${PROPS_TEMPLATE}" ]; then
        cp "${PROPS_TEMPLATE}" "${PROPS_FILE}"
        log_message "[OK] Created test.default.props"
    else
        log_message "ERROR: ${PROPS_TEMPLATE} not found!"
        exit 1
    fi
fi

################################################################################
# CHECK AND CLEANUP TEST SERVER PORTS
# Port 8018 is used by the embedded Jetty test server (configured in test.default.props)
################################################################################

print_header "Checking Test Server Ports"

# Default test port (can be overridden)
TEST_PORT=8018
MAX_PORT_ATTEMPTS=5

log_message "Checking if test server port ${TEST_PORT} is available..."

# Function to find an available port
find_available_port() {
    local port=$1
    local max_attempts=$2
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if ! lsof -i :$port >/dev/null 2>&1; then
            echo $port
            return 0
        fi
        port=$((port + 1))
        attempt=$((attempt + 1))
    done
    
    echo ""
    return 1
}

# Check if port is in use
if lsof -i :${TEST_PORT} >/dev/null 2>&1; then
    log_message "[WARNING] Port ${TEST_PORT} is in use - attempting to kill process"
    PORT_PID=$(lsof -t -i :${TEST_PORT} 2>/dev/null || true)
    if [ -n "$PORT_PID" ]; then
        kill -9 $PORT_PID 2>/dev/null || true
        sleep 2
        
        # Verify port is now free
        if lsof -i :${TEST_PORT} >/dev/null 2>&1; then
            log_message "[WARNING] Could not free port ${TEST_PORT}, searching for alternative..."
            NEW_PORT=$(find_available_port $((TEST_PORT + 1)) $MAX_PORT_ATTEMPTS)
            if [ -n "$NEW_PORT" ]; then
                log_message "[OK] Found available port: ${NEW_PORT}"
                # Update test.default.props with new port
                if [ -f "${PROPS_FILE}" ]; then
                    sed -i.bak "s/hostname=127.0.0.1:${TEST_PORT}/hostname=127.0.0.1:${NEW_PORT}/" "${PROPS_FILE}" 2>/dev/null || \
                    sed -i '' "s/hostname=127.0.0.1:${TEST_PORT}/hostname=127.0.0.1:${NEW_PORT}/" "${PROPS_FILE}"
                    log_message "[OK] Updated test.default.props to use port ${NEW_PORT}"
                    TEST_PORT=$NEW_PORT
                fi
            else
                log_message "[ERROR] No available ports found in range ${TEST_PORT}-$((TEST_PORT + MAX_PORT_ATTEMPTS))"
                exit 1
            fi
        else
            log_message "[OK] Killed process $PORT_PID, port ${TEST_PORT} is now available"
        fi
    fi
else
    log_message "[OK] Port ${TEST_PORT} is available"
fi

# Also check for any stale Java test processes
STALE_TEST_PROCS=$(ps aux | grep -E "TestServer|ScalaTest.*obp-api" | grep -v grep | awk '{print $2}' 2>/dev/null || true)
if [ -n "$STALE_TEST_PROCS" ]; then
    log_message "[WARNING] Found stale test processes - cleaning up"
    echo "$STALE_TEST_PROCS" | xargs kill -9 2>/dev/null || true
    sleep 2
    log_message "[OK] Cleaned up stale test processes"
else
    log_message "[OK] No stale test processes found"
fi

log_message ""

################################################################################
# CLEAN METRICS DATABASE
################################################################################

print_header "Cleaning Metrics Database"
log_message "Checking for test database files..."

# Only delete specific test database files to prevent accidental data loss
# The test configuration uses test_only_lift_proto.db as the database filename
TEST_DB_PATTERNS=(
    "./test_only_lift_proto.db"
    "./test_only_lift_proto.db.mv.db"
    "./test_only_lift_proto.db.trace.db"
    "./obp-api/test_only_lift_proto.db"
    "./obp-api/test_only_lift_proto.db.mv.db"
    "./obp-api/test_only_lift_proto.db.trace.db"
)

FOUND_FILES=false
for dbfile in "${TEST_DB_PATTERNS[@]}"; do
    if [ -f "$dbfile" ]; then
        FOUND_FILES=true
        rm -f "$dbfile"
        log_message "  [OK] Deleted: $dbfile"
    fi
done

if [ "$FOUND_FILES" = false ]; then
    log_message "No old test database files found"
fi

log_message ""

################################################################################
# RUN TESTS
################################################################################

print_header "Running Tests"
log_message "Executing: mvn clean test"
echo ""

START_TIME=$(date +%s)
export START_TIME

# Create flag file to signal background process to stop
MONITOR_FLAG="${LOG_DIR}/monitor.flag"
touch "${MONITOR_FLAG}"

# Optional timeout handling
MAVEN_PID=""
if [ "$TIMEOUT_MINUTES" -gt 0 ] 2>/dev/null; then
    log_message "[INFO] Test timeout set to ${TIMEOUT_MINUTES} minutes"
    TIMEOUT_SECONDS=$((TIMEOUT_MINUTES * 60))
fi

# Background process: Monitor log file and update title bar with progress
(
    # Wait for log file to be created and have Maven output
    while [ ! -f "${DETAIL_LOG}" ] || [ ! -s "${DETAIL_LOG}" ]; do
        sleep 1
    done

    phase="Building"
    in_building=false
    in_testing=false
    timing_file="${LOG_DIR}/phase_timing.tmp"

    # Keep monitoring until flag file is removed
    while [ -f "${MONITOR_FLAG}" ]; do
        # Use tail to look at recent lines only (last 500 lines for performance)
        recent_lines=$(tail -n 500 "${DETAIL_LOG}" 2>/dev/null || true)

        # Switch to "Building" phase when Maven starts compiling
        if ! $in_building && echo "$recent_lines" | grep -q -E 'Compiling|Building.*Open Bank Project' 2>/dev/null; then
            phase="Building"
            in_building=true
            # Record building phase and update terminal (inline to avoid subshell issues)
            current_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || date +%s000)
            if [ -f "$timing_file" ]; then
                phase_start=$(grep "PHASE_START_TIME=" "$timing_file" 2>/dev/null | tail -1 | cut -d= -f2 || echo "0")
                [ -n "$phase_start" ] && [ "$phase_start" -gt 0 ] 2>/dev/null && echo "STARTING_TIME=$((current_time - phase_start))" >> "$timing_file"
            fi
            echo "PHASE_START_TIME=$current_time" >> "$timing_file"
            echo -ne "\033]11;#ff6b35\007\033]10;#ffffff\007"  # Orange background
        fi

        # Switch to "Testing" phase when tests start
        if ! $in_testing && echo "$recent_lines" | grep -q "Run starting" 2>/dev/null; then
            phase="Testing"
            in_testing=true
            # Record testing phase
            current_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || date +%s000)
            if [ -f "$timing_file" ]; then
                phase_start=$(grep "PHASE_START_TIME=" "$timing_file" 2>/dev/null | tail -1 | cut -d= -f2 || echo "0")
                [ -n "$phase_start" ] && [ "$phase_start" -gt 0 ] 2>/dev/null && echo "BUILDING_TIME=$((current_time - phase_start))" >> "$timing_file"
            fi
            echo "PHASE_START_TIME=$current_time" >> "$timing_file"
            echo -ne "\033]11;#001f3f\007\033]10;#ffffff\007"  # Blue background
        fi

        # Extract current running test suite and scenario from recent lines
        suite=""
        scenario=""
        if $in_testing; then
            suite=$(echo "$recent_lines" | grep -E "Test:" 2>/dev/null | tail -1 | sed 's/\x1b\[[0-9;]*m//g' | sed 's/:$//' | tr -d '\n\r' || true)
            scenario=$(echo "$recent_lines" | grep -i "scenario:" 2>/dev/null | tail -1 | sed 's/\x1b\[[0-9;]*m//g' | sed 's/^[[:space:]]*-*[[:space:]]*//' | sed -E 's/^[Ss]cenario:[[:space:]]*//' | tr -d '\n\r' || true)
            [ -n "$scenario" ] && [ ${#scenario} -gt 50 ] && scenario="${scenario:0:47}..."
        fi

        # Calculate elapsed time
        duration=$(($(date +%s) - START_TIME))
        minutes=$((duration / 60))
        seconds=$((duration % 60))
        elapsed=$(printf "%dm %ds" $minutes $seconds)

        # Update title
        title="OBP-API ${phase}"
        [ -n "$suite" ] && title="${title}: ${suite}"
        [ -n "$scenario" ] && title="${title} - ${scenario}"
        title="${title}... [${elapsed}]"
        echo -ne "\033]0;${title}\007"

        sleep 5
    done
) &
MONITOR_PID=$!

# Run Maven with optional timeout
if [ "$TIMEOUT_MINUTES" -gt 0 ] 2>/dev/null; then
    # Run Maven in background and monitor for timeout
    mvn clean test 2>&1 | tee "${DETAIL_LOG}" &
    MAVEN_PID=$!
    
    elapsed=0
    while kill -0 $MAVEN_PID 2>/dev/null; do
        sleep 10
        elapsed=$((elapsed + 10))
        if [ $elapsed -ge $TIMEOUT_SECONDS ]; then
            log_message ""
            log_message "[TIMEOUT] Test execution exceeded ${TIMEOUT_MINUTES} minutes - terminating"
            kill -9 $MAVEN_PID 2>/dev/null || true
            # Also kill any child Java processes
            pkill -9 -P $MAVEN_PID 2>/dev/null || true
            TEST_RESULT="TIMEOUT"
            break
        fi
    done
    
    if [ "$TEST_RESULT" != "TIMEOUT" ]; then
        wait $MAVEN_PID
        if [ $? -eq 0 ]; then
            TEST_RESULT="SUCCESS"
        else
            TEST_RESULT="FAILURE"
        fi
    fi
else
    # Run Maven normally (all output goes to terminal AND log file)
    if mvn clean test 2>&1 | tee "${DETAIL_LOG}"; then
        TEST_RESULT="SUCCESS"
    else
        TEST_RESULT="FAILURE"
    fi
fi

################################################################################
# GENERATE HTML REPORT
################################################################################

print_header "Generating HTML Report"
log_message "Running: mvn surefire-report:report-only -DskipTests"

# Generate HTML report from surefire XML files (without re-running tests)
if mvn surefire-report:report-only -DskipTests 2>&1 | tee -a "${DETAIL_LOG}"; then
    log_message "[OK] HTML report generated"
    
    # Copy HTML reports to test-results directory for easy access
    HTML_REPORT_DIR="${LOG_DIR}/html-reports"
    mkdir -p "${HTML_REPORT_DIR}"
    
    # Copy reports from both modules
    if [ -f "obp-api/target/surefire-reports/surefire.html" ]; then
        cp "obp-api/target/surefire-reports/surefire.html" "${HTML_REPORT_DIR}/obp-api-report.html"
        # Also copy CSS, JS, images for proper rendering
        cp -r "obp-api/target/surefire-reports/css" "${HTML_REPORT_DIR}/" 2>/dev/null || true
        cp -r "obp-api/target/surefire-reports/js" "${HTML_REPORT_DIR}/" 2>/dev/null || true
        cp -r "obp-api/target/surefire-reports/images" "${HTML_REPORT_DIR}/" 2>/dev/null || true
        cp -r "obp-api/target/surefire-reports/fonts" "${HTML_REPORT_DIR}/" 2>/dev/null || true
        cp -r "obp-api/target/surefire-reports/img" "${HTML_REPORT_DIR}/" 2>/dev/null || true
        log_message "  - obp-api report: ${HTML_REPORT_DIR}/obp-api-report.html"
    fi
    if [ -f "obp-commons/target/surefire-reports/surefire.html" ]; then
        cp "obp-commons/target/surefire-reports/surefire.html" "${HTML_REPORT_DIR}/obp-commons-report.html"
        log_message "  - obp-commons report: ${HTML_REPORT_DIR}/obp-commons-report.html"
    fi
    
    # Also check for site reports location (alternative naming)
    if [ -f "obp-api/target/site/surefire-report.html" ]; then
        cp "obp-api/target/site/surefire-report.html" "${HTML_REPORT_DIR}/obp-api-report.html"
        log_message "  - obp-api report: ${HTML_REPORT_DIR}/obp-api-report.html"
    fi
    if [ -f "obp-commons/target/site/surefire-report.html" ]; then
        cp "obp-commons/target/site/surefire-report.html" "${HTML_REPORT_DIR}/obp-commons-report.html"
        log_message "  - obp-commons report: ${HTML_REPORT_DIR}/obp-commons-report.html"
    fi
else
    log_message "[WARNING] Failed to generate HTML report"
fi

log_message ""

# Stop background monitor by removing flag file
rm -f "${MONITOR_FLAG}"
sleep 1
kill $MONITOR_PID 2>/dev/null || true
wait $MONITOR_PID 2>/dev/null || true

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
DURATION_MIN=$((DURATION / 60))
DURATION_SEC=$((DURATION % 60))

# Update title with final results (no suite/scenario name for Complete phase)
FINAL_ELAPSED=$(printf "%dm %ds" $DURATION_MIN $DURATION_SEC)
# Build final counts with module context
FINAL_COMMONS=$(sed -n '/Building Open Bank Project Commons/,/Building Open Bank Project API/{/Tests: succeeded/p;}' "${DETAIL_LOG}" 2>/dev/null | sed 's/.*succeeded //' | sed 's/,.*//' | head -1)
FINAL_API=$(sed -n '/Building Open Bank Project API/,/OBP Http4s Runner/{/Tests: succeeded/p;}' "${DETAIL_LOG}" 2>/dev/null | sed 's/.*succeeded //' | sed 's/,.*//' | tail -1)
FINAL_COUNTS=""
[ -n "$FINAL_COMMONS" ] && FINAL_COUNTS="commons:+${FINAL_COMMONS}"
[ -n "$FINAL_API" ] && FINAL_COUNTS="${FINAL_COUNTS:+${FINAL_COUNTS} }api:+${FINAL_API}"

# Record complete phase start and change to green for completion phase
record_phase_time "complete"
set_terminal_style "Complete"
update_terminal_title "Complete" "$FINAL_ELAPSED" "$FINAL_COUNTS" "" ""

################################################################################
# GENERATE SUMMARY (using DRY function)
################################################################################

if generate_summary "${DETAIL_LOG}" "${SUMMARY_LOG}" "$START_TIME" "$END_TIME"; then
    EXIT_CODE=0
else
    EXIT_CODE=1
fi

# Record end time for complete phase
record_phase_time "end"

log_message ""
log_message "Logs saved to:"
log_message "  ${DETAIL_LOG}"
log_message "  ${SUMMARY_LOG}"
if [ -f "${FAILED_TESTS_FILE}" ]; then
    log_message "  ${FAILED_TESTS_FILE}"
fi
if [ -d "${LOG_DIR}/html-reports" ]; then
    log_message ""
    log_message "HTML Reports:"
    for report in "${LOG_DIR}/html-reports"/*.html; do
        [ -f "$report" ] && log_message "  $report"
    done
fi
echo ""

exit ${EXIT_CODE}
