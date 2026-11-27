# ADR-003: Hash-chained append-only audit log in PostgreSQL

**Status:** Accepted

## Context
The audit trail is the compliance evidence that a call was announced and how the customer answered.
It must be tamper-evident.

## Decision
- One `audit_event` table per deployment, one chain per tenant.
- Each row stores `prev_hash` and `hash = SHA-256(prev_hash | seq | type | intentId | canonical JSON)`.
- Payload stored as TEXT (not JSONB) so the hashed bytes are exactly what is stored.
- A trigger blocks UPDATE and DELETE. `GET /v1/audit/verify` walks the chain.
- Appends lock the tenant's `audit_chain_head` row (`SELECT ... FOR UPDATE`) so sequence numbers never fork.

## Consequences
- An edited, inserted or deleted row breaks the chain at that row.
- Appends are serialised per tenant: fine at demo volume; shard by tenant if it grows.
- A database superuser could rewrite the whole chain. Stretch goal: publish chain heads periodically
  to an external store (or a signed daily digest).
- Kafka was considered and left out: it adds durability plumbing the MVP doesn't need.
