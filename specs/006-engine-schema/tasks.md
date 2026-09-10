---

description: "Task list for 006-engine-schema"
---

# Tasks: The Engine Owns One Schema

**Input**: Design documents from `/specs/006-engine-schema/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md)

**Tests**: One test task, and it comes first. No engine logic changes, so Constitution Principle
VI does not compel it; it is written first anyway because it is the whole proof of the slice —
it fails against `public` and passes against `engine`. The task marked ⚠ MUST be written and seen
failing before the properties that make it pass.

**Organization**: Grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US2, mapping to the spec's prioritized stories
- ⚠: failing-test task — run it, watch it fail, then implement

## Path Conventions

Single Maven module. Main code under `src/main/java/io/guestgraph/`, tests under
`src/test/java/io/guestgraph/`, resources under `src/main/resources/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Nothing to set up. No dependency, no package, no migration (research R6).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Nothing blocks the stories; the slice is two properties and what follows them.

---

## Phase 3: User Story 1 - One Database or Two Is the Deployment's Choice (Priority: P1) 🎯 MVP

**Goal**: Every engine object lives in the `engine` schema, reached as a role that owns it; a
second role in the same database cannot read a single engine table; the API is unchanged.

**Independent Test**: Start the engine against an empty database; verify every table sits in
`engine` and none in `public`, and that a second role owning another schema is refused on each.

### Tests for User Story 1 ⚠

- [X] T001 [US1] ⚠ Create `src/test/java/io/guestgraph/integration/SchemaIsolationTest.java` extending `PostgresIntegrationTest`: query `information_schema.tables` and assert that every table the migrations create — `tenant`, `api_key`, `source_system`, `guest`, `source_record`, `record_identifier`, `record_block_key`, `record_object`, `identifier`, `merge_event`, `resolution_link`, `match_review`, `negative_match_rule`, `identifier_quality_rule`, plus Flyway's history table — has `table_schema = 'engine'` and that no table of the engine's sits in `public` (SC-001); then, as the container's superuser, `create role probe login password 'probe'` and `create schema probe authorization probe`, open a plain JDBC connection as `probe`, and assert that `select count(*) from engine.guest` and one read per table above are refused with a permission error (SC-003). Run and watch it fail: the tables sit in `public`

### Implementation for User Story 1

- [X] T002 [US1] Add to `src/main/resources/application.yaml`, per research R1 and the data model: `spring.datasource.hikari.schema: ${DATABASE_SCHEMA:engine}` and, under the existing `spring.flyway` block, `default-schema: ${DATABASE_SCHEMA:engine}` and `create-schemas: true`, each with a one-line comment naming the rule it serves; no URL parameter, no Hibernate property. Then run T001 and watch it pass
- [X] T003 [P] [US1] Add the deployment paragraph of [data-model.md](data-model.md) to `README.md` under the build-and-run section, verbatim in substance: one schema, one role, the two SQL statements, `DATABASE_SCHEMA`, and the note that the role needs `CREATE` on the database only if the schema is left for the engine to create (FR-002, FR-007)
- [X] T004 [P] [US1] Extend the README's Flyway paragraph: a local database from before this slice holds its tables in `public`, and the engine would create a second set in `engine` beside them rather than fail; drop the volume with `docker compose down -v` (FR-006, research R4)

**Checkpoint**: The engine lives in its schema; a second role is kept out; the guidance exists.

---

## Phase 4: User Story 2 - The Suite and the Diagram Follow the Schema (Priority: P2)

**Goal**: The full suite passes unchanged and the ER drift gate stays green after the move.

**Independent Test**: `./mvnw verify` green with no assertion changed; `./scripts/regen-er.sh`
produces no diff.

### Implementation for User Story 2

- [X] T005 [US2] Change `scripts/regen-er.sh` per research R3: after the container is ready, `create schema engine` through `psql`; apply each migration with the search path set to it (`psql ... -c 'set search_path to engine' -f -` reads the file after the setting, or prefix each file with the `set` on stdin); call mermerd with `-s engine` instead of `-s public`. Run it and confirm `docs/er-schema.mmd` is unchanged
- [X] T006 [US2] Run `./mvnw verify` and read the exit code on its own: every existing suite passes with no assertion changed, `SchemaIsolationTest` included; `PersistenceRulesTest` unchanged (SC-002, SC-004)
- [X] T007 [P] [US2] Confirm `git diff --stat main -- specs/*/contracts/` is empty (SC-004)

**Checkpoint**: Everything that reads the schema reads `engine`.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: The documentation this repository owns.

- [X] T008 [P] `docs/roadmap-notes.md`: mark the cross-cutting note "One schema, one role per service" consumed by `specs/006-engine-schema`, keeping its text; add one sentence that the connector should name its schema through the pool's schema property when slice 5's T008 runs (plan, Complexity Tracking). `docs/matching.md` is untouched: no matcher changed
- [X] T009 [P] `README.md`: no roadmap line — this slice is infrastructure, not a numbered phase; confirm the two README changes of T003 and T004 read as one paragraph each in the prose register
- [X] T010 Walk [quickstart.md](quickstart.md): `docker compose down -v`, start the engine locally, `\dt engine.*` and `\dt public.*`, the slice-1 ingest, and the `probe` role refused; tick each step
- [X] T011 Run `./mvnw spotless:apply`, then `./mvnw verify` and read the exit code on its own, then `./scripts/regen-er.sh` with no diff, then `sh conventions/conventions-check`

---

## Dependencies & Execution Order

### Phase Dependencies

- **US1 (Phase 3)**: T001 first and failing; T002 makes it pass; T003 and T004 in parallel, any time
- **US2 (Phase 4)**: after T002; T005 before T006; T007 any time
- **Polish (Phase 5)**: after both stories; T010 needs Docker and a few minutes; T011 last

### Parallel Opportunities

- T003 and T004; T005 and T007; T008 and T009

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001, watch it fail
2. T002, watch it pass
3. **STOP and VALIDATE**: `\dt engine.*` on a fresh local database

### Incremental Delivery

1. US2 — the script, the full suite, the contract check
2. Polish — the roadmap, the README read-through, the walk, the final verification

---

## Notes

- Eleven tasks, one test, no migration: the size is the point. Anything larger would mean the
  build knew its layout, which FR-003 forbids
- The connector follows R1's property choice in its own task list; nothing here edits slice 5
- Commit after each phase; the branch is `006-engine-schema`
