# Data Model: Apaleo Connector

**Feature**: `005-apaleo-connector` | **Date**: 2026-09-10 | Migration: `V1__connector_state.sql` in `guestgraph/connector-apaleo`

The connector's own state, all of it a cache of Apaleo's and the engine's facts (spec assumptions).
Nothing here is a source record; the engine holds those. One instance serves many connections,
and every table carries `connection_id` as the first column of its primary key or as a NOT NULL
column with an index; every repository method takes a `connectionId` parameter, and an ArchUnit
rule refuses one that does not, the way the engine's refuses a query without a tenant (FR-015).

---

## One schema, one role

The connector owns one PostgreSQL schema, `apaleo_connector` by default, and nothing outside it.
Flyway creates the schema and keeps its history table inside it, the connection pins its search
path to it, and no migration or query names a schema. That is what makes the database topology a
deployment choice rather than a design one: the connector's schema can sit in the engine's
database or in one of its own, with the same build and the same configuration keys.

The connector connects as a role that owns its schema and has no privilege on any other. In a
shared database that role cannot read `guest` or `source_record`, which enforces what the design
already says: the connector is a client of the engine's API, never of its tables. The code cannot
create that role; the deployment does, and the README says how:

```sql
create role apaleo_connector login password '…';
create schema apaleo_connector authorization apaleo_connector;
```

The engine's side of the same rule — its own schema and role, in place of `public` — is a change
of its own in the engine repository, recorded in the roadmap.

---

## `connection`

One row per configured connection, written from configuration at start and updated when the
configuration changes; the secrets themselves never enter the table (FR-015a). The row exists so
that every other table can reference a stable id and so the status can list connections that have
not run yet.

| Column | Type | Notes |
|---|---|---|
| id | text | PK — the connection's name from configuration, stable across restarts |
| tenant_label | text | NOT NULL — the engine tenant's name, for the status only |
| apaleo_account | text | NOT NULL — Apaleo's account id, for the status only |
| property_ids | jsonb | NOT NULL DEFAULT '[]' — empty means every property of the account |
| webhook_secret_hash | text | NOT NULL — the delivery path's secret, hashed; routing compares hashes |
| created_at | timestamptz | NOT NULL |
| last_activity_at | timestamptz | NOT NULL — the last successful run or event; a gap longer than Apaleo's retry window from here triggers a full sync (FR-012a) |

## `object_state`

The last version the connector submitted for a reservation or a booking, and the hash that
decides whether the next one is submitted (research R4).

| Column | Type | Notes |
|---|---|---|
| connection_id | text | PK part, FK → `connection` |
| object_type | text | PK part — `reservation` or `booking` |
| object_id | text | PK part — Apaleo's id for the object |
| property_id | text | NULL — set for a reservation; a booking may span properties |
| booking_id | text | NULL — for a reservation, the booking it belongs to |
| last_modified | timestamptz | NOT NULL — the object's `modified` on the last submitted version |
| roster_hash | text | NOT NULL — hash over role, position and the extracted and name fields of every person, in role order; for a booking also its derived dates |
| last_submitted_at | timestamptz | NOT NULL |
| last_status | text | NULL — a reservation's status on the last submitted version, for the status report only |

A fetched object is submitted when no row exists or the computed hash differs from
`roster_hash`; the row is then replaced. A version whose `modified` is older than `last_modified`
is still submitted when its hash differs — the engine orders versions, not the connector — and
does not move `last_modified` backwards.

## `processed_event`

Every webhook delivery, by Apaleo's event id, so the second delivery is a no-op (FR-010).

| Column | Type | Notes |
|---|---|---|
| connection_id | text | PK part, FK → `connection` — the connection whose secret routed the delivery |
| event_id | text | PK part — the `id` of the delivery |
| event_type | text | NOT NULL — e.g. `reservation/changed`, `booking/changed` |
| object_type | text | NOT NULL — from the topic |
| object_id | text | NOT NULL — `data.entityId` |
| property_id | text | NOT NULL |
| received_at | timestamptz | NOT NULL |
| state | text | NOT NULL CHECK IN (`PENDING`, `DONE`, `IGNORED`, `FAILED`) |
| attempts | int | NOT NULL DEFAULT 0 |
| next_attempt_at | timestamptz | NULL — set while `PENDING` after a failure |
| last_error | text | NULL — the reason, never a payload |

`IGNORED` records an event for a property the connection does not serve. `FAILED` is never
final: a failed event stays retryable and the status counts it as pending retry (FR-011).

## `sync_point`

Per connection and property, how far the connector has submitted (research R6).

| Column | Type | Notes |
|---|---|---|
| connection_id | text | PK part, FK → `connection` |
| property_id | text | PK part |
| modified_through | timestamptz | NOT NULL — the greatest reservation `modified` fully processed |
| last_full_sync_at | timestamptz | NULL |
| last_reconcile_at | timestamptz | NULL |

## `sync_run`

One row per full sync or reconciliation, for the status and for `GET /runs/{id}` (research R9).

