package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.Backfill;
import in.simplifymoney.ledgersync.store.ConsistencyChecker;
import in.simplifymoney.ledgersync.store.InMemoryDocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackfillConsistencyTest {
    @Test
    void backfillIsRetrySafeAndCheckerFindsAlteredContent() throws Exception {
        var dir = Files.createTempDirectory("ledger-sync-backfill");
        try (SqlLedgerStore sql = new SqlLedgerStore(dir.resolve("ledger"))) {
            sql.migrate(java.nio.file.Path.of("db/migration"));
            InMemoryDocumentStore documents = new InMemoryDocumentStore();
            Backfill backfill = new Backfill(sql, documents);
            Backfill.Result first = backfill.run();
            Backfill.Result retry = backfill.run();
            assertTrue(first.written() > 0);
            assertEquals(0, retry.written());
            assertTrue(new ConsistencyChecker(sql, documents).check().isEmpty(),
                    "legacy duplicate rows are one canonical transaction after evidence merge");

            NormalizedTxn original = documents.all().getFirst();
            NormalizedTxn altered = new NormalizedTxn(original.accountLast4(), original.occurredAt(),
                    original.direction(), original.amount().add(new BigDecimal("1.00")), Category.SPEND,
                    original.merchant(), List.copyOf(original.sourceMessageIds()));
            documents.replaceForTest(original, altered);
            assertFalse(new ConsistencyChecker(sql, documents).check().isEmpty());
        }
    }
}
