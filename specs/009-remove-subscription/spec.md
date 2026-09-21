# Feature Specification: Removing a connection's webhook subscription

**Feature Branch**: `009-remove-subscription`

**Created**: 2026-09-12

**Status**: Draft

**Input**: User description: "A connector operator can remove a connection's Apaleo webhook subscription through the connector's own operations surface, so that tearing down a deployment or a tunnel leaves no subscription posting to a dead URL. Deferred from slice 5's sandbox session and recorded in the roadmap."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tear a connection down without leaving a subscription behind (Priority: P1)

An operator is finishing a sandbox session, decommissioning a connection, or taking down the tunnel that made the connector reachable. Before stopping anything, they ask the connector to remove that connection's subscription. The connector deletes it in the source system and reports that it is gone. Nothing is left posting to an address that will stop answering, and the operator did not have to hold the connection's credentials themselves to do it.

**Why this priority**: It is the whole feature. Every other story here is a refinement of the same act, and without this one an operator has to reach for the source system's own API with credentials the connector already holds, which is what happens today.

**Independent Test**: With a subscription in place for a connection, ask the connector to remove it. Read the connector's status and confirm the connection reports no subscription; ask the source system directly and confirm none exists for that endpoint.

**Acceptance Scenarios**:

1. **Given** a connection with an active subscription, **When** an operator removes it, **Then**
   the subscription no longer exists in the source system and the connector's status reports the
   connection as having none.
2. **Given** a connection whose subscription was already removed, **When** an operator removes it
   again, **Then** the connector answers as though it had removed one, because the operator's
   intent — that no subscription exist — is satisfied either way.
3. **Given** an operator without the operations credential, **When** they attempt a removal,
   **Then** the connector refuses and the subscription is untouched.
4. **Given** a connection name no configuration carries, **When** a removal is attempted for it,
   **Then** the connector refuses with the same answer it gives for any unknown connection.

---

### User Story 2 - Know that a removal happened, and by whom (Priority: P2)

An operator who removed a subscription, or a second operator arriving afterwards, needs to see that the connection is deliberately without one rather than broken. The connector's status says so, and the removal is in the log with the actor that asked for it.

**Why this priority**: A connection with no subscription looks identical to a connection whose subscription failed to create. Without this, the next person cannot tell a teardown from a fault, and the connector's own reconciliation would be the only thing that ever noticed.

**Independent Test**: Remove a subscription, then read the status and the log. The status distinguishes "removed on request" from "never created"; the log names the connection and the act.

**Acceptance Scenarios**:

1. **Given** a subscription removed on request, **When** the status is read, **Then** the
   connection reports no subscription and says the absence was asked for.
2. **Given** a removal, **When** the log is read, **Then** it carries one line naming the
   connection and the removal, and no secret.

---

### User Story 3 - A removal is not undone by the connector's own housekeeping (Priority: P2)

The connector creates a subscription at start and confirms it during every reconciliation. An operator who removes one expects it to stay removed for as long as the connector runs, rather than reappearing at the next reconciliation a few minutes later.

**Why this priority**: Without it the feature is a fifteen-minute pause rather than a removal, and an operator tearing a tunnel down would be surprised by traffic resuming. It is separable from story 1 because a removal that survives until the next restart is already useful for the sandbox case that prompted this.

**Independent Test**: Remove a subscription, then trigger a reconciliation. The subscription is still absent and the reconciliation reports success rather than recreating it.

**Acceptance Scenarios**:

1. **Given** a subscription removed on request, **When** a reconciliation runs, **Then** it does
   not recreate the subscription and does not report the connection as failing.
2. **Given** a subscription removed on request, **When** the connector is restarted, **Then** the
   documented behavior is what happens, whichever way it is decided in planning, and the status
   says which.

---

### Edge Cases

- The source system is unreachable or refuses the removal. The connector must not report a
  removal that did not happen, and the operator must be able to try again.
- A delivery arrives after the removal, because the source system had already queued it. The
  connector answers it as it answers any delivery; a subscription's absence is not a reason to
  reject work already in flight.
- Two removals arrive at once for the same connection. The second finds nothing to remove and
  answers as the first did.
