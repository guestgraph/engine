---

description: "Task list for 008-shared-runtime"
---

# Tasks: Shared Runtime Code

**Input**: Design documents from `/specs/008-shared-runtime/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/shared-runtime.md](contracts/shared-runtime.md),
[quickstart.md](quickstart.md)

**Tests**: Test tasks are included and are not optional here. No engine logic changes, so
Constitution Principle VI does not compel them; the plan schedules them because the shape a
caller reads is what the slice is for, and only a test that provokes a refusal from each origin
proves it. Tasks marked ⚠ MUST be written and seen failing before the implementation task that
follows them.

**Organization**: Grouped by user story. The shared package is written and released first, the
engine adopts before the connector (as in slice 7), and the check comes last because it reads
what the adoptions leave.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US3, mapping to the spec's prioritized stories
- ⚠: failing-test task — run it, watch it fail, then implement
- 👤: the owner's step, not an agent's

## Path Conventions

Paths starting with `shared/` are in `guestgraph/service-conventions`; `engine/` is this
repository; `connector/` is `guestgraph/connector-apaleo`; `site/` is
`guestgraph/guestgraph.github.io`. Every other path is relative to the repository the task
names. The shared package's path in a service is `src/main/java/io/guestgraph/service/`.

---

## Phase 1: Setup (the runtime module)

**Purpose**: A place in the shared repository where the shared sources compile, format and test
before any service takes them.

- [x] T001 Create `shared/spring/runtime/pom.xml`: a Maven module `io.guestgraph:service-runtime` naming the parent `service-parent` by `<relativePath>../pom.xml</relativePath>`, with `spring-boot-starter-webmvc` and `spring-boot-starter-test`, no application class; `./mvnw -q verify` in it passes empty; add it to `shared/tests/run` as a step and to `shared/.github/workflows/tests.yml` with `actions/setup-java@v6`; the module is never vendored
- [x] T002 [P] Extend `shared/spring/service-conventions-sync`: a second list `RUNTIME="Problems.java ServiceException.java ServiceExceptionHandler.java RequestSizeLimitFilter.java ApiDocsController.java BearerTokenFilter.java"` fetched from `<stack>/runtime/src/main/java/io/guestgraph/service/` and written to `src/main/java/io/guestgraph/service/`, compared by `check` like every other file; extend `shared/tests/run`'s sync cases with a runtime file drifted and one missing

---

## Phase 2: Foundational (the shared classes)

**Purpose**: The six classes, tested where they are written, before a service depends on them.

**⚠️ CRITICAL**: No adoption can begin until the shared repository is released with them.

- [x] T003 [P] ⚠ Create `shared/spring/runtime/src/test/java/io/guestgraph/service/ProblemsTest.java`: `Problems.of(NOT_FOUND, "not-found", "Resource not found", "x")` has type `https://guestgraph.io/problems/#not-found`, the title, status 404 and the detail; `Problems.write` on a mock response sets 404, `application/problem+json` and a body with the four members; run and watch it fail
- [x] T004 [P] ⚠ Create `shared/spring/runtime/src/test/java/io/guestgraph/service/ServiceExceptionTest.java`: a subclass built with `(GONE, "guest-retired", "Guest id retired", detail).withProperty("guestId", id)` exposes a `ProblemDetail` with that type, status, title, detail and the property; the exception's status code is 410; run and watch it fail
- [x] T005 Create `shared/spring/runtime/src/main/java/io/guestgraph/service/Problems.java` and `ServiceException.java` per the contract's surface: the base `https://guestgraph.io/problems/#`, `of`, `write` (status, `application/problem+json`, the members as JSON through Jackson, named members last), `ServiceException extends ErrorResponseException` with `withProperty`. Then run T003 and T004 and watch them pass
- [x] T006 [P] Create `shared/spring/runtime/src/main/java/io/guestgraph/service/ServiceExceptionHandler.java`: `@RestControllerAdvice @Order(Ordered.LOWEST_PRECEDENCE)`, one handler for `Exception` that logs at error with the throwable and answers `Problems.of(INTERNAL_SERVER_ERROR, "internal-error", "Internal server error", "An unexpected error occurred")`; nothing else
- [x] T007 [P] Create `shared/spring/runtime/src/main/java/io/guestgraph/service/RequestSizeLimitFilter.java` from `engine/src/main/java/io/guestgraph/engine/api/RequestSizeLimitFilter.java` as it stands, chunked bodies included, on every path, reading `service.max-request-bytes` with the default `1048576`, refusing through `Problems.write` with `payload-too-large`, "Payload too large", and a detail naming the cap
- [x] T008 [P] Create `shared/spring/runtime/src/main/java/io/guestgraph/service/ApiDocsController.java` from the connector's, serving `classpath:api/openapi.yaml` at `/api-docs`
- [x] T009 [P] Create `shared/spring/runtime/src/main/java/io/guestgraph/service/BearerTokenFilter.java` from the connector's `OpsTokenFilter`: `@Component @ConditionalOnProperty("service.bearer.token")`, `service.bearer.open-paths` as exact paths or prefixes ending in `/`, matched on the raw request URI, closed by default, the scheme case-insensitive, the token compared with `MessageDigest.isEqual`, refusing through `Problems.write` with `unauthorized`
- [x] T009a [P] ⚠ Create `shared/spring/runtime/src/test/java/io/guestgraph/service/ServiceDefaultsTest.java`: an environment built by hand with `service.schema=probe` set above; after `ServiceDefaults` runs, `management.endpoints.web.exposure.include` is `health`, `spring.datasource.hikari.schema` resolves to `probe`, and a value set above the defaults wins over them; run and watch it fail
- [x] T009b Create `shared/spring/runtime/src/main/resources/service-defaults.yaml` per the contract, `META-INF/spring.factories` naming `io.guestgraph.service.ServiceDefaults`, and `ServiceDefaults implements EnvironmentPostProcessor` adding the file's property source last; extend the sync with the two resources; the check gains `defaults` and reads `health`, `problem-details`, `schema` and `request-cap` from the defaults when vendored; the scaffold and the passing fixture keep only their own settings and `service.schema`. Then run T009a and watch it pass
- [x] T010 In `shared/spring/runtime`: `./mvnw -q spotless:apply`, then `./mvnw -q verify` and read the exit code; `sh tests/run`; open the pull request "The shared package, tested where it is written"

