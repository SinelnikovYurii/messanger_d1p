# ── Этап сборки ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# websocket_server не имеет mvnw — используем system Maven
RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn package -DskipTests -B

# ── Продакшн образ ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache netcat-openbsd
COPY --from=build /app/target/websocket-server-1.0.0.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
