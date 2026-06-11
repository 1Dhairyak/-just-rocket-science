# jrs-api-gateway — Phase 2 Completion Report

---

## What Was Built

The API Gateway is the single entry point for the entire Just Rocket Science
platform. Every HTTP request from every client passes through it before
reaching any downstream service.

---

## Architecture at a Glance

```
Client
  │
  ▼
[jrs-api-gateway :8080]
  │
  ├── CorsFilter              — preflight + header injection
  ├── CorrelationIdFilter     — generate / propagate X-Correlation-ID
  ├── RateLimitFilter         — token bucket per IP or user (Redis-backed)
  ├── JwtAuthenticationFilter — 8-step HS256 token validation + blacklist
  │     └── TokenBlacklistChecker ─ Redis: "blacklist:{jti}" key
  │
  ├── Spring Cloud Gateway routes
  │     ├── /api/v1/auth/**       → jrs-auth-service:8081
  │     ├── /api/v1/rockets/**    → jrs-rocket-service:8082  (Phase 3)
  │     └── /api/v1/launches/**   → jrs-launch-service:8083  (Phase 3)
  │
  ├── CircuitBreaker (Resilience4j) — per-route, fallback → GlobalExceptionHandler
  └── GlobalExceptionHandler  — RFC 7807 error responses
```

---

## Files Delivered (All Phases)

### Phase 2 — Step 1–3: Filters

| File | Purpose |
|---|---|
| `JwtAuthenticationFilter.java` | 8-step token validation, blacklist check, writes 401/403 directly |
| `TokenBlacklistChecker.java` | Redis lookup: `blacklist:{jti}` |
| `CorrelationIdFilter.java` | UUID generation + echo of client-supplied ID |
| `RateLimitFilter.java` | Per-IP / per-user token bucket via Redis |
| `CorsFilter.java` (via CorsProperties) | Preflight + CORS headers |

### Phase 2 — Step 4: JWT Validation

| File | Purpose |
|---|---|
| `JwtValidator.java` | HS256 signature, expiry, issuer, audience, type claim |
| `ClaimsResult.java` | Immutable result type: valid/invalid + extracted claims |

### Phase 2 — Step 5: Properties

| File | Purpose |
|---|---|
| `JwtProperties.java` | `jrs.jwt.*` — secret, issuer, audience, clock-skew |
| `CorsProperties.java` | `jrs.cors.*` |
| `RateLimitProperties.java` | `jrs.rate-limit.*` — two-tier bucket config |
| `CircuitBreakerProperties.java` | `jrs.circuit-breaker.*` |
| `OpenApiProperties.java` | Swagger aggregation config |

### Phase 2 — Step 6: Exception Handling

| File | Purpose |
|---|---|
| `ErrorCode.java` | 21 canonical codes across 5 categories |
| `ErrorResponse.java` | RFC 7807-style, immutable, optional `fieldErrors` |
| `GatewayException.java` | Base + `JwtValidationException` + `RateLimitExceededException` |
| `GlobalExceptionHandler.java` | 8 ordered handlers, no internal leakage |

### Phase 2 — Step 7: Integration Tests

| File | Tests |
|---|---|
| `GatewayIntegrationTestBase.java` | Testcontainers Redis, WebTestClient base |
| `TestTokenFactory.java` | 7 token types (valid, expired, wrong-secret, wrong-issuer, wrong-audience, no-type, refresh) |
| `RouteAccessIT.java` | Public routes, protected routes, CORS preflight |
| `JwtValidationIT.java` | All 8 validation steps + error shape + no-leakage |
| `TokenBlacklistIT.java` | Redis blacklist enforce, TTL expiry, fail-closed |
| `RateLimitIT.java` | Anonymous burst, 429 shape, Retry-After, per-user isolation |
| `CircuitBreakerIT.java` | Open state 503, no-leakage, correlationId propagation |
| `CorrelationIdIT.java` | Generate, echo, present on 401/429, per-request isolation |

### Phase 2 — Step 8: Docker + Deployment

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage: JDK builder → JRE runtime, non-root user, layered jar |
| `docker-compose.yml` | Full platform stack: Redis + auth-service + gateway |
| `.env.example` | All required environment variables with safe defaults |
| `application-docker.yml` | Production Spring profile — all values from env vars |
| `ecs-task-definition.json` | AWS Fargate task definition — secrets from Secrets Manager/SSM |
| `aws-setup.sh` | One-time secret provisioning + deploy / rotate commands |

---

## Key Technical Decisions

### HS256 over RS256

Both auth-service and gateway share one secret via AWS Secrets Manager.
No key-pair infrastructure needed. Correct for a single-developer project
where both services are deployed together. RS256 adds value when external
parties need to verify tokens independently — not this project's scenario.

