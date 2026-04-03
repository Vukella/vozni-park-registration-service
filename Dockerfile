# Stage 1: Build with Leiningen
FROM clojure:lein AS build

WORKDIR /app

# Cache dependencies first
COPY project.clj .
RUN lein deps

# Copy source and build uberjar
COPY . .
RUN lein uberjar

# Stage 2: Minimal runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/uberjar/registration-service-0.1.0-SNAPSHOT-standalone.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]