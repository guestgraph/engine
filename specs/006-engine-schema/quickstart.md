# Quickstart & Validation: The Engine Owns One Schema

**Feature**: 006-engine-schema
**Contracts**: none change (FR-004) · **Model**: [data-model.md](data-model.md) · **Research**: [research.md](research.md)

## Prerequisites

As slices 1–5: JDK 25, Docker, `./mvnw`. A local volume from before this slice holds its tables
in `public`; drop it with `docker compose down -v` before the first start after the move, as the
README now says.

## Run the test suite (primary validation)

```bash
./mvnw verify
./scripts/regen-er.sh   # must produce no diff — the diagram carries no schema name
sh conventions/conventions-check
```

Expected green, including:

- `integration/SchemaIsolationTest` — every engine table in `engine`, none in `public`; a second
  role owning another schema refused on every engine table (SC-001, SC-003)
- every existing suite, unchanged in what it asserts (SC-002, SC-004)

## Local walk

1. `docker compose down -v`, then `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.
   The log shows Flyway creating schema `engine` and migrating into it.
2. `docker exec -it engine-postgres-1 psql -U guestgraph -d guestgraph -c "\dt engine.*"` → every
   engine table; `\dt public.*` → none.
3. The slice-1 quickstart's ingest and read against the demo tenant behave as before.
4. In the same database, run the two statements of the deployment guidance for a second role,
   `probe`, owning schema `probe`; connect as `probe` and `select count(*) from engine.guest` →
   permission denied.

## Success-criteria spot checks

| Criterion | Check |
|---|---|
| SC-001 | Step 2, and `SchemaIsolationTest`. |
| SC-002 | `./mvnw verify` and `regen-er.sh` with no diff. |
| SC-003 | Step 4, and `SchemaIsolationTest`. |
| SC-004 | `git diff --stat` on `specs/*/contracts/` is empty; the integration suites pass unchanged. |