- A connection has no subscription because one was never created. Removal is not an error.
- The subscription in the source system points at a different endpoint than this connector's,
  because a public URL changed. This is the case that motivated the feature; the connector must
  say which subscription it removed so an operator can tell whether the stale one is gone.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: An operator MUST be able to remove one named connection's webhook subscription
  through the connector's operations surface, using the same credential that guards every other
  operation there.
- **FR-002**: The connector MUST remove the subscription in the source system, not merely forget
  it locally. A removal that the source system did not accept is not a removal.
- **FR-003**: Removal MUST be idempotent: asking twice, or asking when none exists, succeeds
  without an error, because the operator asked for an end state rather than for an act.
- **FR-004**: The connector MUST refuse a removal for a connection name it does not serve, with
  the same refusal it gives any unknown connection.
- **FR-005**: The connector's status MUST report, per connection, that a subscription is absent
  and whether the absence was asked for.
- **FR-006**: A removal MUST appear in the log with the connection it concerned, and MUST NOT
  carry the connection's secret, its credentials, or the subscription's endpoint secret.
- **FR-007**: While a connection's subscription is deliberately absent, the connector's
  reconciliation MUST NOT recreate it, and MUST NOT report the connection as failing for lacking
  one.
- **FR-008**: The connector MUST state, in its status and its documentation, what a restart does
  to a deliberately absent subscription, so an operator is never guessing.
- **FR-009**: Removing a subscription MUST NOT change anything else about the connection: its
  stored state, its sync points, its pending deliveries and its held guest ids are untouched, and
  a full sync or a reconciliation still runs on request.
- **FR-010**: An operator MUST be able to restore a subscription afterwards without editing
  configuration or restarting, so that a removal is not a one-way door.
- **FR-011**: The connector MUST answer a removal it could not perform with a refusal that says
  the removal did not happen, distinct from the answer for a removal that found nothing to do.

### Key Entities

- **Connection**: The pairing of one source-system account with one engine tenant, named in
  configuration. The unit a removal names.
- **Subscription**: The source system's record that it should post events to this connector for
  this connection. Owned by the source system; the connector creates, confirms and now removes it.
- **Intended subscription state**: Whether this connection is meant to have a subscription. New
  here: until now the intent was always "yes", implied by the connection existing.

## Success Criteria *(mandatory)*

- **SC-001**: An operator can take a connection's subscription away in one request, without
  holding the connection's source-system credentials and without using any tool but the connector.
- **SC-002**: After a removal, the source system holds no subscription pointing at this
  connector for that connection, confirmed against the source system itself rather than against
  the connector's own record.
- **SC-003**: A removal survives the connector's own housekeeping: after the next reconciliation,
  the subscription is still absent.
- **SC-004**: Reading the connector's status after a removal tells an operator that the absence
  was deliberate, distinguishably from a subscription that failed to create.
- **SC-005**: Repeating a removal, or removing one that never existed, succeeds every time.
- **SC-006**: Nothing else about the connection changes: the counts, sync points and pending work
  read the same immediately before and after.

## Assumptions

- The operations surface and its bearer credential are the right place, because every other act
  an operator performs on a connection is already there and guarded the same way.
- Removal is per connection rather than per connector. A connector serves many connections and
  tearing one down must not touch the others.
- Restoring a subscription is the existing behavior and does not need inventing: the connector
  already creates one when it is missing. What this slice adds is a way to say "not this one"
  and a way to say it again in reverse.
- The source system's own removal is synchronous and either succeeds or fails; no background
  retry of a removal is specified here.
- The engine is not involved. This is entirely between the connector and the source system.

## Out of Scope

- Removing subscriptions in bulk, for every connection at once.
- Removing a subscription automatically when the connector shuts down. That was considered and
  is not wanted: a restart is not a teardown, and a connector that unsubscribed on every stop
  would lose events during a deployment.
- Cleaning up subscriptions belonging to a connection that has been deleted from configuration
  entirely, which is a different problem: the connector no longer has the credentials for it.
- Any change to how subscriptions are created, confirmed or replaced.
