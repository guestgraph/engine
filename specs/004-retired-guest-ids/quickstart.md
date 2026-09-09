# Quickstart & Validation: Retired Guest Ids Resolve

**Feature**: 004-retired-guest-ids
**Contracts**: [contracts/openapi.yaml](contracts/openapi.yaml) (schemas; the changed operations are amended in the slice-1 and slice-3 contract files, research R5)
**Model**: [data-model.md](data-model.md) · **Research**: [research.md](research.md)

## Prerequisites

As slices 1–3: JDK 25, Docker, `./mvnw`. Local run: `./mvnw spring-boot:run
-Dspring-boot.run.profiles=local` (tenant `demo` / key `demo-key`).

The migration adds two indexes only, but Flyway still records it: a local volume created before it
fails the checksum check, so `docker compose down -v` and re-run.

## Run the test suite (primary validation)

```bash
./mvnw verify
./scripts/regen-er.sh   # must produce no diff — indexes are not rendered, the gate re-runs anyway
```

Expected green, including the new suites:

- `resolution/GuestIdResolutionScenarioTest` — pure JVM on `InMemoryGraph`, written first:
  active id; one merge; a three-guest chain; a review-confirmed merge; a split onto two guests;
  a split branch that later merges; a merge into a guest that is later split; a partial unmerge
  that keeps the guest active; two paths to one current guest named once; an id that never
  existed; an id of another tenant (SC-001, SC-002)
- `integration/RetiredGuestIdApiTest` — the walks below end to end, including the 410 shape on
  every sub-resource and a ten-hop chain timed under 1 s (SC-003, SC-005)
- `integration/GuestQueryApiTest`, `ExplainUnmergeApiTest`, `TimelineApiTest` — unchanged
  assertions still pass; the active guest document gains only `status` (FR-012)
- `contract/OpenApiConformanceTest` — unions four feature contracts; no operation was added or
  removed, so the served surface is unchanged

## End-to-end smoke walk (maps to the spec's user stories)

Base `http://localhost:8080/api/v1`, headers `X-API-Key: demo-key`,
`Content-Type: application/json`. `$B` and `$H` as in the slice-2 quickstart.

### US1 — a stored id never goes dark

1. Ingest `a` with an email and `b` with a phone → two guests, `X` and `Y`.
2. Ingest `c` carrying both → `MERGED`; one of the two is absorbed. Call it `X`.
3. `GET $B/guests/{X}` → 200, `status: MERGED`, `currentGuestIds: [Y]`, `retiredAt` set, one
   hop of `kind: MERGE` whose `eventId` is the merge. *Confirms SC-001.*
4. `GET $B/guests/{Y}` → 200, the guest document as before plus `status: ACTIVE`.
5. `GET $B/guests/{random uuid}` → 404, type `not-found`, no `currentGuestIds`.
6. `GET $B/guests/{Y}/explain` → the hop's `eventId` is among the events (Constitution IV).

### US2 — chains are followed to the end

7. Ingest `d` with a new email → guest `Z`; ingest `e` carrying that email and `Y`'s phone →
   `Y` is absorbed into `Z`. The extractor orders identifiers email first and the first matched
   guest survives, so the bridge's only email must be `Z`'s for the chain to grow.
8. `GET $B/guests/{X}` → `currentGuestIds: [Z]`, two hops in order: `X → Y`, then `Y → Z`.
9. `GET $B/guests/{Y}` → `currentGuestIds: [Z]`, one hop.

### US3 — a split resolves to every guest it became

10. Set `reviewThreshold` to 1 with `PUT $B/config/matching`, so a shared email parks instead
    of attaching. Ingest `r1` with that email plus `loyaltyId: L1`, then `r2` with the email
    alone → `r2` parks a review; `POST $B/match-reviews/{id}` with `CONFIRM` → guest `S` holds
    both.
11. `POST $B/guests/{S}/unmerge` with both record ids → `remainingGuestId: null`; the detached
    records replay onto two guests, because the email is still crowded.
12. `GET $B/guests/{S}` → `status: SPLIT`, `currentGuestIds` lists every guest the records
    landed on, one hop of `kind: SPLIT` naming them as successors.
13. Raise `reviewThreshold` to 1000, ingest `v` with a new phone → guest `V`, then a bridge
    carrying that phone and `L1` → `V` survives and `W1` is absorbed. `GET $B/guests/{S}` now
    names `V` and `W2`, with two hops. *Confirms SC-002.*
14. Unmerge only one record off a three-record guest → `GET` on that guest is still `ACTIVE`.

### US4 — everything under a retired id points to the current one

15. `GET $B/guests/{X}/records`, `/explain`, `/timeline` and `POST $B/guests/{X}/unmerge` →
    each 410, type `guest-retired`, `guestId: X`, `currentGuestIds: [Z]`; nothing was recorded.
    *Confirms SC-005.*
16. The same four on a random uuid → 404 `not-found` with no `currentGuestIds` (FR-009).

## Success-criteria spot checks

| Criterion | Check |
|---|---|
| SC-001 | After every merge in the integration suite, the absorbed id answers 200 with the survivor. |
| SC-002 | For every `currentGuestIds` entry the suite reads it back and asserts `status: ACTIVE`. |
| SC-003 | Seed a ten-merge chain; `GET` on its first id under 1 s. Assert in `RetiredGuestIdApiTest`. |
| SC-004 | A scenario suite of at least 20 merge and split sequences, each ending with the integrator rule applied — replace the stored id with `currentGuestIds` when the status is `MERGED` — and a final read that is `ACTIVE`. |
| SC-005 | Every sub-resource request on a retired id in the suite is 410 with the current ids; never 404, never another guest's data. |
