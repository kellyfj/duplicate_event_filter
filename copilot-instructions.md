---
name: Project Instructions
description: "Workspace guidelines for all agent interactions"
---

# General Approach

## Clarifying Questions

When asking for clarification, **always explain upfront what I'll do with the answers**. Don't just ask — say "I'll use this to generate X" or "This will determine Y." Be specific about the outcome.

**Hard limit: never ask more than 3–5 questions at once.** If more information is needed, ask the most critical questions first, then ask follow-ups in a later round once those are answered.


## Response Brevity

- Keep responses **concise and scannable**
- Use bullet points and short paragraphs
- For multiple ideas, use a comparison table if helpful
- Avoid lengthy explanations; ask for clarification instead
- If something requires detail, offer it only if asked

# Sequence of Work

## Order of work — always follow this sequence:
1. **Write and confirm PRD** — capture problem statement, goals, user stories, functional requirements, NFRs, and constraints in `docs/PRD.md`; wait for approval before proceeding
2. **Write and confirm Technical Design** — record technology choices (each mapped to at least one NFR), architecture overview, and API design decisions in `docs/DESIGN.md`; wait for approval before proceeding
3. **Discuss and confirm entities** — propose entity names, key fields, and relationships as a table; wait for approval before writing any spec or code
4. Write and confirm `openapi.yaml` spec (built from the confirmed entities; all paths prefixed with `/api/v1`)
5. Generate scaffold (entities, repositories, controller interfaces) from the spec
6. We may wish to review the SQL schema generated from the entities before proceeding to implementations — if so, generate the schema and present it as a table for review before writing any service code
7. Write service interfaces
8. Add implementations when explicitly asked
9. Add unit tests
10. Add integration tests when explicitly asked

# Requirements & PRD

Before any design or code work begins, capture requirements in a **PRD document** at `docs/PRD.md`.

## PRD must include

| Section | Content |
|---|---|
| **Problem Statement** | What problem is being solved and for whom |
| **Goals** | Measurable outcomes the project must achieve |
| **User Stories** | `As a <role>, I want <action> so that <benefit>` — one per line |
| **Out of Scope** | Explicit list of what will NOT be built |
| **Functional Requirements** | Numbered list of what the system must do |
| **Non-Functional Requirements (NFRs)** | See categories below |
| **Constraints** | Tech stack, deadlines, budget, compliance |
| **Open Questions** | Unresolved decisions that must be answered before build |

## NFR categories to always address

- **Performance** — e.g. p99 response time, throughput targets
- **Scalability** — expected load, growth projections
- **Availability / Reliability** — uptime SLA, failover expectations
- **Security** — auth model, data classification, compliance requirements (e.g. GDPR, SOC 2)
- **Maintainability** — code coverage minimums, linting standards, documentation requirements
- **Observability** — logging, metrics, tracing expectations
- **Portability / Deployment** — target environments (cloud, on-prem, containerised)

> **RULE**: A PRD must be written, reviewed, and confirmed before entity design begins. No entity tables, API specs, or code are produced until the PRD is approved.

> **LIVING DOCUMENT**: `docs/PRD.md` must be kept up to date throughout the project. Whenever requirements change, new user stories emerge, scope is added or cut, or open questions are resolved, update the PRD immediately — do not defer. If a code or design decision contradicts the PRD, flag the conflict and update the PRD before proceeding.

# Technical Design Document

After the PRD is confirmed, capture all significant technical and architectural decisions in `docs/DESIGN.md`.

## DESIGN.md must include

| Section | Content |
|---|---|
| **Architecture Overview** | High-level diagram or description of system components and interactions |
| **Technology Choices** | See table format below — each choice explicitly justified against one or more NFRs |
| **API Design Decisions** | Versioning strategy, auth approach, pagination style, error format |
| **Data Model Summary** | Key entities, relationships, and any notable persistence decisions |
| **Security Design** | Auth/authz flow, secret management, data-at-rest and in-transit decisions |
| **Observability Design** | Logging strategy, metrics, tracing, alerting approach |
| **Deployment Architecture** | Target environment, containerisation, CI/CD pipeline overview |
| **Rejected Alternatives** | What was considered and why it was ruled out |
| **Open Design Questions** | Decisions still to be made before implementation |

## NFR traceability table format

For every **critical NFR** defined in the PRD, record how it is addressed by one or more technology or design choices:

