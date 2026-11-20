# Stage 1: Build with Maven (multi-arch: amd64 & arm64)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Lightweight Runtime (multi-arch: amd64 & arm64)
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create non-root user for container security best practice
RUN groupadd --system nms && useradd --system --gid nms nms
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R nms:nms /app
USER nms

EXPOSE 8080

# Tune JVM for container memory limits
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
