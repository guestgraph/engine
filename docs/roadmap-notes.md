# Roadmap notes — requirements captured for future slices

Requirements that surfaced during slice 1 but belong to later slices. Each slice's
`/speckit-specify` run MUST consume its section here.

## Cross-slice — capability parity with mutable-record identity systems

Identity services built on mutable rows (one row per source object, upserted in place)
offer conveniences that immutability removes; each needs an audit-preserving
replacement:

- **R-X1 Steward corrections (was: `PATCH /guest-identities`)** — corrections enter as
  ordinary immutable records via a built-in `manual-corrections` source system, so
  survivorship surfaces them and the audit trail shows who corrected what and when.
  Later: a first-class steward endpoint that writes such records.
- **R-X2 Correction protection / "our data wins" (was: invalid-email guard)** —
  recency-only survivorship lets the next source update overwrite a steward correction.
  Survivorship v2 needs per-source trust ranking (manual-corrections > PMS > channel
  manager) and/or steward field-pinning; also suppress values whose extraction was
  flagged invalid from the golden profile (today a malformed email still appears in
  `extracted`, only flagged).
- **R-X3 Forced manual attach/merge (was: `PATCH /persons` re-link)** — detach exists
  (unmerge) and queued conflicts exist (match-review), but a steward cannot yet merge
  two guests without a pending review. Add an audited manual-merge operation
  (REVIEW_CONFIRM-style event, matcher `manual-merge`).
- **R-X4 Storage growth** — append-per-observation grows where the old upsert did not.
  R4-1's emit-on-change rule removes most noise; if growth ever matters, add a
  retention/compaction policy for superseded observations that preserves the
  MergeEvent audit chain.
- **R-X5 The guest id as an external reference (was: a stable primary key)** — ✅ consumed by specs/004-retired-guest-ids — the point
  of a golden profile is that other systems can hold its `guestId` as *the* authoritative
  reference for a person. Today they cannot: a merge deletes the absorbed guest
  (`ResolutionEngine.execute` → `deleteGuest`) and `GET /guests/{absorbedId}` then returns a
  bare 404. The reference breaks on exactly the event this product exists to produce. An
  unmerge that empties a guest retires an id the same way (`UnmergeOperation`), so the
  mapping is not always 1:1.

  Observed, not inferred: ingest two records that resolve separately, then one carrying both
  identifiers. The merge reports `MERGED`, and `GET /guests/{absorbedId}` answers
  `404 {"detail":"No guest … in this tenant"}` — as if the person had never existed.

  Nothing is lost — `merge_event` is append-only and records both the survivor (`guest_id`)
  and `absorbed_guest_ids`, so the answer is already stored. What is missing is a query that
  walks it. Needed: retired ids resolve instead of 404ing, e.g. `GET /guests/{id}` returning
  `200 {"status":"MERGED","currentGuestId":…,"mergedAt":…}` — an HTTP-shaped redirect for
  identity — following merge chains transitively (X→Y→Z), and returning the several current
  ids when an unmerge fanned one out. Until this exists, integrators must be told plainly
  that a stored `guestId` can dangle, because the failure is silent and their foreign key
  looks fine right up until it doesn't.

  Cheap, self-contained, and it changes what GuestGraph *is* to an integrator: not a tool
  that de-duplicates a report, but the system of record for identity across the estate.

## Slice 2 — Probabilistic matching (additions) — ✅ consumed by specs/002-probabilistic-matching

- **R2-1 Negative match rules (persistent do-not-merge)** — ✅ delivered in slice 2 — v1 unmerge exclusions bind
  only the replay of the detached records; a *fresh* record carrying the shared
  identifier legitimately re-merges the guests (visibly, via a MERGE event). When a
  steward has explicitly split two people, matchers should be able to consult a
  persistent negative rule (e.g. suppressed identifier↔guest or guest-pair edges,
  written by unmerge/reject decisions) so the correction survives new evidence unless a
  human confirms otherwise. Natural companion to the review queue; per-tenant
  perfect-match/affiliate-style identifier quality rules belong to the same family.

