# Why a guest's timeline is shorter than their records

This is the reference for the timeline: what one entry is, when an entry stops being listed and
why the number of entries does not match the number of records behind them. It is for whoever
answers a guest asking about their stays, for an operator checking an import that looks
incomplete and for a contributor reading the endpoint. If you arrived from
[profile.md](profile.md), where a profile carries one reservation rather than a list of stays,
this is the list — and this is why it is shorter than the record count you were comparing it
against.

**The timeline says where a guest is on an object now, and now is decided by the newest version
of that object alone.** These are invariants of the model rather than a rule a later version
supersedes, so this document is written in the present and edited in place.

## The version is the unit, not the record

Every version of a source object carries a complete roster: a full statement of who was on that
object, in which role, at that version. The engine compares versions chronologically — the
version is the instant the source object itself records as last modified — and the newest roster
decides who is on the object now. A newer version that does not name a guest an earlier version
named is not silent about them. It is a statement that they are not on the object, and the
timeline reads it as one.

**The unit of supersession is the object version, not the record.** A single delivery may carry
several people on one reservation, and they stand or fall together, because they are one
statement made at one moment.

The derivation is pure: no clock, no database, no framework. Everything an entry says follows
from the observations handed to the deriver, which is what makes a guest's association state
recomputable rather than stored and kept in step.

## No person is followed from one version to the next

Persons are deliberately never matched across versions of the same object. Sources such as PMS
reservations carry people with no id to follow across an edit, so any attempt to track one
person from version to version would have to guess who had become who — and would report a
reassignment every time a booking's guest list merely got shorter. **A shrinking guest list is a
smaller roster, never a handover.**

The one case where the engine does name a successor is a genuine one-to-one handover: the role
had a single occupant in the version where this guest last held it, has a single occupant now,
and the two differ. Anything else names nobody. When a booking drops one of two additional
guests, the guest who remains did not take the other's place, and saying so would invent exactly
the transfer that tracking people by their slot gets wrong.

## Current and ended

An association is current while the newest roster still places the guest in that role. It is
ended once the newest roster does not, whether the role passed to someone else or the guest was
dropped from the object entirely. Those are the two statuses an entry can carry.

**Ended associations are returned only when asked for** — `includePast=true` on
`GET /api/v1/guests/{guestId}/timeline`. A default timeline is therefore what the guest holds
now, which is the answer most callers want and the one that needs no explanation.

## Roles, and what position does not mean

A guest holds a role on an object: primary guest, additional guest or booker. One guest can hold
several roles on the same object — a booker who is also the primary guest produces two entries
for one reservation — and holds roles independently across different objects.

**Position within a guest list is descriptive only and confers no identity across versions.** It
is recorded because the source recorded it, and a person who shifts from second to first when a
guest list gets shorter has not become someone else.

## An unparsed version joins no roster

An object version whose submitted state could not be parsed gets no row among the observations,
so it forms no roster and changes nothing about who is on the object. The record itself stays
stored and flagged; [records.md](records.md) says how the flag works and what happens next. The
effect is that a delivery the engine could not read cannot silently remove anyone.

## Records and the timeline are different lengths on purpose

A record is permanent evidence that a source once said something. If a later version of that
reservation does not name the person, the timeline stops listing the reservation for them, while
the record stays exactly where it was. **Neither is wrong: `/guests/{guestId}/records` answers
what was ever observed about a guest, and the timeline answers what the guest has now.**

So a guest holding more records than timeline entries is the ordinary case, not a sign of loss.
The gap is the statements a source has since revised. Reading the records is how you see what
the earlier ones said.

Entries carry the start and end the source object itself records, not the moment the engine
received anything. That is what makes a timeline read as stays rather than as a log of ingest.

## Ordering and paging

One comparator serves the sort, the cursor and the keyset seek, so a page boundary cannot
disagree with the order the entries were sorted in. Entries order by business start, falling back
to the record's own timestamp where the source supplied no business start, then by a tiebreaker
carrying every field that can distinguish two entries. **The ordering has to be total: two
entries that compared equal across a page boundary would drop one of them silently.**

Paging is by opaque cursor rather than by offset. An offset is a contract commitment that
forecloses moving a read into SQL or changing an ordering, and a cursor seeks instead of scanning
and discarding on deep pages; [roadmap-notes.md](roadmap-notes.md), under *One paging idiom*,
carries that decision for every paged endpoint. The seek compares against the cursor's key
directly rather than locating the row it came from, because an association can end between two
page requests and the row may no longer be there.

## Which guest an entry belongs to

The timeline takes the guest link as given. Which guest a record resolves to is resolution's
answer, and [matching.md](matching.md) and [identifiers.md](identifiers.md) are where that answer
is explained. Because associations are derived on read and never stored, a merge or an unmerge
changes a timeline the next time it is read, with nothing to rebuild;
[continuity.md](continuity.md) says what becomes of a guest id when that happens. A timeline
asked for under a retired id answers with the current id rather than an empty page, because an
empty page would read as a guest with no stays.

## Where the rules live

- `timeline/AssociationDeriver` — rosters, supersession, the successor rule and the one ordering
- `timeline/AssociationStatus` — the two statuses and what each means
- `persistence/TimelineQueryService` — the read model and the keyset seek
- `record_object` in `db/migration/V3__timeline_and_actors.sql` — what a version records

Behavior changes here are changes to those files, and this document is edited to match.
