# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package && \
    cp target/ias-credit-applications-*.jar /workspace/application.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --gid 10001 app && \
    useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /workspace/application.jar /app/application.jar

# Deployment databases must be provisioned separately; do not load demo data.
USER app:app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
