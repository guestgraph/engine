# Phase 0 Research: Service Conventions

**Feature**: `007-service-conventions` | **Date**: 2026-09-11

The spec has no clarification markers. This document settles how code-level rules are shared
without a publish step, what the service check reads, and how the two package moves are made. It
starts from the state of the two services on Sep 11, 2026: the same stack and the same guardrails,
copied by hand, with the differences the spec lists.

---

## R1 — Where the rules live, and how a service takes them

**Decision**: A repository `guestgraph/service-conventions`, vendored into each service's
`service-conventions/` directory at a release named by `service-conventions.json` in the service's root, with a
`service-conventions-sync` script that writes the copy from the pin and checks it against the pin, the same
shape as `conventions/` and `conventions.json`. The family's prose and git rules stay in
robertblust/conventions and the new repository vendors them like every member.

**Rationale**: The family has no publish step anywhere, and the tag is the release; a Maven
repository for a shared artifact would be a second mechanism. Vendoring at a pin already works
for prose, its sync check is a sixty-line shell script, and a member has one way of taking shared
files. Java is a guestgraph concern, so the repository is guestgraph's, not the family's.

**Alternative considered**: *a published parent POM and a test library on a Maven repository.*
The cleaner Maven shape, and rejected for now because the family publishes nothing and a GitHub
package registry would need credentials in every build. R2 gets most of the parent's value
without publishing.

---

## R2 — What the shared files are

**Decision**: The rule set is these files under `spring/` in the shared repository, vendored into
a service's `service-conventions/` (R9):

| File | What it holds | Today |
|---|---|---|
| `pom.xml` | the parent POM every service's `pom.xml` names by `relativePath`: the Spring Boot parent and its version, the Java release, the versions of Testcontainers, ArchUnit, WireMock and the other shared dependencies in `dependencyManagement`, and the compiler, surefire, PMD and Spotless plugins with their configuration | in each service's own `pom.xml`, the two differ in nothing but the connector's WireMock |
| `pmd-ruleset.xml` | the source-level rules | in each service's `config/`, differing in the ruleset's name only |
| `ServiceRulesTest.java` | the architecture rules every service holds, as one test class in the default package, reading the service's root package and its scope parameter from `service-conventions.json` | `PersistenceRulesTest` in each service, the same six rules with two names and one parameter changed |
| `regen-er` | the diagram regeneration, reading the schema name from `service-conventions.json` | the engine's `scripts/regen-er.sh`, with the schema written in |
| `verify.yml` | the workflow with the jobs `verify`, `er-drift` and `service-conventions`, copied by the sync into `.github/workflows/` | the engine's `ci.yml` and the connector's `verify.yml`, the second without `er-drift` |
| `service-conventions-check` | the reading of the service against the list (R3) | none |
| `service-conventions-sync` | the vendoring and the sync check | none |
| `AGENTS.md` | the block a service's agent file opens with after the family's, naming the pin, the two checks and where a shared file is edited | none, each agent file says it in its own words |

Maven reads a parent by `relativePath`, so `service-conventions/pom.xml` is a parent without a repository:
each service's `pom.xml` names `io.guestgraph:service-parent` with the pinned version and
`<relativePath>service-conventions/pom.xml</relativePath>`, keeps its own dependencies and its own
`artifactId`, and inherits everything else. A test class in the default package cannot be
imported but runs like any other; it imports the service's classes by the root package name it
reads from `service-conventions.json`, so one file serves every service. Spotless formats it in place and the
formatted text equals the source, which the sync check compares.

**Rationale**: A parent POM by path is the one Maven mechanism that shares plugin configuration
and versions without publishing, and it makes the spec's version edge case disappear: the
versions are the parent's, so a service cannot differ in them without changing a vendored file,
which the sync check names. The ArchUnit rules as one vendored source file are the nearest thing
to a shared test library the family can have; the two rule names that differ today, tenant and
connection scoping, are one rule with a parameter.

**What is not shared**: code a service runs. The request size filter, the API document
controller and the health endpoint are each a few lines that every service carries in its own
`api` package, and the service check reads that they are there (R3). Shared runtime code needs a
library, which needs a publish step; that is the limit of this slice, recorded as a follow-on.

---

## R3 — What the service check reads

**Decision**: `service-conventions-check` is a shell script like `conventions-check`, run from the service's
root, that reads files and answers one ✗ line per missing item, exit 1 when any is missing:

| Item | What is read |
|---|---|
| parent | `pom.xml` names `io.guestgraph:service-parent` at the pinned version by `relativePath` |
| root package | `src/main/java/` holds exactly one root under `io/guestgraph/`, and it is the one `service-conventions.json` names |
| api package | `<root>/api/` exists and holds the classes annotated as controllers and filters; none sits outside it |
| API document | `src/main/resources/api/` holds at least one OpenAPI file and `sources.json`; every file is listed there and equals its source, a local path, a repository at a pinned commit, or `own`; a class under `api` maps `/api-docs` |
| health | `management.endpoints.web.exposure.include` names `health` and nothing else |
| problem details | `spring.mvc.problemdetails.enabled: true` |
| request cap | a property `<service>.max-request-bytes` with a default, and a filter under `api` reading it |
| one schema, one role | `spring.datasource.hikari.schema` and `spring.flyway.default-schema` read one variable `DATABASE_SCHEMA` |
| diagram | `docs/er-schema.mmd` exists and `service-conventions/regen-er` reproduces it |
| local profile | a profile document `local` in `application.yaml`, or `application-local.yaml`, exists |
| README | carries the headings or sentences the list names: how to run, the deployment paragraph with `create role` and `create schema`, the checks |
| agent file | `AGENTS.md` carries the service block after the family's block |

