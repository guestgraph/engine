---

description: "Task list for 007-service-conventions"
---

# Tasks: Service Conventions

**Input**: Design documents from `/specs/007-service-conventions/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/service-conventions.md](contracts/service-conventions.md),
[quickstart.md](quickstart.md)

**Tests**: Test tasks are included and are not optional here. No engine logic changes, so
Constitution Principle VI does not compel them; the plan schedules them because the slice's
correctness lives in two shell scripts that fixtures can pin, and in four service items that only
an integration test proves. Tasks marked ⚠ MUST be written and seen failing before the
implementation task that follows them.

**Organization**: Grouped by user story so each is independently implementable and testable.
The engine adopts before the connector (research R7), and each service's package move is the
first change in that service (R5).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US3, mapping to the spec's prioritized stories
- ⚠: failing-test task — run it, watch it fail, then implement
- 👤: the owner's step, not an agent's

## Path Conventions

Paths starting with `shared/` are in `guestgraph/service-conventions`, a new repository, with
`shared/` standing for its root; paths starting with `engine/` are in this repository; paths
starting with `connector/` are in `guestgraph/connector-apaleo`; paths starting with
`conventions/` are in `robertblust/conventions`. Every other path is relative to the repository
the task names.

---

## Phase 1: Setup (the repository and the family)

**Purpose**: The shared repository exists as a member of the family before anything is put in it.

- [x] T001 Create `guestgraph/service-conventions` on GitHub (public, Apache-2.0, default branch `main`), clone it to `~/git/guestgraph/service-conventions`, and scaffold it as a member: `conventions.json` pinned to the current conventions release, `sh conventions/conventions-sync sync` for `conventions/` and the `AGENTS.md` block, `CLAUDE.md` as the four-line vendor adapter, `.github/workflows/conventions.yml` calling the family's check, `LICENSE`, and a README of one paragraph in the prose register saying what the repository is; open its first pull request
- [x] T002 Protect `main` of `guestgraph/service-conventions` with a ruleset requiring a pull request and the checks `tests` and `conventions / conventions`, no deletion, no force push
- [x] T003 [P] Release robertblust/conventions as a minor version whose `REPOSITORIES.md` names `guestgraph/service-conventions` ("the code-level rules of the guestgraph services: one list, one directory per stack, vendored by every service at a pinned release") with its local path, and places it in the re-sync order between the models and the engine; the three version places as the family's release steps say
- [x] T004 Re-sync the engine, the connector and `guestgraph/service-conventions` to that conventions release, one pull request each

---

## Phase 2: Foundational (the package moves)

**Purpose**: Both services carry their final package layout before any shared file names a root
or a package, so nothing later is written against a name that then changes (research R5).

**⚠️ CRITICAL**: No user story work in a service can begin until that service's move has landed.

- [x] T005 Move the engine's sources from `engine/src/main/java/io/guestgraph/` and `engine/src/test/java/io/guestgraph/` to `.../io/guestgraph/engine/` with `git mv`, rewrite the `package` and `import` lines, edit by hand the nine occurrences outside them (eight in `engine/src/test/java/io/guestgraph/engine/architecture/PersistenceRulesTest.java`, one query string), fold `auth` into `api` (`engine/src/main/java/io/guestgraph/engine/api/`), keep the Maven `groupId` `io.guestgraph`, run `./mvnw spotless:apply` and `./mvnw verify` and read the exit code, and open the pull request "The engine's packages are the engine's"
- [x] T006 Move the connector's `ops` and `events` packages to `connector/src/main/java/io/guestgraph/connector/apaleo/api/ops/` and `.../api/events/`, with their tests, the same way; `./mvnw verify`; open the pull request "The connector's endpoints sit under api"

**Checkpoint**: Both services green on their new package layout; nothing else changed.

---

## Phase 3: User Story 1 - One Rule, One Place, Every Service (Priority: P1) 🎯 MVP

**Goal**: The shared files exist once, each service vendors them at a pin, and a copy that
differs from its pin fails a check naming the file.

**Independent Test**: Change a shared file, release, move one service's pin and re-sync: the diff
is the pin and the file. Edit one character of a vendored file: the check fails naming it.

### Tests for User Story 1 ⚠

- [x] T007 [P] [US1] ⚠ Create `shared/tests/run` (POSIX sh) and `shared/tests/fixtures/`: a fixture service directory whose vendored copy equals `shared/spring/`, run through `service-conventions-sync check` with `SERVICE_CONVENTIONS_SOURCE` pointing at the local `shared/spring/` → exit 0; a fixture with one character changed in `pmd-ruleset.xml` → exit 1 and one line `✗ service-conventions: service-conventions/pmd-ruleset.xml differs from <tag>`; a fixture missing a vendored file → exit 1 naming it. Run and watch it fail (the script does not exist)

### Implementation for User Story 1

- [x] T008 [P] [US1] Create `shared/SERVICE.md` in the prose register: the list every guestgraph service meets whatever its stack (data-model "The list"): an API document served at one path, health without a credential, problem details, a request size cap, one schema and one role, a diagram held against drift, a local profile, the README sections, the agent block; each with the one reason it is on the list; and the rule that a stack's directory says how (research R9)
- [x] T009 [P] [US1] Create `shared/spring/pom.xml`, the parent POM `io.guestgraph:service-parent` at version `0.1.0`: parent `spring-boot-starter-parent` at the version both services use today, `java.version` 25, `dependencyManagement` for `testcontainers-bom`, `archunit-junit5` and `wiremock-standalone` at the versions in the two services, and the `spring-boot-maven-plugin`, `maven-compiler-plugin`, `maven-pmd-plugin` (ruleset `service-conventions/pmd-ruleset.xml`, `failOnViolation`, bound to `verify`) and `spotless-maven-plugin` (google-java-format, `removeUnusedImports`, `formatAnnotations`, `check` bound to `verify`) configured as the engine's `pom.xml` has them; no dependencies of its own
- [x] T010 [P] [US1] Create `shared/spring/pmd-ruleset.xml` from `engine/config/pmd-ruleset.xml` with the name `guestgraph` and the description naming `AGENTS.md`, unchanged otherwise
- [x] T011 [P] [US1] Create `shared/spring/ServiceRulesTest.java`, a JUnit 5 class in the default package, from the engine's `PersistenceRulesTest`: reads `root` and `scope` from `service-conventions.json`, imports classes under `root` filtering Spring AOT's `*__*` classes, and holds the six rules with the scope parameter's name from the pin: every repository method carries the scope parameter or a justified `@…Agnostic` annotation found under `root`, `@Query`-only repositories, no `CrudRepository`, no ad-hoc `EntityManager` queries, `JdbcClient` only in classes the pin's `jdbcClientAllowed` list names (empty by default), JPA confined to `<root>.persistence`
- [x] T012 [P] [US1] Create `shared/spring/regen-er` from `engine/scripts/regen-er.sh`, reading `schema` from `service-conventions.json`, writing `docs/er-schema.mmd` with the same header, otherwise unchanged
- [x] T013 [P] [US1] Create `shared/spring/verify.yml`, the workflow with the jobs `verify` (`./mvnw -B verify`), `er-drift` (`sh service-conventions/regen-er` then `git diff --exit-code docs/er-schema.mmd` with the error annotation the engine's `ci.yml` prints) and `service-conventions` (`sh service-conventions/service-conventions-sync check` then `sh service-conventions/service-conventions-check`), on push to `main` and on pull requests
- [x] T014 [P] [US1] Create `shared/spring/AGENTS.md`, the service block between `<!-- service-conventions · vX -->` and `<!-- end service-conventions -->`: what the pin is, the two checks, `sh service-conventions/service-conventions-sync sync` to take a release, and that a file under `service-conventions/` is edited in the shared repository, never in the service
- [x] T015 [US1] Create `shared/spring/service-conventions-sync` (POSIX sh, `sync|check`) from `conventions-sync`: reads `service-conventions.json` (`repo`, `tag`, `stack`), fetches `<stack>/*` from `https://raw.githubusercontent.com/<repo>/<tag>` or `SERVICE_CONVENTIONS_SOURCE`, writes `service-conventions/*`, `.github/workflows/verify.yml`, `src/test/java/ServiceRulesTest.java` and the service block of `AGENTS.md` after the family's block; `check` compares each by hash and prints `✗ service-conventions: <path> differs from <tag>` per difference, exit 1. Then run T007 and watch it pass
- [x] T016 [US1] Create `shared/README.md` in the prose register: what the repository is, the list and the stacks, how a service takes a release (the pin, the sync, the two checks), how a rule changes (edit here, release, move pins), and that a change under a stack's directory is at least a minor release; add `.github/workflows/tests.yml` running `sh tests/run` as the job `tests`; open the pull request, and on merge tag `v0.1.0` with release notes
- [x] T017 [US1] Adopt in the engine: `engine/service-conventions.json` (`repo`, `tag` `v0.1.0`, `stack` `spring`, `root` `io.guestgraph.engine`, `scope` `tenantId`, `schema` `engine`, `jdbcClientAllowed` naming the tenant lock and the seeder); `sh service-conventions/service-conventions-sync sync`; `engine/pom.xml` names the parent by `relativePath` and drops what it inherits; delete `engine/config/pmd-ruleset.xml`, `engine/scripts/regen-er.sh`, `engine/.github/workflows/ci.yml` and `engine/src/test/java/io/guestgraph/engine/architecture/PersistenceRulesTest.java`, whose rules the vendored class now holds; `./mvnw verify`, `sh service-conventions/service-conventions-sync check`; open the pull request; update the engine's ruleset to require `service-conventions` beside `verify`, `er-drift` and `conventions / conventions`
- [x] T018 [US1] Adopt in the connector the same way: `connector/service-conventions.json` (`root` `io.guestgraph.connector.apaleo`, `scope` `connectionId`, `schema` `apaleo_connector`, `jdbcClientAllowed` empty), the sync, the parent, the deletions of `connector/config/pmd-ruleset.xml`, `connector/.github/workflows/verify.yml` and `connector/src/test/java/io/guestgraph/connector/apaleo/architecture/PersistenceRulesTest.java`; the `@ConnectionAgnostic` annotation stays under `root`; `./mvnw verify` and the sync check; open the pull request; update the connector's ruleset

**Checkpoint**: Both services vendor `v0.1.0`; a one-character drift fails their `service-conventions` job.

---

## Phase 4: User Story 2 - The Shape Every Service Has (Priority: P2)

**Goal**: The service check reads each service against the list and names what is missing, and
both services pass it, which closes the six gaps.

**Independent Test**: The check reports nothing for either service; removing one item from one
service fails it naming the item.

### Tests for User Story 2 ⚠

- [x] T019 [P] [US2] ⚠ Extend `shared/tests/run` and `shared/tests/fixtures/`: a fixture service that passes every item of the contract's table → `service-conventions-check` exit 0, no output; one fixture per item with that item removed (parent, root, api, api-document, health, problem-details, request-cap, schema, diagram, local-profile, readme, agents) → exit 1 and the line `✗ service-conventions: <item>: …`; a fixture whose `api.json` names a file that differs from the owner's at the pinned commit, with the owner served from a local directory through `SERVICE_CONVENTIONS_SOURCE` → exit 1 naming the file. Run and watch it fail
- [x] T020 [P] [US2] ⚠ Create `engine/src/test/java/io/guestgraph/engine/integration/HealthTest.java`: `GET /actuator/health` answers 200 without an API key and `GET /actuator/env` answers 404. Run and watch it fail
- [x] T021 [P] [US2] ⚠ Create `connector/src/test/java/io/guestgraph/connector/apaleo/integration/ApiDocsTest.java`: `GET /api-docs` answers 200 without the ops token with a document whose paths equal those of `api/connector-api.yaml`. Run and watch it fail
- [x] T022 [P] [US2] ⚠ Create `connector/src/test/java/io/guestgraph/connector/apaleo/integration/RequestSizeTest.java`: a webhook body one byte over `connector.max-request-bytes` answers 413 as a problem detail and stores no event; a body under it is accepted. Run and watch it fail
- [x] T023 [P] [US2] ⚠ Create `connector/src/test/java/io/guestgraph/connector/apaleo/integration/LocalProfileTest.java`: the context starts with the profile `local` and no `CONNECTOR_*` variable set, reads `config/connections-local.yaml`, and its status names the sample connection with `sync-on-boot` off. Run and watch it fail

### Implementation for User Story 2

- [x] T024 [US2] Create `shared/spring/service-conventions-check` (POSIX sh) implementing the contract's table item by item in its order, each as one function printing its ✗ line, `SERVICE_CONVENTIONS_ROOT` overriding the directory read; the `api-document` item fetches each `api.json` entry from `https://raw.githubusercontent.com/<repo>/<commit>/<path>` or `SERVICE_CONVENTIONS_SOURCE` and compares by hash. Then run T019 and watch it pass; release `v0.2.0` of the shared repository
- [x] T025 [US2] In the engine: add `spring-boot-starter-actuator` to `engine/pom.xml`, `management.endpoints.web.exposure.include: health` to `engine/src/main/resources/application.yaml`, exempt `/actuator/health` in `engine/src/main/java/io/guestgraph/engine/api/ApiKeyFilter.java`; copy the four spec contracts to `engine/src/main/resources/api/NNN-<slice>.yaml` with `sources.json` naming each original under `specs/`, drop the POM's bundling block and read `classpath:api/*.yaml` in `ApiDocsController`; move the pin to `v0.2.0`, sync, `sh service-conventions/service-conventions-check` → nothing missing. Then run T020 and watch it pass; open the pull request
- [x] T026 [US2] In the connector: `connector/src/main/resources/api/connector-api.yaml` copied from `engine/specs/005-apaleo-connector/contracts/connector-api.yaml` at the engine's `main` commit, `sources.json` beside it naming `guestgraph/engine@<commit>:<path>`, and `connector/src/main/java/io/guestgraph/connector/apaleo/api/ApiDocsController.java` serving the union of `classpath:api/*.yaml` at `/api-docs` outside the ops token; exempt `/api-docs` in `OpsTokenFilter`. Then run T021 and watch it pass
- [x] T027 [US2] In the connector: `connector/src/main/java/io/guestgraph/connector/apaleo/api/RequestSizeLimitFilter.java` on every path, reading `connector.max-request-bytes` (default 1 MiB in `application.yaml`, an event is a few hundred bytes), answering 413 as a problem detail with the cap in the detail. Then run T022 and watch it pass
- [x] T028 [US2] In the connector: a `local` profile document in `connector/src/main/resources/application.yaml` (`spring.config.activate.on-profile: local`) setting `connector.public-url` to `http://localhost:8081`, `connector.ops-token` to `local-ops-token`, `connector.connections-file` to `config/connections-local.yaml`, `connector.sync-on-boot` false; `connector/config/connections-local.yaml` with one connection `local` naming the engine at `http://localhost:8080` with key `demo-key`, tenant label `demo`, and placeholder Apaleo values (`replace-me`) that are no credential; the README's run paragraph says the profile and what the placeholders mean. Then run T023 and watch it pass
- [x] T029 [US2] In the connector: `docs/er-schema.mmd` generated by `sh service-conventions/regen-er` with schema `apaleo_connector`; the vendored workflow's `er-drift` job now passes; move the pin to `v0.2.0`, sync, `sh service-conventions/service-conventions-check` → nothing missing; `./mvnw verify`; open the pull request

**Checkpoint**: Both services pass the service check; the six gaps are closed; every contract line unchanged.

---

## Phase 5: User Story 3 - The Next Service Starts From the Rules (Priority: P3)

**Goal**: A new service scaffolded from the shared repository passes both checks on its first
run, and the family and each service's agent file name the shared rules.

**Independent Test**: Scaffold a throwaway service; its checks and its build pass; the
repository list and the agent files name the shared repository.

### Tests for User Story 3 ⚠

- [x] T030 [US3] ⚠ Extend `shared/tests/run`: `sh new-service spring probe` into a temporary directory produces a tree on which `service-conventions-sync check` and `service-conventions-check` exit 0 with `SERVICE_CONVENTIONS_SOURCE` local. Run and watch it fail

### Implementation for User Story 3

- [x] T031 [US3] Create `shared/new-service` (POSIX sh, `<stack> <name>`): writes the pin with `root` derived from the name, `scope` `tenantId`, `schema` the name with hyphens as underscores; runs the sync from the local stack directory; writes a `pom.xml` naming the parent, `src/main/java/<root>/Application.java`, `<root>/api/ApiDocsController.java`, `<root>/api/RequestSizeLimitFilter.java`, `application.yaml` with the list's properties and a `local` profile, `api/<name>-api.yaml` with one health-free empty document, `db/migration/V1__init.sql` creating one table, `docs/er-schema.mmd`, a README with the required sections and `AGENTS.md`. Then run T030 and watch it pass; release `v0.3.0`
- [x] T032 [US3] Walk the throwaway service by hand: `./mvnw verify` in it passes with Docker; record in `shared/README.md` how a new service starts
- [x] T033 [P] [US3] Rewrite the service-specific part of `engine/AGENTS.md` and `connector/AGENTS.md` below the two blocks: the code conventions paragraph now points at the vendored files and the pin, the checks section names the four required jobs, and the pitfalls list keeps what is the service's own; `sh conventions/conventions-check` in both

**Checkpoint**: A third service can start from the rules; the family knows the repository.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T034 Walk [quickstart.md](quickstart.md) end to end and tick each step; fix what it finds
- [x] T035 [P] `engine/docs/roadmap-notes.md`: mark the "Next — Service conventions" section consumed by `specs/007-service-conventions`, keeping its text; add the follow-on that shared runtime code waits for a library
- [x] T036 [P] `engine/README.md`: one sentence in the checks section naming the shared rules and the `service-conventions` job; concepts, never values
- [x] T037 Run `./mvnw verify` in both services and `sh tests/run` in the shared repository and read each exit code on its own, then `sh conventions/conventions-check` in all three

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first; T002 after T001; T003 any time; T004 after T003
- **Foundational (Phase 2)**: T005 and T006 independent of each other, both after T004
- **US1 (Phase 3)**: T007–T014 in parallel after T001; T015 after T007; T016 after T008–T015; T017 after T016 and T005; T018 after T017 and T006
- **US2 (Phase 4)**: T019–T023 in parallel after T016; T024 after T019; T025 after T024 and T017; T026–T029 after T024 and T018, in order
- **US3 (Phase 5)**: T030 after T024; T031 after T030; T032 after T031; T033 after T025 and T029
- **Polish (Phase 6)**: T034 after T033; T035 and T036 after T034; T037 last

### Within Each User Story

Tests ⚠ before the implementation they pin. The shared repository is released before a service
moves its pin to the release.

### Parallel Opportunities

- T008–T014: seven shared files, seven authors
- T020–T023: four failing integration tests in two services
- T026–T028: three connector items, one pull request

---

## Implementation Strategy

### MVP First (User Story 1 Only)

Phases 1 to 3: the repository, both package moves, the shared files vendored in both services
with the sync check. The list exists as prose in `SERVICE.md`, and the mechanism is proven by a
drift that fails.

### Incremental Delivery

Phase 4 turns the list into a check and closes the six gaps, two pull requests per service.
Phase 5 adds the scaffold and the agent files. The shared repository is released three times,
`v0.1.0`, `v0.2.0`, `v0.3.0`, and each service moves its pin twice.

## Notes

- 37 tasks: 4 setup, 2 foundational, 12 for US1, 11 for US2, 4 for US3, 4 polish; 7 ⚠ test tasks.
- Every pull request is merged on the owner's word, one repository at a time, in the re-sync
  order the family's list gives once T003 lands.
