# GuestGraph — working conventions

Open-source (Apache-2.0) guest identity graph. Spec-driven with spec-kit; the
constitution at `.specify/memory/constitution.md` is non-negotiable (tenant isolation,
immutable source records, never drop parseable data, explainable/reversible resolution,
API-first RFC 9457, TDD on the resolution engine).

## Build & verify

```bash
./mvnw verify              # tests (Testcontainers, needs Docker), ArchUnit, PMD, Spotless
./mvnw spotless:apply      # fix formatting (google-java-format) — check fails otherwise
./scripts/regen-er.sh      # after schema changes — CI checks ER-diagram drift
```

## Code conventions

- **Imports, not inline FQNs.** Types are referenced by simple name with a proper
  import — never `java.time.LocalDate` inline in code. Enforced by PMD
  (`UnnecessaryFullyQualifiedName`, `config/pmd-ruleset.xml`) in `verify`. Exception:
  JPQL query strings, where FQNs are required syntax (enum literals, constructor
  expressions) — PMD doesn't look inside strings.
- **Formatting** is google-java-format via Spotless; don't hand-format.
- **Comments** state constraints the code can't show; no narration.
- Mechanical guardrails live in three places, each with its job: **Spotless** (format),
  **PMD** (source-level conventions), **ArchUnit** (`PersistenceRulesTest`: tenant
  scoping on every repo method, `@Query`-only repositories, no CrudRepository, no ad-hoc
  EntityManager queries, JdbcClient allowlist, JPA confined to `persistence`).

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
  filter them (already done in `PersistenceRulesTest`).
- Test harness truncates tables in `PostgresIntegrationTest.resetDatabase` — add new
  tables there or every integration test fails on FK truncate errors.
- Until the first release, `V1__core_schema.sql`/`V2__*.sql` may be edited in place;
  local Flyway checksum mismatch → `docker compose down -v`. Additive-only after tagging.

## Documentation ownership (prevents drift)

Every fact has **one owning file**; everywhere else links to it. The ambiguity about who owns
what is what causes drift, so the map is explicit:

| Fact | Owner |
|---|---|
| Constants, thresholds, algorithms | the code |
| Matching behaviour | `docs/matching.md`, sectioned per matcher version |
| One slice's decisions | `specs/NNN-*/` — **frozen at merge** |
| Cross-slice decisions, roadmap, deferred work | `docs/roadmap-notes.md` |
| API surface | `specs/*/contracts/openapi.yaml` |
| Why a reader should care | `README.md` — concepts, never values |

**The edit test.** Before writing a number, threshold, or algorithm name into prose, ask: *if
this changes, how many files must I touch?* More than one → link instead of restating. This is
why the README describes the weighted feature vector without naming a single weight.

**Specs are frozen history.** A merged spec records what was decided *then*. Never retro-edit
one; corrections and amendments go forward into `docs/roadmap-notes.md` or the owning doc —
slice 3 amended R4-1 there rather than rewriting slice 2's spec.

**`docs/matching.md` is append-only.** Merge events permanently record the `matcherName` that
decided them, so a new matcher version gets a new section and the old one stays readable.

**A second repository links, never restates.** The org profile at `guestgraph/.github` once
drifted to "Core in development" while two slices had shipped, because it restated a roadmap
living here. No CI in one repo can catch that.

## Process

- Slices follow `/speckit-specify` → `plan` → `tasks` → `implement` on a `NNN-*` branch;
  roadmap-notes (`docs/roadmap-notes.md`) feed each slice's spec.
- TDD is mandatory for engine logic: failing scenario tests first, pure JVM.
- Never mention closed-source predecessor projects in this repo, its docs, or commits.
- Commits happen when the user asks; suggest messages, don't auto-commit.
- **Merge a pull request with a merge commit — `gh pr merge --merge`, never `--squash`.**
  Squashing is not a history preference here. GitHub *re-authors* a squash commit to the
  account that pressed the button, so a commit made locally under the wrong `user.email`
  lands on the default branch looking correct. That is not hypothetical: it was found in
  `robertblust.github.io`, where the local commit was authored `rob@likemagic.tech` and the
  commit that reached `main` read `robert.blust@flatland.ch`, with nothing anywhere saying
  so. A merge commit preserves the author it was given, which is the point — a wrong
  identity surfaces instead of being laundered.
- **The author is `robert.blust@flatland.ch`, and nothing on GitHub enforces it.** The
  ruleset rule that would — `commit_author_email_pattern`, a metadata restriction — is
  rejected on this plan. Tested, not assumed: an otherwise identical ruleset carrying a
  `deletion` rule was accepted in the same breath. So the identity comes from
  `~/.gitconfig`, where three `includeIf` blocks key it to `~/git/robertblust/`,
  `~/git/guestgraph/` and `~/git/companygraph/` and point at `~/.gitconfig-flatland`. The
  global default stays `rob@likemagic.tech`, which is right for `~/git/likemagic-tech` and
  `~/git/3ap-ag`. A clone made outside those three directories gets the global default and
  no warning, so check `git config user.email` before the first commit in a fresh clone.
