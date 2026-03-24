# Technical Design: Duplicate Event Filter

**Version**: 1.0  
**Date**: 2026-03-24  
**Status**: Approved

---

## Architecture Overview

```
HTTP Client
    │
    ▼
┌─────────────────────────────┐
│  Spring Boot REST API        │
│  POST /api/v1/events         │
│  GET  /api/v1/health         │
│                             │
│  ┌─────────────────────┐    │
│  │ EventController      │    │
│  │ (OpenAPI-generated   │    │
│  │  interface impl)     │    │
│  └────────┬────────────┘    │
│           │                 │
│  ┌────────▼────────────┐    │
│  │  DeduplicationService│    │
│  │  - compute key       │    │
│  │  - SHA-256 hash key  │    │
│  │  - Redis SETNX + TTL │    │
│  └────────┬────────────┘    │
│           │                 │
└───────────┼─────────────────┘
            │
    ┌───────▼───────┐
    │  Redis         │
    │  TTL = 15 min  │
    └───────────────┘
```

**Request flow:**
1. Client POSTs a JSON event with `sourceId`, `eventType`, `entityId`, `timestamp`
2. `EventController` validates required fields (Bean Validation / `@Valid`)
3. `DeduplicationService` computes a deduplication key: `{sourceId}:{eventType}:{entityId}:{15-min-bucket}`
4. Key is SHA-256 hashed before being written to Redis (NFR-SEC1)
5. `SET key NX EX 900` (Redis atomic set-if-not-exists with 900-second TTL)
6. If `SET` succeeded → new event → `202 Accepted`
7. If `SET` returned nil → duplicate → `202 Accepted` (silent dedup, decision logged)
8. If Redis is unreachable → `503 Service Unavailable`

---

## Technology Choices

| Technology | Choice | NFR Addressed | Rationale |
|---|---|---|---|
| Language | Java 21 | NFR-M1, NFR-M2 | LTS release; virtual threads available for future I/O scaling |
| Framework | Spring Boot 3.3 | NFR-P1, NFR-D1 | Battle-tested; auto-configuration; Actuator for observability out of the box |
| Build | Gradle 8 (Groovy DSL) | NFR-M2 | Incremental builds; strong ecosystem for OpenAPI Generator plugin |
| Cache / dedup store | Redis 7 (via Spring Data Redis + Lettuce) | NFR-P1, NFR-S1, NFR-A2 | Sub-millisecond atomic `SET NX EX`; TTL-based expiry; horizontally shared across instances |
| API spec | OpenAPI 3.1 + OpenAPI Generator plugin | NFR-M1 | Spec-first guarantees contract consistency; generated interfaces prevent drift |
| Hashing | SHA-256 (JDK `MessageDigest`) | NFR-SEC1 | No PII stored in Redis; hash is deterministic and collision-resistant for dedup purposes |
| Logging | Logback + `logstash-logback-encoder` (structured JSON) | NFR-O1 | Machine-parseable logs; includes dedup key hash and outcome field per event |
| Metrics | Spring Boot Actuator + Micrometer | NFR-O2 | Counters for `events.accepted` and `events.deduplicated`; ready for Prometheus scraping |
| Code coverage | JaCoCo (80% line minimum) | NFR-M1 | Enforced in Gradle `check` task; build fails below threshold |
| Static analysis | Checkstyle + SpotBugs | NFR-M2 | Style and bug checks wired into `check` task |
| Containerisation | Docker (multi-stage build, `eclipse-temurin:21-jre`) | NFR-D1 | Minimal runtime image; no JDK in production container |

---

## NFR Traceability

