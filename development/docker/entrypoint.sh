#!/bin/bash
set -e

export MAVEN_OPTS="-Xss128m \
 --add-opens=java.base/java.util.jar=ALL-UNNAMED \
 --add-opens=java.base/java.lang=ALL-UNNAMED \
 --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"

exec java $MAVEN_OPTS -jar /app/obp-api.jar
