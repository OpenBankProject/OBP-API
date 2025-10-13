#!/bin/bash

# Generate portable Bloop configuration files for OBP-API
# This script creates Bloop JSON configurations with proper paths for any system

set -e

echo "🔧 Generating Bloop configuration files..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory (parent of zed folder)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "📁 Project root: $PROJECT_ROOT"

# Check if we're in the zed directory and project structure exists
if [[ ! -f "$PROJECT_ROOT/pom.xml" ]] || [[ ! -d "$PROJECT_ROOT/obp-api" ]] || [[ ! -d "$PROJECT_ROOT/obp-commons" ]]; then
    echo -e "${RED}❌ Error: Could not find OBP-API project structure${NC}"
    echo "Make sure you're running this from the zed/ folder of the OBP-API project"
    exit 1
fi

# Change to project root for Maven operations
cd "$PROJECT_ROOT"

# Detect Java home
if [[ -z "$JAVA_HOME" ]]; then
    JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    echo -e "${YELLOW}⚠️  JAVA_HOME not set, detected: $JAVA_HOME${NC}"
else
    echo -e "${GREEN}✅ JAVA_HOME: $JAVA_HOME${NC}"
fi

# Get Maven local repository
M2_REPO=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2>/dev/null || echo "$HOME/.m2/repository")
echo "📦 Maven repository: $M2_REPO"

# Ensure .bloop directory exists in project root
mkdir -p "$PROJECT_ROOT/.bloop"

