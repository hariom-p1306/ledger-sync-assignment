package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.InMemoryLedgerStore;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PipelineContractTest {
    @Test
    void corpusProducesCanonicalTraceableLedgerAndIsIdempotent() throws Exception {
        InMemoryLedgerStore store = new InMemoryLedgerStore();
        IngestService service = new IngestService(new Parsers(), store);
        service.ingestFile(Path.of("fixtures/corpus-a.jsonl"));
        long firstRunCount = store.count();
        assertTrue(firstRunCount > 0);
        assertTrue(store.all().stream().allMatch(t -> t.amount().scale() == 2 && !t.sourceMessageIds().isEmpty()));
        assertTrue(store.all().stream().anyMatch(t -> t.category() == Category.MICRO));
        assertTrue(store.all().stream().anyMatch(t -> t.category() == Category.TRANSFER));
        assertEquals(store.count(), new HashSet<>(store.all().stream()
                .map(t -> t.accountLast4()+"|"+t.occurredAt()+"|"+t.direction()+"|"+t.amount()+"|"+t.merchant()).toList()).size());
        service.ingestFile(Path.of("fixtures/corpus-a.jsonl"));
        assertEquals(firstRunCount, store.count());
    }
}
