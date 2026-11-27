-- Hash-chained, append-only audit trail (ADR-003).

CREATE TABLE audit_chain_head (
    tenant_id  TEXT PRIMARY KEY,
    last_seq   BIGINT   NOT NULL,
    last_hash  CHAR(64) NOT NULL
);

CREATE TABLE audit_event (
    tenant_id     TEXT        NOT NULL,
    seq           BIGINT      NOT NULL,
    intent_id     TEXT        NOT NULL,
    event_type    TEXT        NOT NULL,
    -- TEXT, not JSONB: JSONB re-orders keys and would change the bytes the hash was computed over.
    payload_json  TEXT        NOT NULL,
    prev_hash     CHAR(64)    NOT NULL,
    hash          CHAR(64)    NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, seq)
);

CREATE INDEX audit_event_intent_idx ON audit_event (intent_id);

-- Append-only: block UPDATE and DELETE even for the app's own user.
-- (A superuser can still drop the trigger; anchoring chain heads externally is a stretch goal.)
CREATE FUNCTION audit_event_block_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_change
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_block_change();
