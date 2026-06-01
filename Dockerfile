# Single-service image for Railway/any host: the Java backend serves both the
# REST API and the bundled React app on one port (one origin → cookies just work).

# --- Build the React frontend ---
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY front-end/package.json front-end/package-lock.json ./
RUN npm ci || npm install
COPY front-end/ ./
RUN npm run build

# --- Build the Java backend ---
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /be
COPY back-end/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline || true
COPY back-end/src ./src
RUN mvn -q -DskipTests -Dcheckstyle.skip=true package

# --- Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /be/target/banking-app-jar-with-dependencies.jar app.jar
COPY --from=frontend /fe/build ./static
ENV STATIC_DIR=/app/static
ENV PORT=8080
EXPOSE 8080
# Force TLS 1.2 for the Mongo connection. JDK 21 defaults to TLS 1.3, which
# Atlas's shared-tier (M0/free) TLS terminator rejects with
# "Received fatal alert: internal_error" during the handshake. Pinning the
# client to TLS 1.2 is the documented remedy for that error against Atlas.
CMD ["java", "-Djdk.tls.client.protocols=TLSv1.2", "-jar", "app.jar"]