| NFR | Category | Technology / Design Choice | Rationale |
|---|---|---|---|
| NFR-P1: p99 < 50 ms | Performance | Redis `SET NX EX` (atomic, in-memory) + Lettuce async driver | Single round-trip to Redis; no DB query; Lettuce uses non-blocking I/O |
| NFR-S1: Horizontal scaling | Scalability | All dedup state in Redis only — no in-process cache | Multiple API instances share the same Redis; no sticky sessions needed |
| NFR-A1: 503 on Redis down | Reliability | `RedisConnectionFailureException` caught in service layer → `503` | Fail-safe: surface unavailability rather than silently accept or reject events |
| NFR-A2: 99.9% uptime | Reliability | Redis Sentinel or Cluster in production; Spring Boot Actuator readiness probe | Failover support; readiness probe prevents routing to unhealthy pods |
| NFR-SEC1: No PII in Redis | Security | SHA-256 hash of dedup key before `SET` | Raw `sourceId`/`entityId` values never written to Redis |
| NFR-SEC2: Input validation | Security | Bean Validation (`@NotBlank`, `@NotNull`) on request DTO; `@Valid` in controller | Malformed payloads rejected with `400` before reaching service layer |
| NFR-SEC3: TLS for Redis | Security | Lettuce SSL config via `spring.data.redis.ssl=true` in production profile | In-transit encryption for Redis traffic |
| NFR-M1: 80% line coverage | Maintainability | JaCoCo plugin, `jacocoTestCoverageVerification` in Gradle `check` | Hard build gate |
| NFR-M2: Style + bug checks | Maintainability | Checkstyle (v10.12.1) + SpotBugs wired into `check` | Fail-fast on style or bug violations |
| NFR-O1: Structured logging | Observability | `logstash-logback-encoder`; every dedup decision logs `dedupKey`, `outcome` fields | Enables log-based alerting and audit trail |
| NFR-O2: Metrics | Observability | Micrometer counters: `events.accepted.total`, `events.deduplicated.total` | Prometheus-compatible; dashboards can show dedup rate |
| NFR-D1: Containerised | Portability | Multi-stage Dockerfile; `eclipse-temurin:21-jre-alpine` runtime base | Minimal image; runs on any OCI-compatible platform |
| NFR-D2: Externalised config | Portability | `application.properties` with env-var overrides (`${REDIS_HOST:localhost}`) | No rebuild required for environment changes |

---

## API Design Decisions

| Decision | Choice | Rationale |
|---|----------|-----------|
| Versioning | URL path prefix (`/api/v1`) | Simple; proxy/gateway-friendly; no header negotiation needed |
| Auth | None in v1 | Deferred; service assumed to sit behind perimeter/gateway |
| Duplicate response | `202 Accepted` (same as new event) | Idempotent interface; caller code is simpler; dedup decision logged server-side |
| Error format | Spring default `application/problem+json` (`RFC 7807`) | Standardised; easy to parse; includes `status`, `title`, `detail` |
| Pagination | N/A — write-only endpoint in v1 | No list/query endpoints |

---

## Data Model Summary

No relational database. The only persistent state is in Redis.

### Redis key schema

```
dedup:{sha256(sourceId:eventType:entityId:bucketEpochSeconds)}
```

- **Value**: `"1"` (presence flag only — no value semantics needed)
- **TTL**: 900 seconds (15 minutes)
- **Bucket epoch**: `floor(eventTimestampEpochSeconds / 900) * 900`

**Example** — event received at `2026-03-24T10:11:00Z` (epoch `1742810260`):
- Bucket: `floor(1742810260 / 900) * 900 = 1742810100` (= `2026-03-24T10:15:00Z` window start)
- Raw key: `dedup:sha256("acme:ORDER_CREATED:order-99:1742810100")`

---

## Security Design

- No auth in v1 (NFR-SEC4)
- Input validated via Bean Validation before any Redis interaction
- Dedup keys stored as SHA-256 hashes — no raw user data in Redis
- Redis TLS enabled via Spring profile in production
- No secrets stored in code; Redis credentials via environment variables

---

## Observability Design

| Signal | Implementation |
|--------|---------------|
| Structured logs | `logstash-logback-encoder`; log level `INFO` for accept/dedup; `WARN` for Redis errors; `ERROR` for unexpected failures |
| Metrics | Micrometer counters on `DeduplicationService`: `events.accepted.total` and `events.deduplicated.total` |
| Health | `GET /api/v1/health` delegates to Spring Actuator; includes custom `RedisHealthIndicator` |
| Tracing | Not in scope for v1; Micrometer Tracing can be added without code changes |

---

## Deployment Architecture

- Spring Boot fat JAR built by Gradle
- Docker multi-stage build → minimal `eclipse-temurin:21-jre-alpine` image
- Configuration via environment variables (Redis host, port, TTL overrideable)
- Redis: single-node for dev/test; Redis Sentinel or Cluster for production
- CI: `./gradlew check` (Checkstyle + SpotBugs + JaCoCo) + `./gradlew build` + Docker build

---

## Rejected Alternatives

| Alternative | Reason Rejected |
|---|---|
| In-memory `ConcurrentHashMap` for dedup | Breaks horizontal scaling (NFR-S1); state lost on restart |
| PostgreSQL for dedup state | Too slow for p99 < 50 ms target; no built-in TTL; requires schema management |
| `409 Conflict` for duplicates | Complicates client retry logic; idempotent `202` is a cleaner contract for this use case |
| Bloom filter (in-memory) | Probabilistic — false positives would silently suppress valid events; horizontal scaling still requires shared state |

---

## Open Design Questions

None — all questions from the PRD are resolved.
