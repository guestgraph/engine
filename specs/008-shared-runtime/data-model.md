# Data Model: Shared Runtime Code

**Feature**: `008-shared-runtime` | **Date**: 2026-09-11 | Migration: none

No table changes in either service. The model is the shape of a problem, the shared package and
what a service keeps of its own.

---

## A problem

What a guestgraph service answers when it refuses, as `application/problem+json`:

| Member | Value | Rule |
|---|---|---|
| `type` | `https://guestgraph.io/problems/#<slug>` | one of the family's slugs; resolves to the page's section |
| `title` | a short noun phrase | fixed per slug |
| `status` | the HTTP status | the same as the response's |
| `detail` | one sentence for this occurrence | never a credential, never a person's data, never an unforeseen exception's message |
| `instance` | the request path | as Spring sets it |
| named members | what a specific problem adds | the retired-guest problem's `guestId`, `resolutionStatus`, `currentGuestIds` |

The slugs and their statuses:

| Slug | Status | Title | Answered by |
|---|---|---|---|
| `invalid-request` | 400 | Invalid request | both |
| `unauthorized` | 401 | Unauthorized | both, from a filter |
| `not-found` | 404 | Resource not found | both |
| `conflict` | 409 | Conflict | engine |
| `run-in-progress` | 409 | A run is in progress | connector |
| `review-already-decided` | 409 | Conflict | engine |
| `guest-retired` | 410 | Guest id retired | engine, with three members |
| `payload-too-large` | 413 | Payload too large | both, from a filter |
| `invalid-unmerge` | 400 | Invalid unmerge | engine |
| `internal-error` | 500 | Internal server error | both, from the advice |

Problems the framework produces itself, validation and unparseable bodies and unknown paths,
keep the framework's shape, which is the same members without the family's `type`.

## The shared package

`io.guestgraph.service`, in the shared repository under `spring/runtime/`, vendored into a
service's `src/main/java/io/guestgraph/service/`:

| Class | Configuration it reads |
|---|---|
| `Problems` | none |
| `ServiceException` | none |
| `ServiceExceptionHandler` | none |
| `RequestSizeLimitFilter` | `service.max-request-bytes`, default 1 MiB |
| `ApiDocsController` | none; `classpath:api/openapi.yaml` |
| `BearerTokenFilter` | `service.bearer.token`, `service.bearer.open-paths`; absent token, absent filter |

| `ServiceDefaults` | none; loads `service-defaults.yaml` beneath everything |

Two resources travel with the package: `service-defaults.yaml`, vendored into
`src/main/resources/`, and `META-INF/spring.factories`, which names the post-processor.

The package also carries a `pom.xml` of its own in the shared repository, naming the parent, so
the sources compile and their unit tests run where they are written; the sync copies the
classes and not the pom.

## What a service keeps

| Service | Keeps | Extends the base with |
|---|---|---|
| engine | `ApiKeyFilter`, refusing through `Problems` | `NotFoundException`, `ConflictException`, `BadRequestException`, `InvalidUnmergeException`, `ReviewNotFoundException`, `ReviewAlreadyDecidedException`, `InvalidActorClaimException`, `RetiredGuestException` with its members |
| connector | its endpoints, throwing the base's subclasses | `NoSuchConnectionException`, `NoSuchRunException`, `RunInProgressException`, `NotAnEventException` |

## Rules

1. **One writer.** A problem leaves a service through `Problems`, whether a filter, the advice or
   the framework's handler for a `ServiceException` writes it.
2. **The exception carries its answer.** A service's own exception extends `ServiceException`
   with its slug, status and title; no handler is written for it.
3. **Nothing foreseen is 500.** The advice answers `internal-error` only for what nothing else
   caught, and logs it in full on the server.
4. **A slug has a page.** No type is answered that the problems page does not explain.
5. **Defaults beneath, the service above.** A shared setting lives in `service-defaults.yaml`;
   a service's own file names its schema and what is its own, and restates nothing shared.
6. **Shared code is edited once.** A file under `io/guestgraph/service/` in a service is never
   edited there; the sync check names it when it differs.
