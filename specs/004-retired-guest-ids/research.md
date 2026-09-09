# Phase 0 Research: Retired Guest Ids Resolve

**Feature**: `004-retired-guest-ids` | **Date**: 2026-09-09

The spec left no clarification markers. Its two stated assumptions — resolution at guest level,
and refusal rather than redirect under a retired id — are design inputs here, not open
questions. This document resolves the technical unknowns behind them.

---

## R1 — Derive from the merge events, or record retirements?

**Decision**: A retired id is resolved by walking `merge_event`. No new table records
retirements, and nothing is written when a guest is retired.

**Rationale**: Every retirement is already recorded once. A merge writes a `MERGE` or
`REVIEW_CONFIRM` event whose `absorbed_guest_ids` names the retired guests and whose `guest_id`
is the survivor; an unmerge writes an `UNMERGE` event on the split guest naming the detached
records, and each detached record's replay writes the `CREATE`, `ATTACH` or `MERGE` event that
placed it. The table is append-only under a trigger and has been written this way since slice 1,
so the walk needs no backfill and has nothing to drift from. That is the property the roadmap
called "nothing is lost": the answer is stored, only the query is missing.

**Alternatives considered**:

- *A `guest_retirement` table written at merge and unmerge time* — one row per retired id with
  its successors. Rejected: a second copy of what the events already say, maintained at two
  mutation points, and every retirement before this slice would need a backfill computed by
  exactly the walk it was meant to replace.
- *Soft-deleting guests with a `retired_at` and `successor_id` column.* Rejected: a split has
  several successors and one column cannot hold them; `identifier` rows cascade on guest
  deletion today, so keeping the row would change what deletion means for every existing path.

---

## R2 — The walk

**Decision**: A pure-JVM `GuestIdResolver` in `io.guestgraph.resolution`, behind `GraphPort`
like `ExplainOperation`, with these rules.

*Active.* If the guest exists in the tenant, the id is active and the walk stops.

*Retirement event.* For a guest that does not exist, its retirement is the latest event among
those that could have removed it: a `MERGE` or `REVIEW_CONFIRM` whose absorbed list names it, or
an `UNMERGE` whose `guest_id` is it. Latest wins because ids are never reused, so the last such
event is the one after which the guest was gone; a partial unmerge that left the guest alive is
always followed by a later event, and the walk never sees it as the retirement.

*Successors.* For a merge, the survivor. For an unmerge, the guests the detached records landed
on: for each record the event names, the earliest event after it whose `source_record_ids`
names that record, and that event's `guest_id`. The replay writes exactly one such event per
detached record, so the lookup is total.

*Recursion.* Each successor is resolved the same way until every path ends at an existing
guest. A visited set guards against revisiting; termination is guaranteed anyway because every
hop moves strictly forward in event time.

*Never existed.* If the read id itself does not exist and has no retirement event, it never
existed in this tenant — the not-found answer of today. A cross-tenant id gives the same result
because every query carries the tenant, so no oracle is created (Constitution I).

*Status.* Reported from the outcome, not the first hop: `MERGED` when the walk ends at exactly
one guest, `SPLIT` when at several, `RETIRED` when at none. A client asks one question — can I
replace my stored id with one id? — and the status answers it. An id absorbed into a guest that
was later split fans out and reads `SPLIT`; a split whose branches later re-merged reads
`MERGED`. The hops keep the shape of the path for anyone who wants it.

*Dangling.* The walk ends at none only if the audit trail is inconsistent, which the spec's edge
case names as a defect to surface, not to paper over. The resolver reports what it found and
logs the id.

**Rationale**: The rules are subtle in the same way the timeline deriver's were — dedup across
branches, outcome-based status, the latest-event rule — and putting them in a pure-JVM class
behind the port is what lets the scenario tests pin every one of them on `InMemoryGraph` with
no database, which Constitution VI asks for on engine logic.

**Alternatives considered**:

- *Successors of an unmerge read from the current links of the detached records.* Cheaper —
  `guestOfRecord` is one indexed read — and always ends at active guests, but it skips every hop
  between the split and now, so a chain that later merged would show no merge. Rejected for hop
  fidelity; the forward scan of R3 finds the replay events at similar cost.
- *One recursive SQL statement.* Rejected for the reason slice 3 rejected it for the timeline:
  the rules would live in SQL where the scenario tests cannot reach them.

