package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads a corpus of raw messages and puts transactions in the ledger.
 *
 * This is the naive version. It parses each message on its own and saves
 * whatever comes back. It does not ask whether two messages describe the same
 * transaction, and it decides the category from the direction alone.
 */
public final class IngestService {

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {
        List<RawMessage> messages = readCorpus(corpus);
        int parsed = 0;
        int skipped = 0;
        Map<String, List<ParsedTxn>> evidence = new LinkedHashMap<>();
        for (RawMessage m : messages) {
            Optional<ParsedTxn> p = parsers.parse(m);
            if (p.isEmpty()) {
                skipped++;
                continue;
            }
            evidence.computeIfAbsent(TransactionIdentity.of(p.get()), ignored -> new ArrayList<>()).add(p.get());
            parsed++;
        }
        List<NormalizedTxn> canonical = new ArrayList<>();
        for (List<ParsedTxn> group : evidence.values()) canonical.add(toTransaction(group));
        canonical = categorizeTransfers(canonical);

        // Merge an overlapping upload with existing canonical evidence, then persist one snapshot.
        Map<String, NormalizedTxn> merged = new LinkedHashMap<>();
        for (NormalizedTxn transaction : store.all()) merged.put(TransactionIdentity.of(transaction), transaction);
        for (NormalizedTxn transaction : canonical) {
            String key = TransactionIdentity.of(transaction);
            NormalizedTxn existing = merged.get(key);
            merged.put(key, existing == null ? transaction : merge(existing, transaction));
        }
        List<NormalizedTxn> result = categorizeTransfers(new ArrayList<>(merged.values()));
        result.sort(Comparator.comparing(NormalizedTxn::occurredAt).thenComparing(TransactionIdentity::of));
        store.replaceAll(result);
        return new Stats(messages.size(), result.size(), skipped);
    }

    public static List<RawMessage> readCorpus(Path corpus) throws IOException {
        List<RawMessage> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(corpus)) {
            for (String line : (Iterable<String>) lines.filter(s -> !s.isBlank())::iterator) {
                Map<String, Object> o = Json.parseObject(line);
                out.add(new RawMessage(
                        (String) o.get("message_id"),
                        (String) o.get("channel"),
                        (String) o.get("sender"),
                        OffsetDateTime.parse((String) o.get("received_at")),
                        (String) o.get("device_id"),
                        (String) o.get("body")));
            }
        }
        return out;
    }

    private NormalizedTxn toTransaction(List<ParsedTxn> evidence) {
        ParsedTxn p = evidence.getFirst();
        List<String> sources = evidence.stream().map(ParsedTxn::sourceMessageId).distinct().sorted().toList();
        Category c = p.direction() == Direction.DEBIT ? Category.SPEND : Category.INCOME;
        return new NormalizedTxn(p.accountLast4(), p.occurredAt(), p.direction(), p.amount(), c,
                p.merchant(), sources);
    }

    private static NormalizedTxn merge(NormalizedTxn left, NormalizedTxn right) {
        LinkedHashSet<String> sources = new LinkedHashSet<>(left.sourceMessageIds());
        sources.addAll(right.sourceMessageIds());
        return new NormalizedTxn(left.accountLast4(), left.occurredAt(), left.direction(), left.amount(),
                left.category(), left.merchant(), sources.stream().sorted().toList());
    }

    private static List<NormalizedTxn> categorizeTransfers(List<NormalizedTxn> input) {
        List<NormalizedTxn> output = new ArrayList<>();
        for (NormalizedTxn current : input) {
            Category category = classify(current, input);
            output.add(new NormalizedTxn(current.accountLast4(), current.occurredAt(), current.direction(),
                    current.amount(), category, current.merchant(), current.sourceMessageIds()));
        }
        return output;
    }

    private static Category classify(NormalizedTxn t, List<NormalizedTxn> all) {
        boolean ownTransfer = all.stream().anyMatch(other -> !other.accountLast4().equals(t.accountLast4())
                && other.direction() != t.direction() && other.amount().compareTo(t.amount()) == 0
                && TransactionIdentity.merchant(other.merchant()).equals(TransactionIdentity.merchant(t.merchant()))
                && Math.abs(other.occurredAt().toEpochSecond() - t.occurredAt().toEpochSecond()) <= 300);
        if (ownTransfer) return Category.TRANSFER;
        if (t.direction() == Direction.DEBIT && t.amount().compareTo(new java.math.BigDecimal("100.00")) <= 0
                && TransactionIdentity.merchant(t.merchant()).contains("UPI")) return Category.MICRO;
        return t.direction() == Direction.DEBIT ? Category.SPEND : Category.INCOME;
    }

    public record Stats(int messagesRead, int transactionsWritten, int messagesSkipped) {}
}
