# Research: Removing a connection's webhook subscription

**Feature**: 009-remove-subscription | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

Read against the connector at `guestgraph/connector-apaleo@c7ca08a`, the state the API page pins.

## R1 — The reconciliation already leaves a removal alone

**Finding**: `Reconciliation` calls `Subscriptions.check(c)`, not `ensure(c)`. `check` reads
Apaleo, records what it found, and logs a warning when nothing names this connector. It never
creates. Only `ensureAll()`, on `ApplicationReadyEvent`, creates or replaces.

**Decision**: FR-007 costs no change to the reconciliation. What it does cost is the status: a
removed subscription today records `active=false` with the reason "no subscription found", which
is exactly what a failed creation records, and the reconciliation writes a warning line every
fifteen minutes for a connection that is deliberately without one. The work is in telling the two
apart, which is story 2's, not in preventing a recreation that does not happen.

**Alternatives considered**: a flag read by the reconciliation, which would have been dead code.

## R2 — A removal does not survive a restart, and that is the honest default

**Finding**: the intended state has nowhere to live. `Subscriptions` keeps its statuses in a
`ConcurrentHashMap`, which is lost on restart, and `ensureAll()` then creates a subscription for
every configured connection. The `connection` table carries the account, the properties, the
secret hash and the counters, and no intent.

**Decision**: a removal is forgotten on restart, and the connector says so. A connector that
starts subscribes every connection it is configured to serve; that is its contract and this slice
does not change it. FR-008 is satisfied by saying it in the status and the README rather than by
making it untrue.

**Rationale**: the case that prompted this is a teardown — remove, then stop the connector, then
take the tunnel down — in which a restart does not occur. Persisting the intent would need a
column and a migration, and it would buy a foot-gun: a subscription removed during a sandbox
session in March would still be absent in May, silently, and the connector would look healthy
while receiving nothing. Configuration says which connections this connector serves; a runtime
flag that quietly overrides it is a second source of truth.

**Alternatives considered**: a `subscription_wanted` column on `connection`, defaulting to true.
Rejected for the reason above. It becomes the right answer the day an operator wants a connection
configured but dormant, and that is a different feature with its own reasons.

## R3 — Restoring is a second act, not a restart

**Finding**: FR-010 requires restoring without editing configuration or restarting. With R2's
decision, a restart would restore it, but the requirement rules that out as the only way.

**Decision**: the slice adds two acts on one thing, removing and restoring, as the operations
surface already pairs starting a run with reading it. `Subscriptions.ensure(c)` is exactly the
restore and needs no new logic.

**Rationale**: symmetry is what makes the removal safe to use. An operator who can put it back in
one request will remove it; one who has to restart the connector will reach for Apaleo's API
instead, which is the situation this slice exists to end.

## R4 — Apaleo can delete a subscription; the connector cannot

**Finding**: `ApaleoWebhooks` has `list()` and `ensure(...)`, which POST and PUT against
`/v1/subscriptions`. There is no delete. The subscription's id is already modeled, and
`Subscriptions.endpointOf(c)` builds the endpoint URL that identifies this connection's own
subscription among the account's.

**Decision**: add a delete to the webhook client, and have the removal find the subscription by
this connection's endpoint exactly as `check` does, so a connector never deletes a subscription
that is not its own — including another configured connection's on the same Apaleo account, which
`ensure` already takes care to leave alone.

**Rationale**: an account's subscriptions are shared by everything using that client. Deleting by
endpoint rather than by "the first one" is what keeps two connections on one account independent.

## R5 — Which answer a removal gives

**Finding**: the operations surface answers a started run with 202 and a document, and refuses
with the family's problem details. Removal has three outcomes: it deleted one, it found none, or
Apaleo refused.

**Decision**: the first two are one answer, because FR-003 makes the request a statement of the
end state rather than of an act; the response says which happened so an operator tearing down a
stale subscription can tell. Apaleo refusing is a refusal, with the family's shape.

**Alternatives considered**: 204 for "deleted" and 404 for "none existed". Rejected: it makes the
idempotent case look like an error and invites a script to treat a second run as a failure.

## R6 — What the status must now say

**Finding**: `Subscriptions.Status` carries `active`, `id`, `eventTypes`, `checkedAt` and
`reason`. A connection that never had a subscription, one whose creation failed, and one removed
on request are all `active=false` with a `reason` string.

**Decision**: the status gains a state rather than a second boolean, so that the three cases are
named and a fourth is possible later. The removal sets it; the restore clears it; a restart loses
it, which is R2's decision showing through the status honestly.

**Rationale**: a boolean pair invites the combination that means nothing. The connector already
reports `reason` for a human; this is the same fact in a form a script can read.
