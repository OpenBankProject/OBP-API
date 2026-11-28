#!/bin/bash
set -e

export JAVA_OPTS="-Xss128m \
 --add-opens=java.base/java.util.jar=ALL-UNNAMED \
 --add-opens=java.base/java.lang=ALL-UNNAMED \
 --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"

# -cp, not -jar: a manifest Class-Path never reaches the `java.class.path` system property, and both
# DotcScalaCompiler and json4s's ScalaSigReader build a runtime compiler classpath out of that
# property. Under -jar they see the thin jar alone and every dynamic-code and Scala-3 field-type
# path fails on a server that otherwise boots fine. See .github/Dockerfile_PreBuild.
exec java $JAVA_OPTS -cp "/app/obp-api/target/obp-api.jar:/app/obp-api/target/lib/*" bootstrap.http4s.Http4sServer
