# Stage 1: Build the Java application using Maven with JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy dependency definition and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code and package the app
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Create lightweight runtime image with JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]