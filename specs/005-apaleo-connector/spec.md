# Feature Specification: Apaleo Connector

**Feature Branch**: `005-apaleo-connector`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Slice 5 — Connectors. The first connector is Apaleo, read only, living in its own guestgraph repository. It consumes roadmap-notes R4-1 (the externalKey convention for mutable, multi-person source objects: one observation per person per version, the version is the entity's own modified instant, roster-complete emission only when people changed, booking-level contact data stays out of the person fields) and the integrator rule slice 4 added (a stored guest id may be retired; on MERGED replace it, on SPLIT escalate). Write-back of the guest id into the PMS is a later slice."

## Clarifications

### Session 2026-09-09

- Q: Which connector comes first? → A: Apaleo. The roadmap's R4-1 convention was written for its reservation model: entity-less persons, a modified instant per entity, webhooks plus full re-sync.
- Q: Where does the connector live? → A: In its own repository, `guestgraph/connector-apaleo`, a standalone service that talks to the engine over its REST API, the way a third-party connector would. It is a new member of the family, so `REPOSITORIES.md` gains a row through a robertblust/conventions release before the repository is created; that step belongs to the owner, not to this slice's tasks.
- Q: Are bookings in scope, or only reservations? → A: Both, as two source objects. In Apaleo the booker lives on the booking, with the booking's own modified instant and its own events, while the primary and additional guests live on the reservation. Keying the booker by the reservation would lose a booker correction as a duplicate, because the reservation's clock does not move. The booker is therefore an observation on the booking object; this amends roadmap note R4-1, which put the booker on the reservation.
- Q: Does one connector instance serve one hotel or many? → A: Many. An instance serves a list of connections, each one engine tenant with its key and one Apaleo account with its credential and properties. Every row of the connector's state and every query carries the connection, enforced the way the engine enforces the tenant, because the constitution's first principle says tenancy is cheap on day one and brutal to retrofit. Connections come from configuration for now, so no secret sits in a database; a store for them, fairness between connections, per-tenant operator access and Apaleo's multi-account client are later additions that change no table.
- Q: Does the first connector write the guest id back into Apaleo? → A: No. It reads reservations and persons, holds the guest ids the engine returns, and follows the integrator rule on them. Writing into a PMS field is a later slice once the field is known.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Property's Reservations Become Observations (Priority: P1)

A hotel connects its Apaleo account and every booking and reservation the property has, past and future, reaches the guest graph as observations that resolve into guests. Apaleo keeps persons on two objects: a reservation carries the primary guest and the additional guests, and the booking that groups one or more reservations carries the booker. Each object has its own modified instant. The connector emits one observation per person per object version, keyed by the convention slice 3 published, so that the graph can tell which observations describe one reservation or booking and which version of it is current.

Nothing here invents anything about the person. The connector copies what Apaleo states, puts the documented person fields where the engine's extraction reads them, and nests everything that belongs to the booking rather than to the person where extraction never looks. A full re-run of the same account produces no new observations, because every key is derived from Apaleo's own state.

**Why this priority**: It is the slice's reason to exist and the first time real source data reaches the graph. Without it there is nothing to keep current and nothing to hold a guest id for.

**Independent Test**: Point the connector at an Apaleo sandbox account with a few dozen reservations and run a full sync. Verify that every reservation appears as a source object in the engine with the right roster, that persons resolved into guests, and that a second full sync reports every observation as a duplicate and changes nothing.

**Acceptance Scenarios**:

1. **Given** an Apaleo account with bookings and reservations, **When** a full sync runs, **Then** every reservation and every booking the account can list is submitted, one observation per person on its current version, and each observation's key names the object, the person's role and position, and that object's own modified instant.
2. **Given** a booking with one reservation carrying a primary guest and two additional guests, and a booker who is a different person, **When** both are submitted, **Then** three observations reach the engine for the reservation and one for the booking, the reservation's roster lists three entries with their roles and the booking's lists the booker, and the booker is not merged with the primary guest by anything the connector sent.
3. **Given** a booking whose booker is the same person as its reservation's primary guest, **When** both are submitted, **Then** both observations are sent and resolve to the same guest by their shared identifiers, not by any assumption the connector makes.
4. **Given** a reservation, **When** its observation is inspected in the engine, **Then** its timestamp equals the reservation's modified instant, its business start and end equal arrival and departure, and its payload carries the booking's own data — channel, status, comment, property — nested below the person fields where the engine extracts nothing from it.
5. **Given** a completed full sync, **When** the same sync runs again with no change in Apaleo, **Then** every submission is reported as a duplicate and no guest changes.
6. **Given** a booking made through a company or agency, **When** it is submitted, **Then** the booker is still submitted as a person, because Apaleo models every booker as one with at least a last name, while the reservation's company, channel and external references travel as booking-level context inside the payload and produce no guest identifier. The booking's payment account and registered card never travel at all.
9. **Given** a booking, **When** its observation is inspected in the engine, **Then** its business start and end are the earliest arrival and the latest departure of the booking's reservations, derived from Apaleo's state so the booking takes its place on the timeline among the stays it groups.
7. **Given** an account with more reservations than one page can hold, **When** a full sync runs, **Then** every page is fetched and no reservation is skipped or submitted twice.
8. **Given** a reservation whose modified instant cannot be read, **When** it is submitted, **Then** the observation is still sent and the engine flags it for review as the ingest contract requires; the connector never drops it.

---

### User Story 2 - Changes Arrive Within a Minute (Priority: P2)

Reservations change all day: a guest is corrected, a co-traveler is added, a booking is canceled. Apaleo announces each change by a webhook that carries only the reservation's id, at least once, in no guaranteed order, and retries until the connector answers. The connector subscribes to reservation events, answers immediately, fetches the reservation's current state and submits a new version's observations when, and only when, the people on it changed.

The order problem is solved by the convention, not by the connector: because the version is the reservation's own modified instant, a late-arriving older event submits an older version, which the graph orders correctly and never lets displace a newer roster. Duplicate events are recognized by their id and processed once.

**Why this priority**: The full sync of story 1 is a snapshot; this story is what keeps the graph current between snapshots and what makes the connector a live integration rather than an import.

**Independent Test**: With a subscription in place, edit a reservation's primary guest in Apaleo, then edit only its room. Verify one new version reached the engine for the first edit, none for the second, and that the engine's roster shows the corrected guest.

**Acceptance Scenarios**:

1. **Given** an active subscription, **When** a reservation event arrives, **Then** the connector acknowledges it at once and processes it separately, so Apaleo never sees the connector as failed while it fetches and submits.
2. **Given** an event for a reservation whose primary guest was corrected, **When** it is processed, **Then** the connector fetches the reservation and submits every person on the new version, so the version is a complete roster.
2a. **Given** a booking event because the booker was corrected, **When** it is processed, **Then** the connector fetches the booking and submits the booker on the booking's new version, and the booking's reservations are not resubmitted, because no person on them changed.
3. **Given** an event for an edit that changed no person and no roster, such as a room or rate change, **When** it is processed, **Then** nothing is submitted, because Apaleo bumps the modified instant on every edit and emitting would inflate the observation counts that feed the review threshold.
4. **Given** the same event delivered twice, **When** both arrive, **Then** the second is recognized by its event id and causes no fetch and no submission.
5. **Given** two events for one reservation that arrive in the wrong order, **When** both are processed, **Then** the newer version is the engine's current roster regardless of arrival order, and the older version is stored as history.
6. **Given** a reservation is canceled, checked in, checked out, set to no-show or assigned a unit without any person changing, **When** the connector learns of it, **Then** nothing is submitted: no person changed, and the graph has no field a status could land in. A cancellation removes nobody from the guest graph, because the observations happened; whether a canceled booking should leave a guest's timeline is an engine question, recorded in the roadmap.
7. **Given** an event for a reservation the connector cannot fetch, because Apaleo answers an error or the reservation is gone, **When** it is processed, **Then** the event is kept for retry with the reason, and a later reconciliation run picks the reservation up; nothing is silently dropped.
8. **Given** the connector was down for a period, **When** it comes back, **Then** a reconciliation run lists every reservation modified since the last successful point, with an overlap, and submits what the webhooks missed.

---

### User Story 3 - The Connector Can Be Operated (Priority: P3)

A hotel's IT contact, or a GuestGraph steward, needs to know whether the connector is healthy, when it last synced, how many observations it submitted and how many events wait for retry. The connector reports that, runs reconciliation on a schedule, and can be told to run a full sync again. It holds its Apaleo credential and its engine credential as configuration and never prints either.

**Why this priority**: A connector nobody can observe is one that fails silently, which is the failure mode this whole family of slices exists to remove. It ranks below the two data stories because it observes them.

**Independent Test**: Start the connector with two connections configured and read the status; break one connection's Apaleo credential and read it again; trigger a full sync on the other and watch only its counters move.

**Acceptance Scenarios**:

1. **Given** a running connector, **When** its status is read, **Then** it reports, per connection, the tenant and the account and properties served, the time of the last successful full sync and reconciliation, the count of observations submitted, duplicates absorbed, records flagged for review, events pending retry, and whether the webhook subscription is active.
2. **Given** one connection's Apaleo credential stops working, **When** its next fetch fails, **Then** that connection's status shows the failure and its time, the connector keeps acknowledging its events and queueing them rather than losing them, and every other connection continues unaffected.
3. **Given** an operator request for a full sync, **When** it runs, **Then** it proceeds like story 1 and the status shows its progress and completion.
4. **Given** the connector's configuration, **When** any log, status or error is produced, **Then** no credential and no person data appears in it; person data stays in the submissions to the engine.
5. **Given** a scheduled reconciliation, **When** it runs, **Then** it behaves as story 2's scenario 8 and records its outcome in the status.

---

### User Story 4 - The Guest Ids the Connector Holds Stay Valid (Priority: P4)

Every submission answers with the guest each person resolved to. The connector keeps those ids per object, role and position, because they are what a later slice will write back into Apaleo and what an operator asks for today when a front-desk question comes in. A guest id can be retired by a merge or a split after the connector stored it. The connector applies the integrator rule slice 4 defined: it reads the id, replaces it with the current one when the answer is MERGED, and raises a split for a person to decide when the answer is SPLIT, never guessing.

**Why this priority**: It is the consumption of R-X5 that the roadmap tied to this slice, and the rehearsal for write-back. It is last because nothing is written into Apaleo yet, so a stale id costs an operator a lookup, not a wrong record.

**Independent Test**: Sync a reservation, merge its guest with another in the engine, run the connector's refresh, and verify the stored id moved to the survivor. Then unmerge a guest into several and verify the connector reports a split rather than picking one.

**Acceptance Scenarios**:

1. **Given** a submitted reservation, **When** the connector's held ids are read, **Then** each person on the current version has the guest id the engine answered.
2. **Given** a held guest id that was absorbed by a merge, **When** the refresh runs, **Then** the held id is replaced by the current guest and the change is logged with both ids.
3. **Given** a held guest id that was emptied by a split, **When** the refresh runs, **Then** the held id is kept, marked as split with the current ids, and surfaced in the status for a person to resolve.
4. **Given** a held guest id that is still active, **When** the refresh runs, **Then** nothing changes and nothing is logged.
5. **Given** a reservation resubmitted on a new version, **When** the engine answers, **Then** the held ids for that reservation are updated from the answer, so a reassigned reservation holds the new person's guest.

---

### Edge Cases

- Apaleo records the modified instant without a fractional second, so two edits within one second collapse to one version; the later state wins, as the convention accepts.
- A reservation whose persons carry no identifier at all, only a name: it is submitted anyway and resolves by whatever the engine's matchers can do; the connector never filters on data quality.
- A person on a reservation is removed and then re-added on a later version: two versions are submitted, and the graph's roster rule handles both; the connector tracks no person across versions.
- An event arrives for a property the connector is not configured to serve: it is acknowledged and ignored, and counted in the status.
- The engine is unreachable while events arrive: events are acknowledged and queued; submissions resume when the engine answers, in any order, because the keys carry the version.
- The engine answers a retired-id refusal on a sub-resource the connector reads: the connector treats it as the integrator rule instructs and reads the current guest instead.
- Apaleo's rate limit is hit during a full sync: the connector slows down and continues; a full sync that takes an hour is acceptable, a full sync that stops is not.
- The same Apaleo account is configured under two connections to two engine tenants by mistake: each connection is scoped end to end, so the mistake produces two independent graphs, not a leak between them, and the status shows both.
- Two connections name the same webhook secret: the configuration is refused at start, because the secret is what routes a delivery to its connection.

## Requirements *(mandatory)*

### Functional Requirements

**Observations**

- **FR-001**: The connector MUST emit one observation per person per object version, for two object types: a `reservation`, whose persons are the primary guest and the additional guests with their positions, and a `booking`, whose person is the booker. The object id is Apaleo's id for that object and the version is that object's own modified instant.
- **FR-002**: The observation key MUST be derived from the reservation id, the role with its position, and the modified instant, and from nothing that the connector itself generates, so that any repetition of the same source state produces the same key.
- **FR-003**: The observation's timestamp MUST equal the version. Business start and end MUST be the reservation's arrival and departure, and for a booking the earliest arrival and the latest departure of its reservations, derived from Apaleo's state and from nothing else.
- **FR-004**: The person fields the engine extracts — first and last name, email, phone, birthdate, address, identification document — MUST be filled only from the person's own entry in Apaleo; every booking-level field MUST be nested inside the payload where extraction does not read.
- **FR-005**: The booker MUST be read from the booking object, not from the copy a reservation shows on request, and MUST be submitted as a person like the others; the reservation's company, channel, source and external references MUST travel as booking-level context inside every observation's payload and never in the person fields. The booking's payment account and registered card MUST NOT travel in any form.
- **FR-006**: The connector MUST submit every reservation and every booking it can list or fetch, including canceled and no-show reservations, and MUST NOT filter on the quality or completeness of person data (Constitution III applies to the connector as to the engine).

**Keeping current**

- **FR-007**: The connector MUST subscribe, for the configured properties, to the event types that can carry a change of person: on the reservation, created, changed, amended and picked-up-from-block, which do, and checked-in, which probably does because registration data is captured then; on the booking, created and changed. The list MUST be configuration with that default, so an operator can widen it without a release. Event types that cannot change a person — check-out, no-show, cancellation, unit and payment events on either object — are not subscribed, and the deleted events are never subscribed because the object can no longer be fetched. The roster comparison of FR-009 still decides whether anything is submitted; the subscription only decides what is fetched.
- **FR-007a**: Which edits fire which event type MUST be confirmed against an Apaleo sandbox before the default list is fixed: a guest edit, an added guest, and a check-in with registration data, each observed for the event it fires. The scheduled reconciliation of FR-012 is the backstop for any change the list misses.
- **FR-007b**: The webhook endpoint MUST answer Apaleo's reachability check, which posts to the endpoint before and while a subscription exists, with success, and MUST carry a secret in its URL so that only Apaleo's deliveries are processed.
- **FR-008**: On an event, the connector MUST fetch the object's current state from Apaleo and MUST NOT rely on any state carried in the event beyond the object's id.
- **FR-009**: The connector MUST submit a version only when a person's data or the set of persons changed since the last version it submitted for that object, and then MUST submit every person on it. It MUST keep the state that makes this comparison possible, per object.
- **FR-010**: Events MUST be processed once each, recognized by Apaleo's event id, and MUST be tolerated in any order.
- **FR-011**: An event whose reservation cannot be fetched or submitted MUST be retained with its reason and retried; it MUST NOT be dropped.
- **FR-012**: A reconciliation MUST list every reservation modified since the last successful point, with an overlap, submit what was missed, and fetch the booking of every reservation it submits; it MUST run on a schedule and on request, and it MUST check on each run that the subscription still exists, so a lost subscription shows in the status within one interval.
- **FR-012a**: Booking events are the channel for booker changes, delivered and retried by Apaleo for a day. Because the booking list cannot be filtered by modification, a booker-only edit missed during a gap longer than that retry window is recovered by a full sync, which the connector MUST start on its own when it finds the gap since its last successful activity longer than the window. No scheduled poll of the booking list exists.
- **FR-013**: A full sync MUST be runnable on request and MUST page through every reservation of the configured properties and every booking of the account.

**Operation**

- **FR-014**: The connector MUST expose a status that reports what it serves, when it last synced and reconciled, its submission counters by outcome, the events pending retry, the splits awaiting a person, and whether its subscription is active.
- **FR-015**: The connector MUST serve a configured list of connections, each pairing one engine tenant and its credential with one Apaleo account, its credential and its properties. Every row of state and every query MUST carry the connection, and nothing read or written under one connection MUST be visible under another (Constitution I). A failure on one connection MUST NOT stop another.
- **FR-015a**: Connections and their secrets MUST come from configuration, never from the connector's database, so that no credential is stored at rest by this slice; adding a connection is a configuration change.
- **FR-016**: No credential and no person data MUST appear in logs, status or errors.
- **FR-017**: The connector MUST slow down on Apaleo rate limiting and continue, never abort a sync.

**Guest ids**

- **FR-018**: The connector MUST hold, per object, role and position, the guest id the engine answered on the latest submitted version.
- **FR-019**: A refresh MUST read every held id, replace it with the current guest when the answer is MERGED, mark it and surface it when the answer is SPLIT, and leave it when ACTIVE; it MUST NOT choose among several current guests.

### Key Entities

- **Reservation version**: one state of an Apaleo reservation, identified by its id and modified instant, carrying arrival, departure, one of the five statuses Apaleo defines and its primary and additional guests.
- **Booking version**: one state of an Apaleo booking, identified by its id and modified instant, carrying the booker and the list of its reservations; its dates are derived from theirs. A person carries at least a last name and may carry names, email, phone, address, nationality, birth date and place, an identification document with its type and number, a company and a preferred language.
- **Observation**: the engine's record of one person on one reservation version, keyed by the convention, with the person fields at the top of the payload and the booking's data nested below.
- **Event**: an Apaleo webhook delivery, identified by its id, naming a reservation; processed once, retried until it can be.
- **Sync point**: the modified instant up to which the connector has submitted every reservation of a property; the start of the next reconciliation, minus an overlap.
- **Held guest id**: the guest the engine answered for one reservation and role, with its refresh outcome.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A full sync of an account with 10,000 reservations completes within one hour and produces exactly one observation per person per current version, verified by counting source objects in the engine.
- **SC-002**: A second full sync of an unchanged account produces zero new observations and zero guest changes.
- **SC-003**: A change to a person on a reservation is visible in the engine's roster within two minutes of the change in Apaleo, given the one-minute delivery Apaleo states.
- **SC-004**: Over a day of edits in a sandbox, edits that touched no person produce zero submissions, and every edit that touched a person produces exactly one submitted version.
- **SC-005**: After a simulated outage of one hour, reconciliation submits every version missed, verified against Apaleo's list of reservations modified in that hour.
- **SC-006**: After a merge and a split in the engine, the connector's held ids show the survivor for the merge and a raised split for the split, with no id chosen by the connector.
- **SC-007**: No credential and no person field value appears in any log line or status response over a full test run, verified by search.

## Assumptions

- Apaleo is read with the client-credentials flow, one credential per account, which is what the simple client offers; multi-account connect clients are a later concern for the managed offering.
- Apaleo's webhook carries only the reservation id and is delivered at least once, out of order, with retries; the connector's design follows Apaleo's stated behavior and needs no assumption beyond it. Sixteen reservation event types exist; the connector subscribes to the five that can carry a person change and confirms that set against a sandbox (FR-007, FR-007a).
- Apaleo's reservation list pages at up to 500 items, answers an empty page with no content rather than an empty list, filters by modification date and can be sorted by update time; the reconciliation walks it in that order. The booking list pages the same way but has no modification filter and no sort, which is why bookings are reconciled through their reservations and, after a gap longer than Apaleo's retry window, by a full sync.
- The connector keeps state: the last submitted version and person hash per object, the processed event ids, the sync points and the held guest ids. That state is a cache of Apaleo's and the engine's facts; losing it costs a full sync, not correctness. It lives in one database schema of the connector's own, reached as a role that sees nothing else, so sharing a database with the engine or not is a deployment choice.
- Each connection's engine credential is a tenant key registered as an agent-operated credential named for the connector, so anything it does is attributed. A key scoped to ingest and reads only is roadmap R5-1 prerequisite 2 and is out of scope here.
- One instance, many connections, in data and queries from the first table. Around that, the connector stays single-operator and configuration-driven: encrypted secret storage with a management endpoint, fairness between connections under load, per-tenant operator access and Apaleo's multi-account client are later slices that add and do not rewrite.
- The engine's ingest contract, source-object endpoint and retired-id resolution are used as published; this slice asks the engine for nothing new. If implementation finds a gap, it is recorded in the roadmap and built here in a separate change.
- The repository `guestgraph/connector-apaleo` is created by the owner after `REPOSITORIES.md` names it, vendors the conventions at the pinned release, and carries its own suite and the shared conventions job. This spec lives in the engine repository because the roadmap and the slice history live here; the connector repository links to it.
- Write-back of guest ids into Apaleo, and any connector for a second system, are later slices.
