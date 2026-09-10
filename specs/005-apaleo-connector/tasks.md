---

description: "Task list for 005-apaleo-connector"
---

# Tasks: Apaleo Connector

**Input**: Design documents from `/specs/005-apaleo-connector/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/mapping.md](contracts/mapping.md),
[contracts/connector-api.yaml](contracts/connector-api.yaml)

**Tests**: Test tasks are included and are not optional here. No engine code changes, so
Constitution Principle VI does not compel them; the plan schedules them anyway because the
connector's correctness lives in two pure functions, the mapper and the roster hash, and in
protocol handling that only recorded interactions can pin. Tasks marked ⚠ MUST be written and seen
failing before the implementation task that follows them.

**Organization**: Grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US4, mapping to the spec's prioritized stories
- ⚠: failing-test task — run it, watch it fail, then implement
- 👤: the owner's step, not an agent's — a family change or a sandbox session

## Path Conventions

Every path below is relative to the root of `guestgraph/connector-apaleo`, a new repository,
unless it starts with `engine/`, which means this repository. Main code under
`src/main/java/io/guestgraph/connector/apaleo/`, tests under
`src/test/java/io/guestgraph/connector/apaleo/`, migrations under
`src/main/resources/db/migration/`, recorded Apaleo documents under `src/test/resources/apaleo/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: The repository exists, carries the family's conventions and guardrails, and has a
test harness with recorded upstream documents.

