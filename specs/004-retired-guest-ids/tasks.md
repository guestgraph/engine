---

description: "Task list for 004-retired-guest-ids"
---

# Tasks: Retired Guest Ids Resolve

**Input**: Design documents from `/specs/004-retired-guest-ids/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

**Tests**: Test tasks are included and are not optional here. Constitution Principle VI makes TDD
mandatory for engine work, and `GuestIdResolver` lives in the `resolution` package behind
`GraphPort`: its rules — latest-candidate retirement, successors per kind, outcome-based status,
dedup across branches — are where the subtle bugs live. Tasks marked ⚠ MUST be written and seen
failing before the implementation task that follows them.

**Organization**: Grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US4, mapping to the spec's prioritized stories
- ⚠: failing-test task — run it, watch it fail, then implement

## Path Conventions

Single Maven module. Main code under `src/main/java/io/guestgraph/`, tests under
`src/test/java/io/guestgraph/`, migrations under `src/main/resources/db/migration/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: The pure value types every later phase refers to. No new dependencies.

- [X] T001 Create the read-model types in `src/main/java/io/guestgraph/resolution/`: `GuestIdStatus` enum (ACTIVE, MERGED, SPLIT, RETIRED), `ResolutionHop` record (`retiredGuestId`, `kind` as a nested enum MERGE | SPLIT, `eventId`, `at`, `successorGuestIds`), `GuestIdResolution` record (`id`, `status`, `currentGuestIds`, `retiredAt`, `hops`) per [data-model.md](data-model.md). Plain records, no Spring and no JPA imports — the ArchUnit `onlyPersistenceDependsOnJpa` rule covers this package. `currentGuestIds` holds every active guest the walk ended at, each once, in first-reached order; `hops` is in the order the retirements happened

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Port, storage and harness changes every story below depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Add three methods to `src/main/java/io/guestgraph/resolution/GraphPort.java` per [data-model.md](data-model.md): `boolean guestExists(UUID tenantId, UUID guestId)`; `List<MergeEvent> eventsAbsorbing(UUID tenantId, UUID guestId)` — events whose absorbed list contains the guest, oldest first; `List<MergeEvent> eventsSince(UUID tenantId, Instant from, UUID afterId, int limit)` — one page of the tenant's events in `(createdAt, id)` order, at or after `from` when `afterId` is null (replay events may share the unmerge's timestamp) and strictly after `(from, afterId)` otherwise. Javadoc each with the rule it serves (research R2, R3)
- [X] T003 [P] Implement the three methods in `src/test/java/io/guestgraph/resolution/InMemoryGraph.java` over its existing `guests` map and `events` list, with the same ordering as the Postgres adapter: `eventsAbsorbing` filters `absorbedGuestIds().contains(guestId)`; `eventsSince` sorts by `(createdAt, id)` and returns the page after the keyset
- [X] T004 [P] Write additive migration `src/main/resources/db/migration/V4__retired_guest_id_indexes.sql` per [data-model.md](data-model.md): `CREATE INDEX merge_event_absorbed_gin ON merge_event USING gin (absorbed_guest_ids jsonb_path_ops) WHERE absorbed_guest_ids <> '[]'::jsonb;` and `CREATE INDEX merge_event_tenant_time_idx ON merge_event (tenant_id, created_at, id);`. Nothing else — no table changes shape (research R3, R7)
- [X] T005 Add two queries to `src/main/java/io/guestgraph/persistence/repo/MergeEventRepo.java`: a native `@Query(nativeQuery = true)` `findAbsorbing(tenantId, needle)` — `select * from merge_event where tenant_id = :tenantId and absorbed_guest_ids <> '[]'::jsonb and absorbed_guest_ids @> cast(:needle as jsonb) order by created_at, id`, with `:needle` a one-element JSON array string; and two native keyset queries, `findFrom(tenantId, from, limit)` — `created_at >= :from`, the inclusive first page — and `findAfter(tenantId, after, afterId, limit)` — `(created_at, id) > (:after, :afterId)`; two queries rather than one OR on a nullable cursor so the planner keeps an index seek. All carry the tenant predicate the ArchUnit `everyRepositoryMethodIsTenantScoped` rule requires (research R3)
- [X] T006 Implement the three port methods in `src/main/java/io/guestgraph/persistence/PostgresGraph.java`: `guestExists` via `guestRepo.findGuest(...).isPresent()`; `eventsAbsorbing` via `findAbsorbing` with the needle built as `"[\"" + guestId + "\"]"` and mapped through `mappers::toDomain`; `eventsSince` via `findFrom` when `afterId` is null and `findAfter` otherwise
- [X] T007 Run `./scripts/regen-er.sh` and confirm `docs/er-schema.mmd` is unchanged — indexes are not rendered, but CI's er-drift job re-runs the script on every migration change
- [X] T008 Establish the green baseline after the migration: `docker compose down -v` to clear the stale local volume (Flyway checksum mismatch otherwise), then `./mvnw verify` — every slice-1 to slice-3 suite must pass untouched against V4 before story work starts

