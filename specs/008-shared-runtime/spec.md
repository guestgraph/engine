# Feature Specification: Shared Runtime Code

**Feature Branch**: `008-shared-runtime`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Shared runtime code vendored as source: one error shape and the few classes every service carries. The two services do not share one error pattern: the engine maps typed exceptions to problem details with a type URI under guestgraph.io/problems and a catch-all that logs and says nothing, its filters write the same shape by hand; the connector throws Spring's status exception, has one handler for its 409, builds a 400 inline, and its filters write JSON strings with about:blank as the type. The type URIs the contracts promise appear in no contract. The family has no Maven repository, so a library cannot be published; the honest first step is what the sites already do for the design system: a small module vendored as source through service-conventions, with the problem writer, the advice, the base exception, the size filter, the document controller and a bearer-token filter, and a check that no service writes a problem by hand."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One Error Shape in Every Service (Priority: P1)

A caller of any guestgraph service reads a refusal the same way, whatever refused it: a filter before the request reached a controller, a controller that could not find the thing, a run already in progress, or a failure nobody foresaw. Today the engine answers with a type URI, a title and a detail, and logs what it did not foresee without saying it; the connector answers with the right status and media type but no type, and an unforeseen failure falls to the framework's default. After this slice, the error shape is one piece of code every service carries, taken from the shared rules at a pinned release: the writer that turns a problem into the response, the advice that turns an exception into a problem and catches what nobody foresaw, and the exception every service-specific exception extends, carrying its status, its type and its title. A problem's type resolves to a page that says what it means.

**Why this priority**: It is the visible half of the slice, the one a caller meets, and the reason the shared code exists at all: the same refusal answered two ways is the drift the slice is against.

**Independent Test**: Provoke a 401 from a filter, a 404 from a controller, a 409 from a run and a 500 from a planted failure in each service, and verify all four are problem details with a type under the family's problem base, a title, a status, a detail that names no secret and no internal message, and that the 500 was logged in full on the server only.

**Acceptance Scenarios**:

1. **Given** any service, **When** a filter refuses a request, **Then** the answer is a problem detail of the same shape a controller's refusal has, with a type URI, a title, the status and a detail.
2. **Given** any service, **When** a controller throws a service-specific exception, **Then** the answer's status, type and title are those the exception carries, and no handler is written for it.
3. **Given** any service, **When** an exception nobody foresaw escapes, **Then** the answer is a problem detail with the family's internal-error type and a detail that says nothing of the cause, and the cause is logged on the server in full.
4. **Given** a problem's type URI, **When** a reader opens it, **Then** it reaches a page in the family that says what the problem means and what to do.
5. **Given** the engine's existing problems, retired guest among them with its extra members, **When** the shared shape is in place, **Then** every existing integration test passes unchanged in what it asserts.

---

### User Story 2 - The Few Classes Every Service Carries (Priority: P2)

Beyond the error shape, each service carries a few lines that are the same lines: the filter that caps a request body, the controller that serves the API document, and a filter that guards a surface with one bearer token from configuration. Slice 7 left them in each service on purpose, because sharing code needs a library and the family has no repository to publish one to. After this slice, these classes live once, in the shared rules repository as source, and the sync copies them into each service under one package of the family's own, never edited there, the way the sites take the design system as source rather than as a package.

**Why this priority**: It is the mechanism that makes the first story hold over time: a fix to the size filter reaches every service by one pin move, as a rule does.

**Independent Test**: Change one line in a shared class in the shared repository, release, move one service's pin and re-sync: the diff is the pin and the class, the service builds, and its checks pass. Edit one character of the vendored class in the service: the sync check fails naming it.

**Acceptance Scenarios**:

1. **Given** the shared repository at a release, **When** a service re-syncs, **Then** the shared classes under the family's package equal the release's and the sync check passes.
2. **Given** a service, **When** it needs the size cap, the API document or a bearer guard, **Then** it uses the vendored class with its own configuration values and writes none of its own.
3. **Given** a vendored class edited in a service, **When** its checks run, **Then** the sync check fails naming the file.
4. **Given** the two services, **When** the slice ends, **Then** neither carries its own size filter, document controller or problem writer, and the connector's token guard is the vendored one.

---

### User Story 3 - The Check Reads the Error Shape (Priority: P3)

A rule that holds by inspection is one that a new service or a hurried afternoon breaks silently. After this slice, the service check reads a service's own code for what the shared shape forbids: a problem written by hand outside the shared package, a framework status exception thrown in place of a typed one, a handler for an exception that already carries its answer, and the shared package edited in the service. What is missing is named, as every other item is.

