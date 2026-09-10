# Quickstart & Validation: Apaleo Connector

**Feature**: 005-apaleo-connector
**Contracts**: [contracts/mapping.md](contracts/mapping.md) (what is sent), [contracts/connector-api.yaml](contracts/connector-api.yaml) (the connector's own surface)
**Model**: [data-model.md](data-model.md) · **Research**: [research.md](research.md)

The connector lives in `guestgraph/connector-apaleo`; the commands below run there. The engine
runs from this repository as in earlier quickstarts.

## Prerequisites

JDK 25, Docker, `./mvnw`. An Apaleo sandbox account with a client-credentials client granted
`reservations.read`, and a way to reach the connector over HTTPS from Apaleo for the webhook
walk — a tunnel is enough for a sandbox. An engine running locally with the demo tenant, and an
API key registered as agent-operated and named `connector-apaleo`.

Configuration is the table at the end of [data-model.md](data-model.md), as environment
variables. Nothing in it is ever printed.

## Run the test suite (primary validation)

```bash
./mvnw verify
sh conventions/conventions-check
```

Expected green, including:

- `mapping/ApaleoMapperTest` — recorded Apaleo reservation and booking documents in, the records
  of [mapping.md](contracts/mapping.md) out: keys per object, roles and positions, the six
  extracted fields, the nesting of everything else, a booker who is the primary guest, an empty
  additional-guest list, a booking's dates derived from its reservations, payment fields absent
  (SC-001's shape)
- `mapping/RosterHashTest` — a room change hashes equal, a guest correction hashes different, an
  added or removed guest hashes different, an address-only change hashes equal, a booker
  correction changes the booking's hash and not the reservation's (SC-004)
- `integration/FullSyncTest` — WireMock Apaleo with three pages and a 204 end, WireMock engine:
  every person submitted once, held ids stored, a second run submits nothing (SC-001, SC-002)
- `integration/EventTest` — a delivery answered 202 before the fetch, a duplicate delivery
  ignored, two events out of order both processed, a booking event fetching the booking only, a
  fetch failure kept for retry, an event for an unknown property ignored and counted, the
  reachability check answered 200
- `integration/ReconcileTest` — a gap in state, reconciliation from the sync point minus the
  overlap, only the missed versions submitted; the subscription read on each run and its loss
  shown in the status; a gap longer than the retry window starting a full sync that catches a
  booker-only edit (SC-005)
- `integration/RefreshTest` — held ids under ACTIVE, MERGED, SPLIT and RETIRED answers from the
  stubbed engine; MERGED replaced, SPLIT surfaced, nothing chosen (SC-006)
- `integration/StatusTest` — the status document after each of the above, and a log capture
  searched for every credential and every person value used in the run (SC-007)
- the ArchUnit, PMD and Spotless gates

## End-to-end walk (against a local engine and an Apaleo sandbox)

Engine at `$E` with key `$K`; connector at `$C` with ops token `$T`.

### Before the walk: the sandbox items of research R11

1. In the sandbox, edit a reservation's guest, add a guest, remove a guest, and check a
   reservation in with registration data; note which event type each fires. Set
   `APALEO_EVENT_TYPES` accordingly and record the finding in research R11.
2. Create a subscription with the client-credentials client; if it is refused, record it and
   use a connect client for the Webhook API.

### US1 — a property's reservations become observations

3. Start the connector with no state. It registers `apaleo` at the engine, creates its
   subscription, and starts a full sync because no sync point exists.
4. `GET $C/status` → the properties served, `subscription.active: true`, a sync point per
   property once the run finishes, counters that match the sandbox's reservation and person
   counts.
5. Pick a booking with one reservation carrying two additional guests and a distinct booker;
   `GET $E/api/v1/source-objects/apaleo/reservation/{id}` → a roster of three with roles
   `PRIMARY_GUEST`, `ADDITIONAL_GUEST` ×2 and `currentVersion` equal to the reservation's
   `modified`; `GET $E/api/v1/source-objects/apaleo/booking/{bookingId}` → a roster of one,
   `BOOKER`, with business dates spanning the reservation. *Confirms SC-001's shape.*
6. `GET $E/api/v1/guests/{guestId}/records` for the primary guest → the payload carries
   `firstName`, `lastName`, `email` at the top and `person` and `reservation` nested.
7. `POST $C/sync/full` again → the run finishes with `duplicates` equal to the records
   submitted before and `versionsSubmitted: 0`. *Confirms SC-002.*

### US2 — changes arrive within a minute

8. In the sandbox, correct the primary guest's email on a reservation. Within two minutes the
   engine's roster for that reservation shows a new `currentVersion` and the corrected email.
   *Confirms SC-003.*
9. Change only the room on the same reservation. `GET $C/status` → `versionsSubmitted`
   unchanged. *Confirms SC-004 for one edit.*
9a. Correct the booker's email on the booking. Within two minutes the booking's roster in the
    engine shows the new version, and the reservation's `currentVersion` is unchanged.
10. Stop the connector, edit two reservations' guests in the sandbox, wait past the
    reconciliation interval, start the connector → the next reconciliation submits both.
    *Confirms SC-005.*

### US3 — the connector can be operated

11. Set a wrong Apaleo secret and restart → `GET $C/status` shows `lastError.where: APALEO`
    with a time and a reason; events still answer 202 and `pendingEvents` grows. Restore the
    secret → the pending events drain.
12. Search the connector's log for the client secret, the engine key and one guest's email →
    no hit. *Confirms SC-007.*

### US4 — the guest ids the connector holds stay valid

13. In the engine, merge a synced guest with another by ingesting a bridging record; `POST
    $C/refresh` → the run logs the old and new id, and the held id for that slot is the survivor.
14. In the engine, unmerge a synced guest into two; `POST $C/refresh` → `GET $C/status` shows
    `splitsAwaitingPerson: 1` and the held row carries both current ids, unchanged.
    *Confirms SC-006.*

## Success-criteria spot checks

| Criterion | Check |
|---|---|
| SC-001 | Full sync of the sandbox account within the hour budget scaled to its size; source objects in the engine equal reservations, roster entries equal persons. |
| SC-002 | Step 7. |
| SC-003 | Step 8, timed from the sandbox edit. |
| SC-004 | `RosterHashTest`, plus steps 8 and 9. |
| SC-005 | `ReconcileTest`, plus step 10. |
| SC-006 | `RefreshTest`, plus steps 13 and 14. |
| SC-007 | `StatusTest`'s log search, plus step 12. |
