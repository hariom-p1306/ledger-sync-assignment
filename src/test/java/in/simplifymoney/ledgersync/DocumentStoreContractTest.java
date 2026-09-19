package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.InMemoryDocumentStore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentStoreContractTest {
    @Test
    void supportsTheThreeAccessPatternsAndMergesEvidenceIdempotently() {
        InMemoryDocumentStore store = new InMemoryDocumentStore();
        NormalizedTxn first = txn("2026-07-01T10:00:00+05:30", "m-sms");
        NormalizedTxn emailEvidence = txn("2026-07-01T10:00:00+05:30", "m-email");
        NormalizedTxn newest = txn("2026-07-02T10:00:00+05:30", "m-next");
        store.save(first); store.save(emailEvidence); store.save(newest);

        assertEquals(2, store.all().size());
        assertEquals(List.of("m-email", "m-sms"), store.byMessageId("m-sms").orElseThrow().sourceMessageIds());
        assertEquals("m-next", store.forAccountMonth("4821", YearMonth.of(2026, 7)).getFirst().sourceMessageIds().getFirst());
        assertEquals(new BigDecimal("20.00"), store.categoryTotals("4821").get(Category.SPEND));
    }

    private static NormalizedTxn txn(String when, String message) {
        return new NormalizedTxn("4821", OffsetDateTime.parse(when), Direction.DEBIT,
                new BigDecimal("10.00"), Category.SPEND, "MERCHANT", List.of(message));
    }
}
