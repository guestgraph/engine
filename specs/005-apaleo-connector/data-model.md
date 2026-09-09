# Data Model: Apaleo Connector

**Feature**: `005-apaleo-connector` | **Date**: 2026-09-10 | Migration: `V1__connector_state.sql` in `guestgraph/connector-apaleo`

The connector's own state, all of it a cache of Apaleo's and the engine's facts (spec assumptions).
Nothing here is a source record; the engine holds those. Every table carries the property or
account the row belongs to, and one connector instance serves one account and one tenant, so no
table needs a tenant column of its own (FR-015).

---

## `object_state`

The last version the connector submitted for a reservation or a booking, and the hash that
decides whether the next one is submitted (research R4).

| Column | Type | Notes |
|---|---|---|
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
| event_id | text | PK — the `id` of the delivery |
| event_type | text | NOT NULL — e.g. `reservation/changed`, `booking/changed` |
| object_type | text | NOT NULL — from the topic |
| object_id | text | NOT NULL — `data.entityId` |
| property_id | text | NOT NULL |
| received_at | timestamptz | NOT NULL |
| state | text | NOT NULL CHECK IN (`PENDING`, `DONE`, `IGNORED`, `FAILED`) |
| attempts | int | NOT NULL DEFAULT 0 |
| next_attempt_at | timestamptz | NULL — set while `PENDING` after a failure |
| last_error | text | NULL — the reason, never a payload |

`IGNORED` records an event for a property the connector does not serve. `FAILED` is never
final: a failed event stays retryable and the status counts it as pending retry (FR-011).

## `sync_point`

Per property, how far the connector has submitted (research R6).

| Column | Type | Notes |
|---|---|---|
| property_id | text | PK |
| modified_through | timestamptz | NOT NULL — the greatest reservation `modified` fully processed |
| last_full_sync_at | timestamptz | NULL |
| last_reconcile_at | timestamptz | NULL |
| last_activity_at | timestamptz | NOT NULL — the last successful run or event; a gap longer than Apaleo's retry window from here triggers a full sync (FR-012a) |

## `sync_run`

One row per full sync or reconciliation, for the status and for `GET /runs/{id}` (research R9).

| Column | Type | Notes |
|---|---|---|
| id | uuid | PK |
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
6. **Refresh.** For each distinct held `guest_id`, one read of `GET /guests/{id}`; the answer's
   `status` and `currentGuestIds` update every row holding that id.

---

## Configuration

Read from the environment; none of it is logged.

| Property | Meaning |
|---|---|
| `APALEO_CLIENT_ID`, `APALEO_CLIENT_SECRET` | the client-credentials client, one account |
| `APALEO_PROPERTY_IDS` | comma-separated; empty means every property of the account |
| `APALEO_EVENT_TYPES` | default `reservation/created,reservation/changed,reservation/amended,reservation/picked-up-from-block,reservation/checked-in,booking/created,booking/changed` (FR-007) |
| `CONNECTOR_PUBLIC_URL` | the HTTPS base Apaleo can reach; the endpoint is `{base}/apaleo/events/{secret}` |
| `CONNECTOR_WEBHOOK_SECRET` | the path secret |
| `CONNECTOR_OPS_TOKEN` | bearer token for `/status`, `/sync/*`, `/runs/*` |
| `ENGINE_BASE_URL`, `ENGINE_API_KEY` | the engine and the tenant's agent-registered key |
| `ENGINE_SOURCE_SYSTEM` | default `apaleo` |
| `RECONCILE_INTERVAL`, `RECONCILE_OVERLAP` | defaults 15 minutes and 1 hour |
| `RESYNC_AFTER_GAP` | default 24 hours, Apaleo's retry window; a longer gap since the last activity starts a full sync (FR-012a) |
| `REFRESH_CRON` | default nightly |
| `DATABASE_URL` and credentials | the connector's own PostgreSQL database |