## Slice 5 — Commercial layer / MCP

### R5-1: AI agent as merge steward (real goal, not a nice-to-have)

The review queue is deliberately agent-ready: entries carry per-signal score breakdowns,
`explain` + `/records` expose full evidence, decisions are exactly-once and reversible,
and `merge_event.matcher_name`/`evidence` already accommodate an agent identity and its
rationale. An AI steward is structurally "another imperfect matcher" — the same safety
machinery (review bands, unmerge, negative rules) that gates probabilistic scores gates
the agent.

**Operating model**: three-tier stewardship — rules decide the clear cases, the agent
(over MCP tools mapping 1:1 to the REST surface: list/decide reviews, explain, records)
decides high-confidence reviews and escalates ambiguous ones to a human with a
summarized recommendation.

**Prerequisites to build (small, some earlier than slice 5):**

1. ~~**Actor identity** on decisions~~ — ✅ delivered in slice 3. Credentials are registered as
   human- or agent-operated and carry a name; that type is the ceiling a request can never widen,
   though a request may name the individual behind a shared credential. Merge events, review
   decisions, unmerges, and do-not-merge rules all record it. Rules are now *lifted* rather than
   deleted, so prerequisite 3 below has the data it needs: the actor who overrode a split sits
   beside the actor who made it.
2. **Scoped API credentials** — a review-only key: read + decide reviews, but no
   unmerge, no config changes, no lifting of negative rules.
3. **FR-011 carve-out** — confirming across a do-not-merge rule lifts the rule; for
   agents this must be restricted: an agent never overrides a *human's* explicit split.
   Now enforceable: slice 3 records both the creating and the lifting actor on the same rule row,
   so the check is a single-row comparison. Deliberately not enforced yet — it belongs with the
   scoped credentials in (2).
4. **PII/data-residency posture** — review evidence is personal data; an MCP-connected
   agent ships it to a model provider. Needs tenant consent surface and likely an
   on-prem/EU-residency model option in the commercial offering.

## Cross-cutting decisions taken in later slices

- **One schema, one role per service** — ✅ engine side consumed by specs/006-engine-schema.
  Slice 5 gave the Apaleo connector its own schema,
  `apaleo_connector`, reached as a role that sees nothing else, so that sharing a database with
  the engine or not is a deployment choice (specs/005-apaleo-connector, data-model). The engine
  still runs in `public`, which quietly decides the topology for anyone deploying both. **The
  engine moves to a schema of its own, `engine`, with the same role rule, in a slice of its own
  before the first release**, while `V1__core_schema.sql` may still be edited and no consumer
  has a database to migrate. The migrations name no schema, so the move is a Flyway default
  schema, a URL search path, the Testcontainers harness, the ER script and one paragraph of
  deployment guidance. Slice 6 named the schema through the connection pool's schema property
  rather than a URL parameter, one value in one place; the connector did the same when its
  configuration was built (slice 5, T008).

- **One paging idiom.** Slice 3 moved `/match-reviews` and `/negative-rules` off raw
  `limit`/`offset` onto the same opaque keyset cursor the timeline uses. Offsets are a contract
  commitment that foreclose moving a read into SQL or changing an ordering; a cursor keeps that
  replaceable, and seeks rather than scanning-and-discarding on deep pages. Any new paged endpoint
  uses `api/Cursor.java`.

