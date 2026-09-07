FROM eclipse-temurin:25-jre-noble as dist-server

WORKDIR /app

# fontconfig + fonts-dejavu-core: for diagram rendering; curl: for HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/*


COPY build/libs/*.jar app.jar
COPY contract/rest/openapi-v1.yaml .

RUN mkdir -p /config

# Add healthcheck
HEALTHCHECK --interval=30s --timeout=3s CMD curl -sf http://localhost:8096/actuator/health || exit 1

EXPOSE 8095 8096

ENV SERVER_PORT=8095

# Runs the app configured to load config from /config
# Opens JDK modules in case pure-java Snappy is used; native loads on glibc
ENTRYPOINT ["java", \
    "--add-opens=java.base/java.nio=ALL-UNNAMED", \
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", \
    "-jar", "app.jar", \
    "--spring.config.additional-location=file:/config/"]
