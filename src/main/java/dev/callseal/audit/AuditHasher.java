package dev.callseal.audit;

import dev.callseal.common.Hashing;

import java.util.List;

/** Pure functions for the hash chain (ADR-003), kept separate so they're easy to unit-test. */
public final class AuditHasher {

    /** prev_hash of the first event in every tenant's chain. */
    public static final String GENESIS = "0".repeat(64);

    private AuditHasher() {
    }

    public static String next(String prevHash, long seq, String eventType, String intentId, String payloadJson) {
        return Hashing.sha256Hex(prevHash + "|" + seq + "|" + eventType + "|" + intentId + "|" + payloadJson);
    }

    public record Row(long seq, String eventType, String intentId, String payloadJson, String prevHash, String hash) {
    }

    /** Returns the seq of the first broken row, or -1 if the whole chain checks out. */
    public static long firstBreak(List<Row> rowsInSeqOrder) {
        String expectedPrev = GENESIS;
        long expectedSeq = 1;
        for (Row r : rowsInSeqOrder) {
            if (r.seq() != expectedSeq || !r.prevHash().equals(expectedPrev)) {
                return r.seq();
            }
            String recomputed = next(r.prevHash(), r.seq(), r.eventType(), r.intentId(), r.payloadJson());
            if (!recomputed.equals(r.hash())) {
                return r.seq();
            }
            expectedPrev = r.hash();
            expectedSeq++;
        }
        return -1;
    }
}
