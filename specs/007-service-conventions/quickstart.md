# Quickstart & Validation: Service Conventions

**Feature**: 007-service-conventions
**Contract**: [contracts/service-conventions.md](contracts/service-conventions.md) · **Model**: [data-model.md](data-model.md) · **Research**: [research.md](research.md)

## Prerequisites

JDK 25, Docker, `./mvnw` in both services; sh, curl and awk for the scripts; `gh` for the
releases. Three checkouts: the engine, the connector and `guestgraph/service-conventions`.

## Run the test suites (primary validation)

```bash
# in guestgraph/service-conventions
sh tests/run                      # the sync check and the service check against fixtures
sh conventions/conventions-check
# in the engine and in the connector
./mvnw verify
sh service-conventions/service-conventions-sync check
sh service-conventions/service-conventions-check
sh conventions/conventions-check
```

Expected green, including in the shared repository a fixture that passes and one fixture per
item with that item removed, each failing with the item's line; in the engine the health test
and every existing test unchanged; in the connector the API document test, the size cap test,
the local profile start test and every existing test unchanged.

## Walk

1. **A rule reaches both services by one pin move.** In the shared repository, add one rule to
   `service-conventions/pmd-ruleset.xml` and release `v0.1.1`. In each service, change `tag` in
   `service-conventions.json`, run `sh service-conventions/service-conventions-sync sync`, and read the pull request's diff: the pin
   and the vendored file, nothing else. *Confirms SC-001.*
2. **One character of drift fails.** In a service, edit one character of `service-conventions/pmd-ruleset.xml`
   and run `sh service-conventions/service-conventions-sync check` → exit 1 and one line naming the file. Revert.
   *Confirms SC-002.*
3. **Nothing missing, and one thing missing.** In each service, `sh service-conventions/service-conventions-check` → no
   output, exit 0. Remove `health` from the exposure list in `application.yaml` and run it again
   → exit 1 and the line `✗ service-conventions: health: …`. Revert. *Confirms SC-003.*
4. **A new service passes first time.** In the shared repository, `sh new-service spring probe` into a
   temporary directory; there, `./mvnw verify`, `sh service-conventions/service-conventions-sync check` and
   `sh service-conventions/service-conventions-check` → all green, and no file of it was copied from the engine or the
   connector. Delete it. *Confirms SC-004.*
5. **Nothing else changed.** In each service, `git diff main --stat -- 'specs/*/contracts'` is
   empty in the engine and `api/` in the connector equals the engine's file at the pinned
   commit; every test that existed before the slice is present with its assertions unchanged.
   *Confirms SC-005.*
6. **The family knows.** `conventions/REPOSITORIES.md` in any member names
   `guestgraph/service-conventions` with its purpose and its place in the re-sync order, and each
   service's `AGENTS.md` opens with the family's block, then the service block.

## Success-criteria spot checks

| Criterion | Check |
|---|---|
| SC-001 | Step 1. |
| SC-002 | Step 2, and the shared repository's fixture test. |
| SC-003 | Step 3, and the shared repository's fixture tests, one per item. |
| SC-004 | Step 4. |
| SC-005 | Step 5, and both suites green with no assertion changed. |
