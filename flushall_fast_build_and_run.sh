#!/bin/bash

################################################################################
# OBP-API Fast Build and Run Script (HTTP4S Server)
#
# This is an optimized version of build_and_run.sh with:
# - Incremental builds (no clean by default)
# - Offline mode by default (faster, skip remote repo checks)
# - Parallel compilation (uses all CPU cores)
# - Auto-retry with clean build on cache issues
# - More aggressive memory allocation
# - Optimized JVM flags for faster compilation
#
# Usage:
#   ./fast_build_and_run.sh                - Fast incremental build (offline)
#   ./fast_build_and_run.sh --clean        - Force clean build
#   ./fast_build_and_run.sh --online       - Check remote repositories
#   ./fast_build_and_run.sh --no-flush     - Skip Redis flush
#   ./fast_build_and_run.sh --background   - Run server in background
#   ./fast_build_and_run.sh --mtls         - Serve HTTPS with mutual TLS (dev only)
#
# Typical speedup: 2-5x faster than regular build for incremental changes
################################################################################

set -e  # Exit on error

# Parse arguments
DO_CLEAN=""
OFFLINE_FLAG="-o"  # Default to offline mode for faster builds
FLUSH_REDIS=true
RUN_BACKGROUND=false
USE_MTLS=false

for arg in "$@"; do
    case $arg in
        --clean)
            DO_CLEAN="clean"
            echo ">>> Clean build requested"
            ;;
        --online)
            OFFLINE_FLAG=""
            echo ">>> Online mode enabled (will check remote repositories)"
            ;;
        --no-flush)
            FLUSH_REDIS=false
            echo ">>> Skipping Redis flush"
            ;;
        --background)
            RUN_BACKGROUND=true
            echo ">>> Server will run in background"
            ;;
        --mtls)
            USE_MTLS=true
            echo ">>> mTLS mode requested (in-process TLS termination)"
            ;;
    esac
done

# Pin the JDK before any Maven work: the project builds and runs on one JDK only
# (pom.xml <java.version>), and scripts/java_env.sh aborts if it is not available.
. "$(dirname "${BASH_SOURCE[0]}")/scripts/java_env.sh"

# Show offline mode status if not explicitly set
if [ "$OFFLINE_FLAG" = "-o" ]; then
    echo ">>> Using offline mode (default) - use --online to check remote repos"
fi

# Detect CPU cores for parallel builds
if command -v nproc &> /dev/null; then
    CORES=$(nproc)
elif command -v sysctl &> /dev/null; then
    CORES=$(sysctl -n hw.ncpu)
else
    CORES=4
fi
echo ">>> Using $CORES CPU cores for parallel builds"
echo ""

################################################################################
# FLUSH REDIS CACHE (OPTIONAL)
################################################################################

if [ "$FLUSH_REDIS" = true ]; then
    echo "=========================================="
    echo "Flushing Redis cache..."
    echo "=========================================="
    
    if command -v redis-cli &> /dev/null; then
        redis-cli <<EOF
flushall
exit
EOF
        if [ $? -eq 0 ]; then
            echo "✓ Redis cache flushed successfully"
        else
            echo "⚠ Warning: Failed to flush Redis cache. Continuing anyway..."
        fi
    else
        echo "⚠ Warning: redis-cli not found. Skipping Redis flush..."
    fi
    echo ""
fi

################################################################################
# FAST BUILD WITH OPTIMIZATIONS
################################################################################

echo "=========================================="
echo "Fast build: OBP-API with optimizations..."
echo "=========================================="

# Aggressive Maven options for maximum build performance
# Memory:
# - 3-6GB heap (balanced for both Maven and Scala compiler)
# - 2GB metaspace
# - 4m stack for Scala compiler (matches POM config)
#
# JVM Optimizations:
# - G1GC: Better garbage collection for large heaps
# - ReservedCodeCacheSize: More space for JIT compiled code
# - TieredCompilation: Faster JIT compilation
# - TieredStopAtLevel=1: Skip C2 compiler for faster startup (build-time only)
#
# Java Module Opens:
# - Required for Java 11+ compatibility
export MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G -Xss4m \
-XX:+UseG1GC \
-XX:ReservedCodeCacheSize=512m \
-XX:+TieredCompilation \
-XX:TieredStopAtLevel=1 \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens java.base/java.util.jar=ALL-UNNAMED \
--add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED"

