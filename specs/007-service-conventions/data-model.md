# Data Model: Service Conventions

**Feature**: `007-service-conventions` | **Date**: 2026-09-11 | Migration: none

No table changes in either service. The model is files: what the shared repository holds, what a
service vendors, the pin that binds them, and the list the check reads.

---

## The shared repository

`guestgraph/service-conventions`, a member of the family: it vendors `conventions/` like every
member and its own Markdown is held by the prose check.

| Path | Purpose |
|---|---|
| `SERVICE.md` | the list every service meets, whatever its stack (research R9) |
| `spring/pom.xml` | the parent POM of the Spring stack (research R2) |
| `spring/pmd-ruleset.xml` | the source-level rules |
| `spring/ServiceRulesTest.java` | the architecture rules, one class, default package |
| `spring/regen-er` | diagram regeneration, schema from the pin |
| `spring/verify.yml` | the workflow with `verify`, `er-drift` and `service-conventions` |
| `spring/service-conventions-check` | the list, read the way the Spring stack shows it (research R3) |
| `spring/service-conventions-sync` | vendoring and the sync check |
| `spring/AGENTS.md` | the service block for a service's agent file |
| `new-service` | writes a service skeleton from a stack's files |
| `tests/` | fixtures and a runner for the two scripts |
| `README.md`, `AGENTS.md`, `conventions/`, `conventions.json` | as every member has them |

Releases are tags with notes in the prose register; a change to any file under `service-conventions/` is at
least a minor release, a change that asks a service to do more than re-sync is a major.

## What a service holds

| Path in the service | Origin | Rule |
|---|---|---|
| `service-conventions.json` | the service's own | the pin: `{"repo": "guestgraph/service-conventions", "tag": "v0.1.0", "stack": "spring", "root": "io.guestgraph.engine", "scope": "tenantId", "schema": "engine"}` |
| `service-conventions/*` | vendored | equals the pinned release's `<stack>/*`; edited only in the shared repository |
| `.github/workflows/verify.yml` | vendored, written by the sync | equals `service-conventions/verify.yml` |
| `AGENTS.md`, service block | vendored, written by the sync | after the family's block, before the service's own text |
| `src/test/java/ServiceRulesTest.java` | vendored, written by the sync | equals `service-conventions/ServiceRulesTest.java` |
| `pom.xml` | the service's own | names the parent by `relativePath` at the pinned version |
| `src/main/resources/api/*.yaml`, `sources.json` | copies, or the service's own | what `/api-docs` serves and nothing else; `sources.json` names each file's source: a path in this repository, `owner/repo@commit:path`, or `own` |
| `docs/er-schema.mmd` | generated | reproduced by `service-conventions/regen-er`, held by `er-drift` |
| `application.yaml` | the service's own | the properties the list names |
| `README.md` | the service's own | the sections the list names |

The pin's fields: `repo` and `tag` name the release, as `conventions.json` does; `stack` names the
directory vendored, `spring` for both services today; `root` is the
service's package root, `io.guestgraph.` followed by the repository's name with hyphens as dots;
`scope` is the parameter every repository method carries, `tenantId` in the engine, `connectionId`
in the connector; `schema` is the database schema the diagram is drawn from, the same value
`DATABASE_SCHEMA` defaults to.

## The list

The items the service check reads, and where each service stands on Sep 11, 2026:

| Item | Engine | Connector | Closed by |
|---|---|---|---|
| parent POM by path | own POM | own POM | both adopt the parent |
| root package `io.guestgraph.<service>` | `io.guestgraph` | yes | the engine's move |
| endpoints and filters under `api` | `api`, `auth` | `ops`, `events` | the engine folds `auth` into `api`; the connector moves both |
| API document at `/api-docs` from `src/main/resources/api/` | from a POM bundling of `specs/` | none | research R4, both services |
| health without a credential | none | yes | the engine adds the actuator |
| problem details | yes | yes | nothing |
| request size cap | yes | none | the connector adds the filter |
| one schema, one role | yes | yes | nothing |
| diagram with drift check | yes | none | the connector adds both |
| local profile | yes | none | the connector adds one |
| README sections | yes | yes | nothing |
| agent file block | own words | own words | the sync writes it |

## Rules

1. **One origin.** A file under `service-conventions/` in a service is never edited there; the sync check
   compares it with the pinned release and names it when it differs.
2. **Beside, not inside.** A service's own rules, a PMD rule only it needs, an ArchUnit rule of its
   own, sit in its own files beside the shared ones.
3. **The pin is intent.** A pin behind the latest release passes the sync check; moving it is a
   commit that says why.
4. **The list grows by release.** An item is added in the shared repository and reaches a service
   by a pin move, which may then fail the service check until the service has the item; that is
   the point.
5. **One list, one directory per stack.** `SERVICE.md` says what a service has; a stack's
   directory says how, and a new stack adds a directory, never a repository.
6. **Behavior is the service's to prove.** The check reads files; each service's suite proves what
   the files promise.