# Generate obp-commons.json
echo "🔨 Generating obp-commons configuration..."
cat > "$PROJECT_ROOT/.bloop/obp-commons.json" << EOF
{
  "version": "1.5.5",
  "project": {
    "name": "obp-commons",
    "directory": "${PROJECT_ROOT}/obp-commons",
    "workspaceDir": "${PROJECT_ROOT}",
    "sources": [
      "${PROJECT_ROOT}/obp-commons/src/main/scala",
      "${PROJECT_ROOT}/obp-commons/src/main/java"
    ],
    "dependencies": [],
    "classpath": [
      "${PROJECT_ROOT}/obp-commons/target/classes",
      "${M2_REPO}/net/liftweb/lift-common_2.12/3.5.0/lift-common_2.12-3.5.0.jar",
      "${M2_REPO}/org/scala-lang/scala-library/2.12.12/scala-library-2.12.12.jar",
      "${M2_REPO}/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar",
      "${M2_REPO}/org/scala-lang/modules/scala-xml_2.12/1.3.0/scala-xml_2.12-1.3.0.jar",
      "${M2_REPO}/org/scala-lang/modules/scala-parser-combinators_2.12/1.1.2/scala-parser-combinators_2.12-1.1.2.jar",
      "${M2_REPO}/net/liftweb/lift-util_2.12/3.5.0/lift-util_2.12-3.5.0.jar",
      "${M2_REPO}/org/scala-lang/scala-compiler/2.12.12/scala-compiler-2.12.12.jar",
      "${M2_REPO}/net/liftweb/lift-actor_2.12/3.5.0/lift-actor_2.12-3.5.0.jar",
      "${M2_REPO}/net/liftweb/lift-markdown_2.12/3.5.0/lift-markdown_2.12-3.5.0.jar",
      "${M2_REPO}/joda-time/joda-time/2.10/joda-time-2.10.jar",
      "${M2_REPO}/org/joda/joda-convert/2.1/joda-convert-2.1.jar",
      "${M2_REPO}/commons-codec/commons-codec/1.11/commons-codec-1.11.jar",
      "${M2_REPO}/nu/validator/htmlparser/1.4.12/htmlparser-1.4.12.jar",
      "${M2_REPO}/xerces/xercesImpl/2.11.0/xercesImpl-2.11.0.jar",
      "${M2_REPO}/xml-apis/xml-apis/1.4.01/xml-apis-1.4.01.jar",
      "${M2_REPO}/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar",
      "${M2_REPO}/net/liftweb/lift-mapper_2.12/3.5.0/lift-mapper_2.12-3.5.0.jar",
      "${M2_REPO}/net/liftweb/lift-db_2.12/3.5.0/lift-db_2.12-3.5.0.jar",
      "${M2_REPO}/net/liftweb/lift-webkit_2.12/3.5.0/lift-webkit_2.12-3.5.0.jar",
      "${M2_REPO}/commons-fileupload/commons-fileupload/1.3.3/commons-fileupload-1.3.3.jar",
      "${M2_REPO}/commons-io/commons-io/2.2/commons-io-2.2.jar",
      "${M2_REPO}/org/mozilla/rhino/1.7.10/rhino-1.7.10.jar",
      "${M2_REPO}/net/liftweb/lift-proto_2.12/3.5.0/lift-proto_2.12-3.5.0.jar",
      "${M2_REPO}/org/scala-lang/scala-reflect/2.12.20/scala-reflect-2.12.20.jar",
      "${M2_REPO}/org/scalatest/scalatest_2.12/3.0.8/scalatest_2.12-3.0.8.jar",
      "${M2_REPO}/org/scalactic/scalactic_2.12/3.0.8/scalactic_2.12-3.0.8.jar",
      "${M2_REPO}/net/liftweb/lift-json_2.12/3.5.0/lift-json_2.12-3.5.0.jar",
      "${M2_REPO}/org/scala-lang/scalap/2.12.12/scalap-2.12.12.jar",
      "${M2_REPO}/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.jar",
      "${M2_REPO}/com/alibaba/transmittable-thread-local/2.11.5/transmittable-thread-local-2.11.5.jar",
      "${M2_REPO}/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar",
      "${M2_REPO}/org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar",
      "${M2_REPO}/com/google/guava/guava/32.0.0-jre/guava-32.0.0-jre.jar",
      "${M2_REPO}/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar",
      "${M2_REPO}/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
      "${M2_REPO}/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
      "${M2_REPO}/org/checkerframework/checker-qual/3.33.0/checker-qual-3.33.0.jar",
      "${M2_REPO}/com/google/errorprone/error_prone_annotations/2.18.0/error_prone_annotations-2.18.0.jar",
      "${M2_REPO}/com/google/j2objc/j2objc-annotations/2.8/j2objc-annotations-2.8.jar"
    ],
    "out": "${PROJECT_ROOT}/obp-commons/target/classes",
    "classesDir": "${PROJECT_ROOT}/obp-commons/target/classes",
    "resources": [
      "${PROJECT_ROOT}/obp-commons/src/main/resources"
    ],
    "scala": {
      "organization": "org.scala-lang",
      "name": "scala-compiler",
      "version": "2.12.20",
      "options": [
        "-unchecked",
        "-explaintypes",
        "-encoding",
        "UTF-8",
        "-feature"
      ],
      "jars": [
        "${M2_REPO}/org/scala-lang/scala-library/2.12.20/scala-library-2.12.20.jar",
        "${M2_REPO}/org/scala-lang/scala-compiler/2.12.20/scala-compiler-2.12.20.jar",
        "${M2_REPO}/org/scala-lang/scala-reflect/2.12.20/scala-reflect-2.12.20.jar"
      ],
      "analysis": "${PROJECT_ROOT}/obp-commons/target/bloop-bsp-clients-classes/classes-Metals-",
      "setup": {
        "order": "mixed",
        "addLibraryToBootClasspath": true,
        "addCompilerToClasspath": false,
        "addExtraJarsToClasspath": false,
        "manageBootClasspath": true,
        "filterLibraryFromClasspath": true
      }
    },
    "java": {
      "options": ["-source", "11", "-target", "11"]
    },
    "platform": {
      "name": "jvm",
      "config": {
        "home": "${JAVA_HOME}",
        "options": []
      },
      "mainClass": []
    },
    "resolution": {
      "modules": []
    },
    "tags": ["library"]
  }
}
EOF

