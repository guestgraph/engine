# Feature Specification: Retired Guest Ids Resolve

**Feature Branch**: `004-retired-guest-ids`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Retired guest ids resolve instead of 404ing. Consume R-X5 from docs/roadmap-notes.md: after a merge deletes the absorbed guest, GET /guests/{absorbedId} must return a resolution (status MERGED, currentGuestId, mergedAt) instead of a bare 404, following merge chains transitively (X→Y→Z) and returning the several current ids when an unmerge fanned one guest out. A stored guestId becomes a reliable external reference, which is the precondition the roadmap sets for slice 4 connectors. Nothing is lost today — merge_event is append-only and records survivor and absorbed ids — so this slice adds the query that walks it, tenant-scoped like everything else, with RFC 9457 errors for ids that never existed in the tenant."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Stored Guest Id Never Goes Dark (Priority: P1)

The point of a golden profile is that other systems hold its guest id as *the* reference for a person. Today they cannot. When two guests merge, the absorbed guest is removed, and a read of its id answers as if the person had never existed. The reference breaks on exactly the event this product exists to produce, and it breaks silently: the integrator's stored key looks fine right up until the moment it does not.

Nothing was lost. The audit trail already records, for every merge, which guest survived and which were absorbed. What is missing is the query that walks it. With this slice, a guest id that was retired by a merge resolves: the read answers successfully, says that the id was merged, names the guest that now holds the person, and says when the merge happened. An integrator that stored the old id learns where the person went and can update its own key.

**Why this priority**: It is the slice's reason to exist and the roadmap's stated precondition for connectors. A connector writing a guest id back into a property system is the first real holder of that id, and the write-back is unsafe until this works.

**Independent Test**: Ingest two records that resolve to separate guests, then one carrying both identifiers so the guests merge. Read the absorbed id and verify the answer names the surviving guest, the merge, and its time. Read the surviving id and verify it still returns the guest as before.

**Acceptance Scenarios**:

1. **Given** guest X was absorbed into guest Y by a merge, **When** X's id is read, **Then** the response succeeds, states that X was merged, names Y as the current guest, and gives the time of the merge.
2. **Given** guest Y is an active guest, **When** Y's id is read, **Then** the guest is returned as before this slice, and the response states that the id is active.
3. **Given** a guest id that has never existed in the tenant, **When** it is read, **Then** the request is refused with a not-found problem-details error, exactly as today.
4. **Given** a guest id that exists or was retired in another tenant, **When** it is read under this tenant's credential, **Then** the answer is indistinguishable from an id that never existed (Constitution I).
5. **Given** a retired id's resolution, **When** it is inspected, **Then** it names the decision that retired the id, so the reader can follow it into explain (Constitution IV).

---

### User Story 2 - Chains Are Followed to the End (Priority: P2)

Guests merge more than once. X is absorbed into Y on Monday, and Y is absorbed into Z on Friday. An integrator holding X must not be sent to Y, which is itself gone; it must be sent to Z, the guest that holds the person now. The resolution follows the chain of retirements to the current guest and reports the hops it took, so the answer is both usable and explainable.

**Why this priority**: Without it, a stored id is reliable for exactly one merge and then dangles again. It is ranked below the single-hop case only because that case is the one every integrator hits first.

**Independent Test**: Merge X into Y, then Y into Z. Read X and verify the current guest is Z and the two hops are listed in order. Read Y and verify the current guest is Z with one hop.

**Acceptance Scenarios**:

1. **Given** X was absorbed into Y and Y later into Z, **When** X is read, **Then** the current guest is Z, and the two retirements are listed in the order they happened, each with its time.
2. **Given** the same history, **When** Y is read, **Then** the current guest is Z with one hop.
3. **Given** a chain of any length, **When** its first id is read, **Then** the current guest is the one active guest at the end of the chain, and the answer never names a guest that no longer exists as current.

---

### User Story 3 - A Split Guest Resolves to Every Guest It Became (Priority: P3)

A steward can unmerge every record off a guest, and a guest holding no records ceases to exist: its id is retired the same way a merge retires one, but the person did not go to one place. The detached records re-resolved on their own and may now sit on two or three guests. An integrator holding the emptied guest's id must be told all of them, because the honest answer is that the id no longer maps to one person. The resolution reports every guest the records landed on, each followed through any later merges to its own current guest.

**Why this priority**: It completes the promise of stories 1 and 2. A split is rarer than a merge, but an id that resolves after merges and dangles after splits is an id an integrator still cannot trust.

**Independent Test**: Build a guest from three records, unmerge all three so they land on two new guests and the original is removed. Read the original id and verify both new guests are named. Then merge one of them into a third guest and verify the answer follows it.

**Acceptance Scenarios**:

1. **Given** guest Y held three records and a steward detached all of them, leaving Y empty and removed, with the records now on guests W1 and W2, **When** Y is read, **Then** the response states that Y was split and names both W1 and W2 as current guests.
2. **Given** the same history and W1 is later absorbed into V, **When** Y is read, **Then** the current guests are V and W2.
3. **Given** guest Y had records detached but kept at least one, **When** Y is read, **Then** Y is returned as an active guest, because its id was never retired.
4. **Given** X was absorbed into Y and Y was later emptied by a split onto W1 and W2, **When** X is read, **Then** the answer follows X into Y and then into both W1 and W2, and states that the id no longer maps to a single person.

---

### User Story 4 - Everything Under a Retired Id Points to the Current One (Priority: P4)

A guest id is also the root of its records, its explain chain, its timeline and its steward operations. A client that read a retired id's records today gets the same not-found error as for an id that never existed, and cannot tell a typo from a merge. With this slice, every request under a retired id is refused with a problem-details error that carries the current guest id or ids, so a client that hits a retired id anywhere learns where to go, and a client that ignores the hint fails loudly rather than reading stale data.

