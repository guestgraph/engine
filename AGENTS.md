<!-- conventions · v1.10.0 -->
Shared conventions of the robertblust, guestgraph and companygraph organizations live in
`conventions/`, vendored from robertblust/conventions at the release `conventions.json`
names. Read them before writing or committing anything here.

- `conventions/WRITING.md` — how we write: one voice, three registers, English and German.
- `conventions/WORKING.md` — how we work with git and GitHub.
- `conventions/REPOSITORIES.md` — the family: what each repository is and what pins what.
- `conventions/WRITER.md`, `conventions/TRANSLATOR.md`, `conventions/GLOSSARY.md` — the two roles that
  make a text, and the terms they keep.

Everything below this block is this repository's own. `sh conventions/conventions-sync check`
says whether the copy matches the release, `sync` brings it to the release the pin names, and
`sh conventions/conventions-check` holds this repository's own Markdown to `WRITING.md`. Edit
a shared file in robertblust/conventions, never here.
<!-- end conventions -->
<!-- service-conventions · v0.7.0 -->
The code-level rules of every guestgraph service on the Spring stack live in
`service-conventions/`, vendored from guestgraph/service-conventions at the release
`service-conventions.json` names: the parent build every `pom.xml` takes by path, the source rules,
the architecture rules in `src/test/java/ServiceRulesTest.java`, the diagram script, the API
generator `regen-api` that writes the one `openapi.yaml` the service serves from the sources
named beside it, the workflow
in `.github/workflows/verify.yml`, and this block. `sh service-conventions/service-conventions-sync
check` says whether the copy matches the release, `sync` brings it to the release the pin names,
and `sh service-conventions/service-conventions-check` says what of the list the service lacks.
What every service has, whatever its stack, is `SERVICE.md` there. Edit a shared file in
guestgraph/service-conventions, never here.
<!-- end service-conventions -->

# GuestGraph — working conventions

Open-source (Apache-2.0) guest identity graph. Spec-driven with spec-kit; the
constitution at `.specify/memory/constitution.md` is non-negotiable (tenant isolation,
immutable source records, never drop parseable data, explainable/reversible resolution,
API-first RFC 9457, TDD on the resolution engine).

## Build & verify

```bash
./mvnw verify                                        # tests (Testcontainers, needs Docker), the rules, PMD, Spotless
./mvnw spotless:apply                                # fix formatting (google-java-format) — check fails otherwise
sh service-conventions/regen-er                      # after schema changes — CI checks ER-diagram drift
sh service-conventions/service-conventions-check     # what of the list every guestgraph service has this one lacks
sh conventions/conventions-check                     # the prose
```

## Code conventions

- **Imports, not inline FQNs.** Types are referenced by simple name with a proper
  import — never `java.time.LocalDate` inline in code. Enforced by PMD
  (`UnnecessaryFullyQualifiedName`, `service-conventions/pmd-ruleset.xml`) in `verify`. Exception:
  JPQL query strings, where FQNs are required syntax (enum literals, constructor
  expressions) — PMD doesn't look inside strings.
- **Formatting** is google-java-format via Spotless; don't hand-format.
- **Comments** state constraints the code can't show; no narration.
- Mechanical guardrails live in three places, each with its job, and all three come vendored
  from guestgraph/service-conventions at the release `service-conventions.json` names: **Spotless**
  (format) and **PMD** (source-level conventions) from the parent build every service's `pom.xml`
  takes by path, **ArchUnit** from `src/test/java/ServiceRulesTest.java` (`tenantId` on every repo
  method or a justified `@TenantAgnostic`, `@Query`-only repositories, no CrudRepository, no ad-hoc
  EntityManager queries, `JdbcClient` only in the classes the pin's `jdbcClientAllowed` names,
  JPA confined to `persistence`). A rule that every service needs changes there, never here; a
  rule only the engine needs sits beside the shared files.
- **Packages** are `io.guestgraph.engine`, the family's root and the repository's name, with the
  endpoints, filters and error answers under `api`, as in every guestgraph service.

## Architecture in one paragraph

