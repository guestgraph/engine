# Phase 0 Research: Apaleo Connector

**Feature**: `005-apaleo-connector` | **Date**: 2026-09-10

The spec's three decisions — Apaleo, its own repository, read only — were taken by the owner and
are inputs here. This document settles the technical unknowns they leave, and it names what only a
sandbox can settle (R11).

---

## R1 — Stack, repository and guardrails

**Decision**: `guestgraph/connector-apaleo` is a single Maven module on Java 25 and Spring Boot 4,
with PostgreSQL for its own state through Flyway and JPA, and the same three mechanical guardrails
the engine carries: Spotless with google-java-format, PMD with the engine's ruleset, and an
ArchUnit suite holding its repositories to explicit queries. It vendors `conventions/` at the
pinned release and requires the `verify` and `conventions` jobs on `main`.

**Rationale**: The family has one stack and one set of tools, and a connector written in it is a
connector the owner can maintain beside the engine. Nothing in the connector needs what the stack
does not give: an HTTP client, a scheduler, a small database and a web endpoint. A second stack
would buy nothing and cost a second toolchain in CI.

PostgreSQL rather than an embedded store because the connector runs beside an engine that
already has one, the state is small and relational, and the engine's Testcontainers harness
transfers unchanged. The state is a cache (spec assumptions): losing it costs a full sync. The
connector owns one schema and connects as one role (data-model "One schema, one role"), so
whether it shares the engine's database or has its own is decided at deployment, not here.

**Alternatives considered**:

- *A connector package inside the engine.* The owner chose the separate repository so the
  connector is built the way a third party would build one, against the public API. It also keeps
  the engine's single-service shape untouched.
- *An embedded database such as SQLite.* One fewer moving part per deployment, but a second
  persistence stack to test and no Testcontainers parity. Reopen if a connector is ever deployed
  where no PostgreSQL is.

---

## R2 — Talking to Apaleo

**Decision**: A client-credentials client bound to one Apaleo account, tokens from
`https://identity.apaleo.com/connect/token` valid 3,600 seconds and refreshed ahead of expiry;
reservations read with `GET /booking/v1/reservations` filtered by `dateFilter=Modification`,
sorted `updated:asc`, `pageSize=500`; a single reservation with `GET /booking/v1/reservations/{id}`;
bookings read with `GET /booking/v1/bookings/{id}?expand=reservations` and, for a full sync,
`GET /booking/v1/bookings?expand=reservations` paged at 500.

**Rationale**: These are the calls the Booking API document offers for exactly this shape of read.
The booker is read from the booking, where it lives; the copy a reservation shows under its own
expand option is not used, because it carries the booking's data under the reservation's clock
(spec Clarifications). Sorting reservations by update time in ascending order lets the sync point
advance monotonically as pages are consumed, so a run interrupted mid-way resumes from the last
page it finished rather than from the start. An empty page answers 204, which the client treats
as the end of the list rather than as an error. The booking list offers neither a modification
filter nor a sort, which shapes R6.

The scopes needed are `reservations.read` for the Booking API. Whether the same simple client may
manage webhook subscriptions is not stated in the documents and is a sandbox item (R11).

Rate limiting: the documents do not state the limit or the response shape. The client honors a
`Retry-After` header when one comes with a 429, and otherwise backs off exponentially from one
second, capped at a minute, and never gives up a sync (FR-017). The exact behavior is a sandbox
item (R11).

**Alternatives considered**:

- *The authorization-code connect client.* Serves many accounts with one credential and is what a
  marketplace app uses. Not needed for one account per connector instance, and it needs an
  interactive consent step the connector has no user for. Recorded for the managed offering.

---

## R3 — Mapping a reservation to observations

**Decision**: One ingest record per person per object, for two objects, built as follows and
stated in full in [contracts/mapping.md](contracts/mapping.md).

- `sourceSystem`: the code the connector registers, `apaleo`.
- `externalKey`: `{reservationId}:primaryGuest:{modified}` and
  `{reservationId}:additionalGuests[{i}]:{modified}` on the reservation,
  `{bookingId}:booker:{modified}` on the booking — the R4-1 convention with its slice-5
  amendment, each `modified` the object's own, in the ISO-8601 form Apaleo returns.
- `recordTimestamp`: the object's `modified`.
- `sourceObject`: type `reservation` or `booking`, id the object's id, role `PRIMARY_GUEST`,
  `ADDITIONAL_GUEST` or `BOOKER`, position `i` for additional guests, version `modified`,
  business start and end `arrival` and `departure` for a reservation and the earliest arrival
  and latest departure of its reservations for a booking.