**Checkpoint**: Six classes compile, format and test in the shared repository; the sync vendors them.

---

## Phase 3: User Story 1 - One Error Shape in Every Service (Priority: P1) 🎯 MVP

**Goal**: Both services answer every refusal through the shared shape, and every type resolves.

**Independent Test**: A refusal from each origin in each service is a problem detail with a
type under the family's base; the 500 is logged and says nothing.

### Tests for User Story 1 ⚠

- [x] T011 [P] [US1] ⚠ Create `engine/src/test/java/io/guestgraph/engine/integration/ErrorShapeTest.java`: a request without an API key (filter), `GET /api/v1/guests/{random}` with a key (controller, `not-found`), a second decision on a decided review (`review-already-decided`), a body that is not JSON (framework, no family type), and `GET /api/v1/guests/{id}` under a test-only profile bean that throws (advice, `internal-error`, the detail equal to the fixed sentence, the log carrying the class name); every body `application/problem+json` with `type` under `https://guestgraph.io/problems/#` except the framework's. Run and watch it fail
- [x] T012 [P] [US1] ⚠ Create `connector/src/test/java/io/guestgraph/connector/apaleo/integration/ErrorShapeTest.java`: `GET /status` without the token (`unauthorized`), `POST /connections/nope/sync/full` (`not-found`), a second full sync while one runs (`run-in-progress`), a body that is no event on the webhook path (`invalid-request`), and a planted failure (`internal-error`), the same assertions. Run and watch it fail