- [X] T001 👤 Release robertblust/conventions with `REPOSITORIES.md` gaining the row `guestgraph/connector-apaleo — the Apaleo connector: reservations and bookings into the guest graph — main — ~/git/guestgraph/connector-apaleo` and the re-sync order gaining it after the engine; then create the repository in the guestgraph organization, Apache-2.0, default branch `main`, protected by a ruleset requiring the job ids `verify` and `conventions / conventions`
- [X] T002 Scaffold the repository: `pom.xml` on Java 25 and Spring Boot 4 with web, JPA, Flyway, Actuator, the PostgreSQL driver, and test dependencies JUnit 5, AssertJ, Testcontainers and WireMock; `AGENTS.md` opening with the family's block and `CLAUDE.md` as the four-line vendor adapter, `conventions/` vendored at the release T001 made and `conventions.json` pinning it; `compose.yaml` with `postgres:18`; `.github/workflows/verify.yml` with a job id `verify` running `./mvnw verify`, and the conventions job called from robertblust/conventions at the pinned tag (research R1)
- [X] T003 [P] Copy the engine's guardrails: `config/pmd-ruleset.xml`, the Spotless configuration with google-java-format, and `src/test/java/io/guestgraph/connector/apaleo/architecture/PersistenceRulesTest.java` holding repositories to `@Query`-only methods with a `connectionId` parameter or a justified `@ConnectionAgnostic`, and JPA to the `state` package
- [X] T004 [P] Record the Apaleo documents the tests run on, under `src/test/resources/apaleo/`, shaped exactly as the Booking API OpenAPI document defines them: `reservation-three-persons.json` (a primary guest with email and passport, two additional guests), `reservation-single.json`, `booking-distinct-booker.json` and `booking-same-as-primary.json` with `expand=reservations`, `reservations-page-1.json`, `reservations-page-2.json` and the 204 end, `bookings-page-1.json`, `event-reservation-changed.json`, `event-booking-changed.json`, `event-reachability.json` (empty body), `token.json`. Each document carries only fields the mapping reads or nests, plus the payment fields that must never travel, so the tests can prove their absence
- [X] T005 Create the harness `src/test/java/io/guestgraph/connector/apaleo/integration/ConnectorIntegrationTest.java`: one Testcontainers PostgreSQL for the run, one WireMock for Apaleo and one for the engine, the connector's properties pointed at them with two connections configured, `alpha` and `beta`, each with its own webhook secret and engine key, and helpers to stub a reservation, a booking, a list page, a token, an engine ingest answer per record, and an engine guest answer, per connection

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: State, configuration and the two upstream clients every story uses.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 Write `src/main/resources/db/migration/V1__connector_state.sql` per [data-model.md](data-model.md), naming no schema — Flyway's `default-schema` and the connection's search path place it in `DATABASE_SCHEMA` — with: `connection` (PK `id text`; `tenant_label`, `apaleo_account` NOT NULL; `property_ids jsonb NOT NULL DEFAULT '[]'`; `webhook_secret_hash text NOT NULL`; `created_at`, `last_activity_at timestamptz NOT NULL`), `object_state` (PK `connection_id, object_type, object_id`, FK to `connection`; `property_id` NULL; `booking_id` NULL; `last_modified timestamptz NOT NULL`; `roster_hash text NOT NULL`; `last_submitted_at timestamptz NOT NULL`; `last_status text NULL`), `processed_event` (PK `connection_id, event_id`; `event_type`, `object_type`, `object_id`, `property_id` NOT NULL; `received_at NOT NULL`; `state text NOT NULL CHECK IN ('PENDING','DONE','IGNORED','FAILED')`; `attempts int NOT NULL DEFAULT 0`; `next_attempt_at NULL`; `last_error NULL`), `sync_point` (PK `connection_id, property_id`; `modified_through timestamptz NOT NULL`; `last_full_sync_at NULL`; `last_reconcile_at NULL`), `sync_run` (PK `id uuid`; `connection_id text NOT NULL` indexed; `kind text NOT NULL CHECK IN ('FULL','RECONCILE','REFRESH')`; `started_at NOT NULL`; `finished_at NULL`; `outcome NULL CHECK IN ('SUCCEEDED','FAILED')`; six `int NOT NULL DEFAULT 0` counters; `last_error NULL`), `held_guest_id` (PK `connection_id, object_type, object_id, role, position`; `guest_id uuid NOT NULL`; `source_record_id uuid NOT NULL`; `resolution_status text NOT NULL CHECK IN ('ACTIVE','MERGED','SPLIT','RETIRED')`; `current_guest_ids jsonb NOT NULL DEFAULT '[]'`; `refreshed_at NULL`)
- [X] T007 Create the entities and `@Query`-only repositories under `src/main/java/io/guestgraph/connector/apaleo/state/` for the six tables, every method taking `connectionId` (data-model rule 7), with the reads the runs need: the connection by secret hash, state by object, pending events by `next_attempt_at`, the sync point per property, distinct held guest ids, held rows by guest id; `PersistenceRulesTest` must pass against them
- [X] T008 [P] Create `src/main/java/io/guestgraph/connector/apaleo/config/ConnectorProperties.java` bound to the instance variables of the [data-model.md](data-model.md) configuration tables and to the connections file `CONNECTOR_CONNECTIONS_FILE` (one entry per connection with tenant label, engine base URL and key, Apaleo account, client id and secret, property ids and webhook secret; the file is refused at start when two connections share a secret or a name), writing the `connection` rows at start with the secret hashed and never the secrets — `DATABASE_SCHEMA` feeding both `spring.flyway.default-schema` and the JDBC `currentSchema` — with defaults for `DATABASE_SCHEMA` `apaleo_connector`, `APALEO_EVENT_TYPES` (the seven of FR-007), `RECONCILE_INTERVAL` 15 minutes, `RECONCILE_OVERLAP` 1 hour, `RESYNC_AFTER_GAP` 24 hours, `REFRESH_CRON` nightly, `ENGINE_SOURCE_SYSTEM` `apaleo`; a `toString` that masks every secret; and a logging configuration that emits structured JSON with a fixed field set and never a payload (FR-016)
- [X] T009 [P] Create `src/main/java/io/guestgraph/connector/apaleo/apaleo/ApaleoAuth.java` — one per connection: client credentials against `https://identity.apaleo.com/connect/token` with Basic auth of `clientId:clientSecret`, `grant_type=client_credentials`, scope `reservations.read`, the token cached per connection and refreshed one minute before its 3,600-second expiry — and `ApaleoClient.java`, built per connection, with `listReservations(propertyIds, modifiedFrom, page)` (`dateFilter=Modification`, `sort=updated:asc`, `pageSize=500`, all five statuses, 204 as the end), `getReservation(id)`, `getBooking(id)` and `listBookings(page)` both with `expand=reservations`, and a 429 handler honoring `Retry-After` else backing off exponentially from one second capped at a minute, never giving up (research R2); models under `apaleo/model/` carrying the fields [contracts/mapping.md](contracts/mapping.md) reads or nests, and the payment fields deliberately absent from the models
- [X] T010 [P] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/apaleo/ApaleoClientTest.java` on WireMock: token fetched once and reused, refreshed before expiry; a three-page list ending on 204; `expand=reservations` on both booking reads; a 429 with `Retry-After: 2` waited out and retried; a 429 without the header backed off and retried. Run and watch it fail, then make it pass with T009
- [X] T011 [P] Create `src/main/java/io/guestgraph/connector/apaleo/engine/EngineClient.java`, one per connection with its base URL and key — `registerSourceSystem()` treating 409 as registered, `submit(List<IngestRecord>)` posting to `/api/v1/records` with `X-API-Key` and returning every result, `getGuest(UUID)` returning the guest's `status` and `currentGuestIds` for any 200 and distinguishing 404 — with models under `engine/model/` copied from the engine's contracts, and `src/test/java/io/guestgraph/connector/apaleo/engine/EngineClientTest.java` on WireMock for each; batch size 100 (research R7)
- [X] T012 Establish the green baseline: `./mvnw verify` and `sh conventions/conventions-check` pass in the new repository, and the `verify` and `conventions` checks are green on a first pull request there

**Checkpoint**: The repository builds, both upstreams have a tested client, the state exists.

---

## Phase 3: User Story 1 - A Property's Reservations Become Observations (Priority: P1) 🎯 MVP

**Goal**: A full sync turns every reservation and booking of the account into observations under
the mapping contract, submits a version only when its persons changed, and a second run submits
nothing.

**Independent Test**: With WireMock serving two list pages, three reservations and their two
bookings, run a full sync; verify every person was submitted once with the right key, role,
version and payload shape; run it again and verify nothing was submitted.

### Tests for User Story 1 ⚠

- [ ] T013 [P] [US1] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/mapping/ApaleoMapperTest.java` on the recorded documents: the three-person reservation yields three records whose keys are `{id}:primaryGuest:{modified}` and `{id}:additionalGuests[0|1]:{modified}`, roles and positions as in [contracts/mapping.md](contracts/mapping.md), `recordTimestamp` and `sourceObject.version` equal to `modified`, business dates equal to `arrival` and `departure`, the six extracted fields at the top and every other person field under `person`, the reservation's booking-level fields under `reservation`; the booking yields one record keyed `{id}:booker:{modified}` with type `booking`, role `BOOKER`, dates the earliest arrival and latest departure over its reservations, and its `booking` block reduced to id, status, property, dates and channel per reservation; `idDocument` present only when both type and number are; no `loyaltyId`, no `externalGuestId`; `paymentAccount`, `registeredCard` and `hasActivePaymentAccount` absent from every record; an empty `additionalGuests` yields only the primary guest; a booking with no reservations carries no business dates. Run and watch it fail
- [ ] T014 [P] [US1] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/mapping/RosterHashTest.java`: a room, rate or date change on a reservation hashes equal; a corrected email hashes different; an added or removed additional guest hashes different; an address-only change hashes equal; trimming whitespace hashes equal; on a booking a booker correction hashes different and a new reservation joining it hashes different through the derived dates. Run and watch it fail
- [ ] T015 [P] [US1] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/integration/FullSyncTest.java` on the harness: two list pages then 204, three reservations, two bookings fetched by their ids; every person submitted exactly once, one reservation's persons never split across engine batches, `object_state` rows written with hash and `last_modified`, `held_guest_id` rows written from the results, the sync point advanced per property, a `sync_run` of kind `FULL` with the counters; a second run submits nothing and moves no state; an engine `ERROR` result on one record keeps that object's state unwritten and counts an error. Run and watch it fail

