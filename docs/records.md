# Why a guest has several records of the same person

This is the reference for the source record: what one holds, what the engine does with it when
it arrives, and why it never changes afterward. It is written for whoever sends records into
the engine, and for whoever reads them back from `GET /api/v1/guests/{guestId}/records` and
finds the same person four times.

**A source record is one person, in one source object, at one version.** Four records of one
person are four versions of the object that person is on, kept side by side because each was
true when it was sent. Nothing collapses them, because collapsing them would throw away the
only copy of what a system said.

## What a record holds

Five things, and they answer different questions. The payload is the JSON exactly as it
arrived. The extracted fields are what the engine understood of it. The record timestamp is the
source's own instant for the observation, and the received instant is when the engine took it;
survivorship orders by the first and falls back to the second, so a record with no timestamp
still sorts. The review flag and its reasons say whether anything in the payload defeated
extraction. The table is `source_record` in `V1__core_schema.sql`, and
`GET /api/v1/guests/{guestId}/records` returns all five beside the record's normalized
identifiers.

Beside them sits the record's identity: the tenant, the source system and the external key the
sender chose, unique together by a constraint in the same migration.

## Nothing changes after it arrives

The original is the ground truth that makes explain, unmerge and a replay of resolution
possible, and the constitution's second principle is blunt about the consequence: once an
original has been altered, trust in the graph cannot be re-established. So this is not a habit
the application keeps. It is a trigger.

**An update may touch the review flag and nothing else; a change to the payload, the extracted
fields, either timestamp or the identity raises `source_record is immutable (id=…)` and takes
the transaction with it.** The function is `source_record_immutable`, and the exception names
the record it refused, so a caller finds out which one.

Deletion is guarded the same way, by `guard_append_only`: a delete raises unless the session has
set `guestgraph.allow_erasure` to `on`. That setting exists for lawful erasure under
data-protection law, the constitution's single sanctioned exception, and for nothing else. It is
a session setting rather than a permission because switching it on is a deliberate act that
belongs to one transaction.

## The payload is what you said, extracted is what the engine understood

These are two different objects and the difference is worth reading carefully. Every top-level
payload field is copied forward into the extracted fields, and then the canonical form replaces
the raw one for email, phone and birthdate — an address lowercased and trimmed, a number in
E.164, a date in ISO form. `RecordExtractor` does it in that order on purpose, so the canonical
value wins over the copy.

**Reading the payload tells you what the source said; reading the extracted fields tells you
what the engine will match on and put into a profile.**

One field is deliberately not copied forward: `idDocument`. A plaintext document number never
propagates past the payload, and what the engine keeps instead is a hash it can match on.
[identifiers.md](identifiers.md) says what each identifier becomes and why.

## A record the engine could not fully understand is still stored

Source data from hotel systems is dirty, and a record refused at the door is a guest
interaction that no longer exists anywhere the engine can reach. The constitution's third
principle draws the line there: malformed but parseable data is stored and flagged, never
dropped. Only a body that cannot be parsed at all is refused, as RFC 9457 problem details that
say what was wrong.

So a phone that cannot be normalized to E.164, or a birthdate that is not an ISO date, adds a
reason such as `phone: cannot be normalized to E.164` and sets the review flag. The record is
stored, the fields that did parse are extracted, and the rest of the payload is still there to
read. A record carrying no usable strong identifier is flagged too, with
`no valid strong identifiers in record`, because it can be stored but not deterministically
matched.

**A flagged record is repaired by sending a better one, never by editing it** — which is the
same rule as everywhere else on this page, seen from the side of a sender.

## One record per person, per object, per version

The external key is the whole of a record's identity within its source system, and the engine
treats a repeat of one as the record it already has: the ingest result comes back
`DUPLICATE_IGNORED`, carrying the stored record's id, its review flag and the guest it resolved
to. That is what makes a retry, a duplicate change notification and a full re-sync harmless —
none of them writes anything.

**Because a stored record can never be edited, an edit at the source is a new record, and the
external key is what decides whether the engine sees a new one.** A key that carries the source
object's own last-modified instant produces exactly one record per version of that object, per
person on it. The [Apaleo connector](https://github.com/guestgraph/connector-apaleo) does this:
the reservation's id, the person's role and position, and the reservation's modified instant.

For a reader of records, that is where the four come from. A guest accumulates one record per
person per object per version, in the order the versions were made, and the profile is derived
from all of them with the most recent non-null value winning per field — see
[profile.md](profile.md) for which record each field came from. An author writing a connector
needs the convention in full, and it lives in one place: “Submitting mutable, multi-person
source objects” in [the README](../README.md#submitting-mutable-multi-person-source-objects).

## A record carries context, not only a person

A connector may put the source object into the payload beside the person's fields, and the
Apaleo connector does: a record made from a reservation carries a `reservation` block, and the
record made from a booking's booker carries a `booking` block. The point is that a record can be
read on its own — this person, on this object, in this state — without joining anything to it.

**A nested block is an ordinary top-level payload field, so it is copied into the extracted
fields and reaches the guest profile like any other.** Survivorship takes the most recent, which
means the profile carries the newest record's block and not a history of them;
[profile.md](profile.md) says what else is in there. The related rule is the one the README
states for connectors: contact data belonging to the object rather than the person — an agency
phone, a property email — is nested inside a block precisely because extraction reads only the
documented top-level person fields, so it never becomes a guest identifier.

The submission's `sourceObject` block is a different thing and is not payload. It carries the
object's type, id, role, position and version, and it is what the roster and the timeline are
built from; [timeline.md](timeline.md) covers what the engine says a guest currently has.

## The record is the evidence

A profile is derived and a timeline is derived; the record is what a system actually said. That
is why it is worth the immutability and the storage: every other answer the engine gives can be
recomputed from records, and no record can be recomputed from anything.

It also means resolution can change its mind without touching one. A record points at one guest
at a time through its own link row, and a merge or an unmerge moves that link while the record
stays exactly as it arrived — which is why a guest id can disappear while the records under it
do not. [continuity.md](continuity.md) follows a guest id through that, and
[matching.md](matching.md) says how the engine decided two records were the same person in the
first place.