| Column | Type | Notes |
|---|---|---|
| id | uuid | PK |
| connection_id | text | NOT NULL, FK → `connection`, indexed |
| kind | text | NOT NULL CHECK IN (`FULL`, `RECONCILE`, `REFRESH`) |
| started_at | timestamptz | NOT NULL |
| finished_at | timestamptz | NULL |
| outcome | text | NULL CHECK IN (`SUCCEEDED`, `FAILED`) |
| reservations_seen | int | NOT NULL DEFAULT 0 |
| versions_submitted | int | NOT NULL DEFAULT 0 |
| records_submitted | int | NOT NULL DEFAULT 0 |
| duplicates | int | NOT NULL DEFAULT 0 |
| flagged_for_review | int | NOT NULL DEFAULT 0 |
| errors | int | NOT NULL DEFAULT 0 |
| last_error | text | NULL |

## `held_guest_id`

The guest each person on the latest submitted version resolved to, and its refresh outcome
(research R8, FR-018, FR-019).

| Column | Type | Notes |
|---|---|---|
| connection_id | text | PK part, FK → `connection` |
| object_type | text | PK part — `reservation` or `booking` |
| object_id | text | PK part |
| role | text | PK part — `PRIMARY_GUEST` or `ADDITIONAL_GUEST` on a reservation, `BOOKER` on a booking |
| position | int | PK part — 0 except for additional guests |
| guest_id | uuid | NOT NULL — the engine's answer, replaced on `MERGED` |
| source_record_id | uuid | NOT NULL — the observation the answer came with |
| resolution_status | text | NOT NULL CHECK IN (`ACTIVE`, `MERGED`, `SPLIT`, `RETIRED`) — from the last refresh; `MERGED` only transiently, the row is rewritten `ACTIVE` with the new id |
| current_guest_ids | jsonb | NOT NULL DEFAULT '[]' — filled on `SPLIT` and `RETIRED`, for a person to resolve |
| refreshed_at | timestamptz | NULL |

The primary key is the object and slot, so a reassigned reservation or a corrected booker
overwrites the slot with the new person's guest (spec US4 scenario 5). A `SPLIT` row is never resolved by the connector;
it waits for a person, and the status counts it.

---

## Derivation rules

1. **Observation key.** `{reservationId}:primaryGuest:{modified}` and
   `{reservationId}:additionalGuests[{i}]:{modified}` on a reservation, `{bookingId}:booker:{modified}`
   on a booking, each `modified` the object's own, exactly as Apaleo returns it. Nothing the
   connector generates enters the key (FR-002).
2. **Submit or not.** Compute the roster hash of the fetched version; submit when no row or a
   different hash; otherwise record nothing (FR-009).
3. **Roster hash.** Over the object's persons — primary guest then additional guests by index on
   a reservation, the booker on a booking: role, position, and the values of `firstName`,
   `lastName`, `email`, `phone`, `birthDate`, `identificationType`, `identificationNumber`, each
   normalized only by trimming; on a booking also the derived business start and end. A missing
   person entry contributes nothing; a missing field contributes an empty value.
3a. **Booking dates.** Business start is the earliest `arrival` and business end the latest
   `departure` over the booking's reservations as the booking lists them; absent when it lists
   none.
4. **Sync point.** Advances to a reservation's `modified` only after every person of that version
   was submitted and its results read, and the reservation's booking was fetched and handled by
   rule 2; a run interrupted before that resumes from the previous point.
5. **Held id.** Rewritten from every result that carries a `guestId`; `DUPLICATE_IGNORED` results
   carry the existing guest and rewrite it too, so a full sync refreshes the held ids for free.
6. **Refresh.** For each connection and each distinct held `guest_id`, one read of
   `GET /guests/{id}` with that connection's key; the answer's `status` and `currentGuestIds`
   update every row holding that id under that connection.
7. **Connection.** Every query carries the connection id; a delivery belongs to the connection
   whose secret hash matches its path, and nothing under one connection is visible under
   another.

---

## Configuration

Read from the environment or a mounted file; none of it is logged, and none of the secrets
reaches the database (FR-015a).

Per instance:

| Property | Meaning |
|---|---|
| `CONNECTOR_PUBLIC_URL` | the HTTPS base Apaleo can reach; a connection's endpoint is `{base}/apaleo/events/{secret}` |
| `CONNECTOR_OPS_TOKEN` | bearer token for `/status` and `/connections/*` |
| `CONNECTOR_CONNECTIONS_FILE` | path to a YAML file listing the connections below |
| `APALEO_EVENT_TYPES` | default `reservation/created,reservation/changed,reservation/amended,reservation/picked-up-from-block,reservation/checked-in,booking/created,booking/changed` (FR-007) |
| `ENGINE_SOURCE_SYSTEM` | default `apaleo` |

Per connection, in that file, under the connection's name:

| Property | Meaning |
|---|---|
| `tenantLabel` | the engine tenant's name, for the status |
| `engineBaseUrl`, `engineApiKey` | the engine and that tenant's agent-registered key |
| `apaleoAccount`, `apaleoClientId`, `apaleoClientSecret` | the client-credentials client, one account |
| `apaleoPropertyIds` | list; empty means every property of the account |
| `webhookSecret` | the path secret, unique across connections; the configuration is refused otherwise |
| `RECONCILE_INTERVAL`, `RECONCILE_OVERLAP` | defaults 15 minutes and 1 hour |
| `RESYNC_AFTER_GAP` | default 24 hours, Apaleo's retry window; a longer gap since the last activity starts a full sync (FR-012a) |
| `REFRESH_CRON` | default nightly |
| `DATABASE_URL` and credentials | a PostgreSQL database, shared with the engine or not, reached as the connector's own role |
| `DATABASE_SCHEMA` | default `apaleo_connector`; the only schema the connector touches |
