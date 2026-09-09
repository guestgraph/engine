# Data Model: Retired Guest Ids Resolve

**Feature**: `004-retired-guest-ids` | **Date**: 2026-09-09 | Migration: `V4__retired_guest_id_indexes.sql`

No table changes shape and none is added. The slice is a read over `merge_event` as it has been
written since slice 1; the only migration adds two indexes so that read stays fast at scale
(research R3).

---

## Changed table: `merge_event` (indexes only)

| Index | Definition | Serves |
|---|---|---|
| `merge_event_absorbed_gin` | `USING gin (absorbed_guest_ids jsonb_path_ops) WHERE absorbed_guest_ids <> '[]'::jsonb` | "which event absorbed guest X" — the merge hop. Partial, so only merge rows maintain it (research R3) |
| `merge_event_tenant_time_idx` | `(tenant_id, created_at, id)` btree | the tenant's events from an instant forward — the split hop's bounded scan |

Columns, constraints and the append-only trigger are untouched. Existing rows need no backfill:
every retirement since slice 1 is already expressed in `absorbed_guest_ids`, `source_record_ids`
and `created_at`.

---

## Read model (derived, never persisted)

### `GuestIdResolution`

The answer to "where is the person this id referred to". Computed per request by
`GuestIdResolver` (research R2) and returned by `GET /guests/{guestId}` for a retired id.

| Field | Type | Rule |
|---|---|---|
| id | uuid | the id that was read |
| status | `MERGED` \| `SPLIT` \| `RETIRED` | from the outcome: one current guest, several, or none (research R2) |
| currentGuestIds | uuid[] | every active guest the walk ended at, each once, in first-reached order |
| retiredAt | instant | `createdAt` of the read id's own retirement event |
| hops | `ResolutionHop[]` | every retirement the walk crossed, in the order it happened |

An active guest is not a `GuestIdResolution`; it is the existing guest document with
`status: ACTIVE` added (FR-005, FR-012).

### `ResolutionHop`

One retirement on the way from the read id to a current guest.

| Field | Type | Rule |
|---|---|---|
| retiredGuestId | uuid | the guest this hop retired |
| kind | `MERGE` \| `SPLIT` | `MERGE` for a `MERGE` or `REVIEW_CONFIRM` event, `SPLIT` for an `UNMERGE` that emptied the guest |
| eventId | uuid | the retiring event, so a reader can find it in the current guest's explain (Constitution IV) |
| at | instant | the event's `createdAt` |
| successorGuestIds | uuid[] | for `MERGE` the survivor; for `SPLIT` the guests the detached records landed on |

A successor that is itself retired appears as the `retiredGuestId` of a later hop; a successor
that is active appears in `currentGuestIds`.

### `RetiredGuestProblem`

The 410 problem details every sub-resource of a retired id answers with (research R4).

| Member | Type | Rule |
|---|---|---|
| type | uri | `https://guestgraph.io/problems/guest-retired` |
| title | string | `Guest id retired` |
| status | 410 | |
| detail | string | names the id and the current guest ids |
| guestId | uuid | extension: the id that was read |
| resolutionStatus | `MERGED` \| `SPLIT` \| `RETIRED` | extension: the resolution's status; named apart from RFC 9457's own `status` |
| currentGuestIds | uuid[] | extension: the resolution's current guests |

An id that never existed keeps the 404 `not-found` problem of today with no extension members
(FR-006, FR-009).

---

## Derivation rules

Stated once here; the resolver implements them and the scenario tests pin them.

1. **Existence first.** A guest present in the tenant is `ACTIVE`; nothing else is consulted.
2. **Retirement is the latest candidate event.** Candidates for guest g: `MERGE` or
   `REVIEW_CONFIRM` events whose `absorbed_guest_ids` contains g; `UNMERGE` events whose
   `guest_id` is g. The one with the greatest `createdAt` (ties by id) is g's retirement.
3. **Successors.** Merge: the event's `guest_id`. Split: reading the tenant's events from the
   split's `createdAt` forward in `(createdAt, id)` order, the `guest_id` of the first event whose
   `source_record_ids` contains each detached record; distinct, in record order. The scan stops
   when every detached record has been seen.
4. **No candidate.** If g is the read id, it never existed here: not-found. If g is a successor
   reached by a hop, the hop keeps g as a successor, no further hop is recorded for it and it
   contributes nothing to `currentGuestIds`; the resolver logs the inconsistency.
5. **Walk.** Breadth-first from the read id over successors; each guest is expanded at most once.
6. **Status.** `MERGED` if exactly one current guest, `SPLIT` if more than one, `RETIRED` if none.
7. **Tenant.** Every query carries the tenant id; a guest or event of another tenant is invisible,
   so an id from another tenant resolves as never-existed.

---

## `GraphPort` additions

| Method | Returns | Backed by |
|---|---|---|
| `guestExists(tenantId, guestId)` | boolean | `GuestRepo.findGuest` — the timeline already uses the same query through `GuestQueryService` |
| `eventsAbsorbing(tenantId, guestId)` | events whose absorbed list contains the guest, oldest first | native containment query, `merge_event_absorbed_gin` |
| `eventsSince(tenantId, after, afterId, limit)` | the tenant's events later than `(after, afterId)`, oldest first, one page | JPQL keyset range, `merge_event_tenant_time_idx` |

`UNMERGE` candidates come from the existing `eventsForGuests`, filtered by kind. `InMemoryGraph`
implements all three over its event list with the same ordering.

---

## Unchanged

`guest`, `resolution_link`, `source_record` and every companion table; `MergeEvent` and
`MergeEventKind`; the engine, unmerge and review operations. This slice writes nothing during
resolution (FR-010).