**Interview answer:**
> "Both services share an HS256 secret injected from AWS Secrets Manager
> at container startup. RS256 would be appropriate if we exposed a public
> JWKS endpoint for external token verification — which this architecture
> doesn't require."

### Fail-Closed on Redis Unavailability

If the Redis connection fails during a blacklist check, the gateway rejects
the request rather than letting it through. Security > availability.

### JWT Filter Writes Directly — Does Not Throw

`JwtAuthenticationFilter` writes 401/403 to `ServerWebExchange` directly,
bypassing `GlobalExceptionHandler`. This avoids reactive context propagation
issues in the filter chain. Both paths produce the same `ErrorResponse` shape.

### Non-Root Container User

Dockerfile creates a `jrs` system user and runs the JVM under it.
Rejects any attempt to run as root — standard container security hygiene.

### Layered Jar Build

Spring Boot 3 layertools splits the jar into four layers
(`dependencies` → `spring-boot-loader` → `snapshot-dependencies` → `application`).
Only the `application` layer (your code) changes on each build —
the other three are served from Docker layer cache, cutting rebuild time
from ~2 min to ~10 seconds on iterative deploys.

---

## Environment Variables — Master Reference

| Variable | Required | Source | Notes |
|---|---|---|---|
| `JRS_JWT_SECRET` | ✅ | Secrets Manager | Must match auth-service exactly |
| `SPRING_DATA_REDIS_HOST` | ✅ | SSM | ElastiCache primary endpoint |
| `SPRING_DATA_REDIS_PORT` | ✅ | SSM | Default 6379 |
| `SPRING_DATA_REDIS_PASSWORD` | ✅ | Secrets Manager | Redis AUTH password |
| `JRS_ROUTES_AUTH_URI` | ✅ | SSM | `http://jrs-auth-service:8081` |
| `JRS_ROUTES_ROCKET_URI` | ✅ | SSM | `http://jrs-rocket-service:8082` |
| `JRS_ROUTES_LAUNCH_URI` | ✅ | SSM | `http://jrs-launch-service:8083` |
| `JRS_JWT_ISSUER` | ✅ | Env | `jrs-auth-service` |
| `JRS_JWT_AUDIENCE` | ✅ | Env | `jrs-api-gateway` |
| `JRS_CORS_ALLOWED_ORIGINS` | ✅ | SSM | Frontend domain(s) |
| `SPRING_PROFILES_ACTIVE` | ✅ | Env | `docker` |
| `JRS_JWT_CLOCK_SKEW_SECONDS` | optional | Env | Default `30` |
| `RATE_LIMIT_ANON` | optional | Env | Default `10` req/s |
| `RATE_LIMIT_AUTH` | optional | Env | Default `100` req/s |
| `LOG_LEVEL` | optional | Env | Default `INFO` |

---

## Health Endpoints

| Endpoint | Access | Purpose |
|---|---|---|
| `/actuator/health` | Public | ECS / ALB health check |
| `/actuator/health/liveness` | Public | JVM alive — restart if DOWN |
| `/actuator/health/readiness` | Public | Ready to receive traffic |
| `/actuator/circuitbreakers` | Internal | Per-route breaker state |
| `/actuator/metrics` | Internal | Prometheus scrape target |

---

## Pre-Deployment Checklist

- [ ] `openssl rand -base64 32` secret generated and stored in Secrets Manager
- [ ] Same secret value confirmed in `jrs/jwt-secret` for both services
- [ ] Redis password stored in `jrs/redis-password`
- [ ] All SSM parameters populated (routes, CORS, Redis host/port)
- [ ] `.env` not committed to git (check `.gitignore`)
- [ ] ECS task role has `secretsmanager:GetSecretValue` + `ssm:GetParameter` permissions
- [ ] `/actuator/health` returns `UP` before routing traffic
- [ ] Integration tests pass locally: `mvn test -Dspring.profiles.active=test`

---

## What Comes Next — Phase 3

```
jrs-rocket-service   (Spring Boot + PostgreSQL)
  ├── Rocket entity + repository
  ├── CRUD endpoints
  ├── Pagination + filtering
  └── JWT claims forwarded from gateway (X-User-Id, X-User-Role headers)

jrs-launch-service   (Spring Boot + PostgreSQL)
  └── Launch scheduling, rocket association
```

The gateway is fully ready to route to these services the moment they expose
`:8082` and `:8083`. No gateway changes required for Phase 3 unless new
route patterns are needed.

---

*Phase 2 — jrs-api-gateway — COMPLETE*
