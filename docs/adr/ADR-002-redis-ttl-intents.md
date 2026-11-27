# ADR-002: Redis TTL keys for live intents

**Status:** Accepted

## Context
An intent lives about 5 minutes and is read on every voice-agent poll and every customer verdict.
Two verdicts (double tap, two devices) must not both win.

## Decision
- `intent:{id}` is a Redis hash with `EXPIRE` equal to the token lifetime.
- `active:{customerRef}` (SET NX, same TTL) enforces one open call per customer.
- State moves through a Lua script that compares and sets atomically: only `CREATED`/`NOTIFIED` may move,
  and only once to a terminal state.

## Consequences
- Expiry costs nothing and needs no cleanup job.
- Losing Redis drops open intents. Acceptable: those calls go unverified, and every intent is already in the audit log.
- Expiry is not audited yet. Next step: Redis keyspace notifications -> an `EXPIRED` audit event.
- `CallFlowIntegrationTest.concurrentVerdictsOnlyOneWins` proves the compare-and-set.
