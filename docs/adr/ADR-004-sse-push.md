# ADR-004: Server-Sent Events for the customer push

**Status:** Accepted

## Context
The demo "bank app" is a web page. A real bank would push through FCM (Android) or APNs (iOS) into its own app.

## Decision
Spring MVC `SseEmitter`, held in `SseHub` and keyed by customer.

## Consequences
- No third-party push account needed for the demo.
- A page that connects after the call was announced misses the card, because the plain code is never stored.
  Real push (FCM/APNs) queues the message, which removes this gap.
- Next step: put a `PushGateway` interface in front of `SseHub` so an `FcmPushGateway` can replace it.
