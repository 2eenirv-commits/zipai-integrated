FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv \
    && python3 -m venv /opt/zipai-venv \
    && /opt/zipai-venv/bin/pip install --no-cache-dir numpy pandas scikit-learn \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/opt/zipai-venv/bin:${PATH}"

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
COPY index.html /app/index.html
COPY templates /app/templates
COPY static /app/static
COPY rpa/lifestyle_ml /app/rpa/lifestyle_ml

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
