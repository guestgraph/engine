# What happens to a guest id you stored when guests merge

This is the reference for identity continuity: what a merge does to an id that is already in
someone else's database, how to read an id that no guest carries any more, and what can be
undone. It is written for whoever holds a GuestGraph guest id outside the engine — a booking
system, a CRM, a report that ran last quarter. Why two records became one person is a different
document, [matching.md](matching.md); this one starts after that decision.

**A guest id you stored keeps answering after a merge, because the engine records where the
person went instead of forgetting them.** A read of a retired id names the guest that holds the
person now. The id does not dangle, and it does not quietly begin describing someone else.

---

## The trail a decision leaves

Every decision the engine makes about identity is written as an event, and the events are the
only account of what happened to an id. A merge happens because two records shared a normalized
identifier or scored high enough to cross a threshold — [identifiers.md](identifiers.md) says
what counts as identity, [matching.md](matching.md) how the score is reached — and whichever it
was, the outcome lands in one row.

| Kind | What it records |
|---|---|
| `CREATE` | a record matched no existing guest, so a guest was opened for it |
| `ATTACH` | a record joined a guest that already existed |
| `MERGE` | one guest absorbed another; the absorbed id is on the event |
| `UNMERGE` | named records were detached from a guest |
| `REVIEW_CONFIRM` | a queued candidate was accepted and the record joined the guest |
| `REVIEW_REJECT` | a queued candidate was refused |

The six are fixed by a check constraint in `V1__core_schema.sql`, so an event of any other kind
cannot be written. Each row carries the matcher that decided, a confidence between zero and one,
the evidence that matcher saw, the records and absorbed guest ids involved and the actor behind
the decision — system, human or agent, with the identity the credential acts as. Actor
attribution arrived in `V3__timeline_and_actors.sql`; an event written before it reads back as
unattributed rather than as an error.

**Deleting an event raises an exception**, by the same guard that holds source records
immutable. [records.md](records.md) has that guard and the one lawful exception to it.

## Why a retired id still resolves

The event table deliberately has no foreign key on its guest id columns, and the migration says
why: merge and unmerge delete absorbed or emptied guests, but their ids must stay referenceable
in the audit trail forever. **A guest row can be deleted; a guest id in the trail cannot be.**
That one omission is the mechanism behind everything below. Because the id survives in the
events that name it, a walk over those events ends at a guest that exists, and the id you stored
has somewhere to point.

## Reading a retired id

A client reading a guest id is asking one question — can I replace the id I hold with one id? —
and the engine answers that one. For an active guest, a read returns the profile and the
identifiers ([profile.md](profile.md)). For an id a merge absorbed or an unmerge emptied, it
returns no profile at all: the status, the guest ids that hold the person now, the instant the id
was retired and every retirement crossed on the way there. Only an id that never existed in the
tenant is a not-found.

Four statuses answer the question, and they differ in what the client does next.

- `ACTIVE` — the id names a guest. Nothing to do.
- `MERGED` — retired, and exactly one guest holds the person now. The stored id can be replaced
  by that one.
- `SPLIT` — retired, and several guests do. **When several guests hold the person, the graph
  cannot say which one was meant and a person has to.** The engine reports all of them rather
  than picking.
- `RETIRED` — retired, and the walk found no current guest at all. That is an inconsistency in
  the trail, and it is reported as one instead of being hidden behind a not-found.

The status comes from the end of the walk, not from the first hop, because a guest absorbed by a
merge whose survivor was later split has moved twice and the client cares only where it ended.

## When a retired id is used rather than read

Reading the id itself answers. Addressing anything beneath it — the source records
([records.md](records.md)), the stay timeline ([timeline.md](timeline.md)), the explain chain, an
unmerge — refuses with a problem detail of type `guest-retired`, carrying the id that was read,
the resolution status and the guest ids where the person is now. The resolution's status is
carried under its own member name because RFC 9457 already owns `status`.

The refusal is what makes the hint worth reading: a client that hits a retired id anywhere learns
where to go, and **a client that ignores the hint fails loudly rather than reading stale data.**
What a client does with the refusal is on the family's problems page at
<https://guestgraph.io/problems/#guest-retired>.

## Undoing a merge

An unmerge is the reverse operation, and **it is not a delete**: it detaches the named records
from the guest, records the act as an `UNMERGE` event and re-resolves each detached record among
the tenant's other records, excluded from returning to the guest it just left. A detached record
lands on a new guest or on an existing one, and the response says which. The remaining guest is
rebuilt from what is left of it; if nothing is left, that guest ceases to exist and any pending
reviews naming it are canceled. Source records are never touched by any of this.

Three requests are refused as not executable. A request naming no records has nothing to move. A
record not linked to the guest cannot be detached from it. A guest holding a single record cannot
be split, because an unmerge divides a guest between two sides and one record gives only one
side — removing the last record is not an unmerge but a deletion, which the engine does not offer
here.

## A steward's split sticks

An unmerge writes a rule between every detached record and every remaining one, and the engine
will not merge across that rule again. The rule sits between two records rather than between two
guests, and the migration gives the reason: record ids are immutable and survive merges, guest
ids do not. A rule written against a guest id would stop meaning anything the next time that
guest was absorbed, which is precisely the moment it is needed.

**A rule is lifted, never deleted, and lifting carries its own actor**, because lifting is the
act that overrides a steward's split and a deleted row has nowhere to keep who did it. Making
that possible cost the one non-additive change in `V3__timeline_and_actors.sql`: pair uniqueness
had to apply to active rules alone, so a pair that is split, lifted and split again gets a second
row while the lifted one stays readable.

## Reading a merge back

Explain exists so that a merge can be read back afterward: the complete chain of events that put
these records on one guest, in the order they happened. **The chain reaches through every guest
absorbed along the way**, so a guest assembled from three earlier ones still reads as one
history rather than as the last step of it. What an event in the chain means — which matcher,
what evidence, why that confidence — belongs to [matching.md](matching.md).
