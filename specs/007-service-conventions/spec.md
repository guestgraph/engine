# Feature Specification: Service Conventions

**Feature Branch**: `007-service-conventions`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Service conventions: every guestgraph Java service has the same shape by check, not by hand. The engine and the Apaleo connector match in stack, guardrails and CI because the second copied the first, and nothing compares them. A comparison on Sep 10, 2026 found the connector serving no API document where the engine serves the union of its contracts, no request size cap on the webhook endpoint, no ER diagram with a drift job, no local profile and a thinner error layer, and the engine without a health endpoint. The slice: a repository of code-level rules every guestgraph service vendors at a pinned tag, like the family's conventions, with a sync check and a service check beside verify and conventions, so the gaps become a failing check rather than a memory."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One Rule, One Place, Every Service (Priority: P1)

The family's prose and git rules live in one repository and every member vendors them at a pinned release, with a check that fails when a copy differs from its pin. The code-level rules of the guestgraph services, what a service's source must look like and what its build must run, have no such home: each service carries its own copy of the same ruleset, formatting configuration, architecture rules and workflow files, and the second service got them by hand from the first. A maintainer who changes one of these rules today edits every service and hopes to miss none. After this slice, the code-level rules live in one repository of their own, each service takes them at a pinned release, and a service whose copy differs from what its pin names fails its check, exactly as it does for the prose rules today.

**Why this priority**: It is the mechanism everything else in the slice runs on. Without one place and a pin, a service check would be one more thing copied by hand.

**Independent Test**: Change one rule in the shared repository and release it; move the pin in one service and verify its copy follows and its check passes; edit one character of a vendored file in a service and verify its check fails naming the file.

**Acceptance Scenarios**:

1. **Given** the shared repository at a release, **When** a service moves its pin to that release and re-syncs, **Then** every vendored file equals the release's and the check passes.
2. **Given** a service whose vendored copy differs from its pin in one character, **When** its checks run, **Then** the sync check fails and names the file.
3. **Given** a rule that every service must follow, **When** it changes, **Then** it changes in one repository and reaches each service by one pin move and nothing else.
4. **Given** a service that needs a rule of its own beyond the shared ones, **When** it adds it, **Then** the addition sits beside the shared files, never inside them, and the sync check is unaffected.

---

### User Story 2 - The Shape Every Service Has (Priority: P2)

Two services exist, and they differ in ways nobody decided: one serves its API document and the other does not; one caps the size of a request body and the other accepts any; one regenerates its data-model diagram and fails when it is stale, the other has no diagram; one seeds a local setup and the other needs a hand-written file before it starts; one answers a health check and the other cannot be polled; and one names its packages after the service, the other after the family. After this slice, the list of what every service must have is written once, a check reads each service and fails naming what is missing, and both existing services pass it, which closes those gaps.

**Why this priority**: It is what the maintainer gets from the mechanism: the list stops being a memory. It is second because it needs the shared home to live in.

**Independent Test**: Run the service check on both services and verify it lists nothing missing; remove one item from one service, for example its health endpoint, and verify the check fails naming it.

**Acceptance Scenarios**:

1. **Given** the list every service must satisfy, **When** the check runs on the engine and on the connector, **Then** it reports nothing missing for either.
2. **Given** a service missing one item of the list, **When** its checks run, **Then** the service check fails and names the item and the service.
3. **Given** any service, **When** a reader opens the folder of its resources named for the API, **Then** the documents served at `/api-docs` are there and nothing else is served, and beside them one file names where each document comes from: a slice's contract in the same repository, the engine's contract in another, or the service's own; a copy that differs from its source fails the service's checks.
4. **Given** the connector's webhook endpoint, **When** a body larger than the configured cap arrives, **Then** it is refused with a problem-details answer, as the engine refuses on its API.
5. **Given** the engine, **When** a platform polls its health endpoint, **Then** it answers without a credential, as the connector's does.
6. **Given** the connector's migrations, **When** they change, **Then** a committed diagram of its tables is held against them by a drift check, as the engine's is.
7. **Given** a developer starting the connector locally, **When** they use its local profile, **Then** it starts against a seeded engine tenant with a sample connection, and nothing has to be written by hand first.
8. **Given** any service's source, **When** its package root is read, **Then** it is the family's root followed by the service's own name, as its repository is named, so the engine's is `io.guestgraph.engine` as the connector's is `io.guestgraph.connector.apaleo`.
9. **Given** any service's source, **When** its endpoints, filters and error answers are looked for, **Then** they sit under one package named `api` below the root, so the connector's operations and webhook endpoints move under it as the engine's already are.

---

### User Story 3 - The Next Service Starts From the Rules (Priority: P3)

The family's next connector, or any later service, should begin where the first two now stand, not where the engine began. After this slice, a new service takes the shared rules at their current release, satisfies the list, and passes every check on its first run, and the family's list of repositories names the shared repository so that a session in any member knows it exists.

**Why this priority**: It is the reason the first two stories are worth doing at all, and it is last because it can only be shown once they hold.

**Independent Test**: Scaffold a throwaway service from the shared rules and verify its checks pass on the first run without a copied file from another service; verify the family's repository list names the shared repository.

**Acceptance Scenarios**:

