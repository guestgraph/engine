---

description: "Task list for 009-remove-subscription"
---

# Tasks: Removing a connection's webhook subscription

**Input**: Design documents from `/specs/009-remove-subscription/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md),
[contracts/connector-subscription.yaml](contracts/connector-subscription.yaml),
[quickstart.md](quickstart.md)

**Tests**: Test tasks are included and are not optional here. No engine logic changes, so
Constitution Principle VI does not compel them; the plan schedules them because every behavior
this slice adds is about what happens in a system the connector does not own, and only a test
against the stub proves the connector asked for the right thing. Tasks marked ⚠ MUST be written
and seen failing before the implementation task that follows them.

**Organization**: Grouped by user story. The contract and the client's delete come first because
every story needs them. US1 is the removal and is the MVP on its own; US2 makes a teardown
legible; US3 is two assertions and a warning that stops crying wolf.

**Where the work lands**: the specification, the contract and the roadmap line are in
`guestgraph/engine`, this repository. Every line of code is in `guestgraph/connector-apaleo`,
whose paths below are relative to that repository's root.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US3, mapping to the spec's prioritized stories
- ⚠: failing-test task — run it, watch it fail, then implement
- 👤: the owner's step, not an agent's

---

## Phase 1: Setup

- [x] T001 Add this slice's contract to the connector's served document: `src/main/resources/api/sources.json` gains `guestgraph/engine@6eba9cc:specs/009-remove-subscription/contracts/connector-subscription.yaml` beside the slice 5 source it already names, then `sh service-conventions/regen-api` writes `src/main/resources/api/openapi.yaml` from both. **Pinned to this branch's commit rather than to its merge commit**: a commit reachable from the remote survives the merge, so re-pointing afterwards would change the pin without changing what it names. Note that the raw host caches a 404, so a commit fetched before it was pushed stays missing for a minute after.

---

## Phase 2: Foundational (blocking prerequisites)

- [x] T002 ⚠ In `src/test/java/io/guestgraph/connector/apaleo/integration/SubscriptionLifecycleTest.java`, a test that the webhook client deletes by id: stub Apaleo's `DELETE /v1/subscriptions/{id}` to answer 204, call the client, and assert the request went to that path with the connection's bearer token. Run it and watch it fail to compile, because the method does not exist.
- [x] T003 Add `delete(String id)` to `src/main/java/io/guestgraph/connector/apaleo/apaleo/ApaleoWebhooks.java`: `DELETE` against `PATH + "/" + id` with the connection's token, answering nothing, raising the client's existing runtime exception when Apaleo refuses. Then run T002 and watch it pass.
- [x] T004 Replace the boolean with the four states of [data-model.md](data-model.md) in `src/main/java/io/guestgraph/connector/apaleo/api/events/Subscriptions.java`: a nested `enum State { ACTIVE, MISSING, REMOVED, UNKNOWN }`, a `state` component on `Status`, and `active` derived as `state == ACTIVE` so the existing status document keeps publishing it. `ensure` records `ACTIVE` on success and `MISSING` on failure; `check` records `ACTIVE` when it finds one and `MISSING` when it does not; the default status is `UNKNOWN` with the reason it already carries. `./mvnw verify` still passes with no other change.

**Checkpoint**: the connector can delete a subscription in Apaleo and can say which of four states a connection's subscription is in. Nothing exposes either yet.

---

## Phase 3: User Story 1 - Tear a connection down without leaving a subscription behind (Priority: P1) 🎯 MVP

**Goal**: an operator removes one connection's subscription through the operations surface, and
Apaleo holds none afterwards.

**Independent test**: with a subscription in place, call the removal, then ask the Apaleo stub
what it holds. Nothing names that connection's endpoint.

### Tests for User Story 1 ⚠

- [x] T005 [US1] ⚠ In `SubscriptionLifecycleTest`, a removal deletes: with a subscription stubbed for the connection's endpoint, `DELETE /connections/alpha/subscription` with the operations token answers 200, the body has `state: REMOVED`, `removed: true` and the `endpoint` the subscription pointed at, and the stub recorded a delete of that subscription's id. Run and watch it fail.
- [x] T006 [P] [US1] ⚠ In the same file, a removal is idempotent: call it twice, and call it on a connection Apaleo holds nothing for. Every call answers 200 with `state: REMOVED`; the second and third carry `removed: false` and a null `endpoint`, and no second delete reached the stub. Run and watch it fail.
- [x] T007 [P] [US1] ⚠ In the same file, a removal touches nothing else: read the connection's counters, sync points, pending events and held guest ids from `GET /status` and from the database before and after a removal, and assert each is identical, then start a full sync and assert it runs to a successful outcome. Run and watch it fail.
- [x] T008 [P] [US1] ⚠ In the same file, the refusals: without the token the removal answers 401 `unauthorized`; for a connection no configuration carries it answers 404 `not-found`; with Apaleo refusing the delete it answers 502, the body's type is the family's shape, and a second call still finds the subscription, so nothing was reported removed that was not. Each body is `application/problem+json` with a `type` under `https://guestgraph.io/problems/#`. Run and watch it fail.
- [x] T009 [P] [US1] ⚠ In `SubscriptionLifecycleTest`, only this connection's subscription is deleted: with two configured connections on one Apaleo account, each with its own subscription, remove one and assert the other's still exists and its status is unchanged. Run and watch it fail.

