FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradlew.bat .
COPY gradle gradle/
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src src/

# Build application
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

# Runtime image
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy JAR from builder
COPY --from=0 /app/build/libs/*.jar app.jar

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8000/actuator/health || exit 1

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]
