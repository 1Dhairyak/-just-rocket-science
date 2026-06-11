# Higher or Lower Game — Backend

Enterprise-grade Spring Boot backend for the Higher or Lower web game.  
Players compare two items and guess which has more monthly Google search volume.

---

## Tech Stack

| Layer        | Technology                          |
|--------------|-------------------------------------|
| Runtime      | Java 21                             |
| Framework    | Spring Boot 3.3                     |
| Database     | PostgreSQL 15+                      |
| ORM          | Spring Data JPA / Hibernate         |
| Mapping      | Hand-rolled mapper (ItemMapper)     |
| Caching      | Spring Cache (simple / Redis-ready) |
| Validation   | Jakarta Bean Validation             |
| Build        | Maven                               |
| Testing      | JUnit 5 + Mockito + MockMvc         |

---

## Project Structure

```
src/main/java/com/higherlower/game/
├── HigherLowerGameApplication.java   # Boot entry point
│
├── config/
│   ├── WebConfig.java                # CORS configuration
│   └── DataSeeder.java               # Dev seed data (non-prod only)
│
├── controller/
│   ├── GameController.java           # GET /api/game/start, POST /api/game/guess
│   └── LeaderboardController.java    # GET /api/leaderboard
│
├── service/
│   ├── GameService.java              # Interface
│   ├── LeaderboardService.java       # Interface
│   └── impl/
│       ├── GameServiceImpl.java      # Core game loop logic
│       └── LeaderboardServiceImpl.java
│
├── repository/
│   ├── ItemRepository.java
│   ├── GameSessionRepository.java
│   └── LeaderboardRepository.java
│
├── entity/
│   ├── Item.java                     # Game item (title, searchVolume, …)
│   ├── GameSession.java              # Per-player session state
│   ├── LeaderboardEntry.java         # Immutable score record
│   └── enums/
│       ├── GameStatus.java           # ACTIVE | GAME_OVER | ABANDONED
│       └── GuessType.java            # HIGHER | LOWER
│
├── dto/
│   ├── request/
│   │   ├── StartGameRequest.java
│   │   └── GuessRequest.java
│   └── response/
│       ├── ApiResponse.java          # Generic envelope { success, message, data }
│       ├── ItemDto.java
│       ├── GameRoundDto.java
│       ├── GuessResultDto.java
│       └── LeaderboardDto.java
│
├── exception/
│   ├── GameSessionNotFoundException.java
│   ├── InsufficientItemsException.java
│   ├── InvalidGuessException.java
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice
│
└── util/
    └── ItemMapper.java               # Entity ↔ DTO conversion
```

---

## Quick Start

### 1. PostgreSQL Setup

```sql
CREATE DATABASE higher_lower_db;
CREATE USER postgres WITH ENCRYPTED PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE higher_lower_db TO postgres;
```

Or run the full DDL in `src/main/resources/schema.sql`.

### 2. Configure application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/higher_lower_db
spring.datasource.username=postgres
spring.datasource.password=your_password
```

Or use environment variables:
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/higher_lower_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your_password
```

### 3. Build & Run

```bash
mvn clean install
mvn spring-boot:run
```

The app seeds 11 sample items automatically on first startup (non-prod profile).

---

## REST API Reference

### Base URL: `http://localhost:8080/api`

All responses are wrapped in the `ApiResponse<T>` envelope:
```json
{
  "success": true,
  "message": "...",
  "data": { ... },
  "timestamp": "2024-01-15T10:30:00"
}
```

---

### `GET /api/game/start`

Start a new game session. Optionally send a player name.

**Request body (optional):**
```json
{ "playerName": "Alice" }
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Game started successfully",
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "knownItem": {
      "id": "item-1",
      "title": "Budweiser",
      "imageUrl": "https://...",
      "searchVolume": 301000,
      "category": "Food & Drink"
    },
    "challengeItem": {
      "id": "item-2",
      "title": "Corn Flakes",
      "imageUrl": "https://...",
      "searchVolume": null,
      "category": "Food & Drink"
    },
    "currentScore": 0
  }
}
```

> ⚠️ `challengeItem.searchVolume` is always `null` — the volume is intentionally hidden until the guess is submitted.

---

### `POST /api/game/guess`

Submit a Higher or Lower guess for the current round.

**Request body:**
```json
{
  "sessionId":       "550e8400-e29b-41d4-a716-446655440000",
  "challengeItemId": "item-2",
  "guess":           "HIGHER"
}
```

**Response `200 OK` (correct):**
```json
{
  "success": true,
  "message": "Guess processed",
  "data": {
    "correct": true,
    "score": 1,
    "revealedChallengeItem": {
      "title": "Corn Flakes",
      "searchVolume": 450000
    },
    "nextRound": {
      "sessionId": "550e8400-...",
      "knownItem": { "title": "Corn Flakes", "searchVolume": 450000 },
      "challengeItem": { "title": "iPhone", "searchVolume": null },
      "currentScore": 1
    },
    "message": "Correct! Corn Flakes has 450,000 monthly searches. Score: 1",
    "gameOver": false
  }
}
```

**Response `200 OK` (wrong — game over):**
```json
{
  "data": {
    "correct": false,
    "score": 3,
    "revealedChallengeItem": { "title": "Corn Flakes", "searchVolume": 450000 },
    "nextRound": null,
    "message": "Wrong! Corn Flakes has 450,000 monthly searches — higher than Budweiser's 301,000. Game over!",
    "gameOver": true
  }
}
```

---

### `GET /api/leaderboard?limit=10`

Retrieve the global top-N leaderboard (1–100 entries).

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "entries": [
      { "rank": 1, "playerName": "Alice", "score": 42, "achievedAt": "2024-01-15T10:00:00" },
      { "rank": 2, "playerName": "Bob",   "score": 37, "achievedAt": "2024-01-14T18:00:00" }
    ],
    "totalEntries": 157,
    "generatedAt": "2024-01-15T11:00:00"
  }
}
```

---

## Error Responses

| Scenario                         | HTTP Status | `success` |
|----------------------------------|-------------|-----------|
| Validation failure               | 400         | false     |
| Invalid/expired session          | 404         | false     |
| Stale challenge item ID          | 409         | false     |
| Not enough items in DB           | 503         | false     |
| Unexpected server error          | 500         | false     |

---

## Game Logic

### Duplicate Prevention
Each `GameSession` maintains a `seenItemIds` list (comma-delimited IDs).  
The repository queries for random items **excluding all seen IDs**, so an item can never appear twice in the same session.

### Tie-Breaking
When both items have identical search volumes, a `HIGHER` guess is treated as **correct** (player-friendly design).

### Session Lifecycle
```
[New Request] → ACTIVE → (correct guesses) → ACTIVE
                       → (wrong guess)     → GAME_OVER → [Leaderboard Entry Written]
```

---

## Running Tests

```bash
# Unit + controller slice tests (uses H2 in-memory)
mvn test

# With coverage report
mvn test jacoco:report
```

---

## Production Checklist

- [ ] Set `spring.jpa.hibernate.ddl-auto=validate` and use Flyway/Liquibase
- [ ] Replace `spring.cache.type=simple` with Redis (`spring-boot-starter-data-redis`)
- [ ] Lock down CORS `allowedOriginPatterns` to your domain
- [ ] Set `SPRING_DATASOURCE_PASSWORD` via secrets manager (not properties file)
- [ ] Enable Spring Security with JWT for authenticated leaderboard submission
- [ ] Add rate limiting (Bucket4j or API Gateway) on `/api/game/guess`
- [ ] Schedule `abandonStaleSessionsBefore()` cleanup via `@Scheduled`
