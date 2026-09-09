# Implementation Plan: Retired Guest Ids Resolve

**Branch**: `004-retired-guest-ids` | **Date**: 2026-09-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-retired-guest-ids/spec.md`

## Summary

A guest id that a merge absorbed or an unmerge emptied stops answering not-found. Reading it
returns a resolution: the guest or guests that hold the person now, when the id was retired and
the chain of decisions in between, followed transitively to active guests. Every sub-resource
under a retired id refuses with problem details that name the current guests. A stored guest id
thereby becomes the reliable external reference the roadmap requires before connectors (slice 5
in the README numbering) may write one back.

Technically: nothing new is written. The audit trail already records every retirement —
`absorbed_guest_ids` on a merge, the detached records on an unmerge and the replay events that
placed them — so the slice is a pure-JVM walk over `merge_event` behind `GraphPort`, mirroring
`ExplainOperation`, plus three port methods, one native containment query, one migration that
adds a partial GIN index and a btree, a `status` field on the guest document and a 410 problem
type. No table changes shape and no existing rule changes meaning.

## Technical Context

**Language/Version**: Java 25 (virtual threads / Loom), unchanged

**Primary Dependencies**: Spring Boot 4, Spring Data JPA + Hibernate, MapStruct, Flyway. No new
dependencies.

**Storage**: PostgreSQL. Migration `V4__retired_guest_id_indexes.sql` — a partial GIN index
and a btree on `merge_event`, nothing else (research R3, R7). The cost of this slice sits on
the write side, and the index shape is chosen so ingest pays only when a merge happens.

**Testing**: JUnit 5 + AssertJ; pure-JVM scenario tests for the resolver on `InMemoryGraph`
(written first, Constitution VI); Testcontainers-backed integration tests for the SQL and the API
surface; ArchUnit (`PersistenceRulesTest`) unchanged; `OpenApiConformanceTest` auto-enrols this
slice's contract, which declares no operation.

**Target Platform**: Linux server (single Spring Boot service), unchanged

**Project Type**: Web service — single Maven module

**Performance Goals**: SC-003 — a resolution through ten retirements under 1 s. A merge hop is
one indexed containment lookup; a split hop is a forward range scan bounded by the replay it
reads (research R3). Ten hops are tens of index probes, not scans.

**Constraints**: Every query tenant-scoped (Constitution I). Resolution writes nothing (FR-010).
One native query, in the sanctioned form — `@Query(nativeQuery = true)` on a repository with a
tenant predicate, as `GuestRepo` already does. No index that every ingest must maintain. No
`JdbcClient` outside the allowlist. JPA stays confined to `io.guestgraph.persistence`.

**Scale/Scope**: 0 new endpoints, 5 changed responses, 0 new tables, 2 new indexes (one
partial), 1 new pure-JVM class, 3 port methods, 1 new exception and problem type, 1 shared guest
gate replacing four inline checks.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
*Source: `.specify/memory/constitution.md` v1.0.0*

**Initial evaluation — PASS.** **Post-design re-evaluation — PASS** (no design element changed a
verdict; notes below reflect the final design).

- [x] **Tenant isolation (I)**: both new queries carry `tenant_id`, as does `guestExists`; the
      walk never leaves the tenant, so an id from another tenant resolves as never-existed and
      no cross-tenant oracle is created (research R2, data-model rule 7).
- [x] **Immutable source records (II)**: no record, link, guest or event is written or altered.
      The resolution is derived per request from the append-only events and is recomputable by
      definition (research R1).
- [x] **No silent data loss (III)**: no ingest path is touched. The one refusal this slice adds,
      410 under a retired id, is RFC 9457 problem details that tell the caller where the data
      now lives.
- [x] **Explainable & reversible resolution (IV)**: no merge path changes and no matcher is
      added; `ResolutionStrategy` is untouched. Every hop names the `MergeEvent` that caused it,
      so a resolution is traceable into explain on the current guest. Unmerge is unchanged and the
      walk reads its consequences (research R2).
- [x] **API-first (V)**: the capability is reachable at `GET /api/v1/guests/{id}`; refusals are
      RFC 9457 with extension members; auth is unchanged.
- [x] **TDD on the resolution engine (VI)**: the resolver is engine logic — it lives in the
      `resolution` package behind `GraphPort` — and is developed test-first as table-driven
      pure-JVM scenarios; Testcontainers tests cover the containment queries and the API shape.
- [x] **Stack & shape**: Java 25 + Spring Boot 4 + PostgreSQL + Maven, single module. No new
      dependency, no new service.
- [x] **Open-core boundary**: nothing commercial.
- [x] **GDPR readiness**: nothing is added that would resist erasure; a follow-on notes that
      erasure's design must decide what an erased id resolves to (research R7).

## Project Structure

### Documentation (this feature)

```text
specs/004-retired-guest-ids/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output — R1..R7
├── data-model.md        # Phase 1 output — indexes, read model, derivation rules, port additions
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output — schemas and the 410 response; no new operation
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — created by /speckit-tasks, not here
```

### Source Code (repository root)

```text
src/main/java/io/guestgraph/
├── resolution/
│   ├── GuestIdResolver.java          # NEW — pure JVM: the walk of research R2
│   ├── GuestIdResolution.java        # NEW — id, status, currentGuestIds, retiredAt, hops
│   ├── ResolutionHop.java            # NEW
│   ├── GuestIdStatus.java            # NEW — ACTIVE | MERGED | SPLIT | RETIRED
│   ├── GraphPort.java                # + guestExists, eventsAbsorbing, eventsSince
│   └── GraphMutationService.java     # + resolve(tenantId, guestId), read-only transaction
├── persistence/
│   ├── PostgresGraph.java            # implements the three port methods
│   └── repo/MergeEventRepo.java      # + one native containment query, one JPQL keyset range (research R3)
└── api/
    ├── GuestGate.java                # NEW — active | not found | retired, for every guest-rooted endpoint (R6)
    ├── RetiredGuestException.java    # NEW — carries the resolution
    ├── ApiExceptionHandler.java      # + 410 guest-retired with extension members (R4)
    ├── GuestController.java          # GET /guests/{id}: ACTIVE document or resolution; gate on records/explain/unmerge
    └── TimelineController.java       # gate replaces the inline existence check

