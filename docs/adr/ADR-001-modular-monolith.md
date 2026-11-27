# ADR-001: Modular monolith, not microservices

**Status:** Accepted

## Context
One builder, a 3-week timebox, one core flow (announce a call, customer verifies it).

## Decision
One Spring Boot service with package-level modules: `intent`, `verdict`, `push`, `audit`.
Modules talk only through Spring application events (`IntentEvent`); no module calls another's internals.
Audit listens at order 1, push at order 2.

## Consequences
- One deploy, one `docker compose up`, simple local debugging.
- Splitting later is mechanical: the modules already share no tables, and events map onto a broker topic.
- Trade-off: the push module can't scale independently, and SSE connections are pinned to one instance
  (fan out via Redis pub/sub if there are ever several instances).
- Trade-off: events are synchronous, so an audit failure fails the request. That's acceptable (no audit, no call)
  but a Redis write can be left behind; a transactional outbox would close that gap.