### Implementation for User Story 1

- [x] T013 [US1] Release the shared repository as `v0.6.0` after T010 merges, the notes saying it asks more than a re-sync, the step the family calls a major, taken as the minor one below 1.0: a service replaces its own classes with the vendored package and reroutes its refusals through it
- [ ] T014 [US1] In the engine: move the pin and the parent to `v0.6.0`, sync twice; make `NotFoundException`, `ConflictException`, `BadRequestException`, `InvalidUnmergeException`, `ReviewNotFoundException`, `ReviewAlreadyDecidedException`, `InvalidActorClaimException` and `RetiredGuestException` extend `ServiceException` with the slug, status and title the data model gives each, `RetiredGuestException` adding `guestId`, `resolutionStatus` and `currentGuestIds` through `withProperty`; delete `ApiExceptionHandler`, the engine's `RequestSizeLimitFilter` and `ApiDocsController`; `ApiKeyFilter` refuses through `Problems.write` with `unauthorized` and, for a bad actor claim, `invalid-request`; rename `guestgraph.max-request-bytes` to `service.max-request-bytes`, then strip every shared setting from `application.yaml` and set `service.schema: engine`; `./mvnw verify`, both checks. Then run T011 and watch it pass; open the pull request
- [ ] T015 [US1] In the connector: move the pin and the parent to `v0.6.0`, sync twice; create `NoSuchConnectionException` and `NoSuchRunException` (`not-found`), `NotAnEventException` (`invalid-request`) and make `RunInProgressException` extend `ServiceException` (`run-in-progress`, 409, "A run is in progress"); `StatusController` throws them and loses its handler, `EventEndpoint` throws `NotAnEventException`; delete `OpsTokenFilter`, the connector's `RequestSizeLimitFilter` and `ApiDocsController`; `application.yaml` loses every shared setting, sets `service.schema: apaleo_connector` and gains `service.bearer.token: ${CONNECTOR_OPS_TOKEN:}` with `open-paths [/actuator/health, /api-docs, /apaleo/events/]`, and the local profile's token moves under it; `ConnectorProperties` drops `opsToken`; the status suite's 401 assertions read the shared shape; `./mvnw verify`, both checks. Then run T012 and watch it pass; open the pull request
- [x] T016 [P] [US1] Create `site/problems/index.html` through the writer role from a brief: one section per slug of the data model with the slug as its anchor, what the problem means and what a caller does, in the site's shell; add `/problems/` to the site's README table and sitemap; `npm run verify` and the design check as the site's agent file says; open the pull request

**Checkpoint**: Every refusal in both services has one shape; every type resolves.

---

## Phase 4: User Story 2 - The Few Classes Every Service Carries (Priority: P2)

**Goal**: A change to a shared class reaches both services by one pin move, and a vendored
copy edited in a service fails the sync check.

**Independent Test**: Change one line in the shared repository, release, move a pin, re-sync:
the diff is the pin and the class; edit a vendored class in a service: the sync check fails.

- [ ] T017 [US2] Walk quickstart step 2 with a real change: the `payload-too-large` detail sentence in `Problems`, released as `v0.6.1`; move each service's pin in its own pull request and read the diff. Record the walk in the shared repository's README under "How a rule changes" as the runtime's example
- [ ] T018 [P] [US2] Update `shared/new-service`: no `ApiDocsController` and no `RequestSizeLimitFilter` of its own, `service.max-request-bytes` in its `application.yaml`, the shared package vendored by the sync it runs; `sh tests/run`'s scaffold case passes; `./mvnw verify` in a scaffolded service with the wrapper copied in passes

**Checkpoint**: Shared code moves by pin; the scaffold carries it.

---

## Phase 5: User Story 3 - The Check Reads the Error Shape (Priority: P3)