### Implementation for User Story 1

- [ ] T016 [P] [US1] Create `src/main/java/io/guestgraph/connector/apaleo/mapping/ApaleoMapper.java` — pure, no Spring — turning one reservation into its person records and one booking into its booker record per [contracts/mapping.md](contracts/mapping.md); then run T013 and watch it pass
- [ ] T017 [P] [US1] Create `src/main/java/io/guestgraph/connector/apaleo/mapping/RosterHash.java` — pure — implementing data-model rules 3 and 3a with SHA-256 over a canonical string of role, position and the trimmed field values in order, plus the derived dates on a booking; then run T014 and watch it pass
- [ ] T018 [US1] Create `src/main/java/io/guestgraph/connector/apaleo/sync/ObjectSubmitter.java`: given a fetched reservation or booking, compute the hash, compare with `object_state`, and when different map, submit in batches of at most 100 without splitting one object, read every result, rewrite `held_guest_id` rows (data-model rule 5), count outcomes on the current `sync_run`, and replace the `object_state` row; on an `ERROR` result leave the state untouched and count the error (research R7)
- [ ] T019 [US1] Create `src/main/java/io/guestgraph/connector/apaleo/sync/FullSync.java`: for each configured property, page `listReservations` with no date filter and all statuses, submit each reservation through T018 and fetch and submit its booking when `object_state` has no row for it, advancing the sync point per data-model rule 4; then page `listBookings` and submit each; record the run; refuse to start while another full sync runs (409 on the endpoint later). Start one on first boot when no sync point exists. Then run T015 and watch it pass

