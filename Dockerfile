# Multi-stage Dockerfile for Kubernetes deployment
# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime stage (minimal image)
FROM eclipse-temurin:21-jre-alpine
LABEL author="Kamil Barnik <kamil.barnik@gep.com>"
LABEL maintainer="GEP DBNA Team"
LABEL description="DBNA Outbound Service"

# Install curl for health checks
RUN apk add --no-cache curl

# Create app user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

WORKDIR /app

# Copy only the built JAR from builder stage
COPY --from=builder /build/build/libs/*.jar app.jar

# Create necessary directories with proper permissions
RUN mkdir -p /app/storage /app/config && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check endpoint - use new dedicated health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/health/check || exit 1

# Expose port
EXPOSE 8080

# Set Java options for Kubernetes environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseStringDeduplication"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]

