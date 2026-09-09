# Mapping contract: Apaleo reservation and booking to engine observations

**Feature**: `005-apaleo-connector` | **Date**: 2026-09-10

What the connector sends for one version of one Apaleo object. Stated once here, implemented by
the mapper, pinned by unit tests on recorded documents. The engine side is the ingest contract of
slice 1 with the source-object block of slice 3.

## Two objects, two clocks

Apaleo keeps persons on two objects. A **reservation** carries `primaryGuest` and
`additionalGuests`; a **booking** groups one or more reservations and carries the `booker`. Each
has its own `modified` instant and its own webhook topic. The copy of the booker a reservation
shows under its expand option is not used: it is the booking's data under the reservation's
clock, and a booker correction would not change the reservation's key.

For a reservation the persons are, in order: `primaryGuest`, each entry of `additionalGuests` by
index. For a booking the person is `booker`. Each becomes one ingest record; a missing entry
produces none.

## The record

| Ingest field | Reservation | Booking |
|---|---|---|
| `sourceSystem` | `apaleo` (configurable) | same |
| `externalKey` | `{id}:primaryGuest:{modified}` · `{id}:additionalGuests[{i}]:{modified}` | `{id}:booker:{modified}` |
| `recordTimestamp` | the reservation's `modified` | the booking's `modified` |
| `sourceObject.type` | `reservation` | `booking` |
| `sourceObject.id` | the reservation `id` | the booking `id` |
| `sourceObject.role` | `PRIMARY_GUEST` · `ADDITIONAL_GUEST` | `BOOKER` |
| `sourceObject.position` | `i` for additional guests, absent otherwise | absent |
| `sourceObject.version` | the reservation's `modified` | the booking's `modified` |
| `sourceObject.businessStart` | `arrival` | the earliest `arrival` over `reservations` |
| `sourceObject.businessEnd` | `departure` | the latest `departure` over `reservations` |

`modified`, `arrival` and `departure` are passed as Apaleo returns them, ISO-8601 with offset,
without reformatting. Apaleo records `modified` without a fractional second. A booking whose
reservation list is empty carries no business dates.

## The payload

Top level, the person's own fields under the names the engine extracts, present only when Apaleo
supplies them:

| Payload field | From the person entry |
|---|---|
| `firstName` | `firstName` |
| `lastName` | `lastName` |
| `email` | `email` |
| `phone` | `phone` |
| `birthdate` | `birthDate` |
| `idDocument` | `{ "type": identificationType, "number": identificationNumber }`, only when both are present |

Never at the top level: `loyaltyId`, `externalGuestId`. Apaleo persons carry no loyalty number
and no entity id.

Nested, kept verbatim and extracted from by nothing:

- `person`: every other field of the person entry — `title`, `gender`, `middleInitial`,
  `secondLastName`, `address`, `nationalityCountryCode`, `company`, `preferredLanguage`,
  `birthPlace`, the identification issue and expiry fields, the registration-card fields,
  `relationshipToPrimaryGuest`, `vehicleRegistration`.
- `reservation`, on a reservation record: `bookingId`, `status`, `channelCode`, `source`,
  `company`, `externalReferences`, `externalCode`, `comment`, `guestComment`, `travelPurpose`,
  `property`, `unitGroup`, `ratePlan`, `adults`, `childrenAges`, `created`, `modified`,
  `arrival`, `departure`.
- `booking`, on a booking record: `groupId`, `comment`, `bookerComment`, `created`, `modified`,
  and `reservations` reduced to each one's `id`, `status`, `property`, `arrival`, `departure`
  and `channelCode`.

Never anywhere, on either object: `paymentAccount`, `registeredCard`, `hasActivePaymentAccount`,
and any field of a reservation's `services` or `timeSlices`.

## Example

Booking `XPGMSXGF`, modified `2026-07-09T14:30:00Z`, with one reservation `XPGMSXGF-1`, modified
`2026-07-09T14:30:00Z`, primary guest Anna Muster with an email and a passport, one additional
guest, and a booker who is the same person as the primary guest. The reservation yields two
records and the booking one:

```json
[
  {
    "sourceSystem": "apaleo",
    "externalKey": "XPGMSXGF-1:primaryGuest:2026-07-09T14:30:00Z",
    "recordTimestamp": "2026-07-09T14:30:00Z",
    "sourceObject": {
      "type": "reservation", "id": "XPGMSXGF-1", "role": "PRIMARY_GUEST",
      "version": "2026-07-09T14:30:00Z",
      "businessStart": "2026-08-01T15:00:00+02:00", "businessEnd": "2026-08-04T11:00:00+02:00"
    },
    "payload": {
      "firstName": "Anna", "lastName": "Muster", "email": "anna@example.com",
      "idDocument": { "type": "PassportNumber", "number": "X1234567" },
      "person": { "title": "Ms", "nationalityCountryCode": "CH", "address": { "city": "Zürich", "countryCode": "CH" } },
      "reservation": { "bookingId": "XPGMSXGF", "status": "Confirmed", "channelCode": "Ibe", "property": { "id": "BER", "code": "BER" } }
    }
  },
  {
    "sourceSystem": "apaleo",
    "externalKey": "XPGMSXGF-1:additionalGuests[0]:2026-07-09T14:30:00Z",
    "recordTimestamp": "2026-07-09T14:30:00Z",
    "sourceObject": {
      "type": "reservation", "id": "XPGMSXGF-1", "role": "ADDITIONAL_GUEST", "position": 0,
      "version": "2026-07-09T14:30:00Z",
      "businessStart": "2026-08-01T15:00:00+02:00", "businessEnd": "2026-08-04T11:00:00+02:00"
    },
    "payload": {
      "firstName": "Bruno", "lastName": "Muster",
      "person": { "relationshipToPrimaryGuest": "Spouse" },
      "reservation": { "bookingId": "XPGMSXGF", "status": "Confirmed", "channelCode": "Ibe", "property": { "id": "BER", "code": "BER" } }
    }
  },
  {
    "sourceSystem": "apaleo",
    "externalKey": "XPGMSXGF:booker:2026-07-09T14:30:00Z",
    "recordTimestamp": "2026-07-09T14:30:00Z",
    "sourceObject": {
      "type": "booking", "id": "XPGMSXGF", "role": "BOOKER",
      "version": "2026-07-09T14:30:00Z",
      "businessStart": "2026-08-01T15:00:00+02:00", "businessEnd": "2026-08-04T11:00:00+02:00"
    },
    "payload": {
      "firstName": "Anna", "lastName": "Muster", "email": "anna@example.com",
      "person": {},
      "booking": {
        "comment": "Late arrival",
        "reservations": [ { "id": "XPGMSXGF-1", "status": "Confirmed", "property": { "id": "BER" },
                            "arrival": "2026-08-01T15:00:00+02:00", "departure": "2026-08-04T11:00:00+02:00", "channelCode": "Ibe" } ]
      }
    }
  }
]
```

The booker and the primary guest resolve to one guest through the shared email, by the engine's
rules and not by the connector's. When the booker is later corrected, only the booking's record
is sent again, under the booking's new `modified`; the reservation's records are not.

## What is not mapped

- Cancellation, check-in and check-out change no person and produce no version (spec US2
  scenario 6). The status travels in `payload.reservation` when a person next changes.
- A new reservation joining a booking changes the booking's derived dates and therefore its
  hash, so the booking is sent again with the wider dates; its booker is unchanged and resolves
  as a duplicate identifier, which is harmless.
- `deleted` objects are never fetched (research R5).
- No field of either object ever becomes a top-level payload field. A future extractor field is
  added here first, then in the mapper, then in the roster hash.