**Checkpoint**: Port extended on both adapters, indexes in place, harness green.

---

## Phase 3: User Story 1 - A Stored Guest Id Never Goes Dark (Priority: P1) 🎯 MVP

**Goal**: A guest id absorbed by a merge answers 200 with the surviving guest and the time of the
merge; an active guest answers as before plus `status: ACTIVE`; an unknown or cross-tenant id
answers 404 as today.

**Independent Test**: Ingest two records that resolve separately, then one carrying both
identifiers. Read the absorbed id and verify it names the survivor, the merge event and its time;
read the survivor and verify the guest document is unchanged but for `status`.

### Tests for User Story 1 ⚠

- [X] T009 [P] [US1] ⚠ Create `src/test/java/io/guestgraph/resolution/GuestIdResolutionScenarioTest.java` on `EngineFixture` / `InMemoryGraph` with the single-hop scenarios: an active guest resolves `ACTIVE` with no hops; a guest absorbed by an ingest merge resolves `MERGED` with `currentGuestIds` = [survivor], `retiredAt` = the merge event's `createdAt`, and one hop of kind MERGE whose `eventId` is that event; a guest absorbed by a `REVIEW_CONFIRM` resolves the same way; a random id resolves as never-existed (the resolver's `Optional.empty()` or equivalent); an id retired in tenant B resolves as never-existed under tenant A. Run and watch it fail
- [X] T010 [P] [US1] ⚠ Create `src/test/java/io/guestgraph/integration/RetiredGuestIdApiTest.java` extending `PostgresIntegrationTest` with quickstart steps 1–6: after the merge, `GET /api/v1/guests/{absorbed}` is 200 with `status: MERGED`, `currentGuestIds: [survivor]`, `retiredAt`, one hop whose `eventId` appears in `GET /guests/{survivor}/explain`; `GET /guests/{survivor}` carries every slice-1 field unchanged plus `status: ACTIVE`; a random uuid is 404 `not-found` with no `currentGuestIds`; the absorbed id read with tenant B's key is the same 404. Run and watch it fail

### Implementation for User Story 1

- [X] T011 [US1] Create `src/main/java/io/guestgraph/resolution/GuestIdResolver.java` (pure JVM, constructor takes `GraphPort`) implementing derivation rules 1, 2 (merge candidates only for now), 6 and 7 of [data-model.md](data-model.md): `Optional<GuestIdResolution> resolve(UUID tenantId, UUID guestId)` — `ACTIVE` when `guestExists`; otherwise the latest event by `(createdAt, id)` among `eventsAbsorbing` is the retirement, its `guestId` the successor; empty when the read id has no candidate. Record `retiredAt` from the read id's own retirement. Leave the walk single-hop; US2 makes it transitive
- [X] T012 [US1] Wire the resolver: construct it where `ExplainOperation` is constructed (the configuration that builds the pure operations), and add `@Transactional(readOnly = true) Optional<GuestIdResolution> resolve(UUID tenantId, UUID guestId)` to `src/main/java/io/guestgraph/resolution/GraphMutationService.java`
- [X] T013 [US1] Change `GET /{guestId}` in `src/main/java/io/guestgraph/api/GuestController.java`: an existing guest returns `GuestResponse` extended with a `status` field always `"ACTIVE"` (add the field to the record; every existing field keeps its name and meaning, FR-012); otherwise call `mutationService.resolve` and return a `GuestIdResolutionDto` (`id`, `status`, `currentGuestIds`, `retiredAt`, `hops` of `ResolutionHopDto` with `retiredGuestId`, `kind`, `eventId`, `at`, `successorGuestIds`) — the retired document carries no `profile` or `identifiers` key (research R4); empty resolution → `NotFoundException("No guest " + guestId + " in this tenant")` exactly as today. The method's return type becomes `Object` or a `ResponseEntity<?>`; keep the 200 status for both shapes
- [X] T014 [US1] Amend `specs/001-core-identity-resolution/contracts/openapi.yaml`: the `getGuest` 200 response schema becomes `oneOf: [$ref ActiveGuest, $ref GuestIdResolution]` with a `discriminator` on `status`, plus a one-line comment that both schemas are declared in `specs/004-retired-guest-ids/contracts/openapi.yaml` and resolve in the served union (research R5). Do not add or remove any path
- [X] T015 [US1] Add one assertion to `src/test/java/io/guestgraph/integration/GuestQueryApiTest.java` that the guest document now carries `status: ACTIVE`, and confirm no other existing assertion needed changing — that is the FR-012 compatibility check. Then run T009 and T010 and watch them pass

