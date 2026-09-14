# Quickstart & Validation: Removing a connection's webhook subscription

**Feature**: 009-remove-subscription

**Contract**: [contracts/connector-subscription.yaml](contracts/connector-subscription.yaml) ·
**Model**: [data-model.md](data-model.md) · **Research**: [research.md](research.md)

## Prerequisites

JDK 25, Docker and `./mvnw` in the connector. The walk needs an Apaleo test account, a
connections file naming it, and a tunnel, as slice 5's quickstart does; the suites need neither.

## Run the test suites (primary validation)

```bash
# in guestgraph/connector-apaleo
./mvnw verify                                 # the new integration tests among the rest
sh service-conventions/service-conventions-check
sh conventions/conventions-check
# in guestgraph/engine
sh conventions/conventions-check              # this spec is prose too
```

## Walk

1. **A removal removes.** With the connector running against the sandbox and a subscription in
   place, `DELETE /connections/{id}/subscription` with the operations token. Read the answer:
   `state` is `REMOVED`, `removed` is true, `endpoint` is the URL it pointed at. Ask Apaleo
   directly, with its own API, and confirm no subscription names that endpoint. *Confirms SC-001
   and SC-002.*
2. **Twice is once.** Repeat the same request. It succeeds, `state` is `REMOVED`, and `removed`
   is false. Do it again on a connection that never had one: the same. *Confirms SC-005.*
3. **The status tells a teardown from a fault.** Read `GET /status`. The removed connection
   reports `state: REMOVED`. Stop Apaleo from answering — or point a second connection at a bad
   account — and read again: that one reports `MISSING`. The two are distinguishable without
   reading prose. *Confirms SC-004.*
4. **Housekeeping leaves it alone.** Start a reconciliation on the removed connection with
   `POST /connections/{id}/sync/reconcile`, wait for the run to finish, and read the status: still
   `REMOVED`, the run's outcome is success, and no warning line says the subscription is gone.
   *Confirms SC-003.*
5. **Nothing else moved.** Read the status before and after a removal and compare the connection's
   counters, its sync points, its pending events and its held guest ids. All identical. Then start
   a full sync and watch it run normally. *Confirms SC-006.*
6. **The way back.** `PUT /connections/{id}/subscription`. The answer is `ACTIVE` with an id, and
   Apaleo holds a subscription again. Edit a reservation in Apaleo and watch the delivery arrive.
   *Confirms FR-010.*
7. **A restart subscribes again.** With a connection removed, restart the connector and read the
   status: `ACTIVE`. This is the documented behavior, not a bug — research R2 says why, and the
   README says it where an operator will look. *Confirms FR-008.*
8. **The teardown it was built for.** Remove every connection's subscription, stop the connector,
   stop the tunnel. Ask Apaleo what it holds: nothing pointing at the tunnel's address.

## Success-criteria spot checks

| Criterion | Check |
|---|---|
| SC-001 | Step 1, and the integration test for a removal |
| SC-002 | Step 1, against Apaleo itself rather than the connector's own record |
| SC-003 | Step 4, and the integration test that reconciles after a removal |
| SC-004 | Step 3, and the status test |
| SC-005 | Step 2, and the idempotence test |
| SC-006 | Step 5, and the test that reads the five untouched things |
