#!/bin/bash

################################################################################
# OBP-API Fast Build and Run Script (HTTP4S Server)
#
# This is an optimized version of build_and_run.sh with:
# - Incremental builds (no clean by default)
# - Parallel compilation (uses all CPU cores)
# - Offline mode support (skip remote repo checks)
# - More aggressive memory allocation
# - Optimized JVM flags for faster compilation
#
# Usage:
#   ./fast_build_and_run.sh                - Fast incremental build
#   ./fast_build_and_run.sh --clean        - Force clean build
#   ./fast_build_and_run.sh --offline      - Skip remote repo checks
#   ./fast_build_and_run.sh --no-flush     - Skip Redis flush
#   ./fast_build_and_run.sh --background   - Run server in background
#
# Typical speedup: 2-5x faster than regular build for incremental changes
################################################################################

set -e  # Exit on error

# Parse arguments
DO_CLEAN=""
OFFLINE_FLAG=""
FLUSH_REDIS=true
RUN_BACKGROUND=false

for arg in "$@"; do
    case $arg in
        --clean)
            DO_CLEAN="clean"
            echo ">>> Clean build requested"
            ;;
        --offline)
            OFFLINE_FLAG="-o"
            echo ">>> Offline mode enabled"
            ;;
        --no-flush)
            FLUSH_REDIS=false
            echo ">>> Skipping Redis flush"
            ;;
        --background)
            RUN_BACKGROUND=true
            echo ">>> Server will run in background"
            ;;
    esac
done

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
# - 4-8GB heap (more than standard build)
# - 2GB metaspace
# - 128m stack for Scala compiler
#
# JVM Optimizations:
# - G1GC: Better garbage collection for large heaps
# - TieredCompilation: Faster JIT compilation
# - TieredStopAtLevel=1: Skip C2 compiler for faster startup
#
# Java Module Opens:
# - Required for Java 11+ compatibility
export MAVEN_OPTS="-Xms4G -Xmx8G -XX:MaxMetaspaceSize=2G -Xss128m \
-XX:+UseG1GC \
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
mvn -pl obp-api -am \
    $DO_CLEAN \
    package \
    -T 1C \
    $OFFLINE_FLAG \
    -DskipTests=true \
    -Dmaven.test.skip=true \
    -Dcheckstyle.skip=true \
    -Dspotbugs.skip=true \
    -Dpmd.skip=true

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Build failed! Please check the error messages above."
    exit 1
fi

echo ""
echo "✓ Fast build completed successfully"
echo "✓ JAR created: obp-api/target/obp-api.jar"
echo ""

################################################################################
# RUN HTTP4S SERVER
################################################################################

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
--add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED"

if [ "$RUN_BACKGROUND" = true ]; then
    # Run in background with output to log file
    nohup java $JAVA_OPTS -jar obp-api/target/obp-api.jar > http4s-server.log 2>&1 &
    SERVER_PID=$!
    echo "✓ HTTP4S server started in background"
    echo "  PID: $SERVER_PID"
    echo "  Log: http4s-server.log"
    echo ""
    echo "To stop the server: kill $SERVER_PID"
    echo "To view logs: tail -f http4s-server.log"
else
    # Run in foreground (Ctrl+C to stop)
    echo "Press Ctrl+C to stop the server"
    echo ""
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
#
# Typical build times (on modern hardware):
# - Incremental build: 30-60 seconds
# - Clean build: 2-4 minutes
# - Full test suite: 10-15 minutes
################################################################################
