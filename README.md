# Just Rocket Science

A rocket and space-agency catalog: a Spring Boot REST API backed by PostgreSQL, and a React front end to browse it.

## Backend — `NotRocketScienceBackend/`

**Stack:** Java 21 · Spring Boot 3.3 · Spring Data JPA · PostgreSQL · Flyway · MapStruct · Spring Cache · Actuator

Flyway migrations create the schema and seed it with real agencies and launch vehicles
(height, diameter, mass, payload to LEO, thrust, stages, reusability, crew capacity, first launch date).

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/rockets` | Paged rocket summaries |
| `GET` | `/api/rockets/{id}` | Full rocket detail |
| `GET` | `/api/agencies` | Paged agency summaries |
| `GET` | `/api/agencies/{id}` | Agency detail |
| `GET` | `/api/agencies/{agencyId}/rockets` | Rockets built by an agency |

### Run

```bash
cd NotRocketScienceBackend
# PostgreSQL on localhost:5433, database jrs_rocket_db (see src/main/resources/application.properties)
mvn spring-boot:run          # serves on :8082 (override with PORT)
```

Or with Docker: `docker build -t jrs-backend . && docker run -e PORT=8080 -p 8080:8080 jrs-backend`.
`docker-compose.yml` adds Redis and the gateway services.

## Frontend — `NotRocketScienceFrontend/`

React 19 + Vite.

```bash
cd NotRocketScienceFrontend
npm install && npm run dev
```

## Docs in this repo

`NotRocketScienceBackend/ARCHITECTURE_REPORT.md`, `AUDIT_REPORT.md` and `GATEWAY_COMPLETION_REPORT.md` cover the
design, the audit findings and the API gateway work (JWT auth, rate limiting, circuit breaker; that code is parked in
`_excluded/`). The backend's own `README.md` documents the original Higher-or-Lower game design this project grew from.
