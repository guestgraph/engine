# Phase 0 Research: Shared Runtime Code

**Feature**: `008-shared-runtime` | **Date**: 2026-09-11

The spec has no clarification markers. This document settles how code is shared without a
library, what the shared classes are and how they meet the framework, where a problem type
resolves, and what the check reads. It starts from the state of Sep 11, 2026: the engine with a
controller advice, typed exceptions and two filters writing problems by hand; the connector with
Spring's status exception, one handler and two filters writing JSON strings.

---

## R1 — Shared as source, under one package

**Decision**: The shared repository's `spring/runtime/` holds Java sources in the package
`io.guestgraph.service`; the sync copies them into a service's
`src/main/java/io/guestgraph/service/`, and the sync check holds them to the release as it holds
every vendored file. The package name is the one a published library would carry, so the copies
can be replaced by a dependency later without a rename in any service. A change to the runtime
that asks a service to do more than re-sync, this slice's adoption among them, is what the
family's rules call a major; the repository stays below 1.0 until the owner calls it stable, and
under 0.x that step is the minor one: `v0.6.0`.

**Rationale**: The family publishes nothing and has no Maven repository; a GitHub package
registry would put a credential into every build. The sites take the design system as fenced
source for the same reason, and slice 7 already vendors a test class and a generator this way.
Source under one package is the nearest thing to a library the family can have today, and the
sync check is what keeps a copy honest.

**Alternative considered**: *a jar committed into each service and referenced by system path.*
Opaque to review, a binary in git, and no better held than source; rejected.

---

## R2 — The shared classes and how they meet Spring

**Decision**: Six classes, and what each replaces:

| Class | What it does | Replaces |
|---|---|---|
| `Problems` | builds a `ProblemDetail` with the family's type URI, a title, a status and a detail, and writes one to a servlet response as `application/problem+json`; the one place the shape is written | the engine's two `problem(...)` helpers, the connector's two JSON strings |
| `ServiceException` | extends Spring's `ErrorResponseException`, so the framework's own problem-details handler answers it with the status, type and title it carries; a subclass adds named members through `withProperty` | the engine's eight exceptions' handlers, the connector's status exceptions |
| `ServiceExceptionHandler` | a controller advice at the lowest precedence with one handler: any exception nobody foresaw is logged in full and answered as the family's `internal-error` problem with a detail that says nothing | the engine's `ApiExceptionHandler`, which had this and the eight handlers |
| `RequestSizeLimitFilter` | the engine's filter as it stands, chunked bodies included, on every path, reading `service.max-request-bytes` | the engine's on `/api/` only, the connector's header-only one |
| `ApiDocsController` | serves `classpath:api/openapi.yaml` at `/api-docs` | the same class in both services |
| `BearerTokenFilter` | guards every path except the ones `service.bearer.open-paths` names, with the token `service.bearer.token` holds, compared in constant time, refusing through `Problems`; present only when the token property is set | the connector's `OpsTokenFilter` |

Spring's problem-details support, enabled in both services, registers a handler for
`ErrorResponseException`, so a `ServiceException` needs no handler of its own: the advice keeps
only the catch-all. The engine's API key filter stays the engine's, since it resolves a tenant
and an actor, and refuses through `Problems`.

**Rationale**: Every service-specific exception carrying its own answer is what removes the
per-service advice; Spring already knows the type. One writer for filters and advice is what
makes the shape one class. The engine's size filter is the better of the two, since a chunked
body bypasses a header-only check, and every path is the safer scope. The bearer guard is the
connector's need today and the next service's tomorrow, so it is shared and off by default.

---

## R3 — Problem types and their pages

**Decision**: The type base stays `https://guestgraph.io/problems/`, and the types are
fragments of one page: `https://guestgraph.io/problems/#not-found`. The page lives in the
family's site repository as `/problems/index.html`, one section per type with what the problem
means and what a caller does, written by the writer role. The slugs the engine uses today are
kept: `not-found`, `conflict`, `invalid-request`, `invalid-unmerge`, `review-already-decided`,
`guest-retired`, `unauthorized`, `internal-error`, `payload-too-large`; the connector adds
`run-in-progress`. Naming the types in a contract goes forward: the frozen contracts' problem
responses are amended in the roadmap notes to say the types they answer, and a new slice names
them in its contract's problem responses.

**Rationale**: The site is static directories with one page each, and each page carries the
site's checks and images; ten directories for ten problem types would cost more than they say.
One page with anchors reads as a glossary, which is what a problem page is. The fragment form
changes the engine's URIs before its first release, which no test and no contract pins.

**Alternative considered**: *one directory per type, keeping the path form.* Ten pages to keep
for ten sentences; rejected.

---

## R4 — What the check reads

**Decision**: `service-conventions-check` gains one item, `error-shape`, read from the service's
own sources, the shared package excluded:

| Read | Fails when |
|---|---|
| `ResponseStatusException` | thrown or imported anywhere in the service's own code |
| `ProblemDetail.forStatus` | called outside the shared package |
| `application/problem+json` | written as a literal outside the shared package |
| `@ControllerAdvice`, `@RestControllerAdvice` | declared outside the shared package |
| `getWriter().write` in a filter | outside the shared package |

Two existing items learn the shared package: `root` accepts `io/guestgraph/service/` beside the
root, and `api` does not expect the shared controllers and filters under `<root>.api`.

**Rationale**: Each line is a way the shape was broken before the slice, and each is a grep,
which is what the check can be. A service-specific advice for the framework's own errors is
the one legitimate thing the last rule forbids, and none exists; if one is ever needed it is a
shared change.

---

## R5 — What each service changes

**Decision**: The engine: its eight exceptions extend `ServiceException` with their status, type
and title, `RetiredGuestException` adding its three members; `ApiExceptionHandler`,
`RequestSizeLimitFilter` and `ApiDocsController` go; `ApiKeyFilter` refuses through `Problems`;
`guestgraph.max-request-bytes` becomes `service.max-request-bytes`. The connector: three
exceptions, `NoSuchConnectionException` and `NoSuchRunException` as `not-found`,
`RunInProgressException` as `run-in-progress`, and the event endpoint's inline 400 becomes
`NotAnEventException` as `invalid-request`; `OpsTokenFilter`, `RequestSizeLimitFilter` and
`ApiDocsController` go, the bearer guard configured with the ops token and the three open paths;
`connector.max-request-bytes` becomes `service.max-request-bytes`. Every existing test passes
unchanged in what it asserts; the status suite's assertion on the 401 body reads the same shape.

**Rationale**: The smallest change in each service that leaves it with nothing of its own that
the shared package provides, which is what FR-006 and the check ask.

---

## R6 — What proves it

**Decision**: The shared repository tests the runtime where it can without a context: `Problems`
and `ServiceException` as plain unit tests run by a small Maven module under `spring/runtime/`,
which also holds the classes' own `pom.xml` naming the parent, so the shared sources compile
and format in the repository they come from; the check's new item on fixtures. Each service
gains one integration test, `ErrorShapeTest`, that provokes a refusal from each origin, a
filter, a controller, the advice and the framework, and asserts the shape and the type base
(SC-001). The quickstart walks a shared change reaching both services by one pin move.

**Rationale**: The classes are small and the shape is what matters; a test per origin per
service is what a caller would write.