echo "Maven Options: Optimized for speed"
echo "  Heap: 4-8GB"
echo "  Threads: $CORES (1 per core)"
echo "  Mode: ${DO_CLEAN:-Incremental}"
echo ""

# Fast build command with all optimizations
# - -T 1C: Use 1 thread per CPU core (parallel compilation)
# - $DO_CLEAN: Only clean if --clean flag passed (incremental by default)
# - $OFFLINE_FLAG: Skip remote repo checks if --offline passed
# - -DskipTests: Skip test execution
# - -Dmaven.test.skip: Skip test compilation too
# - -Dcheckstyle.skip: Skip code style checks
# - -Dspotbugs.skip: Skip static analysis
# - -Dpmd.skip: Skip PMD checks
echo "Building obp-api module with parallel compilation..."
echo "Build output will be saved to: fast_build.log"

# Record build start time with milliseconds
# macOS compatible: use gdate if available, otherwise use date without milliseconds
if command -v gdate &> /dev/null; then
    BUILD_START=$(gdate '+%Y-%m-%d %H:%M:%S.%3N')
    BUILD_START_EPOCH=$(gdate '+%s%3N')
else
    BUILD_START=$(date '+%Y-%m-%d %H:%M:%S')
    BUILD_START_EPOCH=$(($(date '+%s') * 1000))
fi

# Write build header with timestamp to log
{
    echo "================================================================================"
    echo "Build started at: $BUILD_START"
    echo "Build mode: ${DO_CLEAN:-Incremental}"
    echo "Offline mode: ${OFFLINE_FLAG:+Yes}"
    echo "CPU cores: $CORES"
    echo "================================================================================"
    echo ""
} > fast_build.log

# Run Maven build and append to log
# set +e around the build: this script runs under `set -e`, which would abort here
# on a failing build and make BUILD_EXIT_CODE — and the auto-retry-with-clean block
# below — unreachable. We want to inspect the failure, not die at it.
set +e
mvn -pl obp-api -am \
    $DO_CLEAN \
    package \
    -T 1C \
    $OFFLINE_FLAG \
    -DskipTests=true \
    -Dmaven.test.skip=true \
    -Dcheckstyle.skip=true \
    -Dspotbugs.skip=true \
    -Dpmd.skip=true >> fast_build.log 2>&1
BUILD_EXIT_CODE=$?
set -e

# Record build end time and calculate duration
if command -v gdate &> /dev/null; then
    BUILD_END=$(gdate '+%Y-%m-%d %H:%M:%S.%3N')
    BUILD_END_EPOCH=$(gdate '+%s%3N')
else
    BUILD_END=$(date '+%Y-%m-%d %H:%M:%S')
    BUILD_END_EPOCH=$(($(date '+%s') * 1000))
fi
BUILD_DURATION=$((BUILD_END_EPOCH - BUILD_START_EPOCH))
BUILD_DURATION_SEC=$((BUILD_DURATION / 1000))
BUILD_DURATION_MS=$((BUILD_DURATION % 1000))

# Append build footer with timestamp to log
{
    echo ""
    echo "================================================================================"
    echo "Build ended at: $BUILD_END"
    echo "Build duration: ${BUILD_DURATION_SEC}.${BUILD_DURATION_MS}s"
    echo "Build result: $([ $BUILD_EXIT_CODE -eq 0 ] && echo 'SUCCESS' || echo 'FAILED')"
    echo "================================================================================"
} >> fast_build.log

# Auto-retry online if the offline default could not resolve something. This build
# runs with -o by default, so any artifact that is not in the local repository yet
# fails the whole build - including a plugin newly added to pom.xml, which no
# amount of cleaning or rebuilding can fix. Retrying once online is the only thing
# that helps, and it leaves the local repository warm for subsequent offline runs.
if [ $BUILD_EXIT_CODE -ne 0 ] && [ -n "$OFFLINE_FLAG" ] \
   && grep -q "in offline mode" fast_build.log; then
    echo ""
    echo "⚠️  Offline build could not resolve an artifact. Retrying once with network access..."
    { grep -oE "The following artifacts could not be resolved: [^ ]+|Plugin [^ ]+ or one of its dependencies could not be resolved" fast_build.log | head -1 | sed 's/^/   /'; } || true
    mv fast_build.log fast_build_offline_failed.log
    echo "   Previous build log saved to: fast_build_offline_failed.log"
    OFFLINE_FLAG=""
    set +e
    mvn -pl obp-api -am \
        $DO_CLEAN \
        package \
        -T 1C \
        -DskipTests=true \
        -Dmaven.test.skip=true \
        -Dcheckstyle.skip=true \
        -Dspotbugs.skip=true \
        -Dpmd.skip=true >> fast_build.log 2>&1
    BUILD_EXIT_CODE=$?
    set -e
    echo "   Online retry: $([ $BUILD_EXIT_CODE -eq 0 ] && echo 'SUCCESS' || echo 'FAILED')"