1. **Given** the shared repository at a release, **When** a new service is scaffolded from it, **Then** its sync check and service check pass on the first run.
2. **Given** the family's repository list, **When** it is read, **Then** the shared repository is in it with its purpose and its place in the re-sync order.
3. **Given** a service's agent file, **When** an agent reads it, **Then** it names the shared rules, the pin and the two checks, and says where a shared file is edited and where it is not.

---

### Edge Cases

- A service needs a rule the others do not: the shared set holds what every service needs and nothing that only one does; the one service keeps its own beside the shared files, and the check does not read it.
- A service is deliberately behind the latest release: a pin that is behind is intent until the owner says it is drift, as the family's git rules already say; the sync check compares against the pin, never against the latest.
- The two services run different versions of the same stack: the shared set names the stack's versions and the check reports where a service differs, without failing, because a version pin is the service's own editorial line.
- A served document is a copy of a record kept elsewhere: the engine's contracts are the decision records of its slices, frozen under `specs/`, and the connector's is owned by the engine repository where its specification lives. The service serves copies under its resources, each named with its source, a path in the same repository or a repository at a pinned commit, the way the sites hold the design system, so the record and the copy cannot silently differ.
- The engine's package move touches every source file and the query strings that name its packages in full, which the engine's own rules allow only inside such strings: the move is mechanical, it changes no contract and no table, and it happens before the engine's first release, as the schema move did.
- The shared repository itself is a member of the family: it vendors the family's prose and git rules like every other member and runs the same prose check on its own text.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: One repository MUST hold the code-level rules every guestgraph service follows: the source-level ruleset, the formatting configuration, the architecture rules, the workflow files each service runs in its checks, and the list every service must satisfy.
- **FR-002**: Every guestgraph service MUST vendor those rules at a release named by one visible pin in the service, and MUST be able to bring its copy to the pinned release with one command.
- **FR-003**: A check MUST fail, naming the file, when a service's vendored copy differs from the release its pin names.
- **FR-004**: A check MUST read a service and fail, naming the item and the service, when the service lacks any item of the list: an API document served at one agreed path from the documents under the service's resources, each with its source named beside it; a health endpoint answering without a credential; errors answered as problem details; a cap on the size of a request body; a committed diagram of its tables held against its migrations by a drift check; a local profile that starts the service against a seeded setup; a package root of the family's root followed by the service's name as its repository is named, with the endpoints, filters and error answers under `api` below it; and the sections its README carries, among them the deployment paragraph with the two statements that create its role and schema.
- **FR-005**: Both existing services MUST pass both checks at the end of the slice, which closes the gaps found: the connector serves its API document, caps its webhook body, carries a diagram with a drift check and a local profile; the engine answers a health check and moves its packages under `io.guestgraph.engine`; the connector moves its operations and webhook endpoints under `api`.
- **FR-006**: The two checks MUST run in every service's continuous integration as a required check beside the service's own suite and the family's prose check.
- **FR-007**: A service's own additions to the rules MUST layer beside the shared files and never edit them, and the shared files MUST be edited only in the shared repository.
- **FR-008**: The shared repository MUST be released the way the family releases what others take: a tag and notes in the prose register, at least a minor release when a vendored file changes, a major when a taking service must do more than re-sync.
- **FR-009**: The family's repository list MUST name the shared repository with its purpose and its place in the re-sync order, and every service's agent file MUST name the shared rules, the pin and the two checks.
- **FR-010**: No API contract of either service MUST change, and no behavior beyond the items FR-005 names MUST change.

### Key Entities

- **Rule set**: the code-level rules at a release: the files every service vendors and the list every service must satisfy.
- **Pin**: the one line in a service that names which release of the rule set it vendors.
- **Sync check**: the comparison of a service's vendored copy with the release its pin names.
- **Service check**: the reading of a service against the list, item by item.
- **Item**: one thing every service must have, named so that a check can read it and a maintainer can act on its absence.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A rule changed in the shared repository reaches every service by one pin move per service and no other edit, shown by the diff of each service's pull request.
- **SC-002**: A vendored copy that differs from its pin by one character fails the sync check on the first run, naming the file.
- **SC-003**: The service check reports zero missing items for the engine and for the connector, and fails within one run when any single item is removed from either.
- **SC-004**: A new service scaffolded from the shared rules passes both checks on its first continuous-integration run, with no file copied from another service.
- **SC-005**: No line of any API contract of either service changes, and every existing test of both services passes unchanged in what it asserts.

## Assumptions

- The rules are those of the guestgraph services in their present stack, so the repository belongs to the guestgraph organization; the family's prose and git rules stay where they are and keep covering every member, including the new one.
- Vendoring, pinning and the sync check follow the shape the family already uses for its prose and git rules, so a member has one way of taking shared files, not two.
- The documents a service serves live under its resources with their sources named beside them; the originals stay where they are written, the engine's under `specs/` and the connector's in the engine repository, and the check holds copy and original equal.
- Adding a repository to the family is a conventions release naming it in the repository list, and the members re-sync in the order that list gives.
- The list of items is what the comparison of Sep 10, 2026 found, plus the items both services already share; it grows by a release of the rule set, never by an edit in one service.
