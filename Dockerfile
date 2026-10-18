# syntax=docker/dockerfile:1
# One image per service, built from the repository root:
#
#   docker build --build-arg MODULE=auth -t pavex/auth-service .
#   docker build --build-arg MODULE=platform/api-gateway -t pavex/api-gateway .
#
# MODULE is the Maven module folder: auth, identity, partners, network, pricing, shipment, operations,
# billing, fleet, notifications, platform/discovery-server or platform/api-gateway.
# The image holds no secret: every setting comes from environment variables at run time
# (docs/PAVEX-TAI-LIEU-TONG-HOP.md, "Triển khai thật"), JWT keys from a mounted folder.

FROM eclipse-temurin:21-jdk-noble AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    test -n "$MODULE" || { echo "Set --build-arg MODULE=<module folder>" >&2; exit 1; }; \
    ./mvnw -B -q -pl "$MODULE" -am -DskipTests package \
    && jar="$(find "$MODULE/target" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$jar" && cp "$jar" /app.jar

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 pavex \
    && useradd --system --uid 10001 --gid pavex --home-dir /home/pavex --create-home pavex
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# Mount the key pairs here with the same layout as ~/.pavex/keys (<service>/private.pem, <service>/public.pem).
ENV PAVEX_KEYSDIR=/etc/pavex/keys
USER pavex
# Extra JVM flags go in JAVA_TOOL_OPTIONS.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