fi

# Auto-retry with clean build if incremental build fails
if [ $BUILD_EXIT_CODE -ne 0 ] && [ -z "$DO_CLEAN" ]; then
    echo ""
    echo "⚠️  Incremental build failed. Checking if this is a cache issue..."
    
    # Check if error is related to missing classes/packages (common cache issue)
    if grep -q "is not a member of package\|cannot find symbol\|not found: type\|not found: value" fast_build.log; then
        echo "Detected incremental compilation cache issue. Retrying with clean build..."
        echo ""
        
        # Backup the failed incremental build log
        mv fast_build.log fast_build_incremental_failed.log
        echo "   Previous build log saved to: fast_build_incremental_failed.log"
        
        # Retry with clean build
        echo "   Running clean build (this may take 2-3 minutes)..."
        
        # Record retry start time
        if command -v gdate &> /dev/null; then
            RETRY_START=$(gdate '+%Y-%m-%d %H:%M:%S.%3N')
            RETRY_START_EPOCH=$(gdate '+%s%3N')
        else
            RETRY_START=$(date '+%Y-%m-%d %H:%M:%S')
            RETRY_START_EPOCH=$(($(date '+%s') * 1000))
        fi
        
        # Write retry header with timestamp to log
        {
            echo "================================================================================"
            echo "Clean build retry started at: $RETRY_START"
            echo "Build mode: Clean (auto-retry)"
            echo "Offline mode: ${OFFLINE_FLAG:+Yes}"
            echo "CPU cores: $CORES"
            echo "================================================================================"
            echo ""
        } > fast_build.log
        
        # Run clean build (set +e for the same reason as the first invocation)
        set +e
        mvn -pl obp-api -am \
            clean \
            package \
            -T 1C \
            $OFFLINE_FLAG \
            -DskipTests=true \
            -Dmaven.test.skip=true \
            -Dcheckstyle.skip=true \
            -Dspotbugs.skip=true \
            -Dpmd.skip=true >> fast_build.log 2>&1
        BUILD_EXIT_CODE=$?
        set -e
        
        # Record retry end time and calculate duration
        if command -v gdate &> /dev/null; then
            RETRY_END=$(gdate '+%Y-%m-%d %H:%M:%S.%3N')
            RETRY_END_EPOCH=$(gdate '+%s%3N')
        else
            RETRY_END=$(date '+%Y-%m-%d %H:%M:%S')
            RETRY_END_EPOCH=$(($(date '+%s') * 1000))
        fi
        RETRY_DURATION=$((RETRY_END_EPOCH - RETRY_START_EPOCH))
        RETRY_DURATION_SEC=$((RETRY_DURATION / 1000))
        RETRY_DURATION_MS=$((RETRY_DURATION % 1000))
        
        # Append retry footer with timestamp to log
        {
            echo ""
            echo "================================================================================"
            echo "Clean build retry ended at: $RETRY_END"
            echo "Build duration: ${RETRY_DURATION_SEC}.${RETRY_DURATION_MS}s"
            echo "Build result: $([ $BUILD_EXIT_CODE -eq 0 ] && echo 'SUCCESS' || echo 'FAILED')"
            echo "================================================================================"
        } >> fast_build.log
        
        if [ $BUILD_EXIT_CODE -eq 0 ]; then
            echo ""
            echo "✓ Clean build succeeded! The issue was incremental compilation cache."
            echo "💡 Tip: This usually happens after switching branches or updating dependencies."
        fi
    fi
fi

# Final error check
if [ $BUILD_EXIT_CODE -ne 0 ]; then
    echo ""
    echo "Build failed! Please check fast_build.log for details."
    echo "Last 30 lines of build log:"
    tail -30 fast_build.log
    exit 1
fi

echo ""
echo "✓ Fast build completed successfully"
echo "✓ JAR created: obp-api/target/obp-api.jar (thin jar — runtime deps in obp-api/target/lib/)"
echo "✓ Build log saved to: fast_build.log"
echo ""

