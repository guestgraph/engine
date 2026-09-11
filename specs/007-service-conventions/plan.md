# Implementation Plan: Service Conventions

**Branch**: `007-service-conventions` | **Date**: 2026-09-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-service-conventions/spec.md`

## Summary

The code-level rules of the guestgraph services move into one repository,
`guestgraph/service-conventions`, that each service vendors at a pinned release with a sync
check, the shape the family already uses for its prose and git rules. The shared files are a
parent POM taken by path, the PMD ruleset, the architecture rules as one test class, the diagram
script, the workflow and a service check that reads a service against the list of what every
service must have. Both services adopt it and close the gaps the comparison found: the engine
moves its packages under `io.guestgraph.engine` and answers a health check; the connector moves
its endpoints under `api`, serves its API document, caps request bodies, carries a diagram with
a drift check and a local profile. No table, no contract and no behavior beyond those changes.

## Technical Context

**Language/Version**: Java 25 in both services, unchanged; the scripts in POSIX sh, as the
family's are.

**Primary Dependencies**: Spring Boot 4 with the actuator now in both services; Maven's parent by
`relativePath`; PMD, Spotless, ArchUnit as before; mermerd for the diagram, as the engine has.

**Storage**: none new; no migration in either service.

**Testing**: the shared repository tests its two scripts against fixtures; each service's suite
proves its items with new integration tests; ArchUnit runs from the vendored class.

**Target Platform**: Linux server for the services; the scripts run on macOS and Linux.

**Project Type**: a shared-files repository, plus changes in two web services.

**Performance Goals**: none; the checks add seconds to a CI run.

**Constraints**: no publish step anywhere (research R1); every service keeps one Maven module;
no contract line changes (FR-010); the package moves are single commits verified by the suites
(R5).

**Scale/Scope**: 1 new repository with the list, 8 shared files of one stack and a scaffold script; in the engine 165
Java files renamed, 1 dependency, 1 test, the parent adopted; in the connector 15 files moved,
4 items added with their tests, the parent adopted; 1 conventions release; 3 agent files.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
*Source: `.specify/memory/constitution.md` v1.0.0*

**Initial evaluation — PASS.** **Post-design re-evaluation — PASS.**

- [x] **Tenant isolation (I)**: unchanged; the vendored architecture rule keeps the scope
      parameter on every repository method, `tenantId` in the engine.
- [x] **Immutable source records (II)**: no data path changes.
- [x] **No silent data loss (III)**: the connector's new size cap refuses with a problem detail,
      as the engine's does; nothing parseable is dropped.
- [x] **Explainable & reversible resolution (IV)**: untouched.
- [x] **API-first (V)**: the health endpoint and the API document sit outside `/api/`, as the
      engine's document already does, and carry no tenant data; every existing contract is
      unchanged.
- [x] **TDD on the resolution engine (VI)**: no engine logic changes; the package move is proven
      by the existing suite; new endpoints get integration tests first.
- [x] **Stack & shape**: unchanged; the parent POM is the same stack named once. The shared
      repository is not a service and adds no module to either.
- [x] **Open-core boundary**: nothing commercial.
- [x] **GDPR readiness**: untouched.

## Project Structure

### Documentation (this feature)

```text
specs/007-service-conventions/
├── plan.md              # This file
├── spec.md
├── research.md          # R1..R8
├── data-model.md        # the shared repository, what a service holds, the list, the rules
├── quickstart.md
├── contracts/
│   └── service-conventions.md   # the pin, the sync, the check, the workflow, the scaffold
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — created by /speckit-tasks, not here
```

### Source Code

```text
guestgraph/service-conventions/                  # NEW repository (R1, R2)
├── service/
│   ├── pom.xml                                  # the parent, taken by relativePath
│   ├── pmd-ruleset.xml
│   ├── ServiceRulesTest.java                    # default package, reads service-conventions.json
│   ├── regen-er
│   ├── verify.yml                               # jobs verify, er-drift, service-conventions
│   ├── service-conventions-check                            # the list (R3)
│   ├── service-conventions-sync                             # vendoring and the sync check
│   └── AGENTS.md                                # the service block
├── new-service                                  # scaffold, per stack (R7)
├── tests/                                       # fixtures and a runner (R8)
├── README.md, AGENTS.md, CLAUDE.md, conventions/, conventions.json

