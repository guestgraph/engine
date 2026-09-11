# Where each field in a guest's profile came from

This is the reference for the golden profile: how it is computed, what a field in it does and
does not tell you, and what is never in it at all. It is written for an integrator reading
`GET /api/v1/guests/{id}`, an operator reading a `guest` row, and a contributor changing the
rule.

**Keyed by survivorship version.** A profile is recomputed from records, so what you read today
is whatever today's rule makes of them — and a profile copied into a ticket, an export or a
downstream system outlives the rule that produced it. Nothing in the data names that rule: a
merge event records its `matcherName`, a profile records nothing. So this document is organized
by survivorship version and is append-only. When a v2 rule lands it gets its own section and the
v1 section stays, because a value written down in 2026 has to stay explainable in 2030.

---

## Derived, not authored

A profile is a pure function of a guest's source records: records in, profile out. The engine
recomputes it whenever the set of records behind a guest changes — a record arrives, a steward
confirms a review, an unmerge splits a guest in two — and the result replaces the previous one
whole. **A profile is never edited, only recomputed.** Delete every profile in the database and
they can all be rebuilt from source records that were never altered.

Two things follow, and both are about what a profile is not. It is not a place to put a
correction, because the next recomputation would drop it; a correction enters as a record, like
everything else. And it is not stable in the way an authored row is: which records belong to one
guest is a matching decision, described in [matching.md](matching.md), and a merge or an unmerge
changes the set and therefore the profile. The guest id you hold may not be the id the profile
now sits under; [continuity.md](continuity.md) says what happens to an id after that.

---

## Survivorship v1 — most recent non-null, per field

The engine orders a guest's records oldest to newest and lets each one write the fields it
carries over the fields already there. **Per field, the most recent non-null value wins.**

Most recent means the record's own effective timestamp — the time the source system stated for
it — falling back to the time the engine received it when the source stated none. A backfill
loaded this morning therefore does not overwrite last week's live record unless the source says
it is the newer observation. Where two records carry the same effective timestamp, the one
received later wins.

Because the choice is made field by field, a profile can match no single source record. The last
name comes from one record, the phone from another, the city from a third, and the result is a
row that no system ever sent. That is the ordinary case, not an anomaly, and a field that
disagrees with the record you happen to be looking at is not evidence of a fault.

A value is skipped only when it is null or a string that is empty or whitespace. Nothing else is
skipped. An empty JSON object is a value like any other, so a newer record carrying
`"reservation": {}` overwrites a filled `reservation` block with an empty one, and a reader who
sees an empty object is seeing the rule work as written rather than data lost in transit.

### Email has its own rule

**A masked address never overwrites a real one**, however recent it is. A masked address is the
relay an online travel agency puts between a property and the guest: an alias belonging to one
booking, sometimes handed to a different person on the next. It fills the email field only when
no record for that guest carries a real address, and then the profile sets `emailMasked` to
`true` beside it, so a reader who is about to send something knows what they are sending to. Among real
addresses, and among masked ones when no real address exists, recency decides as everywhere
else. What makes an address masked is a built-in list of relay domains plus the tenant's own
identifier quality rules; [identifiers.md](identifiers.md) has that list and the normalization
behind it.

---

## What a profile never carries

**A plaintext identity document number reaches no profile**, because it reaches no extracted
field to begin with. When a record arrives, the extractor copies the payload's top-level fields
forward but skips `idDocument`, so the number exists in exactly two places: in the immutable
payload the record arrived in, described in [records.md](records.md), and as a hash among the
guest's identifiers, described in [identifiers.md](identifiers.md). Survivorship never sees it
and cannot propagate it.

---

## The fields are whatever the records carried

The engine defines no profile schema. It normalizes the fields it knows — email, phone,
birthdate — and carries every other top-level payload field forward under the name the source
gave it, so the shape of a profile is decided by whoever writes the connector.

A connector that puts a whole source object into the payload puts that object into the profile.
The Apaleo connector does exactly this: a record made from a reservation carries a `reservation`
block, a record made from a booking's booker carries a `booking` block, and a guest's profile
therefore carries the newest of each. **Those blocks are context for the person, not a history
of the person.** The profile holds one reservation — the most recent one any record mentioned —
and the reservation before it was overwritten when that record arrived. A reader who wants to
know where a guest stayed, and when the engine stops saying so, reads the timeline instead:
[timeline.md](timeline.md).

---

## The profile is not evidence

**What a system actually said is the source record.** A payload is stored as received and is
never edited, so a dispute about what a property sent, an audit of what was known on a given
day, or a bug report about a wrong value is settled against records and not against the profile
derived from them. The profile answers "who is this person, as best we can tell right now"; it
was never meant to answer "what did this system tell us". [records.md](records.md) is the
reference for the record side.

---

## Changing the rule

The whole of v1 is `GoldenProfileDeriver`, forty lines of it, and the ordering it depends on is
`SourceRecord.effectiveTimestamp`.

Recency alone has a known cost: the next update from any source overwrites a steward's
correction, and a value the extractor flagged as malformed still appears in the profile, flagged
but present. Per-source trust ranking and steward field-pinning are the planned answer, tracked
in [roadmap-notes.md](roadmap-notes.md) under R-X2. None of it is built, and until it is, the
rule above is the rule in full.

A change to any of that is a new survivorship version, not an edit to this section: add a
section here and leave v1 standing, so a profile someone wrote down under v1 stays readable.
