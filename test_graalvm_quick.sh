#!/bin/bash
# Quick GraalVM test - Run this in your terminal with Java 11

echo "Java version:"
java -version
echo ""

echo "Running DynamicMessageDocTest (this should pass with Java 11)..."
MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G -XX:+UseG1GC" \
  mvn scalatest:test -Dsuites=code.api.v4_0_0.DynamicMessageDocTest -pl obp-api -T 4 -o

echo ""
echo "Check if test passed above. If you see 'BUILD SUCCESS' and no NoSuchMethodError, the fix works!"