# Generate obp-api.json
echo "🔨 Generating obp-api configuration..."
cat > "$PROJECT_ROOT/.bloop/obp-api.json" << EOF
{
  "version": "1.5.5",
  "project": {
    "name": "obp-api",
    "directory": "${PROJECT_ROOT}/obp-api",
    "workspaceDir": "${PROJECT_ROOT}",
    "sources": [
      "${PROJECT_ROOT}/obp-api/src/main/scala",
      "${PROJECT_ROOT}/obp-api/src/main/java"
    ],
    "dependencies": ["obp-commons"],
    "classpath": [
      "${PROJECT_ROOT}/obp-api/target/classes",
      "${PROJECT_ROOT}/obp-commons/target/classes",
      "${M2_REPO}/com/tesobe/obp-commons/1.10.1/obp-commons-1.10.1.jar",
      "${M2_REPO}/net/liftweb/lift-common_2.12/3.5.0/lift-common_2.12-3.5.0.jar",
      "${M2_REPO}/org/scala-lang/scala-library/2.12.12/scala-library-2.12.12.jar",
      "${M2_REPO}/org/slf4j/slf4j-api/1.7.32/slf4j-api-1.7.32.jar",
      "${M2_REPO}/org/scala-lang/modules/scala-xml_2.12/1.3.0/scala-xml_2.12-1.3.0.jar",
      "${M2_REPO}/net/liftweb/lift-util_2.12/3.5.0/lift-util_2.12-3.5.0.jar",
      "${M2_REPO}/org/scala-lang/scala-compiler/2.12.12/scala-compiler-2.12.12.jar",
      "${M2_REPO}/net/liftweb/lift-mapper_2.12/3.5.0/lift-mapper_2.12-3.5.0.jar",
      "${M2_REPO}/net/liftweb/lift-json_2.12/3.5.0/lift-json_2.12-3.5.0.jar",
      "${M2_REPO}/org/scala-lang/scala-reflect/2.12.20/scala-reflect-2.12.20.jar",
      "${M2_REPO}/net/databinder/dispatch/dispatch-lift-json_2.12/0.13.1/dispatch-lift-json_2.12-0.13.1.jar",
      "${M2_REPO}/ch/qos/logback/logback-classic/1.2.13/logback-classic-1.2.13.jar",
      "${M2_REPO}/org/slf4j/log4j-over-slf4j/1.7.26/log4j-over-slf4j-1.7.26.jar",
      "${M2_REPO}/org/slf4j/slf4j-ext/1.7.26/slf4j-ext-1.7.26.jar",
      "${M2_REPO}/org/bouncycastle/bcpg-jdk15on/1.70/bcpg-jdk15on-1.70.jar",
      "${M2_REPO}/org/bouncycastle/bcpkix-jdk15on/1.70/bcpkix-jdk15on-1.70.jar",
      "${M2_REPO}/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar",
      "${M2_REPO}/org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar",
      "${M2_REPO}/com/github/everit-org/json-schema/org.everit.json.schema/1.6.1/org.everit.json.schema-1.6.1.jar"
    ],
    "out": "${PROJECT_ROOT}/obp-api/target/classes",
    "classesDir": "${PROJECT_ROOT}/obp-api/target/classes",
    "resources": [
      "${PROJECT_ROOT}/obp-api/src/main/resources"
    ],
    "scala": {
      "organization": "org.scala-lang",
      "name": "scala-compiler",
      "version": "2.12.20",
      "options": [
        "-unchecked",
        "-explaintypes",
        "-encoding",
        "UTF-8",
        "-feature"
      ],
      "jars": [
        "${M2_REPO}/org/scala-lang/scala-library/2.12.20/scala-library-2.12.20.jar",
        "${M2_REPO}/org/scala-lang/scala-compiler/2.12.20/scala-compiler-2.12.20.jar",
        "${M2_REPO}/org/scala-lang/scala-reflect/2.12.20/scala-reflect-2.12.20.jar"
      ],
      "analysis": "${PROJECT_ROOT}/obp-api/target/bloop-bsp-clients-classes/classes-Metals-",
      "setup": {
        "order": "mixed",
        "addLibraryToBootClasspath": true,
        "addCompilerToClasspath": false,
        "addExtraJarsToClasspath": false,
        "manageBootClasspath": true,
        "filterLibraryFromClasspath": true
      }
    },
    "java": {
      "options": ["-source", "11", "-target", "11"]
    },
    "platform": {
      "name": "jvm",
      "config": {
        "home": "${JAVA_HOME}",
        "options": []
      },
      "mainClass": []
    },
    "resolution": {
      "modules": []
    },
    "tags": ["application"]
  }
}
EOF

echo -e "${GREEN}✅ Generated $PROJECT_ROOT/.bloop/obp-commons.json${NC}"
echo -e "${GREEN}✅ Generated $PROJECT_ROOT/.bloop/obp-api.json${NC}"

# Verify the configurations
echo "🔍 Verifying generated configurations..."
if command -v bloop &> /dev/null; then
    if bloop projects | grep -q "obp-api\|obp-commons"; then
        echo -e "${GREEN}✅ Bloop can detect the projects${NC}"
    else
        echo -e "${YELLOW}⚠️  Bloop server may need to be restarted to detect new configurations${NC}"
        echo "Run: pkill -f bloop && bloop server &"
    fi
else
    echo -e "${YELLOW}⚠️  Bloop not found, skipping verification${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Bloop configuration generation complete!${NC}"
echo ""
echo "📋 Next steps:"
echo "1. Restart Bloop server if needed: pkill -f bloop && bloop server &"
echo "2. Verify projects are detected: bloop projects"
echo "3. Test compilation: bloop compile obp-commons obp-api"
echo "4. Open project in Zed IDE for full language server support"
echo ""
echo -e "${GREEN}Happy coding! 🚀${NC}"
