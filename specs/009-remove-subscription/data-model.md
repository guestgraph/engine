# Data Model: Removing a connection's webhook subscription

**Feature**: 009-remove-subscription | **Date**: 2026-09-12 | **Research**: [research.md](research.md)

**No table changes and no migration.** The one new fact, whether a connection is deliberately without a subscription, lives where the rest of the subscription's state already lives: in the connector's memory, for as long as the connector runs. Research R2 gives the reason.

## The subscription's state, per connection

`Subscriptions.Status` carries `active`, `id`, `eventTypes`, `checkedAt` and `reason` today. It gains one field, and `active` stays for what it says.

| Field | Meaning |
| --- | --- |
| `state` | `ACTIVE`, `MISSING`, `REMOVED` or `UNKNOWN` — see below |
| `active` | `state == ACTIVE`; kept because the status document already publishes it |
| `id` | Apaleo's id for the subscription, when there is one |
| `eventTypes` | what the connector subscribes to, from configuration |
| `checkedAt` | when this was last read from or written to Apaleo |
| `reason` | why it is not active, for a human; never a payload, never a secret |

### The four states

| State | Means | Set by |
| --- | --- | --- |
| `ACTIVE` | Apaleo holds a subscription pointing at this connection's endpoint | `ensure`, `check` |
| `MISSING` | Apaleo holds none and nobody asked for that | `ensure` failing, `check` finding none |
| `REMOVED` | Apaleo holds none because an operator asked | the removal |
| `UNKNOWN` | not read yet, or the last read failed | start-up, a failed `check` |

`MISSING` and `REMOVED` are the pair the status exists to tell apart: the first is a fault worth a warning line, the second is a deployment in the state its operator chose.

`REMOVED` survives until the connector stops or an operator restores. It is not persisted, so a restart raises `ACTIVE` again through `ensureAll()`. The status says so in `reason`, and the README says so in prose.

## Transitions

```text
                    ┌──────────────── restore, or a restart ─────────────────┐
                    ▼                                                        │
  UNKNOWN ──ensure──► ACTIVE ──remove──► REMOVED ──reconciliation──► REMOVED ┘
     │                  │                                  (check leaves it)
     │                  └──check finds none──► MISSING ──ensure──► ACTIVE
     └──ensure fails──► MISSING
```

A removal from `MISSING` or `UNKNOWN` is not an error: it reads Apaleo, deletes whatever names this connection's endpoint if anything does, and lands on `REMOVED`. That is FR-003.

## What a removal does not touch

The `connection` row, its counters and its `last_activity_at`; every `sync_point`; every `processed_event` still pending; every `held_guest_id`; every `sync_run`. A removal is about Apaleo's record and this connector's intent, and nothing else. That is FR-009, and the integration test for it reads those five before and after.

## What identifies a subscription

The endpoint URL the connector builds from its public URL, the fixed path and the connection's webhook secret, which `Subscriptions.endpointOf` already produces. A removal deletes the subscription whose `endpointUrl` equals it, and nothing else in the account. Two connections sharing one Apaleo account therefore cannot remove each other's, which `ensure` already establishes and this keeps.
