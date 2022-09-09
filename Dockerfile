FROM maven:3-jdk-8 as maven
# Build the source using maven, source is copied from the 'repo' build.
ADD . /usr/src/OBP-API
RUN cp /usr/src/OBP-API/obp-api/pom.xml /tmp/pom.xml # For Packaging a local repository within the image
WORKDIR /usr/src/OBP-API
RUN cp obp-api/src/main/resources/props/test.default.props.template obp-api/src/main/resources/props/test.default.props
RUN cp obp-api/src/main/resources/props/sample.props.template obp-api/src/main/resources/props/default.props
RUN --mount=type=cache,target=/root/.m2 mvn install -pl .,obp-commons
RUN --mount=type=cache,target=/root/.m2 mvn install -DskipTests -pl obp-api

FROM openjdk:8-jre-alpine

# Add user 
RUN adduser -D obp

# Download jetty
RUN wget -O - https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.4.15.v20190215/jetty-distribution-9.4.15.v20190215.tar.gz | tar zx
RUN mv jetty-distribution-* jetty

# Copy OBP source code
# Copy build artifact (.war file) into jetty from 'maven' stage.
COPY --from=maven /usr/src/OBP-API/obp-api/target/obp-api-*.war jetty/webapps/ROOT.war

WORKDIR jetty
RUN chown -R  obp /jetty

# Switch to the obp user (non root)
USER obp

# Starts jetty
ENTRYPOINT ["java", "-jar", "start.jar"]
