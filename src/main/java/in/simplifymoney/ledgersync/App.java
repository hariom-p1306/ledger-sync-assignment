package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.report.Reports;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import in.simplifymoney.ledgersync.store.MongoDocumentStore;
import in.simplifymoney.ledgersync.store.Backfill;
import in.simplifymoney.ledgersync.store.ConsistencyChecker;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command line entry point.
 *
 *   migrate                  apply db/migration/*.sql
 *   ingest  <corpus.jsonl>   read a corpus into the ledger
 *   report  <out-dir>        write ledger.json, summary.json, reconciliation.json
 */
public final class App {

    private static final Path DB = Path.of("data", "ledger");
    private static final Path MIGRATIONS = Path.of("db", "migration");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: migrate | ingest <corpus.jsonl> | report <out-dir> [corpus] | backfill | check");
            System.exit(2);
        }
        Files.createDirectories(DB.getParent());

        switch (args[0]) {
            case "migrate" -> {
                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {
                    store.migrate(MIGRATIONS);
                    System.out.println("ledger rows: " + store.count());
                }
            }
            case "ingest" -> {
                if (args.length < 2) throw new IllegalArgumentException("ingest needs a corpus");
                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {
                    store.migrate(MIGRATIONS);
                    var stats = new IngestService(new Parsers(), store)
                            .ingestFile(Path.of(args[1]));
                    System.out.println(stats);
                    System.out.println("ledger rows: " + store.count());
                }
            }
            case "report" -> {
                if (args.length < 2) throw new IllegalArgumentException("report needs a directory");
                Path out = Path.of(args[1]);
                Files.createDirectories(out);
                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {
                    var ledger = store.all();
                    Files.writeString(out.resolve("ledger.json"),
                            Json.writePretty(Reports.ledgerDocument(ledger)));
                    Files.writeString(out.resolve("summary.json"),
                            Json.writePretty(Reports.summary(ledger)));
                    Path corpus = args.length >= 3 ? Path.of(args[2]) : Path.of("fixtures", "corpus-a.jsonl");
                    var reconciliation = Files.exists(corpus)
                            ? Reports.reconciliation(ledger, IngestService.readCorpus(corpus))
                            : Reports.reconciliation(ledger);
                    Files.writeString(out.resolve("reconciliation.json"), Json.writePretty(reconciliation));
                    System.out.println("wrote 3 files to " + out);
                }
            }
            case "backfill" -> {
                try (SqlLedgerStore sql = new SqlLedgerStore(DB);
                     MongoDocumentStore mongo = mongo()) {
                    sql.migrate(MIGRATIONS);
                    System.out.println(new Backfill(sql, mongo).run());
                }
            }
            case "check" -> {
                try (SqlLedgerStore sql = new SqlLedgerStore(DB);
                     MongoDocumentStore mongo = mongo()) {
                    sql.migrate(MIGRATIONS);
                    var differences = new ConsistencyChecker(sql, mongo).check();
                    if (differences.isEmpty()) System.out.println("stores agree");
                    else differences.forEach(System.out::println);
                }
            }
            default -> {
                System.err.println("unknown command: " + args[0]);
                System.exit(2);
            }
        }
    }

    private static MongoDocumentStore mongo() {
        String uri = System.getenv().getOrDefault("MONGO_URI", "mongodb://localhost:27017");
        return new MongoDocumentStore(uri, "ledger_sync");
    }
}