The resolution engine (`resolution` package: `ResolutionEngine`, strategies, gates,
operations) is pure JVM behind the `GraphPort` seam — table-driven scenario tests run it
against `InMemoryGraph`, production wires `PostgresGraph` (JPA + MapStruct, immutable
entities, bulk-update-only mutations). Everything is tenant-scoped; merges are recorded
as append-only `merge_event`s with matcher name + confidence + evidence and are
reversible (unmerge) with steward splits persisted as negative match rules. New matchers
implement `ResolutionStrategy` — do not redesign the engine.

## Non-obvious pitfalls (all bitten before)

- Hibernate's camel-case naming maps trailing single capitals wrong (`recordA` →
  `recorda`): name such columns explicitly with `@Column`.
- Java `UUID.compareTo` (signed longs) disagrees with Postgres uuid ordering: order
  UUID pairs by `toString()` when a DB CHECK depends on it.
- `@Service` beans get no persistence exception translation — only `@Repository` does.
- Spring AOT generates `*__*` classes into `target/classes`; ArchUnit imports must
  filter them (already done in `ServiceRulesTest`).
- Test harness truncates tables in `PostgresIntegrationTest.resetDatabase` — add new
  tables there or every integration test fails on FK truncate errors.
- Until the first release, `V1__core_schema.sql`/`V2__*.sql` may be edited in place;
  local Flyway checksum mismatch → `docker compose down -v`. Additive-only after tagging.
- The engine lives in schema `engine`, and every pooled connection's search path is that
  schema alone. A `psql` session or a script outside the pool sees no tables until it
  qualifies names or runs `set search_path to engine` — `\dt` showing nothing is that, not a
  missing migration.

## Documentation ownership (prevents drift)

Every fact has **one owning file**; everywhere else links to it. The ambiguity about who owns
what is what causes drift, so the map is explicit:

| Fact | Owner |
|---|---|
| Constants, thresholds, algorithms | the code |
| Matching behavior | `docs/matching.md`, sectioned per matcher version |
| One slice's decisions | `specs/NNN-*/` — **frozen at merge** |
| Cross-slice decisions, roadmap, deferred work | `docs/roadmap-notes.md` |
| API surface | `specs/*/contracts/openapi.yaml`, the records; served as the one generated `src/main/resources/api/openapi.yaml`, held to them by regeneration in CI |
| Why a reader should care | `README.md` — concepts, never values |

**The edit test.** Before writing a number, threshold, or algorithm name into prose, ask: *if
this changes, how many files must I touch?* More than one → link instead of restating. This is
why the README describes the weighted feature vector without naming a single weight.

**Specs are frozen history.** A merged spec records what was decided *then*. Never retro-edit
one; corrections and amendments go forward into `docs/roadmap-notes.md` or the owning doc —
slice 3 amended R4-1 there rather than rewriting slice 2's spec. Spelling is the one exception:
a British form brought to `conventions/WRITING.md` changes no decision, and the prose check
reads merged specs like everything else.

**`docs/matching.md` is append-only.** Merge events permanently record the `matcherName` that
decided them, so a new matcher version gets a new section and the old one stays readable.

**A second repository links, never restates.** The org profile at `guestgraph/.github` once
drifted to "Core in development" while two slices had shipped, because it restated a roadmap
living here. No CI in one repo can catch that.

## Process

- Slices follow `/speckit-specify` → `plan` → `tasks` → `implement` on a `NNN-*` branch;
  roadmap-notes (`docs/roadmap-notes.md`) feed each slice's spec.
- TDD is mandatory for engine logic: failing scenario tests first, pure JVM.

## Checks

Four jobs, all required by the ruleset on `main`: `verify`, `er-drift` and `service-conventions`
from the vendored workflow, the last holding the vendored copy to its release and the engine to
the list every guestgraph service meets, and `conventions`, called from robertblust/conventions
at the pinned tag and shown by GitHub as `conventions / conventions`. The prose check leaves out `target`, build output;
`.specify` and `.claude`, spec-kit's templates and skills, which are tooling and not this
repository's prose; and `docs/superpowers`, whose specs quote the very words it scans for. The
feature specs under `specs/` are this repository's own writing and are scanned. Everything
about how to write and how to work with git is in `conventions/`.
