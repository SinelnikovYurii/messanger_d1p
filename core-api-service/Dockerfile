# ── Этап сборки ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Устанавливаем Maven
RUN apk add --no-cache maven

COPY pom.xml .
# Скачиваем зависимости (кэш-слой)
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn package -DskipTests -B

# ── Продакшн образ ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache netcat-openbsd
COPY --from=build /app/target/core-api-service-0.0.1-SNAPSHOT.jar app.jar
VOLUME ["/app/uploads"]
EXPOSE 8082
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
