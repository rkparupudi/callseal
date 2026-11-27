package dev.callseal.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.callseal.intent.CallIntent;
import dev.callseal.intent.IntentEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Append-only, hash-chained audit trail in PostgreSQL (ADR-003).
 * One chain per tenant; the chain head row is locked FOR UPDATE so appends are serialised.
 */
@Component
public class AuditLog {

    public record VerifyResult(String tenantId, long events, boolean valid, Long brokenAtSeq) {
    }

    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    // Sorted keys + no pretty printing = one canonical string per payload.
    private final ObjectMapper canonical = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public AuditLog(JdbcTemplate jdbc, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @EventListener
    @Order(1)
    @Transactional
    public void on(IntentEvent event) {
        CallIntent i = event.intent();
        // Deliberately no spoken code and no code hash in the audit payload.
        Map<String, Object> payload = new TreeMap<>();
        payload.put("customerRef", i.customerRef());
        payload.put("msisdn", i.msisdnMasked());
        payload.put("purpose", i.purpose().name());
        payload.put("agentId", i.agentId());
        payload.put("state", i.state().name());
        payload.put("expiresAt", i.expiresAt().toString());
        append(i.tenantId(), i.id(), event.type().name(), toJson(payload));
    }

    void append(String tenantId, String intentId, String eventType, String payloadJson) {
        jdbc.update("""
                INSERT INTO audit_chain_head (tenant_id, last_seq, last_hash)
                VALUES (?, 0, ?) ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, AuditHasher.GENESIS);

        Map<String, Object> head = jdbc.queryForMap(
                "SELECT last_seq, last_hash FROM audit_chain_head WHERE tenant_id = ? FOR UPDATE", tenantId);
        long seq = ((Number) head.get("last_seq")).longValue() + 1;
        String prevHash = ((String) head.get("last_hash")).trim();
        String hash = AuditHasher.next(prevHash, seq, eventType, intentId, payloadJson);

        jdbc.update("""
                INSERT INTO audit_event (tenant_id, seq, intent_id, event_type, payload_json, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, tenantId, seq, intentId, eventType, payloadJson, prevHash, hash);
        jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ? WHERE tenant_id = ?",
                seq, hash, tenantId);
    }

    @Transactional(readOnly = true)
    public VerifyResult verify(String tenantId) {
        List<AuditHasher.Row> rows = jdbc.query("""
                        SELECT seq, event_type, intent_id, payload_json, prev_hash, hash
                        FROM audit_event WHERE tenant_id = ? ORDER BY seq
                        """,
                (rs, n) -> new AuditHasher.Row(rs.getLong("seq"), rs.getString("event_type"),
                        rs.getString("intent_id"), rs.getString("payload_json"),
                        rs.getString("prev_hash").trim(), rs.getString("hash").trim()),
                tenantId);
        long broken = AuditHasher.firstBreak(rows);
        if (broken >= 0) {
            metrics.counter("callseal.audit.chain.verify.failures").increment();
        }
        return new VerifyResult(tenantId, rows.size(), broken < 0, broken < 0 ? null : broken);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return canonical.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