- **Slice 5 amendments taken during implementation.** The plan's structure tree drew a `state/`
  package; the connector lays out `persistence/entity` and `persistence/repo` as the engine does,
  without a mapper layer, because a cache has no domain to map to. Task T007 named a repository
  read of the connection by secret hash; a delivery routes through the configuration file, which
  FR-015a makes the authority, and a connection removed from the file keeps its row and its state
  as history, unread until configured again, so the secret hash carries no uniqueness in the
  table. Task T008 named the JDBC `currentSchema` parameter; the connector names its schema
  through the pool's schema property, as slice 6 chose for the engine. The `processed_event`
  table admits a `FAILED` state that nothing writes, because a failed event stays pending by
  design (FR-011); a later migration may drop the value or a terminal case may earn it.
  User stories 1 and 2 added four more. The submitter makes one engine call per object
  version rather than batching a hundred records, because a version's state is written only
  when every one of its results was read, and a batch across versions would tie their states
  together; SC-001 is met at one call per version, and batching returns only if a full sync of
  a large account proves too slow. A person whose only identification field is the type stays
  under `person` in the payload rather than becoming a bare `idDocument`, since a document type
  without a number identifies nothing. The event endpoint answers 400 as a problem detail to a
  body that is not an Apaleo event, a status the contract does not list; the contract lists what
  Apaleo sends, and a body Apaleo did not send has no place in it. Event submissions carry no run
  id, so user story 3 moved the status counters onto the connection row, where every
  submission counts whether a run or an event made it, and a run's own counters stay on the
  run. The connection rows are written on the application-ready event,
  and a delivery that arrives before them, in the seconds between the server listening and
  the rows written, answers 404 and is retried by Apaleo. The refresh of user story 4 walks no
  Apaleo list, so it runs beside a full sync or a reconciliation and never answers 409, which
  the contract never gave it; an id the engine does not know is left and counted, and the
  status reads the last finished refresh rather than the last one without such an id. Research R9 chose structured JSON
  logs for the connector; the family logs one way, as the engine does, in Logback's default
  text, so the connector dropped the structured format. FR-016 holds in either: what a line may
  carry is the rule, not its shape.

- **Sandbox session and quickstart walk, Sep 11, 2026 (research R11, tasks T033 and T034).**
  Run against an Apaleo test account with five properties and a hundred reservations, a local
  engine and a tunnel, with the connector at its pull request 11. The six items: (1) a guest
  correction, an added guest and a removed guest each fire `reservation/changed`; a check-in
  fires `reservation/checked-in` and changed no person field; a room assignment fires
  `reservation/amended` and submitted nothing, as the hash rule intends; the default event list
  stands and `picked-up-from-block` stays unverified. (2) A simple client, the client-credentials
  kind, may list and create subscriptions; no connect client is needed. (3) No 429 was met by a
  hundred reservations and their bookings; the limit stays unverified. (4) The reachability check
  is not an empty body: a JSON document with topic `system`, type `healthcheck`, the account, an
  empty `propertyIds` and a `timestamp` in epoch milliseconds, no entity, and it needs a 2xx; a
  reservation delivery carries `propertyId`, a booking delivery none; listing subscriptions with
  none answers 204; Apaleo returns `modified` in the property's offset, `+02:00` in the test
  account, not in Z, and the observation key carries it as returned. (5) A person-only edit moves
  the reservation's modified instant. (6) A booker edit moves the booking's modified only; no
  reservation event followed it. The walk found five things the code now does: the token error
  names the OAuth code, an unreadable connections file is named in quotes, the reachability check
  is answered 200, a booking event is served under a configured property list, and a removed
  guest releases its held slot. The second full sync submitted nothing, zero duplicates, where
  the quickstart's step 7 expected duplicates equal to the first run's records; the hash rule
  never sends an unchanged version, which is the stronger form of SC-002. Step 10, the gap rule,
  was not waited for; its integration test stands for it. Three observations without a change:
  the test account gives every guest one phone number, so the engine's probabilistic matcher
  queued 9,602 reviews during the first full sync, which is the data's doing; the engine logs a
  constraint violation at ERROR when a source system is registered a second time, though it
  answers 409, and a lookup before the insert would spare the line; and a booking was once
  resubmitted by the reconciliation right after its reservation's check-in, its hash moved while
  its key did not, and the engine dropped the copy as a duplicate.