**Rationale**: A file reader is what a shell script can be and what the family already trusts for
prose; what a service *does* with these files is each service's own suite's to prove, and both
suites already cover the endpoints they have. A check that started the service to poll it would
need a database in the `service-conventions` job and would test Spring, not the service.

**Alternative considered**: *a Maven plugin or an ArchUnit-only check.* ArchUnit reads classes,
not `application.yaml` or the README; a plugin would need publishing. The shell script reads
everything and runs anywhere.

---

## R4 — The connector's API document

**Decision**: What a service serves at `/api-docs` is what lies under `src/main/resources/api/`,
and nothing else: no bundling block in the POM, no directory at the repository root. Beside the
documents, `sources.json` names where each comes from: a path in the same repository, a
repository at a pinned commit as `owner/repo@commit:path`, or `own` for a document written here.
The engine keeps its four contracts under `specs/*/contracts/` as the frozen records of their
slices and serves copies of them, one file per slice; the connector serves a copy of the engine's
`specs/005-apaleo-connector/contracts/connector-api.yaml` at a pinned engine commit. The
controller merges what is under `api/` at runtime into one document, as the engine's does today.
The service check holds every served file equal to its source.

**Rationale**: A reader who opens the source tree finds the API where the configuration is, in
one place in every service, and the file beside it says whether what they read is the original
or a copy and of what. The originals stay where they are written, since a spec's contract is a
frozen record and the connector's is owned by the engine; a copy held against its source is how
the sites take the design system and how the diagram is held against the migrations, so nothing
new is invented and a stale copy fails a check rather than drifting. A single merged document
would read best for a consumer, but generating it needs a YAML merge the family does not ship;
the runtime merge exists in Java already.

---

## R5 — The two package moves

**Decision**: The engine moves every source and test file from `io.guestgraph` to
`io.guestgraph.engine` by `git mv` of the two source trees and a substitution of the package and
import lines; the nine occurrences outside those lines, eight in the ArchUnit test and one in a
query string, are edited by hand. The Maven `groupId` stays `io.guestgraph`. The connector moves
`ops` and `events` to `api.ops` and `api.events` the same way, fifteen files. Both moves are one
commit each, formatted by Spotless, verified by the full suite, and land before anything else in
their service so the later changes are written against the new names.

**Rationale**: Mechanical and reversible, and the suite proves it: no table, no contract and no
message changes. Before the engine's first release, as the schema move was.

---

## R6 — The engine's health endpoint and the connector's four items

**Decision**: The engine adds the actuator with health only, exposed without a credential, as the
connector has it. The connector adds: a request size filter under `api` with a property
`connector.max-request-bytes`, answering 413 as a problem detail, on every path; the API document
of R4; `docs/er-schema.mmd` regenerated by the shared script with `er-drift` in its workflow; the
engine's four contracts copied under its resources with their sources; and a
`local` profile that points at a committed sample connections file with placeholder values and
`sync-on-boot` off, so the connector starts against the engine's `local` profile without a file
written by hand, and fails only where a real Apaleo credential would be needed.

**Rationale**: Each closes one line of the spec's FR-005 in the smallest form the engine already
has for it. The sample connections file carries no secret, since a placeholder is not a
credential, and the README says it must be replaced before any Apaleo call is expected to work.

---

## R7 — The family's bookkeeping and the order of work

**Decision**: The new repository is created from the engine's files, released as `v0.1.0`, and
added to `REPOSITORIES.md` by a conventions release, in the re-sync order between the models and
the engine, since both services take it. The engine adopts it first, then the connector, each in
its own pull request per step: the package move, the parent and the vendored files, the items,
the agent file. A scaffold script `new-service` in the shared repository writes a service's
skeleton from the rule set, and its output is the throwaway service of user story 3.

**Rationale**: The order the family already prescribes, and one pull request per step keeps each
diff readable: a package move touches every file and is best reviewed alone.

---

## R8 — What proves it

**Decision**: The shared repository carries its own tests: the sync check and the service check
run against fixtures, a passing service and one with each item removed, the way the conventions
repository tests its scripts. Each service's own suite proves its items: the engine's health test,
the connector's API document, size cap and local profile tests. The quickstart walks the four
success criteria by hand once: a rule change reaching both services by one pin move, a
one-character drift failing, the check listing nothing missing, and a scaffolded service passing
on its first run.

**Rationale**: SC-001 to SC-004 are about the mechanism and are cheap to walk; SC-005, no contract
line changed and every test unchanged in what it asserts, is a diff and a green suite.

---

## R9 — One repository, one directory per stack

**Decision**: What every guestgraph service has is stack-independent and is written once, in
`SERVICE.md` at the shared repository's root: an API document served, health, problem details, a
request cap, one schema and one role, a diagram held against drift, a local profile, the README
sections, the agent block. What makes it true is a stack's own: `spring/` holds the files of R2
and the check for Java 25 with Spring Boot 4. The pin names its stack, `"stack": "spring"`, the
sync vendors that directory, and the check reads the items the way that stack shows them. A
second stack, whenever a service is written in one, adds a directory beside `spring/` with its
own files and its own reading of the same list, and no second repository or mechanism.

**Rationale**: The list is the family's idea of a service; the files are how one stack meets it.
Keeping them apart means a new stack starts from the list rather than from Spring, and the
services of one stack keep vendoring one directory. Nothing is built for the second stack now.

