FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven build file and source
COPY pom.xml .
COPY src src/

# Build application
RUN mvn -B -Dmaven.test.skip=true package

# Runtime image
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8000/actuator/health || exit 1

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]
