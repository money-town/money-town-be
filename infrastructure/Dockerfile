FROM eclipse-temurin:17-jre

ARG SERVICE
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY ${SERVICE}/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]