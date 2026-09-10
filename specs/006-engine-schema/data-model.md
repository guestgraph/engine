# Data Model: The Engine Owns One Schema

**Feature**: `006-engine-schema` | **Date**: 2026-09-10 | Migration: none

No table, column, index or constraint changes. The move is where the existing objects live and
who may see them.

---

## Namespace

| Object | Before | After |
|---|---|---|
| every table of V1 to V4, their indexes, triggers and the `guard_append_only` function | `public` | `engine` (configurable) |
| Flyway's history table | `public` | `engine` |
| the role the engine connects as | the database owner in local setups; unspecified in deployments | a role that owns `engine` and nothing else |

The schema name is one configuration value:

| Property | Meaning |
|---|---|
| `DATABASE_SCHEMA` | default `engine`; feeds the pool's connection schema and Flyway's default schema (research R1) |

## Rules

1. **Nothing names the schema.** No migration, query, entity mapping or script carries a schema
   qualifier; the connection's search path decides (FR-003). The ER script sets the search path
   before applying each migration for the same reason.
2. **One schema per service, one role per schema.** The engine's role owns `engine`; the Apaleo
   connector's role owns `apaleo_connector`; neither is granted the other's (FR-002, FR-007).
   A shared database holds both by running each service's two statements once.
3. **Before the move is dropped, not migrated.** A local database with tables in `public` is
   recreated (FR-006).

## Deployment guidance, the paragraph the README carries

The engine creates every table in one schema, `engine` by default, and connects as a role that
owns that schema and nothing else, so that a second service in the same database — the Apaleo
connector, say — cannot read the engine's tables, and so that one database or two is the
deployment's choice. Before the first start:

```sql
create role engine login password '…';
create schema engine authorization engine;
```

Point the engine at the database as that role. To use another schema name, set
`DATABASE_SCHEMA`. If the schema is left for the engine to create, the role needs `CREATE` on the
database for the first start only.
