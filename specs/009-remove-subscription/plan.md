# Implementation Plan: Removing a connection's webhook subscription

**Branch**: `009-remove-subscription` | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-remove-subscription/spec.md`

## Summary

The connector gains two acts on one thing: an operator can take a connection's Apaleo
subscription away and put it back, through the operations surface and behind the token that
guards the rest of it. Removal deletes the subscription whose endpoint is this connection's and
nothing else in the account, and it is a statement of the end state, so asking twice succeeds.
The subscription's state stops being a boolean and becomes four named states, so that a
deployment deliberately without one no longer looks like a deployment whose subscription failed.
No table changes, no migration, and nothing about how a subscription is created.

## Technical Context

**Language/Version**: Java 25 in the connector, unchanged. The spec and its contract are prose
and YAML in the engine.

**Primary Dependencies**: none new. The webhook client gains a delete against the endpoint it
already talks to.

**Storage**: none. The intended state lives in memory beside the rest of the subscription's
status, and a restart loses it on purpose (research R2).

**Testing**: the connector's integration suite against its WireMock Apaleo stub, which already
models the subscription endpoints; a sandbox walk for what a stub cannot prove.

**Target Platform**: unchanged.

**Project Type**: two new paths on an existing service, plus a contract and a spec in the engine.

**Performance Goals**: none. Two operator-initiated calls, one Apaleo request each.

**Constraints**: slice 5's contract is frozen, so the new paths get their own file and the
connector lists both as sources (R4 of slice 7 established that shape). The connector's served
document is generated and held against the pins by CI.

**Scale/Scope**: 2 paths, 1 new contract file, 1 enum, 1 delete on the webhook client, 2
controller methods, 1 status field, 6 integration tests, 3 documents.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
*Source: `.specify/memory/constitution.md` v1.0.0*

**Initial evaluation — PASS.** **Post-design re-evaluation — PASS.**

- [x] **Tenant isolation (I)**: every act is scoped to one named connection, which is the
      connector's tenant equivalent; a removal cannot touch another connection's subscription
      even on a shared Apaleo account, which the endpoint match enforces (R4).
- [x] **Immutable source records (II)**: no record is written, read or altered.
- [x] **No silent data loss (III)**: a delivery already in flight is answered as any delivery is;
      pending deliveries, sync points and held ids are untouched (FR-009). A removal that Apaleo
      refused is reported as a refusal, never as success (FR-011).
- [x] **Explainable & reversible resolution (IV)**: untouched, and the feature is itself
      reversible by design (FR-010).
- [x] **API-first (V)**: the two paths are in a contract before they are in code, refusals carry
      the family's problem shape, and the connector's served document is regenerated from the
      contract rather than written.
- [x] **TDD on the resolution engine (VI)**: no engine logic changes. The connector's own tests
      are written first regardless, as slice 5's were.
- [x] **Stack & shape**: unchanged; no module, no dependency, no table.
- [x] **Open-core boundary**: nothing commercial.
- [x] **GDPR readiness**: no personal data is read or written. The log line names the connection
      and not the secret in its endpoint (FR-006).

## Project Structure

### Documentation (this feature)

```text
specs/009-remove-subscription/
├── plan.md              # This file
├── spec.md
├── research.md          # R1..R6
├── data-model.md        # the four states, what a removal does not touch
├── quickstart.md
├── contracts/
│   └── connector-subscription.yaml
└── checklists/
    └── requirements.md
```

### Source Code

```text
guestgraph/connector-apaleo/
├── src/main/resources/api/sources.json          # gains this slice's contract as a second source
├── src/main/resources/api/openapi.yaml          # regenerated from both
├── src/main/java/io/guestgraph/connector/apaleo/
│   ├── apaleo/ApaleoWebhooks.java               # + delete(id)
│   ├── api/events/Subscriptions.java            # + State, remove(c), restore(c); Status gains state
│   └── api/ops/StatusController.java            # + removeSubscription, restoreSubscription
│   └── api/ops/StatusDocuments.java             # the subscription block gains state
└── src/test/java/io/guestgraph/connector/apaleo/integration/
    └── SubscriptionLifecycleTest.java           # the six behaviors

guestgraph/engine/
└── docs/roadmap-notes.md                        # the deferral recorded, then consumed
```

**Structure Decision**: the connector keeps its shape. The spec, the contract and the roadmap
line live in the engine, where every slice's decisions live and where the connector's own
contract already is.

## Design Decisions Carried From Phase 0

| # | Decision | Where |
| --- | --- | --- |
| R1 | The reconciliation already never recreates; the work is in the status, not in a guard | [research.md](research.md) |
| R2 | A removal is forgotten on restart, said plainly rather than made untrue by a migration | [research.md](research.md) |
| R3 | Restoring is a second act on the same path, not a restart | [research.md](research.md) |
| R4 | Delete by this connection's endpoint, never by "the account's first" | [research.md](research.md) |
| R5 | One answer for removed and nothing-to-remove, with a field saying which | [research.md](research.md) |
| R6 | Four named states replace a boolean that could not tell a teardown from a fault | [data-model.md](data-model.md) |

## Complexity Tracking

No Constitution Check violations — the table is intentionally empty.

One choice worth naming. **The intended state is deliberately not persisted**, which means the
connector can be in a state its configuration does not describe, until it restarts. The
alternative, a column, was rejected because a subscription removed in a sandbox session would
otherwise still be absent months later while the connector looked healthy. The cost is that an
operator who removes and then redeploys gets a subscription back; research R2 says so and the
README says so where they will look.

## Follow-ons Not In This Slice

- A connection configured but dormant, which is what a persisted intent would really be for, and
  which wants its own reasons and its own configuration rather than a runtime flag.
- Removing every connection's subscription in one request, which a teardown script can do in a
  loop today and which hides how many it touched.
- Cleaning up after a connection deleted from configuration, whose credentials are gone with it.
