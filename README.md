# Duplicate Event Filter

A Spring Boot REST API that deduplicates incoming JSON events within a 15-minute window, backed by Redis.

Both new and duplicate events return `202 Accepted` — deduplication is silent and idempotent.

## How it works

1. Client POSTs a JSON event with `sourceId` (UUID v7), `eventType`, `entityId`, and `timestamp`
2. The service computes a deduplication key: `sha256(sourceId:eventType:entityId:15-min-bucket)`
3. Redis `SET NX EX 900` — atomic set-if-not-exists with 15-minute TTL
4. **New event** → key stored, `202 Accepted` + `"Event received"`
5. **Duplicate** → key already exists, `202 Accepted` + `"Duplicate event suppressed"`
6. **Redis unreachable** → `503 Service Unavailable`

## Requirements

- Java 21
- Docker + Docker Compose (for local dev)
- Redis 7 (or use `docker-compose up`)

## Quick start

```bash
# Start app + Redis together
docker-compose up --build

# Submit an event
curl -s -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "019612ab-1c3e-7000-8000-3e5f6a7b8c9d",
    "eventType": "ORDER_CREATED",
    "entityId": "order-99",
    "timestamp": "2026-03-24T10:11:00Z"
  }' | jq

# Health check
curl -s http://localhost:8080/api/v1/health | jq
```

## Build

```bash
./gradlew build          # compile + test + Checkstyle + SpotBugs + JaCoCo
./gradlew test           # unit tests only
./gradlew check          # all quality gates
./gradlew bootJar        # build fat JAR (skip tests)
```

## Configuration

All settings can be overridden via environment variables:

| Property | Env var | Default | Description |
|---|---|---|---|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | Redis hostname |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | Redis port |
| `spring.data.redis.ssl.enabled` | `REDIS_SSL` | `false` | Enable TLS for Redis |
| `app.deduplication.ttl-seconds` | `DEDUP_TTL_SECONDS` | `900` | Dedup window in seconds |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port |

## API

Full spec: [`src/main/resources/openapi.yaml`](src/main/resources/openapi.yaml)

### `POST /api/v1/events`

Submit an event for deduplication.

**Request body:**
```json
{
  "sourceId": "019612ab-1c3e-7000-8000-3e5f6a7b8c9d",
  "eventType": "ORDER_CREATED",
  "entityId": "order-99",
  "timestamp": "2026-03-24T10:11:00Z"
}
```

| Field | Type | Constraints |
|---|---|---|
| `sourceId` | string | UUID v7 format |
| `eventType` | string | 1–100 chars |
| `entityId` | string | 1–255 chars |
| `timestamp` | string | ISO-8601 UTC (`date-time`) |

**Responses:**

| Status | Meaning |
|---|---|
| `202 Accepted` | Event received (new or duplicate — identical response) |
| `400 Bad Request` | Missing or invalid required field |
| `503 Service Unavailable` | Redis unreachable |

### `GET /api/v1/health`

Returns service and Redis connectivity status.

## Metrics

Exposed via Spring Boot Actuator at `/actuator/metrics`:

| Metric | Description |
|---|---|
| `events.accepted.total` | Count of new (non-duplicate) events processed |
| `events.deduplicated.total` | Count of suppressed duplicate events |

## Project structure

```
src/
  main/
    java/com/example/duplicateeventfilter/
      config/          RedisConfig
      controller/      EventsController, HealthController
      exception/       GlobalExceptionHandler, DeduplicationStoreUnavailableException
      service/         DeduplicationService (interface), RedisDeduplicationService
    resources/
      openapi.yaml     API spec (source of truth)
      application.properties
  test/
    java/.../
      controller/      EventsControllerTest, HealthControllerTest
      exception/       GlobalExceptionHandlerTest
      integration/     DeduplicationIntegrationTest (Testcontainers)
      service/         RedisDeduplicationServiceTest
config/
  checkstyle/checkstyle.xml
  spotbugs/exclude.xml
```