---

## R3 — Finding events by an id inside a jsonb array

**Decision**: One native `@Query` on `MergeEventRepo` using jsonb containment for the merge hop,
backed by a partial GIN index that covers only rows whose absorbed list is non-empty. The split
hop uses no containment query: it is a bounded forward scan from the split's timestamp over a
btree on `(tenant_id, created_at)`. Migration `V4__retired_guest_id_indexes.sql` adds those two
indexes.

**Rationale**: The cost of this feature sits on the write side, not the read. `merge_event` gains
one row per ingest, and ingest is the hot path, serialized per tenant by the advisory lock. A GIN
index is updated on every insert and is slower to maintain than a btree, so a full GIN index on
either jsonb column would charge every ingest for a lookup that runs only when a retired id is
read — an active guest is answered by the existence check before any jsonb is touched. Two facts
about the data make that charge avoidable.

*Absorbed ids are empty on almost every row.* Only `MERGE` and `REVIEW_CONFIRM` events carry any.
A partial index

```sql
create index merge_event_absorbed_gin on merge_event
    using gin (absorbed_guest_ids jsonb_path_ops)
    where absorbed_guest_ids <> '[]'::jsonb;
```

is maintained only when a merge happens, a small fraction of ingests, and stays proportional to
the number of merges rather than the number of records. The query it serves is

```sql
select * from merge_event
 where tenant_id = :tenantId
   and absorbed_guest_ids <> '[]'::jsonb
   and absorbed_guest_ids @> cast(:needle as jsonb)
 order by created_at, id
```

with `:needle` a one-element JSON array; the non-empty predicate is repeated so the planner can
use the partial index. JPQL has no containment operator, and native queries are the sanctioned
explicit-SQL corner of this repository — `GuestRepo`, `MatchReviewRepo`, `NegativeMatchRuleRepo`
and `TenantRepo` each carry one — and remain `@Query` methods with a tenant predicate, so every
ArchUnit rule holds unchanged.

*The replay events sit right after the split.* `UnmergeOperation` replays every detached record
inside the same transaction that writes the `UNMERGE` event, so the events that placed those
records are the next events in the tenant's timeline. The split hop therefore reads the tenant's
events from the split's `created_at` forward, in order, and stops once every detached record has
been seen in a `source_record_ids` list. That is a JPQL range query over a btree

```sql
create index merge_event_tenant_time_idx on merge_event (tenant_id, created_at, id);
```

which is append-friendly — inserts land at the right edge — and costs ingest almost nothing.
The scan is bounded in practice by the size of the replay, a handful of rows; the resolver reads
in pages so a pathological gap cannot load a tenant's history into memory.

**Alternatives considered**:

- *Two full GIN indexes, one per jsonb column.* The first draft of this plan. Rejected for the
  write-side reason above: it puts the cost on the hot path for a rare read.
- *No index at all.* Correct, and free on write, but a merge-hop lookup becomes a scan of the
  tenant's events filtered by containment; at a million events per tenant, ten hops would not
  meet SC-003.
- *A companion table with one row per absorbed guest*, written in the same transaction as the
  event and backfilled from existing rows in the migration by one insert-select. Btree, plain
  JPQL, no native query, and the only alternative that removes the jsonb query entirely. Rejected
  for now because it is code that must be kept in step with the event, where an index cannot
  drift. It is the fallback if the partial index ever shows in a profile.
- *Successors of a split read from the current links of the detached records.* One indexed read
  each, but it skips every hop between the split and now (see R2). The forward scan keeps hop
  fidelity at similar cost.

---

## R4 — API shape

**Decision**: `GET /guests/{guestId}` answers 200 for every id that exists or existed in the
tenant. An active guest returns the document it returns today plus `status: ACTIVE`. A retired
id returns a resolution document: the read `id`, `status` (`MERGED`, `SPLIT` or `RETIRED`),
`currentGuestIds`, `retiredAt` and `hops`. Sub-resources and operations under a retired id —
`/records`, `/explain`, `/timeline`, `/unmerge` — answer **410 Gone** as problem details of type
`guest-retired`, carrying `guestId`, `resolutionStatus` and `currentGuestIds` as extension members —
`resolutionStatus` because RFC 9457 already owns the member named `status`.

