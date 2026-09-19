package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.InMemoryLedgerStore;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs the whole pipeline in memory against fixtures/corpus-a.jsonl and prints
 * what it produced next to what fixtures/corpus-a-totals.json says it should
 * have produced.
 *
 * No database, no network, no test framework. `./gradlew selfCheck`.
 */
public final class SelfCheck {

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(args.length > 0 ? args[0] : "fixtures/corpus-a.jsonl");
        Path totals = Path.of(args.length > 1 ? args[1] : "fixtures/corpus-a-totals.json");

        InMemoryLedgerStore store = new InMemoryLedgerStore();
        IngestService ingest = new IngestService(new Parsers(), store);
        IngestService.Stats stats = ingest.ingestFile(corpus);

        System.out.println("INGEST");
        System.out.printf("  messages read       %d%n", stats.messagesRead());
        System.out.printf("  transactions written %d%n", stats.transactionsWritten());
        System.out.printf("  messages skipped    %d%n", stats.messagesSkipped());

        List<NormalizedTxn> ledger = store.all();
        Map<Category, BigDecimal> cats = in.simplifymoney.ledgersync.report.Reports
                .byCategory(ledger);
        System.out.println("\nBY CATEGORY");
        cats.forEach((c, v) -> System.out.printf("  %-9s %12s%n", c, v.toPlainString()));
        System.out.println("\nSUMMARY");
        System.out.println(Json.writePretty(in.simplifymoney.ledgersync.report.Reports.summary(ledger)));

        Map<String, Object> want = Json.parseObject(Files.readString(totals));
        @SuppressWarnings("unchecked")
        Map<String, Object> accounts = (Map<String, Object>) want.get("accounts");

        System.out.println("\nAGAINST fixtures/corpus-a-totals.json");
        System.out.printf("  transactions   expected %s, produced %d%n",
                want.get("transactions_expected"), ledger.size());

        for (Map.Entry<String, Object> e : accounts.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) e.getValue();
            BigDecimal opening = new BigDecimal((String) a.get("opening_balance"));
            BigDecimal closing = new BigDecimal((String) a.get("closing_balance"));

            BigDecimal running = opening;
            long n = 0;
            for (NormalizedTxn t : ledger) {
                if (!t.accountLast4().equals(e.getKey())) continue;
                n++;
                running = switch (t.direction()) {
                    case DEBIT -> running.subtract(t.amount());
                    case CREDIT -> running.add(t.amount());
                };
            }
            System.out.printf("  **%s  txns %d (expected %s)%n",
                    e.getKey(), n, a.get("transactions_expected"));
            System.out.printf("           balance from ledger %s, bank says %s, difference %s%n",
                    running.toPlainString(), closing.toPlainString(),
                    running.subtract(closing).toPlainString());
        }
        System.out.println("\nThis is the starting point, not the finish line.");
    }
}
