# Implementation Plan: The Engine Owns One Schema

**Branch**: `006-engine-schema` | **Date**: 2026-09-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-engine-schema/spec.md`

## Summary

The engine moves from the database's default schema into a schema of its own, `engine`, reached
as a role that owns it and nothing else, so that one database or two is a deployment choice and
a second service's role cannot read the engine's tables. Nothing in the engine names a schema
today, so the move is two properties fed by one configuration value, the ER regeneration script
following the schema, one integration test that proves the isolation, and one paragraph of
deployment guidance in the README. No migration changes, no contract changes.

## Technical Context

**Language/Version**: Java 25, unchanged

**Primary Dependencies**: Spring Boot 4, HikariCP as the pool it ships, Flyway, the PostgreSQL
driver. No new dependency.

**Storage**: PostgreSQL. No migration. The pool's connection schema and Flyway's default schema
both read `DATABASE_SCHEMA`, default `engine` (research R1).

**Testing**: one new Testcontainers test, `SchemaIsolationTest`; every existing suite unchanged;
ArchUnit, PMD, Spotless as before; the ER drift gate after the script follows the schema.

**Target Platform**: Linux server, unchanged

**Project Type**: Web service — single Maven module

**Performance Goals**: none new; a search path on each pooled connection costs nothing measurable.

**Constraints**: Nothing may name the schema (FR-003). Every API contract and response is
unchanged (FR-004). A database from before the move is dropped, not migrated (FR-006).

**Scale/Scope**: 2 properties in `application.yaml`, 1 script change, 1 new test, 1 README
paragraph and 1 README sentence, 0 migrations, 0 contract changes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
*Source: `.specify/memory/constitution.md` v1.0.0*

**Initial evaluation — PASS.** **Post-design re-evaluation — PASS.**

- [x] **Tenant isolation (I)**: unchanged inside the engine; strengthened outside it, because a
      role that owns only `engine` is what keeps another service's role out of every tenant's
      rows (FR-007).
- [x] **Immutable source records (II)**: no row is touched; the tables move namespace, not
      content, and only before any deployment exists.
- [x] **No silent data loss (III)**: no ingest path changes. The one silent case, a stale local
      volume whose tables sit in `public`, is documented as a drop before the first release
      (research R4).
- [x] **Explainable & reversible resolution (IV)**: untouched.
- [x] **API-first (V)**: no contract or response changes (FR-004, SC-004).
- [x] **TDD on the resolution engine (VI)**: no engine logic changes; the new test is written
      first anyway and fails against `public` before the properties are set.
- [x] **Stack & shape**: unchanged.
- [x] **Open-core boundary**: nothing commercial.
- [x] **GDPR readiness**: erasure and backups act on the schema as they did on the database.

## Project Structure

### Documentation (this feature)

```text
specs/006-engine-schema/
├── plan.md              # This file
├── spec.md
├── research.md          # R1..R6
├── data-model.md        # the namespace move, the rule, the README paragraph
├── quickstart.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — created by /speckit-tasks, not here
```

### Source Code (repository root)

```text
src/main/resources/application.yaml                          # spring.datasource.hikari.schema and spring.flyway.default-schema from DATABASE_SCHEMA (R1)
scripts/regen-er.sh                                          # create schema engine; search_path on each migration; mermerd -s engine (R3)
src/test/java/io/guestgraph/integration/SchemaIsolationTest.java   # NEW — catalog query and second-role refusal (R5)
README.md                                                    # the deployment paragraph of data-model.md; the drop-the-volume sentence (R4)
docs/roadmap-notes.md                                        # the cross-cutting note marked consumed
```

**Structure Decision**: No new package, no migration. The schema is named in one configuration
value and read by the two components that need it; everything else follows the connection.

## Design Decisions Carried From Phase 0

| # | Decision | Where |
|---|---|---|
| R1 | Pool schema and Flyway default schema from one value; no URL parameter | [research.md](research.md) |
| R2 | A role that owns the schema; two statements of guidance | [research.md](research.md), [data-model.md](data-model.md) |
| R3 | The ER script creates the schema and reads it | [research.md](research.md) |
| R4 | Harness and compose unchanged; a stale volume is dropped, and the README says so | [research.md](research.md) |
| R5 | One integration test proves both success criteria | [research.md](research.md) |
| R6 | V1 is not edited | [research.md](research.md) |

## Complexity Tracking

No Constitution Check violations — the table is intentionally empty.

One choice worth naming: **the connector's configuration names the schema on the JDBC URL**
(slice 5, data model) while this slice uses the pool's schema property. Both work; one value in
one place is the better shape, and the connector should follow it when its configuration is
built (slice 5, T008). Recorded as a follow-on rather than edited into slice 5's frozen data
model.

## Follow-ons Not In This Slice

- Slice 5, T008: name the connector's schema through the pool's schema property rather than a
  URL parameter, matching R1.
- An upgrade path from `public` to `engine`, only if a deployment from before the first release
  ever needs one; none exists.