### Implementation for User Story 1

- [x] T010 [US1] Add `remove(ConnectionConfig c)` to `Subscriptions`: find by `endpointOf(c)` exactly as `check` does, delete it through `ApaleoWebhooks.delete` when one is found, record `Status` with `state = REMOVED`, a null id, the endpoint it deleted and `checkedAt` now, and return whether it deleted one. A connection with nothing to delete records the same state and returns false. Apaleo refusing is left to propagate, unrecorded, so no removal is reported that did not happen.
- [x] T011 [US1] Add `SubscriptionRemoved` to `src/main/java/io/guestgraph/connector/apaleo/api/ops/StatusDocuments.java` with the contract's fields: `state`, `id`, `eventTypes`, `checkedAt`, `removed`, `endpoint`.
- [x] T012 [US1] Add `removeSubscription` to `src/main/java/io/guestgraph/connector/apaleo/api/ops/StatusController.java`: `@DeleteMapping("/connections/{connectionId}/subscription")`, resolving the connection the way every other method there does so an unknown one raises `NoSuchConnectionException`, calling `Subscriptions.remove`, and answering 200 with the document.
- [x] T013 [US1] Add `SubscriptionUnreachableException` beside the connector's other exceptions in `src/main/java/io/guestgraph/connector/apaleo/api/`, extending `ServiceException` with 502, the slug `apaleo-unreachable` and a detail that names neither credential nor secret; `removeSubscription` raises it when the client throws. Add the slug to guestgraph.io's problems page in the same slice — the page is the type's meaning and a type without a section is a link to nothing.
- [x] T014 [US1] Run T005 to T009 and watch them pass. Then `./mvnw verify`, `sh service-conventions/service-conventions-check` and `sh conventions/conventions-check`, reading each exit code on its own.

**Checkpoint**: US1 is complete and shippable. An operator can remove a subscription and Apaleo holds none.

---

## Phase 4: User Story 2 - Know that a removal happened, and by whom (Priority: P2)

**Goal**: a connection deliberately without a subscription no longer looks like one whose
subscription failed.

**Independent test**: remove a subscription, read `GET /status`, and see a state that says the
absence was asked for; break a second connection's creation and see a different state.

### Tests for User Story 2 ⚠

- [x] T015 [US2] ⚠ In `src/test/java/io/guestgraph/connector/apaleo/integration/StatusTest.java`, the status distinguishes the two: after a removal the connection reports `subscription.state` of `REMOVED`; a connection whose `ensure` failed reports `MISSING`; a connection with one reports `ACTIVE` and its id. Run and watch it fail.
- [x] T016 [P] [US2] ⚠ In `SubscriptionLifecycleTest`, the log carries the act and no secret: capture the connector's log across a removal, assert one line names the connection and the removal, and assert the line contains neither the connection's webhook secret nor any part of its Apaleo credentials. Run and watch it fail.

### Implementation for User Story 2

- [x] T017 [US2] Publish the state in the status document: `StatusDocuments.Subscription` gains `state` beside `active`, and `StatusController.status` passes it through from `Subscriptions.status`.
- [x] T018 [US2] Log one line at info in `Subscriptions.remove`, naming the connection and whether a subscription was deleted, never the endpoint's secret — the endpoint goes in the response, which is behind the operations token, and not in the log.
- [x] T019 [US2] ~~Amend slice 5's status contract forward~~ — **not possible, and recorded instead**: `regen-api` merges component schemas by name with the first source winning, so a later contract cannot extend an earlier one's schema, and slice 5's is frozen. The served document therefore describes `subscription` with `active` alone while the response also carries `state`; the block permits extra members, so it under-describes rather than contradicts. The contract says so where a reader will look, and T028 records the limit and its options in `docs/roadmap-notes.md`.
- [x] T020 [US2] Run T015 and T016 and watch them pass, then `./mvnw verify`.

**Checkpoint**: a teardown reads as a teardown, in the status and in the log.

---

## Phase 5: User Story 3 - A removal is not undone by the connector's own housekeeping (Priority: P2)

**Goal**: a removal lasts as long as the connector runs, and the reconciliation stops warning
about a connection that is deliberately without a subscription.

**Independent test**: remove a subscription, run a reconciliation, and find the state still
`REMOVED` with a successful run and no warning line.

### Tests for User Story 3 ⚠