**Checkpoint**: The MVP. An account's reservations and bookings are in the graph, and re-running changes nothing.

---

## Phase 4: User Story 2 - Changes Arrive Within a Minute (Priority: P2)

**Goal**: Webhooks are received safely and processed once, reconciliation covers what they miss,
and a long gap triggers a full sync.

**Independent Test**: Deliver a reservation event and a booking event to the endpoint; verify
each is answered 202 before any fetch, processed once, and that only changed persons are
submitted; deliver the same event twice and verify one processing; simulate a gap and verify the
reconciliation and the gap rule.

### Tests for User Story 2 ⚠

- [ ] T020 [P] [US2] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/integration/EventTest.java`: a delivery to `/apaleo/events/{secret}` answers 202 with no Apaleo call yet; the worker then fetches the reservation and submits its persons; the same event id delivered again answers 202 and causes no fetch; a `booking/changed` event fetches the booking only and submits the booker only; two events for one reservation processed in reverse order both submit their versions and `object_state` keeps the later `modified`; an edit that changed no person submits nothing; an event whose fetch answers 500 stays `PENDING` with `attempts` 1, `next_attempt_at` in the future and a reason, and succeeds on the next attempt; an event for an unconfigured property is `IGNORED` and counted; an empty body answers 200; a wrong secret answers 404. Run and watch it fail
- [ ] T021 [P] [US2] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/integration/ReconcileTest.java`: with a sync point set, reconciliation lists from the point minus the overlap sorted by update, submits only the reservations whose hash changed, fetches their bookings, advances the point, reads the subscription and marks the status inactive when Apaleo answers 404 for it; with `last_activity_at` older than `RESYNC_AFTER_GAP`, the next reconciliation starts a full sync instead, which catches a booker-only edit that no event delivered. Run and watch it fail
- [ ] T022 [P] [US2] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/apaleo/ApaleoWebhooksTest.java` on WireMock: on startup with no subscription, one is created with `endpointUrl` `{CONNECTOR_PUBLIC_URL}/apaleo/events/{secret}`, the configured `events` and `propertyIds`; with one whose endpoint or events differ, it is replaced; with a matching one, nothing is written. Run and watch it fail

### Implementation for User Story 2

- [ ] T023 [US2] Create `src/main/java/io/guestgraph/connector/apaleo/events/EventEndpoint.java`: `POST /apaleo/events/{secret}`, no ops token; the secret's hash selects the connection, and no match is 404; an empty body is 200; otherwise insert a `processed_event` row `PENDING` under that connection from `id`, `topic`, `type`, `propertyId` and `data.entityId`, ignoring a duplicate key, and answer 202; a property outside the connection's set is inserted `IGNORED` (research R5, R12)
- [ ] T024 [US2] Create `src/main/java/io/guestgraph/connector/apaleo/events/EventWorker.java`: a scheduled drain of `PENDING` rows whose `next_attempt_at` is null or past, connection by connection in `received_at` order so one connection's failures never block another's, using that connection's clients; fetch the object by type, submit through T018, mark `DONE`; on failure increment `attempts`, set `next_attempt_at` by exponential backoff from ten seconds capped at an hour, store the reason, leave `PENDING`; update `last_activity_at` on success. Then run T020 and watch it pass
- [ ] T025 [US2] Create `src/main/java/io/guestgraph/connector/apaleo/apaleo/ApaleoWebhooks.java`: list, create and replace the subscription against `https://webhook.apaleo.com/v1/subscriptions` per T022, run on startup, and expose `exists()` for the reconciliation's check. Then run T022 and watch it pass
- [ ] T026 [US2] Create `src/main/java/io/guestgraph/connector/apaleo/sync/Reconciliation.java` (scheduled every `RECONCILE_INTERVAL` and on request) and `sync/GapGuard.java`: reconciliation per connection and property from `modified_through` minus `RECONCILE_OVERLAP`, with that connection's clients, each reservation through T018 and its booking fetched and submitted when the reservation was; then `ApaleoWebhooks.exists()` recorded for the status; the gap guard runs before it and starts a full sync when `now − last_activity_at > RESYNC_AFTER_GAP` (FR-012a). Then run T021 and watch it pass