**Checkpoint**: The MVP. A merged id resolves, an active guest is unchanged but for `status`, unknown ids still 404.

---

## Phase 4: User Story 2 - Chains Are Followed to the End (Priority: P2)

**Goal**: A resolution follows retirements transitively to active guests and lists every hop in
order; a guest reached by two paths is named once.

**Independent Test**: Merge X into Y, then Y into Z. Read X and verify the current guest is Z with
two hops in order; read Y and verify one hop.

### Tests for User Story 2 ⚠

- [X] T016 [P] [US2] ⚠ Add to `GuestIdResolutionScenarioTest`: X→Y→Z resolves `MERGED` with `currentGuestIds: [Z]` and hops `[X→Y, Y→Z]` in event order; Y resolves with one hop; a chain of ten merges resolves to its end; a guest that appears as successor on two paths (build it in US3's fixtures if needed, or with two absorbed guests merged into one survivor) is present once in `currentGuestIds`. Run and watch it fail
- [X] T017 [P] [US2] ⚠ Add to `RetiredGuestIdApiTest` quickstart steps 7–9, plus the SC-003 timing: seed a ten-merge chain and assert `GET /guests/{first}` answers under 1 s. Run and watch it fail

### Implementation for User Story 2

- [X] T018 [US2] Make `GuestIdResolver` transitive per rule 5 of [data-model.md](data-model.md): breadth-first over successors with a visited set so each guest is expanded at most once; an existing successor joins `currentGuestIds` (deduplicated, first-reached order); a retired successor produces the next hop. Hops are appended in walk order, which is event order for a chain; sort by `at` before returning so branches interleave chronologically. Then run T016 and T017 and watch them pass

**Checkpoint**: Any merge chain resolves to its end with an explainable path.

---

## Phase 5: User Story 3 - A Split Guest Resolves to Every Guest It Became (Priority: P3)

**Goal**: A guest emptied by an unmerge resolves to every guest its records landed on, each
followed through later merges; a partial unmerge leaves the guest active; a walk that ends
nowhere reports `RETIRED` rather than inventing a guest.

**Independent Test**: Build a guest from three records, unmerge all three so they land on two
guests. Read the original id and verify both are named; merge one of them into a third guest and
verify the answer follows it.

### Tests for User Story 3 ⚠

- [X] T019 [P] [US3] ⚠ Add to `GuestIdResolutionScenarioTest` using `UnmergeOperation` on the fixture: a three-record guest S unmerged completely so the records land on W1 and W2 resolves `SPLIT` with `currentGuestIds: [W1, W2]` and one hop of kind SPLIT with `successorGuestIds: [W1, W2]`; after W1 merges into V, S resolves to `[V, W2]` with two hops; X absorbed into Y and Y later emptied resolves `SPLIT` through both; a guest with one record detached and two kept resolves `ACTIVE`; a guest whose split branches later re-merge into one guest resolves `MERGED`; a retirement whose successor has no candidate event and does not exist (construct by saving a MERGE event by hand on `InMemoryGraph` whose survivor was never created) resolves `RETIRED` with empty `currentGuestIds` and the hop still recorded. Run and watch it fail
- [X] T020 [P] [US3] ⚠ Add to `RetiredGuestIdApiTest` quickstart steps 10–14. Run and watch it fail

### Implementation for User Story 3

- [X] T021 [US3] Extend `GuestIdResolver` with the split branch of rules 2, 3 and 4 of [data-model.md](data-model.md): candidates also include `UNMERGE` events from `eventsForGuests(tenantId, List.of(g))`, and the latest candidate by `(createdAt, id)` across both kinds is the retirement; for an `UNMERGE`, read `eventsSince(tenantId, event.createdAt(), null, PAGE)` forward — inclusive on the first page, then strictly after the last event seen — collecting for each detached record the `guestId` of the first event whose `sourceRecordIds` contains it, and stop once every detached record is seen; successors distinct in record order; a successor with no candidate and no guest is kept on the hop, expanded no further, logged at WARN with the tenant and id, and contributes nothing to `currentGuestIds` (rule 4). Choose `PAGE` as a small constant (e.g. 200) and document why the scan is bounded (research R3). Then run T019 and T020 and watch them pass

**Checkpoint**: Merges and splits both resolve; status reflects the outcome.

---

## Phase 6: User Story 4 - Everything Under a Retired Id Points to the Current One (Priority: P4)

**Goal**: Records, explain, timeline and unmerge on a retired id answer 410 problem details of type
`guest-retired` naming the current guests; on an unknown id they answer the plain 404 of today.

**Independent Test**: Merge X into Y, then request X's records, timeline and explain and attempt
an unmerge on X; verify each is 410 naming Y and that nothing was recorded.

### Tests for User Story 4 ⚠

- [X] T022 [US4] ⚠ Add to `RetiredGuestIdApiTest` quickstart steps 15–16: for a merged X, `GET /guests/{X}/records`, `GET /guests/{X}/explain`, `GET /guests/{X}/timeline` and `POST /guests/{X}/unmerge` each answer 410 with `type` ending `/guest-retired`, `title: Guest id retired`, `guestId: X`, `resolutionStatus: MERGED`, `currentGuestIds: [survivor]`; the unmerge attempt recorded no event (explain on the survivor has the same count before and after); the same four on a random uuid answer 404 `not-found` with no `currentGuestIds` member. Run and watch it fail

### Implementation for User Story 4

- [X] T023 [P] [US4] Create `src/main/java/io/guestgraph/api/RetiredGuestException.java` carrying the `GuestIdResolution`, and add a handler to `src/main/java/io/guestgraph/api/ApiExceptionHandler.java` returning `HttpStatus.GONE` with type slug `guest-retired`, title `Guest id retired`, a detail naming the id and the current guest ids, and `setProperty` for `guestId`, `resolutionStatus` and `currentGuestIds` — named `resolutionStatus` because RFC 9457 owns the member `status` (research R4, data-model `RetiredGuestProblem`)
- [X] T024 [US4] Create `src/main/java/io/guestgraph/api/GuestGate.java` (a `@Component` taking `GuestQueryService` and `GraphMutationService`) with `void require(UUID tenantId, UUID guestId)`: returns when `guestExists`; otherwise resolves and throws `RetiredGuestException` when a resolution exists, else `NotFoundException("No guest " + guestId + " in this tenant")`. The existence check comes first so the timeline's hot path pays nothing new (research R6)
- [X] T025 [US4] Replace the four inline checks with the gate: `requireGuest` and the `findRecords` not-found branch in `src/main/java/io/guestgraph/api/GuestController.java` (records, explain, unmerge — the gate runs before `mutationService.unmerge`, so no event is recorded), and the `guestExists` check in `src/main/java/io/guestgraph/api/TimelineController.java`. Delete `requireGuest`. Then run T022 and watch it pass
- [X] T026 [P] [US4] Amend the owning contract files (research R5): in `specs/001-core-identity-resolution/contracts/openapi.yaml` add `"410": { $ref: "#/components/responses/Retired" }` to `getGuestRecords`, `explainGuest` and `unmergeGuest`; in `specs/003-timeline-journey/contracts/openapi.yaml` add the same to `getGuestTimeline`; each with a one-line comment that `Retired` is declared in the 004 contract and resolves in the served union. `OpenApiConformanceTest` must stay green — no path is added or removed

**Checkpoint**: No request under a retired id fails silently or returns another guest's data.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation ownership, the SC-004 suite, and the final verification.

- [X] T027 [P] Update `docs/roadmap-notes.md`: mark R-X5 ✅ consumed by `specs/004-retired-guest-ids` in its heading, keep its text as history, and replace the slice-4 "either build the resolution endpoint in this slice, or state the constraint in the connector contract" paragraph with the integrator rule the connector contract now states — on `MERGED`, replace the stored id with the current one; on `SPLIT`, escalate — and a link to the 004 spec. `docs/matching.md` is untouched: no matcher changed
- [X] T028 [P] Update `README.md`: the roadmap list already names this slice as current; flip it to ✅ and drop *(current)* in the same commit that completes the slice, and add one sentence to the API-surface paragraph saying a retired guest id resolves to the current one (concepts, never values — link to the 004 spec for the shape)
- [X] T029 [P] Check the org profile README at `guestgraph/.github` (`profile/README.md`): it says "Connectors … are next", which stays true once this slice ships; if the wording names slice numbers or a "current" phase, open a separate pull request there — it is a separate repository and has drifted before
- [X] T030 [P] Add the SC-004 suite to `GuestIdResolutionScenarioTest`: at least 20 table-driven sequences of merges and splits, each followed by the integrator rule — on `MERGED` replace the stored id with `currentGuestIds.get(0)` — and a final assertion that the held id resolves `ACTIVE` (SC-004), plus for every `currentGuestIds` entry in every scenario an assertion that `guestExists` (SC-002)
- [X] T031 Run `./mvnw spotless:apply`, then `./mvnw verify` and read the exit code on its own — Spotless, PMD, ArchUnit (`PersistenceRulesTest` must accept the new native query and the two new port implementations), the scenario suites and every integration suite — then `./scripts/regen-er.sh` with no diff, then `sh conventions/conventions-check`
- [X] T032 Walk [quickstart.md](quickstart.md) end to end against a local run and tick each step

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: T002 first; T003, T004 in parallel after it; T005 then T006; T007 and T008 last. Blocks every story
- **US1 (Phase 3)**: after Phase 2. The MVP
- **US2 (Phase 4)**: after US1 — it extends the same resolver and test classes
- **US3 (Phase 5)**: after US2 — the split branch builds on the transitive walk
- **US4 (Phase 6)**: after US1 only — the gate needs a resolver that answers single-hop; it can run alongside US2 and US3 if a second developer takes it
- **Polish (Phase 7)**: after every story

### Within Each User Story

- ⚠ tests are written and seen failing before the implementation task that follows
- Resolver before controller, controller before contract amendment
- Story complete before the next priority

### Parallel Opportunities

- T003 and T004 (in-memory adapter and migration) touch different files
- T009 and T010 (scenario and integration tests for US1) touch different files
- T023 and T026 (exception plus handler, and the contract amendments) touch different files
- T027 to T030 are four different files

---

## Parallel Example: User Story 1

```bash
# Both failing tests first, together:
Task: "⚠ GuestIdResolutionScenarioTest single-hop scenarios"
Task: "⚠ RetiredGuestIdApiTest quickstart steps 1–6"

# Then the resolver, then its wiring, then the controller, then the contract:
Task: "GuestIdResolver single-hop"
Task: "GraphMutationService.resolve"
Task: "GuestController GET /{guestId}"
Task: "getGuest 200 oneOf in the slice-1 contract"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 and Phase 2 — types, port, indexes, green baseline
2. Phase 3 — a merged id resolves, an active guest carries `status`
3. **STOP and VALIDATE**: quickstart steps 1–6

### Incremental Delivery

1. US2 makes the walk transitive — quickstart steps 7–9 and the timing
2. US3 adds the split branch — steps 10–14
3. US4 closes the sub-resources — steps 15–16
4. Polish — docs, the SC-004 suite, full verification

---

## Notes

- The resolver is the one place the rules live; the controller and the gate only present what it
  returns. If a rule needs changing, it changes in `GuestIdResolver` and its scenario test, never
  in a controller
- `specs/001-*` and `specs/003-*` contract files are edited on purpose (research R5); no other
  file under a merged spec is touched
- Commit after each phase or logical group; the branch is `004-retired-guest-ids`
