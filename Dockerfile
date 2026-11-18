# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -ntp verify \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar ./app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent --show-error --max-time 4 --output /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
