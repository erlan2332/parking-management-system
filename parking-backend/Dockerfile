FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon clean bootJar \
    && cp build/libs/parking-backend-0.0.1-SNAPSHOT.jar app.jar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S parking && adduser -S parking -G parking

WORKDIR /app

COPY --from=build --chown=parking:parking /workspace/app.jar app.jar

USER parking

EXPOSE 10000

ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