**Checkpoint**: The connector is live: events within a minute, reconciliation behind them, recovery after a long gap.

---

## Phase 5: User Story 3 - The Connector Can Be Operated (Priority: P3)

**Goal**: A status document, run endpoints, health, and logs that carry no secret and no person.

**Independent Test**: Read the status after a full sync and after a failed Apaleo call; start
runs through the endpoints; search a captured log for every credential and person value used.

### Tests for User Story 3 ⚠

- [ ] T027 [US3] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/integration/StatusTest.java`: `GET /status` without the token is 401; with it, after a full sync on `alpha`, the document matches [contracts/connector-api.yaml](contracts/connector-api.yaml) with one entry per configured connection, `alpha` showing the run and `beta` untouched — tenant label, account, properties, `subscription.active` and `eventTypes`, a sync point per property, counters equal to the run's, `pendingEvents`, `splitsAwaitingPerson`, `lastActivityAt`, `lastError` null; after Apaleo answers 401 to a fetch, `lastError.where` is `APALEO` with a time and a reason that contains neither the secret nor a person field; `POST /connections/alpha/sync/full` answers 202 with a run id and 409 while one runs, `POST /connections/nope/sync/full` is 404; `GET /connections/alpha/runs/{id}` shows progress then the outcome; `/actuator/health` is 200 without a token; a log capture of the whole test contains none of the client secret, the engine key, the ops token, the webhook secret, or any email, phone or name from the recorded documents (SC-007). Run and watch it fail

### Implementation for User Story 3

- [ ] T028 [P] [US3] Create `src/main/java/io/guestgraph/connector/apaleo/ops/OpsTokenFilter.java` requiring `Authorization: Bearer {CONNECTOR_OPS_TOKEN}` on `/status` and `/connections/*`, and nothing else
- [ ] T029 [US3] Create `src/main/java/io/guestgraph/connector/apaleo/ops/StatusController.java` serving `GET /status` and, under `/connections/{connectionId}`, `POST .../sync/full`, `POST .../sync/reconcile`, `POST .../refresh` and `GET .../runs/{runId}` per [contracts/connector-api.yaml](contracts/connector-api.yaml), the status assembled per connection from `connection`, `sync_point`, `sync_run`, `processed_event`, `held_guest_id` and the last recorded error, an unknown connection answering 404; enable Actuator health only. Then run T027 and watch it pass
- [ ] T030 [P] [US3] Write the connector repository's `README.md` in the prose register: what the connector does in one paragraph, the two objects and why, how to run it with the configuration table linked to the spec, the one-schema-one-role rule with the two SQL lines a deployment runs and the note that one database or two is its choice, the connections file and the rule that one instance serves many connections, what the status means, and the integrator rule; link to `specs/005-apaleo-connector/` in the engine repository rather than restating any decision

**Checkpoint**: An operator can see, start and trust the connector.

---

## Phase 6: User Story 4 - The Guest Ids the Connector Holds Stay Valid (Priority: P4)

**Goal**: Held guest ids follow the integrator rule on a nightly refresh and on request.

**Independent Test**: With held ids in state, stub the engine to answer ACTIVE, MERGED, SPLIT and
RETIRED for four of them; run a refresh; verify each row's outcome and that the split is surfaced
in the status with nothing chosen.

### Tests for User Story 4 ⚠

- [ ] T031 [US4] ⚠ Create `src/test/java/io/guestgraph/connector/apaleo/integration/RefreshTest.java`: four held ids on `alpha` answered `ACTIVE`, `MERGED` to one current id, `SPLIT` to two, `RETIRED` to none, and one held id on `beta` that the engine stub for `beta` answers `ACTIVE`; after `POST /connections/alpha/refresh` the active row is untouched, the merged row carries the new id with status `ACTIVE` and a log line naming both ids, the split row keeps its id with status `SPLIT` and both current ids, the retired row likewise with none; `GET /status` reports `splitsAwaitingPerson: 2`; a resubmitted reservation version rewrites the slot's held id from the new result. Run and watch it fail

### Implementation for User Story 4

- [ ] T032 [US4] Create `src/main/java/io/guestgraph/connector/apaleo/sync/Refresh.java` (scheduled by `REFRESH_CRON` and on request, per connection): for each distinct held `guest_id` of the connection one `EngineClient.getGuest` with its key, applying data-model rule 6 — replace on `MERGED`, mark and keep on `SPLIT` and `RETIRED`, leave on `ACTIVE` — recorded as a `sync_run` of kind `REFRESH`. Then run T031 and watch it pass

**Checkpoint**: R-X5 is consumed: the connector never holds a dangling id and never guesses.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: The sandbox facts, the release, and the documentation this repository owns.

- [ ] T033 👤 The sandbox session of research R11: record which event fires for a guest edit, an added guest, a removed guest and a check-in with registration data; whether the client-credentials client may create subscriptions; the rate-limit status and headers; the bodies of the reachability check and a delivery; whether a person-only edit moves the reservation's `modified`; whether a booker edit moves only the booking's. Amend research R11 with the findings, dated, and set the `APALEO_EVENT_TYPES` default and the 429 handling from them
- [ ] T034 Walk [quickstart.md](quickstart.md) end to end against a local engine and the sandbox and tick each step; fix what it finds
- [ ] T035 [P] `engine/docs/roadmap-notes.md`: mark the Slice 4 — Connectors section consumed by `specs/005-apaleo-connector`, keeping its text; `docs/matching.md` is untouched, no matcher changed
- [ ] T036 [P] `engine/README.md`: flip roadmap line 5 to ✅ and add one sentence naming the Apaleo connector with a link to its repository; concepts, never values
- [ ] T037 [P] Update the org profile README at `guestgraph/.github` (`profile/README.md`): the "Where to start" table gains the connector repository and "Where we are" says the first connector is built — a separate repository and a separate pull request, opened only after the connector's first release
- [ ] T038 Release the connector: tag `v0.1.0` and a GitHub Release with notes in the prose register — what it does for a consumer, how to run it, what the sandbox session found and what remains unverified; no publish step, the tag is the release
- [ ] T039 Run `./mvnw spotless:apply`, then `./mvnw verify` in the connector repository and read the exit code on its own, then `sh conventions/conventions-check` there and in this repository

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 is the owner's and precedes everything in the new repository; T002 next; T003, T004 in parallel; T005 after T004
- **Foundational (Phase 2)**: T006 then T007; T008, T009, T011 in parallel after T007; T010 with T009; T012 last. Blocks every story
- **US1 (Phase 3)**: after Phase 2. The MVP
- **US2 (Phase 4)**: after US1 — events and reconciliation reuse the submitter
- **US3 (Phase 5)**: after US2 — the status reports runs and events; T030 can start any time
- **US4 (Phase 6)**: after US1 only — it needs held ids and the engine client; it can run alongside US2 and US3
- **Polish (Phase 7)**: T033 before T038; T035 to T037 after T038; T039 throughout and last

### Within Each User Story

- ⚠ tests are written and seen failing before the implementation task that follows
- Pure functions before the submitter, the submitter before the runs, the runs before the endpoints
- Story complete before the next priority

### Parallel Opportunities

- T003 and T004; T008, T009 and T011; T013, T014 and T015; T016 and T017; T020, T021 and T022; T028 and T030; T035, T036 and T037

---

## Parallel Example: User Story 1

```bash
# Three failing tests first, together:
Task: "⚠ ApaleoMapperTest on the recorded documents"
Task: "⚠ RosterHashTest"
Task: "⚠ FullSyncTest on the harness"

# Then the two pure functions together, then the submitter, then the full sync:
Task: "ApaleoMapper"
Task: "RosterHash"
Task: "ObjectSubmitter"
Task: "FullSync"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 — the owner's family change, then the repository and the harness
2. Phase 2 — state, configuration, both clients, green baseline
3. Phase 3 — a full sync that maps both objects and submits on change only
4. **STOP and VALIDATE**: quickstart steps 3–7 against the sandbox

### Incremental Delivery

1. US2 makes it live — steps 8–10
2. US3 makes it operable — steps 11–12
3. US4 consumes R-X5 — steps 13–14
4. Polish — the sandbox facts, the release, the documentation

---

## Notes

- The two pure functions, the mapper and the hash, are the only places the mapping and the
  change rule live; a change to either goes to [contracts/mapping.md](contracts/mapping.md)
  first, then to the function, then to its test
- Payment fields are absent from the Apaleo models on purpose (T009), so they cannot travel by
  accident; T013 proves it on documents that carry them
- Nothing in the engine repository changes but its documentation (T035, T036); the connector
  needs no new engine endpoint
- Commit after each phase or logical group; the branch here is `005-apaleo-connector`, and in the
  connector repository the tasks land on branches named for what they do