**Why this priority**: It is what turns the first two stories from a state into a rule; last because it can only read what the first two put in place.

**Independent Test**: Run the service check on both services and verify it names nothing; add a hand-written problem in one controller and verify it fails naming the file and the line.

**Acceptance Scenarios**:

1. **Given** both services after the slice, **When** the service check runs, **Then** it names nothing missing and nothing forbidden.
2. **Given** a controller that writes a problem by hand or throws the framework's status exception, **When** the check runs, **Then** it fails naming the file.
3. **Given** the scaffold of a new service, **When** one is written, **Then** it carries the shared package and passes the check on its first run.

---

### Edge Cases

- A service-specific exception needs members beyond the standard four, as the engine's retired-guest problem carries the current guest ids: the base exception lets a subclass add named members, and the advice writes them, so the engine keeps its answer unchanged.
- The framework's own problems, validation and unparseable bodies and unknown paths, keep coming from the framework, since it produces problem details already; the shared advice sits behind them and takes only what they leave.
- A filter that refuses cannot use the advice, which runs only for controllers: the shared writer is what both use, so the shape is one class rather than one convention.
- The connector's webhook endpoint answers Apaleo, which counts any non-2xx as a failure and retries for a day: its refusals keep their statuses, and only their shape changes.
- The engine's API key guard is the engine's own, since it resolves a tenant and an actor; it uses the shared writer for its refusals and stays where it is.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The shared rules repository MUST hold, for the Spring stack, a set of source files in one package of the family's own that every service vendors unchanged: a writer that turns a problem into a response, an advice that turns an exception into a problem and answers every unforeseen exception with the family's internal-error type after logging it in full, a base exception carrying status, type and title that a service's own exceptions extend, a filter that caps a request body, a controller that serves the API document, and a filter that guards a surface with one bearer token from configuration.
- **FR-002**: The sync MUST write those files into the service and the sync check MUST fail naming a file that differs from the pinned release.
- **FR-003**: Every problem a guestgraph service answers MUST carry a type URI under one family base, a title, its status and a detail, whether a filter, a controller, the advice or the framework produced it, and MUST be answered as `application/problem+json`.
- **FR-004**: Every type URI MUST resolve to a page in the family that says what the problem means and what a caller does about it; the contracts MUST name the types a service can answer.
- **FR-005**: No answer MUST carry an exception's message for an unforeseen failure, nor a credential, nor a person's data; the cause MUST be logged on the server in full.
- **FR-006**: Both existing services MUST use the shared classes at the slice's end and carry no size filter, document controller or problem writer of their own; the connector's token guard MUST be the shared one; every existing test of both services MUST pass unchanged in what it asserts.
- **FR-007**: The service check MUST fail naming the file when a service's own code writes a problem by hand, throws the framework's status exception, or edits the shared package.
- **FR-008**: A service-specific exception MUST be able to add named members to its problem, and the engine's retired-guest answer MUST keep its members.
- **FR-009**: The scaffold MUST write a service that carries the shared package and passes the check on its first run.

### Key Entities

- **Shared package**: the source files every service vendors, under one package name of the family's, edited only in the shared repository.
- **Problem**: what a service answers when it refuses: type, title, status, detail, and any named members a specific problem adds.
- **Problem type**: one URI under the family's base, with a page that explains it.
- **Service exception**: a service's own exception, extending the shared base, carrying its problem.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every refusal either service answers in its integration suites is a problem detail with a type under the family's base; verified by a test that provokes one from each origin, filter, controller, advice and framework, in each service.
- **SC-002**: A change to a shared class reaches both services by one pin move per service and no other edit, shown by the diff of each service's pull request.
- **SC-003**: The service check names nothing in either service at the slice's end, and fails within one run when a hand-written problem or a framework status exception is added.
- **SC-004**: Every existing integration test of both services passes unchanged in what it asserts.
- **SC-005**: Every problem type either service answers is named in that service's contract and resolves to a page.

## Assumptions

- Vendoring as source follows the shape slice 7 built: the shared repository's stack directory holds the files, the sync copies them, the check holds them; a published library is the follow-on once the family has a repository to publish to, and the package name is chosen so that a library could replace the copies without a rename.
- The family's problem base is the one the engine already uses, under `guestgraph.io`, and the pages live where the family's site is built; naming the types in the contracts goes forward into the roadmap notes for the frozen slices and into the contract for a new one.
- The engine's tenant and actor resolution stays the engine's own; only its refusals go through the shared writer.
- The framework's own problem details stay as they are; the shared advice adds the type and the catch-all and takes nothing from them.