## Next — Service conventions — ✅ consumed by specs/007-service-conventions

Every guestgraph Java service should have the same shape by check, not by hand: the engine and
the connector match in stack, guardrails and CI because the second copied the first, and nothing
compares them. A comparison on Sep 10, 2026 found the connector serving no API document where
the engine serves the union of its contracts at `/api-docs`, no request size cap on the webhook
endpoint, no ER diagram with a drift job, no local profile and a thinner error layer, and the
engine without a health endpoint. The slice: a repository `guestgraph/service-conventions`,
vendored at a pinned tag like `conventions/`, holding the PMD ruleset, the Spotless
configuration, the shared ArchUnit rules as source, the CI workflow templates and a service check
that asserts the list every service must have; each service runs its sync check and the service
check beside `verify` and `conventions`. Adding a repository means a conventions release naming
it in `REPOSITORIES.md`.

*Taken by slice 7, Sep 11, 2026*: the repository exists, released four times on the day, and both
services vendor it and pass the service check. Two decisions moved during the build. The served
API lives under `src/main/resources/api/` in every service, with `sources.json` beside the
documents naming where each comes from, rather than a POM bundling of the specs directory in the
engine and a root directory in the connector; the engine's contracts under `specs/` stay the frozen
records and are served as copies held equal to them. And what is shared is rules, configuration
and one test class, not runtime code: the size filter, the health endpoint and the document
controller are a few lines each service carries, because shared code needs a library and a
publish step the family does not have; that is the follow-on.

## Scale levers (when volume demands, not before)

- `ResolutionEngine.rebuildGuest` is O(records-on-guest) per ingest and loads full rows
  including jsonb payloads; for crowded guests, first switch to a projection without
  `payload` (survivorship never reads it), then incremental profile update.
- Per-tenant advisory lock serializes ingest within a tenant (~30–100 records/s); bulk
  backfills of millions per tenant want a bulk-import mode that pre-partitions records
  by identifier cluster.

## Slice 3 — Timeline / journey

### R3-1: "What reservations does this guest have?" — ✅ consumed by specs/003-timeline-journey

Slice 1 answers *"what did we observe about this person"* (`GET /guests/{id}/records`);
it deliberately does not answer *"what does this person currently have"*. Multiple
observations of the same source object (e.g. Apaleo reservation `R1` whose guest was
edited from person A to person B) live as independent immutable records on different
guests — both guests' record lists reference R1, with no supersession link.

Slice 3 made source objects (reservation first) first-class **associations** on resolved guests.
Note what changed from the sketch below: rather than grouping by `(object, role slot)` and
superseding slot by slot, **the object version became the unit of supersession** — the newest
version's complete person roster determines who is on the object, and persons are never matched
across versions. Sources carrying entity-less persons give no id to follow across edits, so slot
tracking would have to guess, and would report a reassignment every time a guest list shrank. The
roster model also makes *removal* detectable, which no slot scheme handles honestly.

The original sketch:

- Group observations by business-object identity (reservation id from the payload) and
  role slot (primaryGuest / additionalGuests[n] / booker).
- Later observations of the same `(object, slot)` supersede earlier ones: the event moves
  to the guest of the latest observation.
- Query contract: for the A→B reassignment case, guest B's timeline returns R1;
  guest A returns nothing for R1 (or an explicitly closed/transferred association —
  spec decision). **Decided**: omitted by default, returnable with `includePast=true` marked
  ENDED, naming a successor only for a genuine one-to-one handover of the role.
- The full observation history stays reachable (Constitution II — nothing is lost,
  supersession is a view, not a deletion).

### R3-2: A canceled booking on a guest's timeline — surfaced by specs/005-apaleo-connector