src/main/resources/db/migration/
└── V4__retired_guest_id_indexes.sql  # NEW — partial GIN + btree, additive

src/test/java/io/guestgraph/
├── resolution/GuestIdResolutionScenarioTest.java   # NEW — table-driven, pure JVM, written first
├── resolution/InMemoryGraph.java                   # + the three port methods
├── integration/RetiredGuestIdApiTest.java          # NEW — API shape, 410s, ten-hop timing
└── integration/…                                   # existing suites: `status: ACTIVE` tolerated, nothing else changes

specs/001-core-identity-resolution/contracts/openapi.yaml  # getGuest 200 oneOf; 410 on records, explain, unmerge (R5)
specs/003-timeline-journey/contracts/openapi.yaml          # 410 on timeline (R5)
README.md                                                  # roadmap line already moved; API paragraph gains one sentence
docs/roadmap-notes.md                                      # R-X5 marked consumed; slice-4 condition resolved
```

**Structure Decision**: Single Maven module, unchanged. The resolver joins `ExplainOperation`
and `UnmergeOperation` in `io.guestgraph.resolution` as pure JVM behind `GraphPort`, which is
what lets the scenario tests pin its rules on `InMemoryGraph`. Persistence and API additions
follow the existing layout; the ArchUnit rules constrain them with no rule changes.

## Design Decisions Carried From Phase 0

| # | Decision | Where |
|---|---|---|
| R1 | Walk the events; no retirement table, no backfill | [research.md](research.md) |
| R2 | Latest-candidate retirement, successors per kind, outcome-based status, breadth-first walk | [research.md](research.md), [data-model.md](data-model.md) |
| R3 | One partial GIN for the merge hop, a btree forward scan for the split hop, in `V4` | [research.md](research.md) |
| R4 | 200 with `status` on `GET /guests/{id}`; 410 `guest-retired` with extension members under it | [research.md](research.md), [contracts/openapi.yaml](contracts/openapi.yaml) |
| R5 | Schemas in this contract; changed operations amended in the owning contract files | [research.md](research.md) |
| R6 | One `GuestGate` for the four guest-rooted checks | [research.md](research.md) |
| R7 | Indexes-only migration; harness unchanged; erasure follow-on | [research.md](research.md) |

## Complexity Tracking

No Constitution Check violations — the table is intentionally empty.

Two choices worth naming even though neither is a violation:

- **Editing two earlier slices' contract files** (research R5). The served document refuses a
  path declared twice, so an operation's response can only change in the file that declared it.
  Slice 3 did the same for paging and stated it; this plan states it the same way.
- **A migration for indexes alone.** The walk is correct without them; they exist for SC-003 at
  the scale `merge_event` reaches, one row per ingest. The partial GIN index is the one place
  this repository queries inside a jsonb column; the companion-table alternative that would
  remove it is recorded in research R3 as the fallback if the index ever shows in a profile.

## Follow-ons Not In This Slice

- Roadmap note R-X5 is marked consumed at merge, and the slice-4 connector paragraph loses its
  "build it or state the constraint" condition: the endpoint exists, so the connector contract
  states the integrator rule instead — on `MERGED`, replace the stored id.
- Lawful erasure, when built, decides what an erased id resolves to (research R7).
- A `/guest-ids/{id}` alias that never returns a profile, if a consumer asks for one (research
  R4).
- A companion table of absorbed guest ids, replacing the partial GIN index and its native
  query, if the index ever shows in a profile (research R3).
