# Quickstart & Validation: Shared Runtime Code

**Feature**: 008-shared-runtime
**Contract**: [contracts/shared-runtime.md](contracts/shared-runtime.md) · **Model**: [data-model.md](data-model.md) · **Research**: [research.md](research.md)

## Prerequisites

JDK 25, Docker, `./mvnw` in both services; four checkouts: the engine, the connector,
`guestgraph/service-conventions` and `guestgraph/guestgraph.github.io`.

## Run the test suites (primary validation)

```bash
# in guestgraph/service-conventions
sh tests/run                                  # the checks on fixtures, the error-shape cases among them
(cd spring/runtime && ./mvnw -q verify)       # the shared classes compile, format and pass their unit tests
sh conventions/conventions-check
# in the engine and in the connector
./mvnw verify                                 # ErrorShapeTest among the rest, every existing test unchanged
sh service-conventions/service-conventions-sync check
sh service-conventions/service-conventions-check
sh conventions/conventions-check
```

## Walk

1. **Every origin, one shape.** In each service, provoke a 401 from the bearer or key filter, a
   404 from a controller, a 409 from a run or a conflict, a 400 from the framework with an
   unparseable body, and a 500 from a planted failure behind a test profile. Read each body:
   `application/problem+json`, a `type` under `https://guestgraph.io/problems/#`, a title, the
   status, a detail; the 500's detail says nothing of the cause and the server log carries the
   stack trace. *Confirms SC-001.*
2. **A shared change reaches both by one pin move.** In the shared repository, change the
   detail sentence of `payload-too-large` in `Problems`, release, move each service's pin and
   re-sync, and read the pull request's diff: the pin and the class, nothing else; the test of
   step 1 shows the new sentence. *Confirms SC-002.*
3. **Nothing forbidden, then one thing.** `sh service-conventions/service-conventions-check` in
   each service → no output. Add a `throw new ResponseStatusException(…)` to one controller →
   exit 1 and the line naming the file. Revert. *Confirms SC-003.*
4. **Nothing else changed.** Every integration test that existed before the slice is present
   with its assertions unchanged, and both suites are green. *Confirms SC-004.*
5. **Every type has its page.** For each slug in the data model, open
   `https://guestgraph.io/problems/#<slug>` and read its section; each service's contract, or
   the roadmap amendment for a frozen one, names the slugs it answers. *Confirms SC-005.*

## Success-criteria spot checks

| Criterion | Check |
|---|---|
| SC-001 | Step 1, and `ErrorShapeTest` in each service. |
| SC-002 | Step 2. |
| SC-003 | Step 3, and the shared repository's fixtures. |
| SC-004 | Step 4. |
| SC-005 | Step 5. |
