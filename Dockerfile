FROM maven:3-eclipse-temurin-11 as maven
# Build the source using maven, source is copied from the 'repo' build.
COPY . /usr/src/OBP-API
RUN cp /usr/src/OBP-API/obp-api/pom.xml /tmp/pom.xml # For Packaging a local repository within the image
WORKDIR /usr/src/OBP-API
RUN cp obp-api/src/main/resources/props/test.default.props.template obp-api/src/main/resources/props/test.default.props
RUN cp obp-api/src/main/resources/props/sample.props.template obp-api/src/main/resources/props/default.props
RUN --mount=type=cache,target=$HOME/.m2 MAVEN_OPTS="-Xmx8G -Xss4m" mvn install -pl .,obp-commons -X
RUN --mount=type=cache,target=$HOME/.m2 MAVEN_OPTS="-Xmx8G -Xss4m" mvn install -DskipTests -pl obp-api -X

FROM jetty:9.4-jdk11-alpine

COPY --from=maven /usr/src/OBP-API/obp-api/target/obp-api-1.*.war /var/lib/jetty/webapps/ROOT.war

USER root
ENV USE_SYSTEM_CA_CERTS=1
WORKDIR /certificates
COPY --chmod=777 CA-ALL-PROD.crt .
COPY --chmod=777 CA-ALL-TEST.crt .
RUN /bin/sh -c /__cacert_entrypoint.sh
WORKDIR /var/lib/jetty
USER jetty