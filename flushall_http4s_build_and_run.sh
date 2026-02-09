#!/bin/bash

# Script to flush Redis, build the project, and run the http4s server
#
# This script should be run from the OBP-API root directory:
#   cd /path/to/OBP-API
#   ./flushall_http4s_build_and_run.sh
#
# The http4s server will run in the foreground on the port configured
# in your props file (default: 8086)

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
echo "Building with Maven..."
echo "=========================================="
export MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G"
mvn -pl obp-http4s-runner -am clean package -DskipTests=true -Dmaven.test.skip=true

echo ""
echo "=========================================="
echo "Starting http4s server (foreground)..."
echo "=========================================="
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens java.base/java.util.jar=ALL-UNNAMED \
  --add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
  -jar obp-http4s-runner/target/obp-http4s-runner.jar
