# Docker Configuration (Optional)
# Uncomment to create a Dockerfile for containerization

FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# Create directory for keystores
RUN mkdir -p /app/config

ENTRYPOINT ["java","-jar","/app.jar"]
EXPOSE 8080

