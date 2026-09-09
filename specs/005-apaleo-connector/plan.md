# Implementation Plan: Apaleo Connector

**Branch**: `005-apaleo-connector` | **Date**: 2026-09-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-apaleo-connector/spec.md`

## Summary

The first connector turns an Apaleo account's reservations and bookings into observations under
the convention slice 3 published and keeps them current: webhooks announce changes on both
objects, a reconciliation walks the reservation list behind them, a full sync walks everything on
request and after a gap longer than Apaleo's retry window, and a version is submitted only when a
person on the object changed. Two objects
because Apaleo keeps the guests on the reservation and the booker on the booking, each with its
own clock (spec Clarifications). The connector holds the guest id the engine answers for every person and
applies the integrator rule slice 4 defined, replacing on MERGED and raising SPLIT for a person.
It writes nothing into Apaleo.

Technically: a standalone Spring Boot service in `guestgraph/connector-apaleo`, on the engine's
stack and guardrails, with a small PostgreSQL state of its own — the last submitted version and
roster hash per reservation, processed events, sync points, runs and held guest ids. Two clients,
one for Apaleo's Booking and Webhook APIs and one for the engine's ingest and guest endpoints; a
mapper and a roster hash as pure functions; a worker draining the event queue; a scheduler for
reconciliation and refresh; and an operations surface of five endpoints. The engine is asked for
nothing new.

## Technical Context

**Language/Version**: Java 25 (virtual threads / Loom), the family's stack (research R1)

**Primary Dependencies**: Spring Boot 4 — web, scheduling, a `RestClient` per upstream, Actuator
health; Spring Data JPA + Hibernate; Flyway; Jackson. Tests: JUnit 5, AssertJ, Testcontainers,
WireMock. No library the engine does not already carry except WireMock.

**Storage**: PostgreSQL, the connector's own database, migration `V1__connector_state.sql` per
[data-model.md](data-model.md). All of it a cache; loss costs a full sync.

**Testing**: Pure-JVM unit tests for the mapper and the roster hash on recorded Apaleo documents;
Testcontainers integration tests with WireMock for both upstreams; one end-to-end walk by hand
against a local engine and an Apaleo sandbox (research R10). ArchUnit, PMD and Spotless as in the
engine.

**Target Platform**: Linux server, one instance per Apaleo account and engine tenant, reachable
over HTTPS by Apaleo for the webhook

**Project Type**: Web service — single Maven module, in a new repository

**Performance Goals**: SC-001, a 10,000-reservation account in under an hour: 20 pages of 500,
some 15,000 records in batches of 100, against the engine's 30–100 records per second per tenant.
SC-003, a person change visible within two minutes, with Apaleo's one-minute delivery.

**Constraints**: One account, one tenant per instance (FR-015). Every key derived from Apaleo's
state (FR-002). No person data and no credential in any log, status or error (FR-016). Never
drop an event or a reservation (FR-006, FR-011). Never give up a sync on rate limiting (FR-017).

**Scale/Scope**: 1 new repository; 2 source object types; 5 tables; 2 upstream clients; 1
webhook endpoint; 5 operations endpoints; 2 scheduled runs; 0 engine changes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
*Source: `.specify/memory/constitution.md` v1.0.0*

The constitution governs the engine. The connector is a client of it and is held to the
principles as they reach a client.

**Initial evaluation — PASS.** **Post-design re-evaluation — PASS.**

- [x] **Tenant isolation (I)**: an instance holds one tenant's credential and one account's, and
      its state has no tenant column because it has one tenant. Two instances for two tenants
      share nothing (FR-015; spec edge case on a doubly connected account).
- [x] **Immutable source records (II)**: the connector only submits; it never asks the engine to
      alter or remove a record, and a canceled or deleted reservation removes nothing (research
      R5). Its own state is a cache, not a record.
- [x] **No silent data loss (III)**: every reservation it can list is submitted whatever its data
      quality (FR-006); an unreadable version is submitted for the engine to flag (US1 scenario
      8); a failed event or fetch is kept with its reason and retried (FR-011).
- [x] **Explainable & reversible resolution (IV)**: the connector makes no resolution decision.
      Its credential is agent-operated and named, so every record it submits is attributed to it;
      it never confirms, rejects, unmerges or lifts anything.
- [x] **API-first (V)**: the connector uses only the published ingest, source-object and guest
      endpoints and needs no new one; its own surface is documented in
      [contracts/connector-api.yaml](contracts/connector-api.yaml).
- [x] **TDD on the resolution engine (VI)**: no engine code changes. The connector's own pure
      functions, the mapper and the roster hash, are written test-first on recorded documents
      because that is where its correctness lives (research R10).
- [x] **Stack & shape**: the engine's stack, one module, in a separate repository by the owner's
      decision; the engine stays a single service.
- [x] **Open-core boundary**: the connector is open, in the guestgraph organization, Apache-2.0.
- [x] **GDPR readiness**: the connector's state holds no person field — only ids, hashes, times
      and counters — so erasure in the engine leaves nothing to erase here beyond held guest
      ids, which the refresh reports as not found when the time comes.

## Project Structure

### Documentation (this feature)

```text
specs/005-apaleo-connector/           # in guestgraph/engine — the slice's decisions live here
├── plan.md                           # This file
├── spec.md
├── research.md                       # R1..R11
├── data-model.md                     # the connector's five tables, rules, configuration
├── quickstart.md
├── contracts/
│   ├── mapping.md                    # Apaleo reservation → engine observations
│   └── connector-api.yaml            # the connector's own operations surface — not openapi.yaml, which the engine would bundle
├── checklists/
│   └── requirements.md
└── tasks.md                          # Phase 2 — created by /speckit-tasks, not here
```

### Source Code (`guestgraph/connector-apaleo`, a new repository)

```text
AGENTS.md, CLAUDE.md                  # the family's vendor adapter; conventions/ vendored at the pin
conventions.json                      # the pin
.github/workflows/verify.yml          # job id `verify`; the conventions job called from robertblust/conventions
pom.xml                               # Java 25, Spring Boot 4; Spotless, PMD, ArchUnit as in the engine
compose.yaml                          # local PostgreSQL

