# Feature Specification: The Engine Owns One Schema

**Feature Branch**: `006-engine-schema`

**Created**: 2026-09-10

**Status**: Draft

**Input**: User description: "The engine owns one database schema and one role. Consume the roadmap's cross-cutting note "One schema, one role per service": the engine moves from the default public schema to a schema of its own, engine, reached as a role that owns that schema and nothing else, so that sharing a database with the Apaleo connector or any later service is a deployment choice rather than a design one, and so a connector's role cannot read the engine's tables. The migrations name no schema, so the move is a Flyway default schema, a connection search path, the Testcontainers harness, the ER regeneration script, the local compose and seeder, and one paragraph of deployment guidance with the two SQL lines a deployment runs. Before the first release, while V1 may still be edited and no consumer has a database to migrate; no existing deployment is migrated."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One Database or Two Is the Deployment's Choice (Priority: P1)

Whoever deploys the engine beside the Apaleo connector, or beside any later service of the family, decides whether they share one database or run in two. Today the engine quietly decides for them: it creates its tables in the database's default schema, the one every role sees, so a second service in the same database shares a namespace with it and a role that can reach the database can reach the engine's tables. The engine moves into a schema of its own, `engine`, and connects as a role that owns that schema and nothing else. Nothing in its tables, its queries or its migrations names the schema; the connection says where the engine lives, so the same build runs in a shared database and in a dedicated one with only configuration changed.

**Why this priority**: It is the slice's reason to exist, and it is cheap only now: before the first release, while the first migration may still be edited and no consumer has a database that would need moving.

**Independent Test**: Start the engine against an empty database with the schema and role of the deployment guidance created; verify every table sits in the `engine` schema, none in the default one, and the API behaves as before. Then create a second role that owns another schema in the same database and verify it cannot read a single engine table.

**Acceptance Scenarios**:

1. **Given** an empty database prepared as the deployment guidance says, **When** the engine starts, **Then** it creates all of its tables and its migration history in the `engine` schema and nothing in the default schema.
2. **Given** a running engine, **When** any API operation of slices 1 to 4 is exercised, **Then** it behaves exactly as before the move; no contract and no response changes.
3. **Given** a second role in the same database that owns a schema of its own, **When** it attempts to read any engine table, **Then** it is refused.
4. **Given** a deployment that prefers a dedicated database, **When** the engine is pointed at it with the same configuration keys, **Then** it runs the same way; nothing in the engine knows which layout it is in.
5. **Given** the local development setup, **When** a developer starts the database and the engine as the README says, **Then** the demo tenant is seeded and reachable as before, in the `engine` schema.

---

### User Story 2 - The Suite and the Diagram Follow the Schema (Priority: P2)

The engine's own checks read its schema: the integration harness resets tables between tests, and the ER diagram is regenerated from the migrations and held against drift in CI. Both must follow the engine into its schema, or the move would pass the API tests and fail the build.

**Why this priority**: Without it the move cannot be verified, and the drift gate that protects the diagram would either lie or break.

**Independent Test**: Run the full suite and the diagram regeneration after the move; both green, and the regenerated diagram shows the same tables it showed before.

**Acceptance Scenarios**:

1. **Given** the move, **When** the full suite runs, **Then** every existing test passes unchanged in what it asserts.
2. **Given** the move, **When** the ER diagram is regenerated, **Then** it renders the same tables and constraints as before, taken from the `engine` schema, and the drift gate reports no difference.
3. **Given** a stale local database from before the move, **When** the engine starts against it, **Then** it fails plainly rather than mixing two schemas, and the README says what to do: drop the volume, as it already says for a checksum mismatch.

---

### Edge Cases

- A deployment names a different schema in configuration: the engine follows the name; `engine` is the default, not a constant.
- The role owns the schema but the schema does not exist yet: the engine creates it on first start, as its migration tooling can, so the two SQL lines of the guidance are the minimum and a missing schema is not a trap.
- The role can see the default schema as well, because the database grants it to every role: the engine still creates nothing there, because its search path names only its own schema.
- The same database serves the engine and the connector, and both start at once: each creates its own schema and its own migration history; neither sees the other's.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The engine MUST create every table, index, trigger, function and its migration history in one schema, `engine` by default and configurable, and nothing outside it.
- **FR-002**: The engine MUST connect as a role that owns that schema; the deployment guidance MUST state the two statements that create the role and the schema, and MUST say that the role is granted nothing else.
- **FR-003**: No migration, query or entity mapping MUST name a schema; the connection alone MUST decide where the engine lives, so one build runs in a shared database and in a dedicated one.
- **FR-004**: Every API contract and response MUST be unchanged by the move.
- **FR-005**: The integration harness, the local development setup with its seeded demo tenant, and the ER regeneration script MUST operate on the engine's schema, and the full suite and the drift gate MUST pass after the move.
- **FR-006**: The first migration MAY be edited to make the move, because no release has been tagged; no upgrade path from the default schema is provided, and the README MUST say that a database from before the move is dropped, not migrated.
- **FR-007**: A second service's role in the same database MUST NOT be able to read the engine's tables; the deployment guidance MUST state this as the reason for the role.

### Key Entities

- **Schema**: the engine's namespace inside a database, `engine` by default; every object the engine creates lives in it.
- **Role**: the database identity the engine connects as; owns the schema and nothing else.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After the move, a query of the database catalog lists every engine table in the `engine` schema and zero in the default schema.
- **SC-002**: The full suite passes with no test assertion changed, and the ER drift gate reports no difference.
- **SC-003**: A role that owns another schema in the same database is refused on every engine table, verified by attempting one read per table.
- **SC-004**: The move changes no line of any API contract file and no response in any integration test.

## Assumptions

- The move happens before the engine's first release, so the first migration may be edited and no deployment is migrated; a local database from before the move is dropped.
- The schema name is configuration with the default `engine`; the role name is the deployment's choice and the guidance uses `engine` for it too.
- The connector's own schema rule is already specified in slice 5; this slice completes the pair so that one database holds both with neither able to read the other.
- Lawful erasure and backups are unaffected: they act on the schema as they would on the database.
