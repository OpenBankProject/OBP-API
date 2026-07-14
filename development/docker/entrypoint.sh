#!/bin/bash
set -e

export JAVA_OPTS="-Xss128m \
 --add-opens=java.base/java.util.jar=ALL-UNNAMED \
 --add-opens=java.base/java.lang=ALL-UNNAMED \
 --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"

exec java $JAVA_OPTS -jar /app/obp-api/target/obp-api.jar
