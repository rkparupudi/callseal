package dev.callseal.audit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHasherTest {

    private static List<AuditHasher.Row> chain(int length) {
        List<AuditHasher.Row> rows = new ArrayList<>();
        String prev = AuditHasher.GENESIS;
        for (long seq = 1; seq <= length; seq++) {
            String payload = "{\"state\":\"CREATED\",\"n\":" + seq + "}";
            String hash = AuditHasher.next(prev, seq, "CREATED", "intent-" + seq, payload);
            rows.add(new AuditHasher.Row(seq, "CREATED", "intent-" + seq, payload, prev, hash));
            prev = hash;
        }
        return rows;
    }

    @Test
    void intactChainVerifies() {
        assertThat(AuditHasher.firstBreak(chain(5))).isEqualTo(-1);
        assertThat(AuditHasher.firstBreak(List.of())).isEqualTo(-1);
    }

    @Test
    void editedPayloadIsCaught() {
        List<AuditHasher.Row> rows = chain(5);
        AuditHasher.Row r = rows.get(2);
        rows.set(2, new AuditHasher.Row(r.seq(), r.eventType(), r.intentId(),
                r.payloadJson().replace("CREATED", "VERIFIED"), r.prevHash(), r.hash()));
        assertThat(AuditHasher.firstBreak(rows)).isEqualTo(3);
    }

    @Test
    void deletedRowIsCaught() {
        List<AuditHasher.Row> rows = chain(5);
        rows.remove(1);
        assertThat(AuditHasher.firstBreak(rows)).isEqualTo(3);
    }
}
