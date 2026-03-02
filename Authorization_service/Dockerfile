# ── Этап сборки ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Копируем POM отдельно для кэширования зависимостей
COPY pom.xml .
COPY mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Скачиваем зависимости (кэшируется если pom.xml не менялся)
RUN ./mvnw dependency:go-offline -B

# Копируем исходники и собираем JAR
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Продакшн образ ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Устанавливаем netcat для healthcheck
RUN apk add --no-cache netcat-openbsd
COPY --from=build /app/target/Authorization_service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
