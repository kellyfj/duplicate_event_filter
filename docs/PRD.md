# PRD: Duplicate Event Filter

**Version**: 1.0  
**Date**: 2026-03-24  
**Status**: Approved

---

## Problem Statement

Systems that receive events over HTTP from external sources (e.g. webhooks, third-party integrations) can receive the same event more than once — due to retries, at-least-once delivery guarantees, or network failures. Downstream processing of duplicate events causes incorrect business state: double-charges, duplicate notifications, inflated counters, etc.

This service provides a **stateful deduplication layer**: any event submitted within 15 minutes of an identical previously-seen event is detected and rejected before it reaches downstream processing.

---

## Goals

| # | Goal | Measurable Outcome |
|---|------|--------------------|
| G1 | Detect and silently deduplicate duplicate events in real time | Duplicate events are detected and suppressed; caller receives `202 Accepted` (idempotent response) |
| G2 | Accept and record genuinely new events | New events return `202 Accepted` and are stored in Redis for the deduplication window |
| G3 | Auto-expire deduplication state | Redis TTL ensures deduplication keys expire after 15 minutes with no manual cleanup |
| G4 | Provide a clean REST API | Versioned API (`/api/v1`) with OpenAPI spec as the source of truth |

---

## User Stories

- As an **event producer**, I want to submit an event so that it is processed exactly once within a 15-minute window.
- As an **event producer**, I want to receive a `202 Accepted` response whether my event is new or a duplicate so that my client code handles both cases identically (idempotent submission).
- As an **operator**, I want duplicate detection to be stateless from the application's perspective (backed by Redis) so that I can scale API instances horizontally without false duplicates.
- As an **operator**, I want deduplication keys to expire automatically so that events with the same identity can be re-processed after the 15-minute window.

---

## Out of Scope

- Persisting events to a relational database
- Downstream event forwarding or routing
- Authentication / authorisation (deferred to v2)
- Exposing duplicate vs. new status to callers in the HTTP response
- Event schema validation beyond required fields
- Support for deduplication windows other than 15 minutes (hardcoded initially)

---

## Functional Requirements

| # | Requirement |
|---|-------------|
| FR-1 | The service SHALL expose a `POST /api/v1/events` endpoint that accepts a JSON event payload |
| FR-2 | Every incoming event payload MUST contain: `sourceId` (string), `eventType` (string), `entityId` (string), `timestamp` (ISO-8601 UTC string) |
| FR-3 | The service SHALL compute a **deduplication key** from the tuple: `sourceId + eventType + entityId + timestamp-bucket` where the timestamp bucket is the event's timestamp truncated to the nearest 15-minute window |
| FR-4 | If the deduplication key is NOT present in Redis, the service SHALL store it with a TTL of 15 minutes and return `202 Accepted` |
| FR-5 | If the deduplication key IS already present in Redis, the service SHALL return `202 Accepted` without storing the event again (silent deduplication) |
| FR-6 | The service SHALL return `400 Bad Request` for payloads missing any of the required fields |
| FR-7 | The service SHALL expose a `GET /api/v1/health` endpoint that returns service and Redis connectivity status |

---

## Non-Functional Requirements (NFRs)

### Performance
- NFR-P1: p99 response time for `POST /api/v1/events` SHALL be < 50 ms under normal load (Redis in same availability zone)

### Scalability
- NFR-S1: The service SHALL be horizontally scalable — deduplication state is stored exclusively in Redis, not in-process memory

### Availability / Reliability
- NFR-A1: The service SHALL return `503 Service Unavailable` when Redis is unreachable rather than silently accepting or rejecting events
- NFR-A2: Target uptime: 99.9%

### Security
- NFR-SEC1: No PII is stored — deduplication keys are hashed (SHA-256) before being written to Redis
- NFR-SEC2: All inputs are validated; malformed or oversized payloads are rejected with `400 Bad Request`
- NFR-SEC3: Redis connection uses TLS in production
- NFR-SEC4: No authentication required in v1; API is assumed to run behind a network perimeter or API gateway

### Maintainability
- NFR-M1: Minimum 80% line coverage enforced via JaCoCo
- NFR-M2: Checkstyle and SpotBugs checks must pass on every build

### Observability
- NFR-O1: Structured logging (JSON) for every accept and reject decision, including the deduplication key (hashed) and decision outcome
- NFR-O2: Spring Boot Actuator metrics exposed for monitoring

### Portability / Deployment
- NFR-D1: Packaged as a Docker container
- NFR-D2: Configuration (Redis host/port, TTL) via environment variables / `application.properties`

---

## Constraints

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Build**: Gradle (Groovy DSL)
- **Cache**: Redis (via Spring Data Redis / Lettuce)
- **API spec**: OpenAPI 3.x (spec-first, OpenAPI Generator plugin)
- **Deduplication window**: 15 minutes (fixed for v1)

---

## Open Questions

| # | Question | Owner | Resolution |
|---|----------|-------|------------|
| OQ-1 | Should the `200 OK` response return the submitted event echoed back, or just an acknowledgement message? | Product | **Resolved**: Return `202 Accepted` with no body (acknowledgement only) |
| OQ-2 | Should the `409 Conflict` response include the original `firstSeenAt` timestamp? | Product | **Resolved**: No `409` — duplicates also return `202 Accepted` (silent/idempotent deduplication) |
| OQ-3 | Is authentication (JWT / API key) required in v1? | Product | **Resolved**: No auth in v1; deferred to v2 |
