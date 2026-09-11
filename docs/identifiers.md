# What makes an identifier strong enough to merge on

This is the reference for identifiers: the five kinds the engine stores, what normalization does
to each before it is written down, and the rules that decide whether a shared value is evidence of
one person at all. It is written for anyone tracing why two records did or did not end up on the
same guest, and it sits under [matching.md](matching.md), which scores candidates once identifiers
have decided who the candidates are.

An identifier is a claim strong enough to merge two people's records on, and what makes it strong
is normalization plus a trust rule, not the raw value. `+41 44 123 45 67` and `+41441234567` are
one claim written twice; a booking.com relay address on two reservations is not one claim at all.
Normalization settles the first question, trust rules the second. What the record around the
identifier says — the payload it arrived in, the stay it describes — is in
[records.md](records.md) and [timeline.md](timeline.md).

**Keyed by normalization version and append-only.** Normalization decides what was *stored*, not
only what was compared, so a value written under one rule has to stay interpretable after the rule
changes: a phone row written in 2026 is the E.164 form the 2026 parser produced, and the merge
event that cites it is permanent. A change to any rule below is a new version with its own section,
and the section for version 1 stays.

---

## Normalization version 1

### The five types

An identifier has a type and a normalized value, and the pair is what the engine indexes and
compares. The types are fixed — the enum and a CHECK constraint on both identifier tables carry the
same five names, so a sixth type is a migration and not a configuration.

| Type | Normalized to | When the value is unusable |
|---|---|---|
| `EMAIL` | trimmed, lowercased | no identifier, reason recorded |
| `PHONE` | E.164 | no identifier, reason recorded |
| `LOYALTY_ID` | trimmed | — |
| `ID_DOCUMENT` | SHA-256 of `TYPE:NUMBER` | no identifier, reason recorded |
| `EXTERNAL_KEY` | source system code, colon, the key | — |

A value that cannot be normalized is never guessed at and never silently dropped. The record is
stored as it arrived and flagged for review carrying the extractor's reason — `phone: cannot be
normalized to E.164`, `idDocument: requires both type and number` — and a record with no usable
identifier at all says so in the same way.

### Email

Trimmed and lowercased, and rejected when the result is not a plausible address: something before
the `@`, something after it, a dot in the domain part, no whitespace anywhere. The check is
deliberately coarse. Its job is to keep a phone number, a note or an empty string out of the email
index, not to decide whether a mailbox exists.

### Phone

Parsed and validated with libphonenumber and stored in E.164, so the country prefix, spacing and
punctuation of the source system stop mattering. **A number that cannot be parsed and validated is
rejected rather than guessed.** A plausible-looking string turned into a wrong `+41` number would
be indistinguishable from a real identifier and could merge two strangers.

A national-format number needs to know which country it is from, and that is the tenant's default
region. Where no default region is configured, only numbers that already carry their international
prefix are accepted; the rest are rejected with the same reason as any unparseable number.

### Identity document

A passport or ID card number is stored as a hash and never in plaintext — not in the identifier
row and not in the extracted fields, where the document is the one payload field that is not copied
through. The hash input is `TYPE:NUMBER`, the type trimmed and uppercased, the number uppercased
with all whitespace removed, then SHA-256.

Both halves are required. A document with a number and no type, or a type and no number, produces
no identifier at all and flags the record for review, because `X1234567` alone is not a claim: the
same digits can belong to two documents from two countries.

### Loyalty id and external key

A loyalty id is trimmed and otherwise left alone. It is issued by one program, and any further
normalization would be a guess about a format the program owns.

An external key is namespaced by the code of the source system it came from, so guest `4711` in the
PMS and guest `4711` in the booking tool never collide. **The namespace is what makes an external
key an identifier rather than a row number.**

### Looking up a document

Because a document is stored as a hash, looking one up means handing the engine the type and the
number and letting it hash them — there is no stored value to search for. Pass the two halves in
the same `TYPE:NUMBER` form the hash is built from, together with the type:

```
GET /api/v1/guests?identifier=PASSPORT:X1234567&type=ID_DOCUMENT
```

Without an explicit type, the lookup tries every type whose normalization accepts the value, and
`ID_DOCUMENT` is deliberately left out of that sweep. **A document is only ever hashed when the
caller names the type.** Otherwise any string containing a colon would be hashed as a passport on
the off chance, and a lookup for an external key would quietly probe the document index.

A lookup answers with the guest holding that identifier now. After a merge or an unmerge that is a
different id than before, and [continuity.md](continuity.md) says what became of the old one.

### Trust rules: when a shared value is not evidence

Not every identifier deserves a merge. A front desk's phone number, a property's own email address
or a placeholder loyalty id is shared by hundreds of people, and a merge on one of those is a wrong
merge every time. A per-tenant rule marks a value, or a whole email domain, with one of three
effects:

| Effect | What it does |
|---|---|
| `IGNORE` | the identifier connects nothing — it is skipped when candidates are found |
| `PERFECT_MATCH` | may merge only when the two names agree exactly after folding, otherwise review |
| `MASKED_ALIAS` | email domains only: no deterministic merge, and the address is marked in the profile |

`PERFECT_MATCH` is the middle setting for an identifier that is usually right and occasionally
shared, such as a family email address: the identifier still finds the candidate, and the merge
proceeds only if both first and last name match exactly. Where a name is missing on either side,
agreement cannot be verified and the candidate goes to review.

**Rules are applied at matching time, not at ingest.** A rule added today silences identifier rows
written last year with no backfill, which is what makes a rule a correction a steward can make
after seeing the damage rather than a decision that had to be right before the data arrived.

### Masked aliases ship with the product

The online travel agencies issue relay addresses — a per-booking alias at a domain such as
`guest.booking.com` that forwards to the guest — and the same alias is sometimes reused for
different people. These domains are in the engine as code constants active for every tenant, not as
rows: the list updates when the product updates, with no migration, and the rules API shows the
built-ins read-only beside the tenant's own rules.

A masked address does two things and refuses a third. It contributes candidacy, so repeat bookings
behind one alias can still be found and scored. It is marked in the record, so it never overwrites a
real address in a guest's profile — the profile half of that is in [profile.md](profile.md). And it
never drives a merge on its own, at any confidence.

### An identifier many records share

An identifier held by very many records is not evidence of one person; it is evidence of a shared
mailbox, a travel agency's phone or a front-desk address that nobody wrote a rule for yet. When the
number of records carrying an identifier is above the tenant's review threshold, a column on the
tenant, the engine declines to merge on it and queues the candidate for review instead, citing the
count and the threshold.

This catches the case a trust rule cannot, because it needs no one to have noticed the value first.
[matching.md](matching.md) says what happens to a review entry and who decides it.
