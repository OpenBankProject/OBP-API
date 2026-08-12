#!/bin/bash

################################################################################
# OBP-API Build and Run Script (HTTP4S Server)
#
# This script builds the OBP-API project and runs the HTTP4S server.
# It replaces the obsolete flushall_build_and_run.sh which referenced
# the removed obp-http4s-runner module and Jetty server.
#
# Usage:
#   ./build_and_run.sh              - Build and run with Redis flush
#   ./build_and_run.sh --no-flush   - Build and run without Redis flush
#   ./build_and_run.sh --background - Run server in background
#   ./build_and_run.sh --mtls       - Serve HTTPS with mutual TLS (dev only)
#
# The HTTP4S server runs on the port configured in your props file
# (default: 8080 for dev.port, or 8086 for hostname port)
################################################################################

set -e  # Exit on error

# Parse arguments
FLUSH_REDIS=true
RUN_BACKGROUND=false
USE_MTLS=false

for arg in "$@"; do
    case $arg in
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
# BUILD PROJECT
################################################################################

echo "=========================================="
echo "Building OBP-API with Maven..."
echo "=========================================="

# Maven options for build performance
# Memory:
# - 3-6GB heap (balanced for both Maven and Scala compiler)
# - 2GB metaspace for class metadata
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

echo "Maven Options: ${MAVEN_OPTS}"
echo ""

# Build obp-api module (includes obp-commons as dependency)
# - clean: Remove old build artifacts
# - package: Compile and create JAR with maven-shade-plugin
# - -pl obp-api -am: Build obp-api and all required modules
# - -DskipTests: Skip test execution for faster builds
# - -T 4: Use 4 threads for parallel compilation
echo "Building obp-api module..."
echo "Build output will be saved to: build.log"
> build.log
# Show last 3 lines of build output in real-time
tail -n 3 -f build.log &
TAIL_PID=$!
# set +e: this script runs under `set -e`, which would abort at a failing build
# before the BUILD_EXIT check below could print the log tail.
set +e
mvn -pl obp-api -am clean package -DskipTests=true -Dmaven.test.skip=true -T 4 > build.log 2>&1
BUILD_EXIT=$?
set -e
kill $TAIL_PID 2>/dev/null || true
wait $TAIL_PID 2>/dev/null || true

if [ $BUILD_EXIT -ne 0 ]; then
    echo ""
    echo "❌ Build failed! Please check build.log for details."
    echo "Last 30 lines of build log:"
    tail -30 build.log
    exit 1
fi

echo ""
echo "✓ Build completed successfully"
echo "✓ JAR created: obp-api/target/obp-api.jar (thin jar — runtime deps in obp-api/target/lib/)"
echo "✓ Build log saved to: build.log"
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

RUNTIME_LOG=/tmp/obp-api.log

if [ "$RUN_BACKGROUND" = true ]; then
    # Run in background with output redirected straight to a log file. Do NOT
    # use `> >(tee "$RUNTIME_LOG")` here: process substitution's tee inherits
    # this script's own stdout, so a caller that captures this script's output
    # via `out=$(./flushall_build_and_run.sh --background ...)` never sees EOF
    # — the substitution hangs forever, since the server (and thus the tee
    # process backing the substitution) never exits on its own.
    nohup java $JAVA_OPTS -jar obp-api/target/obp-api.jar > "$RUNTIME_LOG" 2>&1 &
    SERVER_PID=$!
    # Report the port the server will actually bind (dev.port in props), so callers
    # that capture this script's output (e.g. smoke_test.sh) can parse it out.
    SERVER_PORT=$(grep -E '^dev.port=' obp-api/src/main/resources/props/default.props 2>/dev/null | cut -d= -f2)
    echo "✓ HTTP4S server started in background"
    echo "  PID: $SERVER_PID"
    echo "  PORT: ${SERVER_PORT:-8080}"
    echo "  Log: $RUNTIME_LOG"
    echo ""
    echo "To stop the server: kill $SERVER_PID"
    echo "To view logs: tail -f $RUNTIME_LOG"
else
    # Run in foreground (Ctrl+C to stop). Also tee output to /tmp so it can be
    # tailed from another terminal without taking over this one.
    echo "Press Ctrl+C to stop the server"
    echo "Runtime log also written to: $RUNTIME_LOG"
    echo ""
    java $JAVA_OPTS -jar obp-api/target/obp-api.jar 2>&1 | tee "$RUNTIME_LOG"
fi
