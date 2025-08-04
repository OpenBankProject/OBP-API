FROM maven:3.9.6-eclipse-temurin-17

WORKDIR /app

# Copy all project files into container
COPY . .

EXPOSE 8080

# Build the project, skip tests to speed up
RUN mvn install -pl .,obp-commons -am -DskipTests

# Copy entrypoint script that runs mvn with needed JVM flags
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Use script as entrypoint
CMD ["/app/entrypoint.sh"]