guestgraph/engine/
├── service-conventions.json                                 # the pin
├── service/                                     # vendored
├── .github/workflows/verify.yml                 # vendored (replaces ci.yml)
├── src/test/java/ServiceRulesTest.java          # vendored (replaces architecture/PersistenceRulesTest)
├── pom.xml                                      # names the parent; actuator added
├── src/main/java/io/guestgraph/engine/**        # moved from io/guestgraph/** (R5); auth folded into api
└── src/main/resources/application.yaml          # management.endpoints.web.exposure.include: health

guestgraph/connector-apaleo/
├── service-conventions.json, service/, .github/workflows/verify.yml, src/test/java/ServiceRulesTest.java
├── api/connector-api.yaml, api.json             # the pinned copy of the engine-held contract (R4)
├── docs/er-schema.mmd                           # generated (R6)
├── pom.xml                                      # names the parent; bundles api/*.yaml
├── src/main/java/io/guestgraph/connector/apaleo/api/
│   ├── ops/**, events/**                        # moved (R5)
│   ├── ApiDocsController.java                   # /api-docs (R4)
│   └── RequestSizeLimitFilter.java              # 413 as a problem detail (R6)
├── src/main/resources/application.yaml          # local profile document; max-request-bytes
└── config/connections-local.yaml                # placeholders, no secret (R6)

robertblust/conventions                          # a release adding the repository to REPOSITORIES.md (R7)
```

**Structure Decision**: One new repository holding files, no new module in either service. The
services change where their packages sit and gain the items the list names; everything shared
arrives by the sync and is never edited in place.

## Design Decisions Carried From Phase 0

| # | Decision | Where |
|---|---|---|
| R1 | A guestgraph repository vendored at a pin, the `conventions/` shape; no publish step | [research.md](research.md) |
| R2 | The shared files: parent POM by path, ruleset, one ArchUnit class, diagram script, workflow, two scripts, agent block | [research.md](research.md), [data-model.md](data-model.md) |
| R3 | The service check reads files; behavior is each suite's to prove | [research.md](research.md), [contracts/service-conventions.md](contracts/service-conventions.md) |
| R4 | The connector serves a pinned copy of its engine-held contract | [research.md](research.md) |
| R5 | Two package moves, one commit each, first in each service | [research.md](research.md) |
| R6 | The engine's health; the connector's cap, document, diagram, local profile | [research.md](research.md) |
| R7 | Created from the engine's files, released, added to the family; engine first, then the connector; a scaffold script | [research.md](research.md) |
| R8 | Fixture tests for the scripts, item tests in the suites, a quickstart walk | [research.md](research.md), [quickstart.md](quickstart.md) |
| R9 | One list at the root, one directory per stack, `spring` first; the pin names the stack | [research.md](research.md), [data-model.md](data-model.md) |

## Complexity Tracking

No Constitution Check violations — the table is intentionally empty.

Two choices worth naming. **A parent POM by `relativePath`** is unusual outside multi-module
builds; it is used here because it is the one Maven mechanism that shares versions and plugin
configuration without a repository to publish to, and the sync check holds the file it reads.
**An ArchUnit test in the default package** is unusual too; it is what lets one vendored file run
in every service without a package path to match, reading the root from the pin.

## Follow-ons Not In This Slice

- Shared runtime code, the size filter and the API document controller among it, needs a library
  and so a publish step; until the family has one, each service carries its own few lines and the
  check reads that they are there.
- A version of the check that starts the service and polls `/actuator/health` and `/api-docs`,
  should a service's own suite ever stop proving them.
- The engine's `ci.yml` job id `er-drift` moves into the shared workflow unchanged, so its ruleset
  keeps requiring the same ids; a service whose ruleset predates the slice adds `service-conventions`.
