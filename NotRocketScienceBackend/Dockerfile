# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Dependency layer — cached unless pom.xml changes
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Source layer
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Extract layered jar (Spring Boot 3 layertools)
RUN java -Djarmode=layertools \
         -jar target/jrs-api-gateway-*.jar extract \
         --destination /build/extracted

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S jrs && adduser -S jrs -G jrs

WORKDIR /app

# Copy Spring Boot layers — least-changed to most-changed for cache efficiency
COPY --from=builder --chown=jrs:jrs /build/extracted/dependencies/          ./
COPY --from=builder --chown=jrs:jrs /build/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=jrs:jrs /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=jrs:jrs /build/extracted/application/           ./

USER jrs

# Reactive gateway runs on 8080
EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport          — respects cgroup memory limits
#   -XX:MaxRAMPercentage=75.0         — leaves headroom for OS/Redis client
#   -XX:+OptimizeStringConcat         — minor WebFlux string perf
#   -Dreactor.netty.http.server.accessLogEnabled=true — access logs to stdout
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+OptimizeStringConcat \
               -Dreactor.netty.http.server.accessLogEnabled=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# ─────────────────────────────────────────────────────────────────────────────
# Build args for CI tagging
# ─────────────────────────────────────────────────────────────────────────────
ARG BUILD_VERSION=local
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="jrs-api-gateway" \
      org.opencontainers.image.version="${BUILD_VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.source="https://github.com/1Dhairyak/just-rocket-science"
