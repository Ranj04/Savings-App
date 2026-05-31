# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Pre-fetch dependencies for faster rebuilds (best-effort).
RUN mvn -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN mvn -q -DskipTests -Dcheckstyle.skip=true package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/banking-app-jar-with-dependencies.jar app.jar
ENV PORT=1299
EXPOSE 1299
CMD ["java", "-jar", "app.jar"]
