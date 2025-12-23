ARG NUGET_MAIB_USER
ARG NUGET_MAIB_PASS

FROM registry.maib.md/maven:3.9.6-eclipse-temurin-11 as maven

# Устанавливаем необходимые пакеты
USER root
RUN apt-get update && apt-get install -y scala curl git

# Копируем исходный код проекта
COPY . /usr/src/OBP-API
WORKDIR /usr/src/OBP-API

# Копируем конфигурационные файлы
RUN cp obp-api/src/main/resources/props/test.default.props.template obp-api/src/main/resources/props/test.default.props
RUN cp obp-api/src/main/resources/props/sample.props.template obp-api/src/main/resources/props/default.props

# Удаляем кэш Maven (без монтирования)
RUN rm -rf /root/.m2/repository

# Сборка зависимостей проекта
RUN MAVEN_OPTS="-Xmx4G -Xss4m" mvn clean install -pl .,obp-commons -DskipTests -U -X

# Сборка основного модуля obp-api
RUN MAVEN_OPTS="-Xmx4G -Xss4m" mvn clean install -pl obp-api -DskipTests -U -X

# Финальный образ на базе Jetty
FROM registry.maib.md/jetty:9.4-jdk11-alpine

# Копируем собранный WAR-файл
COPY --from=maven /usr/src/OBP-API/obp-api/target/obp-api-1.*.war /var/lib/jetty/webapps/ROOT.war

# Устанавливаем сертификаты
USER root
ENV USE_SYSTEM_CA_CERTS=1
WORKDIR /certificates
COPY --chmod=777 CA-ALL-PROD.crt .
COPY --chmod=777 CA-ALL-TEST.crt .
RUN /bin/sh -c /__cacert_entrypoint.sh

# Запускаем Jetty
WORKDIR /var/lib/jetty
USER jetty