src/main/java/io/guestgraph/connector/apaleo/
├── apaleo/
│   ├── ApaleoAuth.java               # client credentials, token cache, refresh ahead of expiry (R2)
│   ├── ApaleoClient.java             # list reservations (paged, sorted, 204 = end), list bookings, fetch either, backoff (R2)
│   ├── ApaleoWebhooks.java           # list / create / replace the subscription on startup (R5)
│   └── model/                        # Reservation, Booking, Guest, Booker, Event — the fields the mapping reads
├── mapping/
│   ├── ApaleoMapper.java             # pure: one reservation or booking version → N ingest records per contracts/mapping.md (R3)
│   └── RosterHash.java               # pure: the hash of data-model rule 3, per object (R4)
├── engine/
│   ├── EngineClient.java             # register source system, submit batches, read results, read a guest (R7, R8)
│   └── model/                        # IngestRecord, IngestResult, GuestResolution — the engine's contract
├── sync/
│   ├── ObjectSubmitter.java          # fetch → hash → submit → update state, sync point and held ids (rules 2, 4, 5)
│   ├── FullSync.java                 # every reservation, all statuses, then every booking (R6)
│   ├── Reconciliation.java           # from sync point minus overlap, bookings through their reservations, subscription check (R6)
│   ├── GapGuard.java                 # a gap longer than Apaleo's retry window since the last activity starts a full sync (R6)
│   └── Refresh.java                  # the integrator rule over held ids (R8)
├── events/
│   ├── EventEndpoint.java            # POST /apaleo/events/{secret}: store, 202; empty body 200; wrong secret 404 (R5)
│   └── EventWorker.java              # drain PENDING events with backoff (R5)
├── ops/
│   ├── StatusController.java         # GET /status, POST /sync/full, /sync/reconcile, /refresh, GET /runs/{id} (R9)
│   └── OpsTokenFilter.java           # bearer token for the ops surface
├── state/                            # entities, @Query-only repositories, Flyway-backed
└── config/                           # ConnectorProperties from the environment; masked in logs