**Why this priority**: It closes the last silent failure. The guest resource is where the resolution lives; its sub-resources only need to point there, and doing so is small.

**Independent Test**: Merge X into Y, then request X's records, timeline and explain, and attempt an unmerge on X. Verify each is refused with a problem-details error that names Y as the current guest.

**Acceptance Scenarios**:

1. **Given** X was absorbed into Y, **When** X's records or timeline are read, **Then** the request is refused with a problem-details error stating that X was retired and naming Y as the current guest.
2. **Given** X was absorbed into Y, **When** a steward operation such as unmerge is attempted on X, **Then** it is refused the same way and no decision is recorded.
3. **Given** a retired id that fanned out to several guests, **When** a sub-resource of it is requested, **Then** the error names all of the current guests.
4. **Given** an id that never existed in the tenant, **When** a sub-resource of it is requested, **Then** the error is the plain not-found answer of today and carries no current guest.

---

### Edge Cases

- The chain from a retired id ends at another retired id whose own retirement has no successor, which the recorded decisions do not allow to happen: every merge names a survivor and every split leaves its records somewhere. If the walk finds no end, the resolution reports the id as retired with no current guest rather than inventing one, and the condition is surfaced as a defect of the audit trail.
- A retired id is read while a merge that would extend its chain is in progress: the answer reflects the decisions recorded at the moment of the read, and a later read may differ. No answer is cached beyond the request.
- The same guest appears more than once among the current guests of a split, because two detached records landed on the same guest or two branches merged later: each current guest is named once.
- A merge chain and a split meet, so that a single stored id fans out and one branch later rejoins another: the answer names each distinct current guest once and lists the hops that led to each.
- A very long chain, produced by a guest that was merged and re-merged many times: the walk terminates because every decision is recorded once and each hop moves strictly forward in time.
- A decision recorded before this slice shipped: chains through it resolve like any other, because the data this slice reads has been recorded since slice 1.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Reading a guest id that was retired by a merge MUST succeed and MUST state that the id was merged, name the current guest and give the time of the merge.
- **FR-002**: Reading a guest id that was retired by a split that left it empty MUST succeed and MUST state that the id was split, naming every guest its records landed on.
- **FR-003**: Resolution MUST follow retirements transitively until every path ends at an active guest; no guest named as current may itself be retired.
- **FR-004**: The resolution MUST list the hops it took from the read id to each current guest, in the order they happened, each naming the kind of decision, the decision itself and its time, so that the answer can be followed into explain.
- **FR-005**: Reading an active guest MUST return the guest as before this slice and MUST state that the id is active, so a client can tell the two answers apart by one field.
- **FR-006**: Reading a guest id that has never existed in the tenant, or that exists only in another tenant, MUST be refused with the same not-found problem-details error as today; the two cases MUST be indistinguishable to the caller.
- **FR-007**: Every resolution MUST be scoped to the calling tenant; no decision recorded under another tenant may contribute to an answer or to a hop.
- **FR-008**: Requests for sub-resources or operations under a retired id, including records, explain, timeline and unmerge, MUST be refused with a problem-details error that states the id was retired and names every current guest; no state change may result.
- **FR-009**: The same error for an id that never existed MUST NOT carry any current guest, so a caller can distinguish a retired id from an unknown one.
- **FR-010**: Resolution MUST read only the recorded decisions; it MUST NOT create, alter or delete any decision, record or guest (Constitution II and IV).
- **FR-011**: Each distinct current guest MUST be named once, even when several paths lead to it.
- **FR-012**: The answer for an active guest MUST remain backward compatible: every field returned before this slice is still returned with the same meaning.

### Key Entities

- **Guest id resolution**: the answer to "where is the person this id referred to". Carries the read id, its status (active, merged or split), the current guest or guests, and the hops from the read id to each of them.
- **Hop**: one retirement on the way from a read id to a current guest. Names the retired id, the guest or guests it led to, the kind of decision (merge or split), the decision that recorded it and the time it happened.
- **Retired id**: a guest id that once named a guest and no longer does, because the guest was absorbed by a merge or emptied by a split. Retired ids are never reused.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After any merge, 100% of reads of the absorbed guest id answer with the surviving guest; none answers not-found.
- **SC-002**: After any sequence of merges and splits, every guest named as current by a resolution is an active guest at the time of the read, verified by reading it.
- **SC-003**: A resolution through a chain of 10 retirements returns in under 1 second.
- **SC-004**: An integrator following the documented rule, "on a retired answer, replace the stored id with the current one", holds a valid guest id after every merge in a scenario suite of at least 20 merge and split sequences, with no manual intervention.
- **SC-005**: No request under a retired id returns data of a different guest, and none returns the plain not-found error that an unknown id returns.

## Assumptions

- Resolution is defined at the level of guests and the decisions that retired them, not at the level of individual records. A split resolves to the guests its detached records landed on at the time of the split, each followed through later decisions. The finer question, "where is each record the retired guest once held", is answered by the existing records and explain endpoints on the current guests and is out of scope here.
- A guest that is retired never comes back under the same id. An unmerge that leaves a guest with at least one record does not retire it; only a guest left with no records is removed.
- The resolution is a read. It introduces no new decision kind and records nothing; the data it walks has been recorded, append-only, since slice 1 and needs no backfill.
- The retired answer is a distinct document from an active guest; one status field, present on both, tells them apart. The precise fields are decided in the API contract at planning.
- Sub-resources of a retired id refuse rather than redirect, because a fan-out has no single target and a silent redirect would hide the merge from the client.
- Connectors, the consumer this slice exists for, are slice 4 and are not built here. The published rule for integrators is one sentence: a stored guest id may be retired, and reading it tells you the current one.