**Rationale**: The spec fixes the observable behavior — a retired id's read succeeds, an active
guest's read is unchanged but for a status, sub-resources refuse with a pointer — and this is the
smallest contract that meets it. One discriminator, `status`, present on both shapes of the
guest document, is what lets a client tell them apart with one field (FR-005). The retired
document carries no `profile` or `identifiers` key at all, so a client that ignores `status` sees
missing fields rather than an empty profile it might mistake for a guest.

410 rather than 404 for the sub-resources because FR-009 wants a retired id and an unknown id
distinguishable, and a status code does that before a client parses anything. The resource
existed at that address and does not anymore, which is what 410 says; the extension members say
where it went. RFC 9457 allows the members, and Spring's `ProblemDetail` carries them as
top-level properties.

**Alternatives considered**:

- *A 3xx redirect from the retired id to the current guest.* The roadmap's phrase "HTTP-shaped
  redirect for identity" invites it. Rejected: a split has no single target, and a client library
  that follows redirects silently would read the current guest without ever learning that its
  stored id is stale — the failure this slice exists to make loud.
- *404 with extension members for the guest resource itself.* Keeps the old status code for old
  clients, but the spec's FR-001 requires the read to succeed, and it would mean a 404 that a
  client is supposed to parse for a success payload.
- *A separate `/guest-ids/{id}` resolution resource.* Clean for typed clients, but it leaves
  `GET /guests/{id}` answering 404 for a retired id, which is the silent failure the roadmap
  named. Not chosen; it can be added later as a pure alias if a consumer wants a resolution
  endpoint that never returns a profile.

---

## R5 — Which contract file carries the change

**Decision**: This slice's `contracts/openapi.yaml` declares the new schemas and the 410
response and declares no path. The four prior operations whose responses change are amended in
the contract files that own them: `getGuest`, `getGuestRecords`, `explainGuest` and
`unmergeGuest` in slice 1's, `getGuestTimeline` in slice 3's.

**Rationale**: The served document is the union of every feature contract, and its merger
refuses a path declared twice — `ApiDocsController.merge` throws on a duplicate. So an operation
can only be described in the file that first declared it. Slice 3 set the precedent when it moved
`/match-reviews` and `/negative-rules` to cursor paging (its research R9) and edited the slice-1
and slice-2 contract files, stated in its plan rather than absorbed silently. The same rule
applies here: `specs/*/contracts/openapi.yaml` is the owner of the API surface in the
documentation map, and that ownership is per path, not per slice. The frozen-spec rule protects
decisions; the contract files are the living surface.

The 004 file still exists so the merged document's `info` carries this feature's description and
version, and so the new schemas have a home.

---

## R6 — One guest gate for every guest-rooted endpoint

**Decision**: A small `GuestGate` in `io.guestgraph.api` that, given a tenant and a guest id,
returns nothing for an active guest, throws `NotFoundException` for an id that never existed and
throws a new `RetiredGuestException` carrying the resolution for a retired one. It replaces the
four `No guest … in this tenant` sites: records, explain and unmerge in `GuestController`, and
the timeline's existence check.

**Rationale**: Four sites that each answer the same question the same way are four places for
the retired case to be forgotten. The gate keeps the timeline's deliberate cheap existence check
— it asks `guestExists` first and only resolves when that says no, so the hot path pays nothing
new. `ApiExceptionHandler` maps `RetiredGuestException` to the 410 of R4.

---

## R7 — Migration and test-harness consequences

**Decision**: `V4__retired_guest_id_indexes.sql` with the partial GIN index and the btree of R3,
and nothing else.

- No new table, so `PostgresIntegrationTest.resetDatabase` is unchanged.
- `./scripts/regen-er.sh` runs anyway because the er-drift job re-runs it on every migration
  change; indexes are not part of the rendered diagram, so the expected diff is empty.
- `InMemoryGraph` implements the three new port methods over its existing event list.
- The unmerge fan-out relies on the replay events naming the detached record. Verified in
  `ResolutionEngine.decisionEvent`: `CREATE`, `ATTACH` and `MERGE` each carry the resolved record
  in `source_record_ids`, and `UnmergeOperation` replays every detached record through it.

**Follow-on, not in this slice**: a guest removed under lawful erasure (Compliance section of the
constitution, not yet built) would read as never-existed. When erasure is built, its design
decides whether an erased id resolves to an explicit `ERASED` status or to not-found; this walk
gives it a place to say so.
