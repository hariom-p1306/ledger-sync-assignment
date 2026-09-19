package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.report.ReconciliationService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReconciliationTest {
    @Test
    void doesNotHideABankBalanceGapByInventingATransaction() throws Exception {
        var discrepancies = ReconciliationService.findDiscrepancies(
                IngestService.readCorpus(Path.of("fixtures/corpus-a.jsonl")));
        assertTrue(discrepancies.stream().anyMatch(d -> "4821".equals(d.get("account_last4"))));
    }
}