- `payload`, top level, only the person's own fields under the names the engine's extractor
  reads: `firstName`, `lastName`, `email`, `phone`, `birthdate` (from `birthDate`) and
  `idDocument` as `{type, number}` from `identificationType` and `identificationNumber`. Every
  other person field — title, gender, middle initial, address, nationality, company, preferred
  language, the registration-card fields — goes under `payload.person` untouched, and every
  booking-level field — status, channel code, source, company, external references, comments,
  property, booking id — under `payload.reservation`.
- No `loyaltyId` and no `externalGuestId`: Apaleo persons carry neither a loyalty number nor an
  entity id, and inventing one would be exactly the identity the convention refuses to assume.
- Nothing from the booking's payment account or registered card, in any form.

**Rationale**: The extractor reads a fixed set of top-level names and nothing else, so the person
fields go there and everything else goes one level down, where it is kept for the audit trail and
extracted from by nothing (FR-004, R4-1's field-mapping rule). The engine hashes the id document
from type and number, so Apaleo's identification type enum is passed as the type without
translation; the hash makes the value comparable within Apaleo's own vocabulary, which is what
resolution needs.

**Alternatives considered**:

- *Flattening every person field to the top level.* Harmless for extraction today, because it
  reads only its names, but a later extractor field would silently start reading Apaleo data that
  was never reviewed for it. The nested form keeps the reviewed surface explicit.

---

## R4 — Knowing whether people changed

**Decision**: Per object — a reservation or a booking — the connector stores the modified instant
and a roster hash of the last version it submitted. The hash covers, for every person in role
order, the role, the position and the six extracted fields plus the name fields; for a booking it
also covers the derived dates, so a new reservation joining the booking moves its dates on the
timeline. A fetched object is submitted when the connector has no state for it or its hash
differs; a version with the same hash is not submitted, whatever its modified instant.

**Rationale**: This is the stateful variant R4-1 prefers. The content-derived key alternative
reuses an earlier key on an A→B→A revert and breaks the newest-version rule; the hash on the
connector's side keeps the key derived from Apaleo's state while still emitting only when people
changed. Hashing only the extracted and name fields means an address correction alone does not
emit; if a later extractor reads addresses, the hash gains the field in the same change.

**Alternatives considered**:

- *Submitting every version and letting the engine absorb duplicates.* Duplicates are absorbed
  only for identical keys, and every edit changes the key through `modified`; the engine would
  store a version per room change and the review threshold would suffer, which is the harm R4-1
  describes.

---

## R5 — Receiving events

**Decision**: `POST /apaleo/events/{secret}` answers 202 as soon as the body is stored, and a
worker processes the queue. Each event is stored by Apaleo's event id in a `processed_event` table
whose primary key makes the second delivery a no-op. Processing fetches the reservation (R2),
applies R4, submits (R7) and marks the event done; a failure keeps the event with its reason and
next attempt time, retried with exponential backoff and never discarded (FR-011). On startup the
connector lists its subscriptions, creates one for the configured properties and event types when
none exists, and replaces one whose endpoint or events differ. The reachability check is a POST
to the endpoint with an empty body; the endpoint answers 200 to it.

The event types default to `reservation/created`, `reservation/changed`, `reservation/amended`,
`reservation/picked-up-from-block`, `reservation/checked-in`, `booking/created` and
`booking/changed`, as FR-007 states, as a configuration list. A booking event fetches the
booking; a reservation event fetches the reservation and, when the connector has no state for
its booking yet, the booking too.

**Rationale**: Apaleo delivers at least once, unordered, and counts any non-2xx as failure with
retries for up to a day; the only safe receiver stores first and answers. Ordering needs no
handling: the version is the modified instant, and the engine orders by it (FR-019 of slice 3).
Dedup on Apaleo's own id is what its best-practices page asks for.

Neither `deleted` event is subscribed: the object cannot be fetched afterwards and the event
would retry forever. Everything already observed about it stays in the graph, as immutability
requires.

---

## R6 — Reconciliation and full sync

**Decision**: A sync point per property is the greatest reservation `modified` the connector has
finished submitting. Reconciliation lists reservations with `dateFilter=Modification`, `from` the
sync point minus one hour, sorted by update time, runs R4 on each, fetches the booking of every
reservation it submits, and reads the subscription once to confirm it still exists; it runs every
fifteen minutes by default and on request. A full sync lists every reservation of the properties
with no date filter and all five statuses, in the same order, then every booking, and advances
the sync point as it goes; it runs on request, once on first start when no sync point exists, and
on the connector's own initiative when the gap since its last successful activity is longer than
Apaleo's retry window of 24 hours — the only case in which a booking event can have been lost,
because the booking list cannot be listed by modification.

**Rationale**: The one-hour overlap covers the delay Apaleo states for delivery and any clock
skew between the two systems, at the cost of re-hashing an hour's worth of reservations, which
the hash makes free of submissions. Booking events are the channel for booker changes, so no
scheduled poll of the booking list exists; a poll would run every night to cover an outage that
happens rarely, and the full sync after such an outage walks the booking list anyway. Both runs
are idempotent by construction: R4 decides submissions, and the engine absorbs any duplicate key.

**Alternative considered**: *a nightly sweep of every booking.* Twenty reads a night for ten
thousand bookings, but the wrong shape — polling to cover a rare event — and a second mechanism
where the full sync already is one. Kept as nothing; an operator who suspects a gap runs a full
sync.

---

## R7 — Talking to the engine

**Decision**: On startup the connector registers its source system with `POST /source-systems`
and treats the conflict answer as already registered. Observations go to `POST /records` in
batches of up to one hundred, one reservation's persons never split across batches; every result
is read: `guestId` updates the held ids (R8), `DUPLICATE_IGNORED` and `needsReview` are counted,
and an `ERROR` result keeps the reservation for retry. The credential is a tenant API key
registered as agent-operated and named `connector-apaleo`; the connector sends no actor headers,
so every record it submits is attributed to that agent.

**Rationale**: The ingest contract already batches and answers per record, so the connector
needs no new endpoint. Keeping one reservation inside one batch keeps a version's roster
arriving together, which slice 3 asks of submitters. Reading every result is what turns a
fire-and-forget import into a connector that knows what happened to each person.

---

## R8 — Held guest ids and the integrator rule

**Decision**: A `held_guest_id` row per object, role and position stores the guest id from the
latest submitted version's result. A refresh, nightly by default and on request, reads `GET /guests/{id}`
for every distinct held id: `ACTIVE` leaves it, `MERGED` replaces it with the one current id and
logs both, `SPLIT` marks the row with the current ids and surfaces it in the status, `RETIRED`
marks it likewise. The connector never picks among several current ids (FR-019).

**Rationale**: This is R-X5 consumed as the roadmap asked: the connector holds ids only because
they resolve. The refresh is the rehearsal for write-back; when a later slice writes the id into
Apaleo, it writes the held id and nothing else changes.

---

## R9 — Operations surface

**Decision**: `GET /status` returns the report FR-014 lists as one JSON document; `POST /sync/full`
and `POST /sync/reconcile` start runs and answer 202 with the run id; `GET /runs/{id}` reports a
run's progress. Spring Boot Actuator's health endpoint is enabled for liveness. All of these sit
behind a bearer token from configuration, distinct from the webhook secret. Logging is structured
JSON with a fixed field set; person data is never a log argument, and the two credentials are
held in configuration properties the logger masks.

**Rationale**: A small surface an operator can read with curl and a health check a platform can
poll. The status is a single document because the question it answers — is this connector fine
— has one reader and one moment.

---

## R10 — Testing

**Decision**: Three layers. Pure-JVM unit tests for the mapping (R3) and the roster hash (R4) on
recorded Apaleo reservation documents. Integration tests with WireMock standing in for Apaleo and
for the engine, on Testcontainers PostgreSQL, covering every user-story walk: full sync with
paging and the 204 end, events in both orders and delivered twice, reconciliation after a gap,
the refresh under each guest status, and the status document. One end-to-end walk in the
quickstart against a local engine and an Apaleo sandbox, run by hand before release.

**Rationale**: The connector's correctness is in the mapping and the change rule, which are pure
functions worth pinning on real documents; everything else is protocol handling best tested
against recorded interactions. The engine is not published as an image, so a live engine in CI
would mean building it there; a stub of its contract, checked against the engine's own
`openapi.yaml`, is the honest substitute until an image exists.

---

## R11 — What only a sandbox can settle

Before the event list of FR-007 is fixed and before release, one session against an Apaleo
sandbox records:

1. which event type fires for a guest edit, an added guest, a removed guest, and a check-in with
   registration data (FR-007a);
2. whether the client-credentials client may create subscriptions, or a connect client is required
   for the Webhook API;
3. the rate-limit response: status, headers, and the limit itself;
4. the exact body of the reachability check and of a delivery;
5. whether a reservation's `modified` changes when only a person changes, which the convention
   assumes;
6. whether a booker edit moves the booking's `modified` only, or the reservations' too — the
   spec assumes only the booking's, which is why the booker is keyed by it.

Findings go into this document as amendments, dated, and into the default configuration.
