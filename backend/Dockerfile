# ==============================================================================
# Stage 1: Build stage with Maven and OpenJDK 21
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pre-fetch dependencies to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code and compile production executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Production runtime stage with lightweight Eclipse Temurin JRE 21
# ==============================================================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create dedicated non-root user and group for security
RUN groupadd -r hrgenius && useradd -r -g hrgenius -d /app hrgenius

# Create directory for documents/file storage with appropriate ownership
RUN mkdir -p /app/uploads/documents && chown -R hrgenius:hrgenius /app

# Copy the compiled executable JAR from the builder stage
COPY --from=build --chown=hrgenius:hrgenius /build/target/*.jar /app/app.jar

# Switch to non-root execution
USER hrgenius

# Document internal container port (Render forwards external traffic here)
EXPOSE 8080

# Production-tuned container environment defaults
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=dev \
    DOCS_STORAGE_DIR=/app/uploads/documents \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Start the Spring Boot application binding to 0.0.0.0 and dynamic $PORT
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -Dserver.address=0.0.0.0 -jar /app/app.jar"]