################################################################################
# RUN HTTP4S SERVER
################################################################################

# Applied after the build so it only affects the running server, never compilation.
if [[ "$USE_MTLS" = true ]]; then
    . "$(dirname "${BASH_SOURCE[0]}")/scripts/mtls_env.sh"
    echo ""
fi

echo "=========================================="
if [ "$RUN_BACKGROUND" = true ]; then
    echo "Starting HTTP4S server (background)..."
else
    echo "Starting HTTP4S server (foreground)..."
fi
echo "=========================================="

# Java options for runtime
# - Module opens for Kryo serialization and reflection
JAVA_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens java.base/java.util.jar=ALL-UNNAMED \
--add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
--add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens java.base/java.security=ALL-UNNAMED"

if [ "$RUN_BACKGROUND" = true ]; then
    # Run in background with output to log file
    env \
      OBP_CONNECTOR="${OBP_CONNECTOR:-star}" \
      OBP_HOSTNAME="${OBP_HOSTNAME:-http://localhost:8080}" \
      OBP_STARCONNECTOR_SUPPORTED_TYPES="${OBP_STARCONNECTOR_SUPPORTED_TYPES:-mapped,internal,cardano_vJun2025}" \
      OBP_BERLIN_GROUP_MANDATORY_HEADERS="${OBP_BERLIN_GROUP_MANDATORY_HEADERS:-}" \
      OBP_BERLIN_GROUP_MANDATORY_HEADER_CONSENT="${OBP_BERLIN_GROUP_MANDATORY_HEADER_CONSENT:-}" \
      OBP_API_INSTANCE_ID="${OBP_API_INSTANCE_ID:-local}" \
      nohup java $JAVA_OPTS -jar obp-api/target/obp-api.jar > http4s-server.log 2>&1 &
    SERVER_PID=$!
    # Report the port the server will actually bind (dev.port in props), so callers
    # that capture this script's output (e.g. smoke_test.sh) can parse it out.
    SERVER_PORT=$(grep -E '^dev.port=' obp-api/src/main/resources/props/default.props 2>/dev/null | cut -d= -f2)
    echo "✓ HTTP4S server started in background"
    echo "  PID: $SERVER_PID"
    echo "  PORT: ${SERVER_PORT:-8080}"
    echo "  Log: http4s-server.log"
    echo ""
    echo "To stop the server: kill $SERVER_PID"
    echo "To view logs: tail -f http4s-server.log"
else
    # Run in foreground (Ctrl+C to stop)
    echo "Press Ctrl+C to stop the server"
    echo ""
    env \
      OBP_CONNECTOR="${OBP_CONNECTOR:-star}" \
      OBP_HOSTNAME="${OBP_HOSTNAME:-http://localhost:8080}" \
      OBP_STARCONNECTOR_SUPPORTED_TYPES="${OBP_STARCONNECTOR_SUPPORTED_TYPES:-mapped,internal,cardano_vJun2025}" \
      OBP_BERLIN_GROUP_MANDATORY_HEADERS="${OBP_BERLIN_GROUP_MANDATORY_HEADERS:-}" \
      OBP_BERLIN_GROUP_MANDATORY_HEADER_CONSENT="${OBP_BERLIN_GROUP_MANDATORY_HEADER_CONSENT:-}" \
      OBP_API_INSTANCE_ID="${OBP_API_INSTANCE_ID:-local}" \
      java $JAVA_OPTS -jar obp-api/target/obp-api.jar
fi

################################################################################
# PERFORMANCE TIPS
################################################################################
# 
# For even faster builds:
# 1. Use --offline flag if dependencies haven't changed
# 2. Don't use --clean unless you have compilation issues
# 3. Increase heap size if you have more RAM: export MAVEN_OPTS="-Xms6G -Xmx12G ..."
# 4. Use SSD for faster I/O
# 5. Close other applications to free up CPU cores
# 6. Ensure you have at least 8GB RAM available for optimal performance
#
# Optimizations applied:
# - Scala compiler: 3GB heap, 4MB stack, G1GC
# - Parallel backend compilation: 4 threads
# - Incremental compilation with Zinc
# - Maven parallel builds: 1 thread per CPU core
# - Code cache: 512MB for JIT compilation
#
# Typical build times (on modern hardware with optimizations):
# - Incremental build: 20-40 seconds (was 30-60s)
# - Clean build: 1.5-3 minutes (was 2-4 minutes)
# - Full test suite: 10-15 minutes
################################################################################
