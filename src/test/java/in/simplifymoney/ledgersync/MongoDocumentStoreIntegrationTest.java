package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClients;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.MongoDocumentStore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Requires docker compose up -d mongo. */
class MongoDocumentStoreIntegrationTest {
    @Test
    void mongoAdapterSupportsUpsertEvidenceAndAllThreeQueries() {
        String database = "ledger_sync_integration_test";
        try (var cleanup = MongoClients.create("mongodb://localhost:27017")) {
            cleanup.getDatabase(database).drop();
            try (MongoDocumentStore store = new MongoDocumentStore("mongodb://localhost:27017", database)) {
                NormalizedTxn sms = txn("2026-07-01T10:00:00+05:30", "sms-evidence");
                NormalizedTxn email = txn("2026-07-01T10:00:00+05:30", "email-evidence");
                NormalizedTxn later = txn("2026-07-02T10:00:00+05:30", "later-evidence");
                store.save(sms); store.save(email); store.save(later);
                assertEquals(2, store.all().size());
                assertEquals(List.of("email-evidence", "sms-evidence"),
                        store.byMessageId("sms-evidence").orElseThrow().sourceMessageIds());
                assertEquals("later-evidence", store.forAccountMonth("4821", YearMonth.of(2026, 7))
                        .getFirst().sourceMessageIds().getFirst());
                assertEquals(new BigDecimal("20.00"), store.categoryTotals("4821").get(Category.SPEND));
            } finally {
                cleanup.getDatabase(database).drop();
            }
        }
    }

    private static NormalizedTxn txn(String timestamp, String evidence) {
        return new NormalizedTxn("4821", OffsetDateTime.parse(timestamp), Direction.DEBIT,
                new BigDecimal("10.00"), Category.SPEND, "MERCHANT", List.of(evidence));
    }
}
