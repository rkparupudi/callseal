# CallSeal

> ⚠️ **Work in progress: this build has not been verified yet.** The code has not been compiled or run. First milestone: `mvn verify` green and the end-to-end demo working.

**Verified AI voice calls.** CallSeal lets a customer confirm, during the call itself, that a bank's AI voice agent is genuine. The bank announces each call to CallSeal. The customer's bank app then shows a two-word code, which the agent speaks aloud. A voice-clone scammer has no announced call and no code.

> **Status: scaffold, not yet built or run.** It was written without access to Maven Central, so it has never been compiled. Your first job is `mvn verify`; expect a few small fixes (see "Known gaps").

## How it works

1. The bank backend gets an OAuth 2.0 token (client credentials) and calls `POST /v1/call-intents`.
2. CallSeal stores the intent in Redis (5-minute TTL), appends an audit event, and returns a signed call token (ES256) and the spoken code.
3. The customer's bank app receives a card with the code (Server-Sent Events in this demo).
4. The bank's voice agent says: "Your app shows the words TIGER MANGO."
5. The customer taps **Genuine** or **Report**. A Redis Lua compare-and-set ensures exactly one verdict wins.

The code only travels app → customer's eyes, and the agent speaks it. The customer never reads a code out, which keeps this clear of the OTP-phishing pattern.

## Run it

Prerequisites: Java 21, Maven 3.9+, Docker.

```bash
mvn verify                                          # unit + Testcontainers integration tests (Docker must be running)
docker compose -f infra/docker-compose.yml up --build
```

Then:

1. Open http://localhost:8080/bank-app.html and click **Connect** (customer `cust-001`).
2. Run `./scripts/demo.sh` (Git Bash on Windows works).
3. Open the voice-agent link it prints, click **Start call**, and answer in the bank app.
4. Grafana: http://localhost:3000 (admin / admin), dashboard "CallSeal".

## API

| Method and path | Auth | Purpose |
| --- | --- | --- |
| `POST /oauth2/token` | client credentials | Bank access token |
| `POST /v1/call-intents` | `calls.write` + `Idempotency-Key` header | Announce a call |
| `GET /v1/call-intents/{id}` | `calls.read` | Voice agent reads state |
| `GET /v1/audit/verify` | `calls.read` | Walk the bank's audit hash chain |
| `GET /.well-known/callseal-jwks.json` | none | Public key for call tokens |
| `GET /demo/customers/{ref}/pending-calls` | **demo only, none** | SSE stream for the demo bank app |
| `POST /demo/call-intents/{id}/verdict` | **demo only, none** | Customer verdict |

The `/demo/**` endpoints have no customer authentication. In a real deployment they live behind the bank app's own session.

## Design

- Spec, threat model and plan: see the design doc (CallSeal — Verified AI Voice Calls: Design Spec)
- [ADR-001 Modular monolith](docs/adr/ADR-001-modular-monolith.md)
- [ADR-002 Redis TTL intents](docs/adr/ADR-002-redis-ttl-intents.md)
- [ADR-003 Hash-chained audit log](docs/adr/ADR-003-hash-chained-audit.md)
- [ADR-004 SSE push](docs/adr/ADR-004-sse-push.md)

## Known gaps (fix in week 1)

- **Never compiled.** Check `mvn verify` first. The most likely break: `OAuth2AuthorizationServerConfiguration.applyDefaultSecurity` is deprecated, and may be removed in the Spring Authorization Server version you get. The comment in `AuthorizationServerConfig` says what to switch to.
- Spring Boot is pinned to `3.5.0`; bump to the latest 3.5.x patch.
- Word list has 64 words (4,096 codes). Target: 2,048 words.
- Expired intents are not audited yet (ADR-002).
- No `PushGateway` interface yet (ADR-004).
- Keys (OAuth and call-token) are generated at startup; tokens die on restart.

## Measured performance

_Not measured yet._ Fill in real p95 numbers from the load test. Don't publish targets as results.