An association carries business dates but no status, so a canceled or no-show reservation looks
on the timeline exactly like one the guest will arrive for. The connector cannot express it: a
cancellation changes no person, so under the roster rule it submits nothing, and if it did submit,
the status would sit inside the payload where nothing reads it. If a consumer needs "does this
guest currently hold a booking" to exclude cancellations, the answer is an optional status on the
source-object block of the ingest contract and on the association, with a connector emitting a
version when it changes — an engine slice, not a connector one.

## Slice 4 — Connectors — ✅ consumed by specs/005-apaleo-connector

**R-X5 is built** ([specs/004-retired-guest-ids](../specs/004-retired-guest-ids/spec.md)), so
connectors may hold a `guestId`: writing one back into a PMS or CRM is what makes GuestGraph the
system of record rather than a report. The connector contract states the integrator rule rather
than a constraint: a stored guest id may be retired by a merge or a split; reading it answers
`MERGED` with the one current id, which the connector stores in its place, or `SPLIT` with
several, which the connector escalates rather than guesses. Every sub-resource under a retired
id refuses with the current ids, so a connector that skips the read still fails loudly.


### R4-1: externalKey convention for mutable, multi-person source objects (Apaleo pattern) — contract published by specs/003-timeline-journey; amended by specs/005-apaleo-connector

*Amended by slice 5*: the booker is a role on the **booking** object, not on the reservation. In
Apaleo the booker lives on the booking with the booking's own modified instant and events, and a
reservation only shows a copy of it on request; keyed by the reservation, a booker correction
would not change the key and would be lost as a duplicate. So a connector emits two source
objects — `reservation` with the primary and additional guests, `booking` with the booker — each
versioned by its own clock, and derives the booking's business dates from its reservations.

`externalKey` identifies an *observation*, not the source object (see slice-1 API
contract). For PMS reservations carrying entity-less persons the convention is:

```
{reservationId}:{personRole}:{entityModifiedTimestamp}
e.g. XPGMSXGF-1:primaryGuest:2026-07-09T14:30:00Z
     XPGMSXGF-1:additionalGuests[0]:2026-07-09T14:30:00Z
```

- **One record per person per version** — a reservation version with 3 persons emits 3
  records; the role segment prevents dedup-key collisions.
- **Version discriminator = the entity's own `modified` timestamp**, not the webhook
  event id: derivable from source state alone, therefore idempotent across webhook
  retries, duplicate change-pings that fetch the same final state, and full
  backfills/re-syncs. Two edits within timestamp granularity collapse to one
  observation (acceptable — the later state wins anyway).
- `recordTimestamp` = the same `modified` value, so survivorship and slice-3
  supersession order observations identically.
- **Emit the complete roster, only when people changed** *(amended by slice 3 — was
  "emit only on person-data change")*: a version carrying only the person who changed would read
  as a booking that lost its other guests, because the newest version's roster **is** the answer
  to who is on the object. So when any person's data or the guest list changed, emit every person
  on that version. Edits touching no person at all still emit nothing, which is where essentially
  all of the noise reduction lives. The original rationale, unchanged: the source bumps `modified`
  on *any* reservation edit (dates, room, price). Emitting person records for every edit is
  identity-neutral
  (they just re-attach) but pollutes the observation history and inflates the
  records-per-identifier count that feeds the review threshold — a chatty reservation
  could push a normal guest's email over the threshold and cause false review parkings.
  The connector MUST hash the extracted person fields per `(reservation, role)` slot and
  emit only when the hash changed. Stateless alternative: content-derived key
  `{reservationId}:{role}:{hash(personFields)}` lets the server dedup via
  DUPLICATE_IGNORED — but an A→B→A revert then reuses A's original key/timestamp, which
  breaks slice-3 "latest observation wins" ordering; prefer the stateful variant.
- **Field-mapping rule**: reservation-level contact data that is not personal (agency
  phone, property email, shared office numbers) MUST NOT be extracted as guest
  identifiers — persistent non-personal identifiers on a reassigned reservation would
  transitively merge different people (slice-1 review threshold is the backstop, not
  the fix).
