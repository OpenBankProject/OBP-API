# --- Stage 1: Build with Maven ---
FROM maven:3-eclipse-temurin-11 as maven

# Установка рабочей директории
WORKDIR /usr/src/OBP-API

# Копирование исходников
COPY . .

# Копирование props-шаблонов
RUN cp obp-api/src/main/resources/props/test.default.props.template obp-api/src/main/resources/props/test.default.props
RUN cp obp-api/src/main/resources/props/sample.props.template obp-api/src/main/resources/props/default.props

# Опционально: очистка Maven кэша
RUN rm -rf $HOME/.m2/repository

# Сборка obp-commons
RUN --mount=type=cache,target=$HOME/.m2 \
    MAVEN_OPTS="-Xmx8G -Xss8m -XX:MaxMetaspaceSize=2G" \
    mvn clean install -pl obp-commons -DskipTests -T 1C

# Сборка obp-api
RUN --mount=type=cache,target=$HOME/.m2 \
    MAVEN_OPTS="-Xmx8G -Xss8m -XX:MaxMetaspaceSize=2G" \
    mvn clean install -pl obp-api -DskipTests -T 1C

# --- Stage 2: Jetty Runtime ---
FROM jetty:9.4-jdk11-alpine

# Копирование собранного WAR
COPY --from=maven /usr/src/OBP-API/obp-api/target/obp-api-1.*.war /var/lib/jetty/webapps/ROOT.war

# Настройка сертификатов
USER root
WORKDIR /certificates
ENV USE_SYSTEM_CA_CERTS=1
COPY --chmod=777 CA-ALL-PROD.crt .
COPY --chmod=777 CA-ALL-TEST.crt .
RUN /bin/sh -c /__cacert_entrypoint.sh

# Вернуть пользователя jetty
WORKDIR /var/lib/jetty
USER jetty