- [x] T021 [US3] ⚠ In `src/test/java/io/guestgraph/connector/apaleo/integration/ReconcileTest.java`, a reconciliation after a removal: the run's outcome is success, the state is still `REMOVED`, the stub recorded no create, and the log carries no warning about a missing subscription. Run and watch it fail on the warning.
- [x] T022 [P] [US3] ⚠ In `SubscriptionLifecycleTest`, the way back: `PUT /connections/alpha/subscription` after a removal answers 200 with `state: ACTIVE` and an id, the stub recorded a create for the connection's endpoint, and a delivery to that endpoint is then accepted. Its refusals match T008's: 401 without the token, 404 for an unknown connection, 502 when Apaleo refuses. Run and watch it fail.

### Implementation for User Story 3

- [x] T023 [US3] In `Subscriptions.check`, leave a `REMOVED` connection alone: when the recorded state is `REMOVED` and Apaleo holds none, record `REMOVED` again rather than `MISSING`, and log nothing. A subscription that reappears while the state says `REMOVED` is recorded `ACTIVE`, because Apaleo's answer is the truth and the intent was not honored.
- [x] T024 [US3] Add `restore(ConnectionConfig c)` to `Subscriptions`, which is `ensure(c)` under a name that says what an operator is doing, and `restoreSubscription` to `StatusController` as `@PutMapping("/connections/{connectionId}/subscription")`, answering 200 with the state document and raising the same three refusals as the removal.
- [x] T025 [US3] Run T021 and T022 and watch them pass, then `./mvnw verify`.

**Checkpoint**: all three stories are complete. The slice is functionally done.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T026 [P] `connector-apaleo/README.md`: the "How changes arrive" section says a subscription can be removed and restored through the operations surface, and says plainly that a restart subscribes every configured connection again, so an operator tearing down stops the connector before the tunnel. The "Watching and driving it" section lists the two new calls beside the runs.
- [x] T027 [P] Add the `apaleo-unreachable` section to `guestgraph.github.io/problems/index.html` in English, with its status and the service that answers it, then the `ids` list in `verify/check.mjs`; the German follows the owner's review, as every page's does.
- [x] T028 [P] `docs/roadmap-notes.md` in the engine: record the deferral this slice consumes, under the sandbox session that found it, and mark it consumed by `specs/009-remove-subscription`. The deferral was never written down, which the specification's checklist records; this is where it belongs.
- [ ] T029 Move the API page's connector pin: `guestgraph.github.io/api-sources.json` takes the connector's new commit, `npm run api` rewrites the rows, and the two new operations appear with their schemas. `build-api.mjs` needs `removeSubscription` and `restoreSubscription` placed in `IN_SECTION` and their refusals in `PROBLEMS`, or the build stops and names them, which is the point of both lists.
- [ ] T030 👤 Walk [quickstart.md](quickstart.md) with the owner against the sandbox, including step 8, the teardown it was built for. Record what the walk finds in `docs/roadmap-notes.md`, not in this spec, which is frozen at merge.
- [ ] T031 Run `./mvnw verify` in the connector, `sh service-conventions/service-conventions-check`, and `sh conventions/conventions-check` in the engine, the connector and the site, reading each exit code on its own. Then open the spec branch's pull request with every task ticked.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: T001 can run at any time, but is re-run before the connector's pull request with the merge commit.
- **Foundational (Phase 2)**: blocks every story. T003 gives the client its delete, T004 gives the status its states.
- **US1 (Phase 3)**: needs Phase 2. Independently shippable.
- **US2 (Phase 4)**: needs US1, because there is nothing to report until a removal exists.
- **US3 (Phase 5)**: needs US1. Independent of US2.
- **Polish (Phase 6)**: needs every story; T029 needs the connector merged.

### User story dependencies

- **US1** stands alone once Phase 2 is done, and is the MVP.
- **US2** and **US3** each need US1 and are independent of each other, so they can be built in either order or at once.

### Parallel opportunities

- T005 to T009 are one file but separate methods; write them together, then implement.
- T006, T007, T008 and T009 are marked [P] because they touch no shared fixture beyond the stub.
- T015 and T016 are in different files and run together.
- T026, T027 and T028 are three repositories and run together.

---

## Implementation Strategy

**MVP**: Phase 1, Phase 2 and Phase 3. At that point an operator can remove a subscription and
Apaleo holds none, which is the whole reason the slice exists. Stopping there leaves a teardown
that reads as a fault in the status, which is a real cost but not a broken one.

**Then US3 before US2** if only one more can be done: a reconciliation warning every fifteen
minutes about a deliberate absence is noisier than a status field nobody reads yet, and the
restore in T024 is what makes the removal safe to use at all.

**One pull request per repository**, in the order the family re-syncs: the engine's spec branch
last, after the connector's code and the site's page have merged, so its tasks can be ticked
against work that exists.
