# Implementation Plan: Shared Runtime Code

**Branch**: `008-shared-runtime` | **Date**: 2026-09-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-shared-runtime/spec.md`

## Summary

Six classes under one package of the family's, `io.guestgraph.service`, held in the shared
rules repository as source and vendored into every service by the sync slice 7 built: the
problem writer, the advice with the one catch-all, the base exception every service-specific
exception extends, the size filter, the document controller and a bearer guard. Every refusal a
guestgraph service answers then has one shape, with a type that resolves to a page on the
family's site, and the service check reads a service's own code for the ways that shape was
broken before. Both services drop their own copies and extend the base; no contract line and no
existing assertion changes.

## Technical Context

**Language/Version**: Java 25, unchanged; the shared sources compile against the same parent.

**Primary Dependencies**: Spring's `ErrorResponseException` and its problem-details handler,
already enabled in both services; nothing new.

**Storage**: none; no migration.

**Testing**: unit tests on the shared classes in the shared repository's runtime module;
fixtures for the check's new item; one `ErrorShapeTest` per service; every existing test
unchanged.

**Target Platform**: Linux server, unchanged.

**Project Type**: a source module in the shared repository, plus changes in two web services and
one page in the family's site.

**Performance Goals**: none.

**Constraints**: no publish step (R1); the shared package is never edited in a service; the
engine's tenant and actor resolution stays its own; the connector's webhook statuses stay as
Apaleo expects them.

**Scale/Scope**: 6 shared classes with tests; in the engine 8 exceptions rebased, 3 classes
removed, 1 filter's refusals rerouted, 1 test added; in the connector 4 exceptions added, 3
classes removed, 1 test added; 1 check item; 1 site page; 1 release of the shared repository that asks the services to replace their own classes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
*Source: `.specify/memory/constitution.md` v0.6.0*

**Initial evaluation — PASS.** **Post-design re-evaluation — PASS.**

- [x] **Tenant isolation (I)**: unchanged; the engine's key filter stays the engine's.
- [x] **Immutable source records (II)**: no data path changes.
- [x] **No silent data loss (III)**: refusals stay refusals with a problem detail that says
      what was wrong; the shared shape adds the type and takes nothing from the detail.
- [x] **Explainable & reversible resolution (IV)**: untouched.
- [x] **API-first (V)**: every error is RFC 9457, now with the type the principle implies; the
      shared advice makes even an unforeseen failure a problem detail in both services.
- [x] **TDD on the resolution engine (VI)**: no engine logic changes; the new tests come first.
- [x] **Stack & shape**: unchanged; the shared package is source in the same module.
- [x] **Open-core boundary**: nothing commercial.
- [x] **GDPR readiness**: a detail never carries a person's data (FR-005), which tightens the
      rule rather than loosens it.

## Project Structure

### Documentation (this feature)

```text
specs/008-shared-runtime/
├── plan.md
├── spec.md
├── research.md          # R1..R6
├── data-model.md        # the problem, the package, what a service keeps, the rules
├── quickstart.md
├── contracts/
│   └── shared-runtime.md    # the shape, the classes' surface, the sync, the check, the page
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — created by /speckit-tasks, not here
```

### Source Code

```text
guestgraph/service-conventions/
├── spring/runtime/
│   ├── pom.xml                                  # the module's own build, naming the parent; not vendored
│   ├── src/main/java/io/guestgraph/service/
│   │   ├── Problems.java
│   │   ├── ServiceException.java
│   │   ├── ServiceExceptionHandler.java
│   │   ├── RequestSizeLimitFilter.java
│   │   ├── ApiDocsController.java
│   │   └── BearerTokenFilter.java
│   └── src/test/java/io/guestgraph/service/    # unit tests on Problems and ServiceException
├── spring/service-conventions-sync              # vendors runtime/src/main/java/io/guestgraph/service/*.java
├── spring/service-conventions-check             # error-shape; root and api learn the package
├── new-service                                  # no own api classes; service.max-request-bytes
└── tests/                                       # error-shape fixtures

guestgraph/engine/
├── src/main/java/io/guestgraph/service/         # vendored
├── src/main/java/io/guestgraph/engine/api/      # 8 exceptions extend ServiceException; ApiKeyFilter refuses through Problems
│                                                # ApiExceptionHandler, RequestSizeLimitFilter, ApiDocsController removed
├── src/main/resources/application.yaml          # service.max-request-bytes
└── src/test/java/io/guestgraph/engine/integration/ErrorShapeTest.java

guestgraph/connector-apaleo/
├── src/main/java/io/guestgraph/service/         # vendored
├── src/main/java/io/guestgraph/connector/apaleo/api/   # 4 exceptions; OpsTokenFilter, RequestSizeLimitFilter, ApiDocsController removed
├── src/main/resources/application.yaml          # service.max-request-bytes, service.bearer.*
└── src/test/java/io/guestgraph/connector/apaleo/integration/ErrorShapeTest.java

guestgraph/guestgraph.github.io/
└── problems/index.html                          # one section per slug (R3)
```

**Structure Decision**: One package of the family's, present in every service by the sync,
edited in one place. Each service keeps its own exceptions and its own guard where the guard is
its own logic.

## Design Decisions Carried From Phase 0

| # | Decision | Where |
|---|---|---|
| R1 | Shared as source under `io.guestgraph.service`; taking it is the 0.x minor step the rules call a major | [research.md](research.md) |
| R2 | Six classes; `ServiceException` extends `ErrorResponseException`, so the advice keeps the catch-all only | [research.md](research.md), [contracts/shared-runtime.md](contracts/shared-runtime.md) |
| R3 | Types as fragments of one page on the family's site; frozen contracts amended forward | [research.md](research.md), [data-model.md](data-model.md) |
| R4 | The check's `error-shape` item, five greps; `root` and `api` learn the package | [research.md](research.md), [contracts/shared-runtime.md](contracts/shared-runtime.md) |
| R5 | What each service drops and extends | [research.md](research.md), [data-model.md](data-model.md) |
| R6 | Unit tests in the runtime module, fixtures, one `ErrorShapeTest` per service | [research.md](research.md), [quickstart.md](quickstart.md) |

## Complexity Tracking

No Constitution Check violations — the table is intentionally empty.

One choice worth naming: **a Maven module inside the shared repository that is never vendored
as a module.** It exists so the shared sources compile, format and test where they are written;
a service takes the classes, not the module, because a service is one Maven module by the
constitution and the family has no repository to publish a second one to.

## Follow-ons Not In This Slice

- A published library replacing the copies, once the family has a Maven repository; the package
  name is already the library's.
- The framework's own problems carrying the family's `type`, which would need the shared advice
  to take over Spring's handlers; left with the framework's shape until a caller needs it.
