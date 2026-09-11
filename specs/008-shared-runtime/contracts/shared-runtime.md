# Contract: the shared package, the problem shape and the check

**Feature**: `008-shared-runtime` | **Date**: 2026-09-11

## The problem shape

Every refusal a guestgraph service answers:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{ "type": "https://guestgraph.io/problems/#not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "No guest 3f2b… in this tenant",
  "instance": "/api/v1/guests/3f2b…" }
```

Named members follow when a problem has them, never under the names RFC 9457 owns. The
framework's own problems, validation and unparseable bodies and unknown paths, carry the same
members without the family's `type`.

## `io.guestgraph.service`

| Class | Surface |
|---|---|
| `Problems` | `static ProblemDetail of(HttpStatus status, String slug, String title, String detail)`; `static void write(HttpServletResponse response, ProblemDetail problem)`; `static final String BASE = "https://guestgraph.io/problems/#"` |
| `ServiceException` | `ServiceException(HttpStatus status, String slug, String title, String detail)`; `ServiceException withProperty(String name, Object value)`; extends `org.springframework.web.ErrorResponseException` |
| `ServiceExceptionHandler` | `@RestControllerAdvice @Order(LOWEST_PRECEDENCE)`; one `@ExceptionHandler(Exception.class)` answering `internal-error` after logging at error with the stack trace |
| `RequestSizeLimitFilter` | `@Component @Order(HIGHEST_PRECEDENCE)`; every path; `service.max-request-bytes` with the default `1048576`; a declared length over the cap and an undeclared body that overruns it both answer `payload-too-large` |
| `ApiDocsController` | `GET /api-docs` answering `classpath:api/openapi.yaml` as JSON |
| `BearerTokenFilter` | `@Component @ConditionalOnProperty("service.bearer.token")`; refuses every path with `unauthorized` unless `Authorization: Bearer <token>` matches in constant time, the scheme case-insensitive, or the raw path equals one of `service.bearer.open-paths` or starts with one that ends in `/` |

Configuration, as a service's `application.yaml` names it:

```yaml
service:
  max-request-bytes: ${MAX_REQUEST_BYTES:1048576}
  bearer:                       # only a service with a token-guarded surface
    token: ${CONNECTOR_OPS_TOKEN:}
    open-paths: [/actuator/health, /api-docs, /apaleo/events/]
```

## The sync

`service-conventions-sync` vendors `runtime/*.java` from the stack's directory into
`src/main/java/io/guestgraph/service/`, and `check` names any that differs, as for every other
vendored file.

## `service-conventions-check`

One new item, and two items that learn the shared package:

| Item | Passes when |
|---|---|
| `error-shape` | in the service's own sources, outside `io/guestgraph/service/`: no `ResponseStatusException`; no `ProblemDetail.forStatus`; no `application/problem+json` literal; no `@ControllerAdvice` or `@RestControllerAdvice`; no `getWriter()` in a class that extends a filter |
| `root` | `io/guestgraph/service/` beside the root is not a stray root |
| `api` | controllers and filters under `io/guestgraph/service/` are not expected under `<root>.api` |

Failure lines name the file: `✗ service-conventions: error-shape: <path> throws ResponseStatusException`.

## The problems page

`https://guestgraph.io/problems/`, one section per slug with the anchor of the slug, saying
what the problem means and what a caller does. The slugs in the data model are the page's
sections; a new slug is added to the page before a service answers it.

## `new-service`

The scaffold vendors the shared package through the sync, writes `service.max-request-bytes` and
no `api` classes of its own beyond what a feature adds, and passes the check on its first run.
