
# Etapa 1: compilación
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src

RUN ./mvnw -B -DskipTests package \
    && cp target/*.jar app.jar

# Etapa 2: ejecución
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /app/app.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
