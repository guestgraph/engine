# Phase 0 Research: The Engine Owns One Schema

**Feature**: `006-engine-schema` | **Date**: 2026-09-10

The spec has no clarification markers. This document settles how the engine is placed in its
schema, and it starts from one fact checked before the spec was drafted: no migration, query,
entity or script in the engine names a schema, except the ER regeneration script, which reads
`public` by name.

---

## R1 — Where the schema is named

**Decision**: In exactly two properties, both fed by one configuration value, `DATABASE_SCHEMA`,
default `engine`:

- `spring.datasource.hikari.schema`, which makes every pooled connection open with that schema
  as its search path — the PostgreSQL driver runs `SET search_path` when the pool sets the
  connection's schema — so JPA, the two `JdbcClient` users (the tenant lock and the local
  seeder) and the integration harness's `TRUNCATE` all resolve unqualified names there;
- `spring.flyway.default-schema`, which makes Flyway create the schema when it is missing, keep
  its history table in it, and run the unqualified migrations into it.

No JDBC URL parameter and no Hibernate default-schema property. The URL stays whatever the
deployment supplies, and the same two properties hold in every environment because they live in
`application.yaml`, which the tests inherit.

**Rationale**: One value, one place, and nothing that a deployment could set inconsistently: a
`currentSchema` parameter on the URL would have to agree with Flyway's property by hand, and the
local docker-compose support generates the URL, where a parameter would need a compose label.
The pool's schema property applies to every connection the engine ever opens, whatever produced
the URL. Hibernate needs no property of its own because `ddl-auto: validate` reads the
connection's default schema.

**Alternatives considered**:

- *`currentSchema=engine` on the JDBC URL*, the form the connector's data model names. Works,
  but it is a second place to keep in step and it does not reach a compose-generated URL without
  a label. The connector should follow this decision when its configuration is built (slice 5,
  T008); noted there.
- *Qualifying every table with the schema in the migrations.* Rejected by the spec's FR-003: the
  build would then know its layout, and moving it would mean editing every file.

---

## R2 — The role and the two statements

**Decision**: The deployment guidance in the README states:

```sql
create role engine login password '…';
create schema engine authorization engine;
```

and that the role is granted nothing else. With the schema owned by the role, Flyway's
schema creation finds it present; when a deployment creates only the role, Flyway creates the
schema on first start, which needs the role to hold `CREATE` on the database, and the guidance
says so as the one alternative.

**Rationale**: Ownership is the privilege that makes the rest unnecessary: the owner may create
and alter inside its schema and nobody else may read it unless granted. PostgreSQL 15 and later
withhold `CREATE` on `public` from ordinary roles, so a role that owns `engine` and nothing else
cannot stray into the default schema even by accident. The same two lines, with the names
changed, are the connector's guidance, so a shared database is set up by running them twice.

---

## R3 — The ER regeneration script

**Decision**: `scripts/regen-er.sh` creates the schema in its throwaway container, applies each
migration with the search path set to it, and points the diagram tool at `engine` instead of
`public`. The rendered diagram carries no schema name, so its content is unchanged and the
drift gate stays green.

**Rationale**: The script bypasses Flyway on purpose, to render the migrations as SQL alone, so
it has to do by hand what Flyway does for the application: make the schema and run into it.
Reading `public` after the move would render an empty diagram and fail the gate.

---

## R4 — The harness and the local setup

**Decision**: Nothing changes in `PostgresIntegrationTest` beyond what `application.yaml` gives
it: its `TRUNCATE` names tables unqualified and the pooled connection's search path resolves
them. The seeder is the same. `compose.yaml` is unchanged; the compose database user owns the
database and Flyway creates the schema on first start. The README's paragraph on Flyway
checksum mismatches gains the move: a local volume from before it is dropped with
`docker compose down -v`, because the old tables sit in `public` and the engine would create a
second set in `engine` beside them rather than fail.

**Rationale**: That last point is the one honest wrinkle. A stale volume does not error; it
gives a fresh, empty engine while the old data sits in the other schema. The README says so,
and the spec's US2 scenario 3 asks for plainness, which a documented drop gives where a
detection would be code for a case that exists only before the first release.

---

## R5 — What proves it

**Decision**: One new integration test, `SchemaIsolationTest`: it queries the catalog and asserts
that every table of the engine, listed by name from the migrations, sits in `engine` and none in
`public`; it creates a second role owning a second schema in the same container database and
asserts that a read of each engine table under that role is refused. The existing suites prove
FR-004 by passing unchanged.

**Rationale**: The catalog query is SC-001 verbatim, and the second role is SC-003 verbatim; the
container's user is a superuser, so the test can create the role itself and needs no fixture.

---

## R6 — The first migration is not edited

**Decision**: `V1__core_schema.sql` stays as it is. The spec allows editing it; nothing in it
names a schema, so nothing needs to change, and leaving it alone keeps the checksum stable for
the one case that matters, a local volume that was already on V4.

**Rationale**: FR-006 permits the edit; FR-003 makes it unnecessary.