src/main/resources/db/migration/V1__connector_state.sql

src/test/java/io/guestgraph/connector/apaleo/
├── mapping/ApaleoMapperTest.java             # recorded reservation and booking documents in src/test/resources/apaleo/
├── mapping/RosterHashTest.java
├── integration/ConnectorIntegrationTest.java # Testcontainers PostgreSQL + WireMock Apaleo + WireMock engine
├── integration/FullSyncTest.java
├── integration/EventTest.java
├── integration/ReconcileTest.java
├── integration/RefreshTest.java
├── integration/StatusTest.java               # incl. the log search of SC-007
└── architecture/PersistenceRulesTest.java    # @Query-only repositories, JPA confined to state
```

**Structure Decision**: One module, packages by upstream and by run. The two pure functions the
connector's correctness depends on, the mapper and the hash, take no Spring and no JPA so they
are tested on documents alone; the clients, the runs and the endpoints are thin around them. The
engine's contract is copied as three small records in `engine/model`, not generated from its
OpenAPI, so a change in the engine's contract is a visible edit here rather than a silent
regeneration.

## Design Decisions Carried From Phase 0

| # | Decision | Where |
|---|---|---|
| R1 | The engine's stack and guardrails, PostgreSQL state, a separate repository | [research.md](research.md) |
| R2 | Client credentials; reservations sorted by update, paged at 500; the booker read from the booking; backoff on 429 | [research.md](research.md) |
| R3 | Mapping for two objects: six extracted fields at the top, everything else nested, booking dates derived, no payment data | [contracts/mapping.md](contracts/mapping.md) |
| R4 | Roster hash per object over role, position and the extracted and name fields; submit on change only | [research.md](research.md), [data-model.md](data-model.md) |
| R5 | Store-then-202 endpoint, dedup on Apaleo's id, seven event types by configuration, no `deleted` | [research.md](research.md) |
| R6 | Sync point per property; reconciliation from point minus one hour reaching bookings through reservations; no poll — a full sync after a gap longer than the retry window | [research.md](research.md) |
| R7 | Batches of 100, one reservation per batch, every result read; agent-registered key | [research.md](research.md) |
| R8 | Held ids per slot; nightly refresh applies the integrator rule; SPLIT surfaced, never chosen | [research.md](research.md) |
| R9 | Five operations endpoints behind a bearer token; structured logs with no person data | [contracts/connector-api.yaml](contracts/connector-api.yaml) |
| R10 | Unit tests on recorded documents, WireMock integration, one manual end-to-end walk | [research.md](research.md) |
| R11 | Six sandbox items to settle before the event list and the release | [research.md](research.md) |

## Complexity Tracking

No Constitution Check violations — the table is intentionally empty.

Three choices worth naming even though none is a violation:

- **A second repository.** The owner's decision, for the reason that a connector built against
  the public API is the connector a third party could build. The cost is a family change:
  `REPOSITORIES.md` gains a row through a robertblust/conventions release, the new repository
  vendors the conventions and runs the shared job, and the re-sync order in `REPOSITORIES.md`
  gains a member. Those steps are the owner's and precede the first commit there.
- **A database for a cache.** Five small tables could live in memory with a rebuild on start, but
  a rebuild is a full sync, and a connector that resyncs an account on every restart is one that
  gets restarted less than it should. PostgreSQL costs one more container per deployment and
  nothing in code the engine does not already have.
- **Stubbing the engine in CI.** A live engine in the connector's CI would mean building it
  there. The stub is checked against the engine's contract files by hand at planning and by the
  end-to-end walk before release; an engine image is the follow-on that removes the stub.

## Follow-ons Not In This Slice

- Write-back of the held guest id into an Apaleo field, once the field is known; the refresh of
  R8 is its rehearsal.
- An ingest-scoped engine credential for connectors (roadmap R5-1, prerequisite 2), so a
  connector's key cannot unmerge or change configuration.
- An engine container image, so the connector's CI can run the real engine instead of a stub.
- A status on the source-object block and the association, so a canceled booking can leave a
  guest's timeline (roadmap R3-2).
- A connect client for many accounts, for the managed offering (research R2).
