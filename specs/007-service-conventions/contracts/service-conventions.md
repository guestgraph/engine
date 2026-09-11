# Contract: the pin, the sync and the check

**Feature**: `007-service-conventions` | **Date**: 2026-09-11

This slice exposes no HTTP surface of its own. Its contract is what a service commits and what
two scripts answer, so that a service written by anyone passes or fails for the same reasons.

## `service-conventions.json`

One line in the service's root, the pin:

```json
{ "repo": "guestgraph/service-conventions", "tag": "v0.1.0", "stack": "spring",
  "root": "io.guestgraph.engine", "scope": "tenantId", "schema": "engine" }
```

| Field | Meaning | Rule |
|---|---|---|
| `repo` | the shared repository | fixed |
| `tag` | the release vendored | a tag of that repository; moved on purpose |
| `stack` | the directory of the shared repository vendored | `spring` today; a later stack is its own directory |
| `root` | the service's package root | `io.guestgraph.` then the repository name, hyphens as dots |
| `scope` | the parameter every repository method carries | `tenantId` or `connectionId` |
| `schema` | the database schema the diagram is drawn from | the default of `DATABASE_SCHEMA` |

## `api.json`

Present only when a service serves an API document owned by another repository:

```json
{ "repo": "guestgraph/engine", "commit": "<sha>",
  "files": { "api/connector-api.yaml": "specs/005-apaleo-connector/contracts/connector-api.yaml" } }
```

Each entry maps a file in the service to its path in the owning repository at that commit; the
service check fetches the owner's file and compares.

## `sh service-conventions/service-conventions-sync sync | check`

- `sync` writes `service-conventions/*` from the pinned release's `<stack>/`, `.github/workflows/verify.yml`, `src/test/java/ServiceRulesTest.java`
  and the service block of `AGENTS.md` from the pinned release. It never touches anything else.
- `check` exits 0 when every vendored file equals the pinned release's, and 1 with one line
  `✗ service-conventions: <path> differs from <tag>` per file that does not, or does not exist.
- The source is `https://raw.githubusercontent.com/<repo>/<tag>`; `SERVICE_CONVENTIONS_SOURCE` overrides it
  with another base URL or a local directory, which is how the tests run offline.
- Needs sh, curl, awk, sed, and shasum or sha256sum.

## `sh service-conventions/service-conventions-check`

Reads the service from its root and answers one line per finding:

```
✗ service-conventions: <item>: <what is missing, with the file it was looked for in>
```

Exit 0 with no output when nothing is missing; 1 otherwise. The items, in the order checked:

| Item | Passes when |
|---|---|
| `parent` | `pom.xml` names `io.guestgraph:service-parent` at the pinned `tag`'s version with `relativePath` `service-conventions/pom.xml` |
| `root` | exactly one directory under `src/main/java/io/guestgraph/`, and it is `root`'s path |
| `api` | `<root>/api/` exists; every class annotated `@RestController`, `@Controller` or extending a servlet filter is under it |
| `api-document` | `api/` holds at least one `.yaml`; `pom.xml` bundles `api/*.yaml` into `api-contracts`; a class under `<root>/api/` maps `/api-docs`; every entry of `api.json`, when present, equals the owner's file at the pinned commit |
| `health` | `management.endpoints.web.exposure.include` is `health` |
| `problem-details` | `spring.mvc.problemdetails.enabled` is `true` |
| `request-cap` | a property ending in `.max-request-bytes` with a default, and a class under `<root>/api/` reading it |
| `schema` | `spring.datasource.hikari.schema` and `spring.flyway.default-schema` both read `DATABASE_SCHEMA` with the default `schema` |
| `diagram` | `docs/er-schema.mmd` exists and opens with the generated header |
| `local-profile` | a document with `spring.config.activate.on-profile: local` in `application.yaml`, or `application-local.yaml` |
| `readme` | `README.md` carries `create role` and `create schema`, `./mvnw verify` and `service-conventions-check` |
| `agents` | `AGENTS.md` carries the service block after the family's block |

`SERVICE_CONVENTIONS_ROOT` overrides the directory read, which is how the tests point it at a fixture.

## Workflow

`service-conventions/verify.yml`, written into `.github/workflows/verify.yml`, carries three jobs, and every
service's ruleset requires them by these ids beside `conventions / conventions`:

| Job | Runs |
|---|---|
| `verify` | `./mvnw -B verify` |
| `er-drift` | `sh service-conventions/regen-er`, then fails when `docs/er-schema.mmd` changed |
| `service-conventions` | `sh service-conventions/service-conventions-sync check`, then `sh service-conventions/service-conventions-check` |

## `new-service <stack> <name>`

In the shared repository: writes a skeleton for `guestgraph/<name>` of that stack into a directory: the pin
with `root` derived from the name, the vendored files, a `pom.xml` naming the parent, one
`api` package with the API document controller, the size filter and a health-exposing
`application.yaml`, a first migration, a README with the required sections and the agent file.
The output passes both checks and `./mvnw verify` on its first run.
