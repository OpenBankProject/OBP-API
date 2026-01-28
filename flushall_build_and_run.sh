#!/bin/bash

# Script to flush Redis, build the project, and run both Jetty and http4s servers
#
# This script should be run from the OBP-API root directory:
#   cd /path/to/OBP-API
#   ./flushall_build_and_run.sh
#
# The http4s server will run in the background on port 8081
# The Jetty server will run in the foreground on port 8080

set -e  # Exit on error

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
echo "Building and running with Maven..."
echo "=========================================="
export MAVEN_OPTS="-Xss128m --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED"
mvn install -pl .,obp-commons

echo ""
echo "=========================================="
echo "Building http4s runner..."
echo "=========================================="
export MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G"
mvn -pl obp-http4s-runner -am clean package -DskipTests=true -Dmaven.test.skip=true

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
export MAVEN_OPTS="-Xss128m --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED"
mvn jetty:run -pl obp-api
