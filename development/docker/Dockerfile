FROM maven:3-eclipse-temurin-25 as maven
# Build the source using maven, source is copied from the 'repo' build.
COPY . /usr/src/OBP-API
RUN cp /usr/src/OBP-API/obp-api/pom.xml /tmp/pom.xml # For Packaging a local repository within the image
WORKDIR /usr/src/OBP-API
RUN cp obp-api/src/main/resources/props/test.default.props.template obp-api/src/main/resources/props/test.default.props
RUN cp obp-api/src/main/resources/props/sample.props.template obp-api/src/main/resources/props/default.props
RUN --mount=type=cache,target=$HOME/.m2 MAVEN_OPTS="-Xmx3G -Xss2m" mvn install -pl .,obp-commons
RUN --mount=type=cache,target=$HOME/.m2 MAVEN_OPTS="-Xmx3G -Xss2m" mvn install -DskipTests -pl obp-api

FROM eclipse-temurin:25-jre-alpine
COPY --from=maven /usr/src/OBP-API/obp-api/target/lib /app/lib
COPY --from=maven /usr/src/OBP-API/obp-api/target/obp-api.jar /app/obp-api.jar
# -cp, not -jar: a manifest Class-Path never reaches the `java.class.path` system property, and both
# DotcScalaCompiler and json4s's ScalaSigReader build a runtime compiler classpath out of that
# property. Under -jar they see the thin jar alone and every dynamic-code and Scala-3 field-type
# path fails on a server that otherwise boots fine. See .github/Dockerfile_PreBuild.
ENTRYPOINT ["java", "-cp", "/app/obp-api.jar:/app/lib/*", "bootstrap.http4s.Http4sServer"]