| NFR | Category | Technology / Design Choice | Rationale |
|---|---|---|---|
| e.g. p99 response < 200ms | Performance | Connection pooling (HikariCP), indexed queries | Eliminates connection overhead; index on hot query paths |
| e.g. 99.9% uptime SLA | Reliability | PostgreSQL with replication, health-check endpoints | Failover support; readiness probe enables zero-downtime deploys |
| e.g. GDPR compliance | Security | JWT (jjwt), TLS in transit, encrypted PII columns | Stateless auth; data encrypted at rest and in transit |
| e.g. HIPAA compliance | Security | AES-256 encryption at rest, TLS 1.2+ in transit, audit log table, role-based access control | PHI must be encrypted, access logged, and access-controlled per HIPAA Security Rule (45 CFR §164) |
| e.g. PCI-DSS compliance | Security | Tokenisation (no raw card storage), TLS 1.2+, network segmentation, WAF | Card data must never be stored or transmitted in the clear; scope reduction via tokenisation limits PCI audit surface |
| e.g. SOC 2 Type II compliance | Security / Observability | Centralised structured logging (e.g. ELK / CloudWatch), immutable audit trail, alerting on anomalous access | SOC 2 Trust Criteria require evidence of continuous monitoring, availability, and access control over a defined audit period |
| e.g. CCPA compliance (California) | Security | Consent management, data deletion API endpoint, PII inventory, encrypted PII columns | CCPA grants California residents the right to know, delete, and opt out of sale of personal information; deletion endpoint fulfils right-to-delete obligation |

Every critical NFR from the PRD must have at least one row. Technology choices that do not address any NFR do not need to appear here — requirements drive technology selection, not the other way around.

> **RULE**: `docs/DESIGN.md` must be written and confirmed before entity design or API spec work begins. Technology choices made informally during earlier discussions must be backfilled here before code is generated.

> **LIVING DOCUMENT**: `docs/DESIGN.md` must be kept up to date throughout the project. Whenever a new technology is adopted, an architectural decision is made or revised, a rejected alternative is reconsidered, or an NFR traceability mapping changes, update DESIGN.md immediately — do not defer. If a code change implies an architectural decision not yet recorded, add it to DESIGN.md as part of the same change.

## Technology Decision Process

When proposing any technology choice (e.g. database, cache, message broker, container platform, DRM vendor, auth provider):

1. **One technology at a time** — do not batch multiple choices into a single table or paragraph; present each decision separately
2. **State the chosen option** and the primary NFR(s) it satisfies
3. **Present exactly 2 named alternatives** that were genuinely considered
4. **Explain why each alternative was rejected** — be specific (cost, operational burden, missing feature, risk, etc.)
5. **Wait for confirmation** before recording the choice in `docs/DESIGN.md`

**Format for each technology decision:**

> **Decision: \<component\>**
> - **Chosen**: \<technology\> — \<one-line justification tied to an NFR\>
> - **Alternative 1**: \<technology\> — rejected because \<specific reason\>
> - **Alternative 2**: \<technology\> — rejected because \<specific reason\>

Do **not** compress multiple technology decisions into a single response. Work through them one at a time and wait for approval at each step.


# Coding Approach

## Present Ideas Before Code

When proposing solutions:
1. **Present 3–5 ideas first** with brief descriptions (what each does, tradeoffs)
2. **Wait for feedback** before implementing
3. Only write code after you've gotten input or guidance

This approach ensures we discuss options, constraints, and design upfront rather than jumping straight to implementation.

# Database Design

**Primary Key Strategy** (applies to all languages): Never use UUID v4 as a primary key — random UUIDs cause B-tree index fragmentation, page splits, and cache thrash on every insert. When entity design begins, always prompt the user to choose one of the approved strategies:

> "For primary keys, which strategy do you prefer?
> - **UUID v7** — time-ordered, so inserts are near-sequential and index-friendly; non-enumerable and safe for distributed generation. Best for APIs where IDs are exposed externally.
> - **BIGSERIAL** (auto-increment `BIGINT`) — simplest and fastest; always sequential. Fine for internal systems, but exposes row ordering in the API (enumerable IDs)."

Default to **UUID v7** unless the user explicitly chooses otherwise. Never silently use UUID v4.

## Language-specific notes

### Java / Spring Boot
- Use the `uuid-creator` library for UUID v7 generation
- Never use `@GeneratedValue(strategy = GenerationType.UUID)` or `UUID.randomUUID()` as a primary key

### Go
- Use `github.com/google/uuid` v1.6+ or `github.com/gofrs/uuid/v5` for UUID v7 generation
- Never use `uuid.New()` as a primary key

