#!/bin/bash

# Fast build script - skips clean, uses parallel builds, more RAM
#
# This script should be run from the OBP-API root directory:
#   cd /path/to/OBP-API
#   ./flushall_fast_build_and_run.sh
#
# Options:
#   --clean    Force a clean build (slower, but useful if you have issues)
#   --offline  Skip checking remote repos (faster if deps haven't changed)
#
# The http4s server will run in the background on port 8081
# The Jetty server will run in the foreground on port 8080

set -e  # Exit on error

# Parse arguments
DO_CLEAN=""
OFFLINE_FLAG=""
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

# Common Maven options for better performance
# - More heap memory (4G-8G)
# - More metaspace (2G)
# - Larger stack for Scala compiler
# - Java module opens for compatibility
export MAVEN_OPTS="-Xms4G -Xmx8G -XX:MaxMetaspaceSize=2G -Xss128m \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED"

echo "=========================================="
echo "Flushing Redis cache..."
echo "=========================================="
redis-cli <<EOF
flushall
exit
EOF

if [ $? -eq 0 ]; then
    echo "Redis cache flushed successfully"
else
    echo "Warning: Failed to flush Redis cache. Continuing anyway..."
fi

echo ""
echo "=========================================="
echo "Fast build: installing commons + packaging http4s runner..."
echo "=========================================="

# Build everything needed in ONE Maven invocation:
# - Install root pom and obp-commons first
# - Then package obp-http4s-runner (which depends on obp-api)
#
# Optimizations:
# - -T 1C: Use 1 thread per CPU core
# - No 'clean' by default (incremental build)
# - -DskipTests: Skip running tests
# - -Dmaven.test.skip: Skip compiling tests too
# - $OFFLINE_FLAG: Skip remote repo checks if --offline passed

mvn install -pl .,obp-commons \
    -T 1C \
    $OFFLINE_FLAG \
    -DskipTests=true \
    -Dmaven.test.skip=true \
    -Dcheckstyle.skip=true \
    -Dspotbugs.skip=true \
    -Dpmd.skip=true

mvn package -pl obp-http4s-runner -am \
    -T 1C \
    $DO_CLEAN \
    $OFFLINE_FLAG \
    -DskipTests=true \
    -Dmaven.test.skip=true \
    -Dcheckstyle.skip=true \
    -Dspotbugs.skip=true \
    -Dpmd.skip=true

echo ""
echo "=========================================="
echo "Starting http4s server in background..."
echo "=========================================="
java -jar obp-http4s-runner/target/obp-http4s-runner.jar > http4s-server.log 2>&1 &
HTTP4S_PID=$!
echo "http4s server started with PID: $HTTP4S_PID (port 8081)"
echo "Logs are being written to: http4s-server.log"
echo ""
echo "To stop http4s server later: kill $HTTP4S_PID"
echo ""

echo "=========================================="
echo "Starting Jetty server (foreground)..."
echo "=========================================="
mvn jetty:run -pl obp-api $OFFLINE_FLAG