**Goal**: The service check names a problem written by hand, a framework status exception, an
advice of a service's own, and a filter writing a body, in the service's own code.

**Independent Test**: The check names nothing in either service; a planted violation fails it
naming the file.

### Tests for User Story 3 ⚠

- [ ] T019 [US3] ⚠ Extend `shared/tests/run` and `shared/tests/fixtures/passing`: the passing fixture carries the shared package under `src/main/java/io/guestgraph/service/` (written by the sync) and none of the five forbidden things; one case per grep of the contract's `error-shape` item, each planted in a file under the fixture's root package and each failing with `✗ service-conventions: error-shape: <path> …`; a case with a stray `io/guestgraph/other/` still fails `root` while `io/guestgraph/service/` does not; a case with the shared controller in place passes `api`. Run and watch it fail

### Implementation for User Story 3

- [ ] T020 [US3] Extend `shared/spring/service-conventions-check`: the `error-shape` item with the five greps over `src/main/java` excluding `io/guestgraph/service/`; `root` and `api` learn the shared package. Then run T019 and watch it pass; release `v0.7.0`
- [ ] T021 [US3] Move both services' pins to `v0.7.0`, one pull request each; `sh service-conventions/service-conventions-check` answers nothing in either

**Checkpoint**: The shape is a rule, not a state.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T022 Walk [quickstart.md](quickstart.md) end to end and tick each step; fix what it finds
- [ ] T023 [P] `engine/docs/roadmap-notes.md`: the frozen contracts of slices 1 to 5 are amended forward with the slugs their problem responses answer (data-model table), and the slice's follow-ons: a published library, the framework's problems carrying the family's type; mark the shared-runtime follow-on of slice 7 consumed by `specs/008-shared-runtime`
- [ ] T024 [P] `engine/AGENTS.md` and `connector/AGENTS.md`: the code conventions name the shared package, the base exception and the rule that no problem is written by hand; `engine/README.md`'s API paragraph names the problems page
- [ ] T025 Run `./mvnw verify` in both services, `sh tests/run` and the runtime module's `verify` in the shared repository, and read each exit code on its own, then `sh conventions/conventions-check` in all four repositories

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first; T002 in parallel with it
- **Foundational (Phase 2)**: T003, T004 after T001; T005 after them; T006–T009 in parallel after T001; T010 after all
- **US1 (Phase 3)**: T011, T012 in parallel after T010; T013 after T010 merges; T014 after T013; T015 after T014; T016 any time after T005
- **US2 (Phase 4)**: T017 after T015 and T016; T018 after T013
- **US3 (Phase 5)**: T019 after T018; T020 after T019; T021 after T020 and T017
- **Polish (Phase 6)**: T022 after T021; T023, T024 after T022; T025 last

### Parallel Opportunities

- T006–T009: four classes, four authors
- T011 and T012: the two failing tests
- T016 and T018: the site page and the scaffold, beside the adoptions

---

## Implementation Strategy

### MVP First (User Story 1 Only)

Phases 1 to 3: the module, the six classes with their tests, `v0.6.0`, both adoptions and the
problems page. A caller then reads one shape from both services with types that resolve.

### Incremental Delivery

Phase 4 proves the pin move with a real change and brings the scaffold along; phase 5 turns the
shape into a check and releases `v0.7.0`; phase 6 records the amendments and walks the
quickstart. Three releases of the shared repository, and each service moves its pin three
times.

## Notes

- 27 tasks: 2 setup, 10 foundational, 6 for US1, 2 for US2, 3 for US3, 4 polish; 6 ⚠ test
  tasks. User story 4, the defaults, is built in the foundational phase (T009a, T009b) and
  taken by the adoptions (T014, T015), since it travels with the same release.
- Every pull request is merged on the owner's word, one repository at a time; a stacked pull
  request is retargeted before its base is deleted, and rebased before it merges.
