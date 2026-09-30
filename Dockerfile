FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
COPY templates /app/templates
COPY static /app/static
COPY rpa/lifestyle_ml /app/rpa/lifestyle_ml

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
