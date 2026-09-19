package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic document-store test double. Its map is keyed by a canonical
 * financial event and the reverse evidence index is keyed by upload message ID.
 * Production adapters must provide the same upsert and query semantics.
 */
public final class InMemoryDocumentStore implements DocumentStore {
    private final Map<String, NormalizedTxn> byFinancialKey = new LinkedHashMap<>();
    private final Map<String, String> financialKeyByMessageId = new LinkedHashMap<>();

    @Override
    public synchronized void save(NormalizedTxn incoming) {
        String key = TransactionKeys.financial(incoming);
        NormalizedTxn existing = byFinancialKey.get(key);
        NormalizedTxn merged = existing == null ? incoming : merge(existing, incoming);
        byFinancialKey.put(key, merged);
        for (String messageId : merged.sourceMessageIds()) financialKeyByMessageId.put(messageId, key);
    }

    @Override
    public synchronized List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month) {
        return byFinancialKey.values().stream()
                .filter(t -> t.accountLast4().equals(accountLast4)
                        && YearMonth.from(t.occurredAt()).equals(month))
                .sorted(Comparator.comparing(NormalizedTxn::occurredAt).reversed())
                .toList();
    }

    @Override
    public synchronized Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Map<Category, BigDecimal> totals = new LinkedHashMap<>();
        for (Category category : Category.values()) totals.put(category, BigDecimal.ZERO.setScale(2));
        for (NormalizedTxn t : byFinancialKey.values()) {
            if (t.accountLast4().equals(accountLast4)) totals.put(t.category(), totals.get(t.category()).add(t.amount()));
        }
        return totals;
    }

    @Override
    public synchronized Optional<NormalizedTxn> byMessageId(String messageId) {
        return Optional.ofNullable(financialKeyByMessageId.get(messageId)).map(byFinancialKey::get);
    }

    @Override
    public synchronized List<NormalizedTxn> all() {
        return byFinancialKey.values().stream().sorted(Comparator.comparing(NormalizedTxn::occurredAt)).toList();
    }

    /** Test hook used to prove that ConsistencyChecker detects altered documents. */
    public synchronized void replaceForTest(NormalizedTxn original, NormalizedTxn altered) {
        byFinancialKey.put(TransactionKeys.financial(original), altered);
        for (String id : altered.sourceMessageIds()) financialKeyByMessageId.put(id, TransactionKeys.financial(original));
    }

    private static NormalizedTxn merge(NormalizedTxn left, NormalizedTxn right) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(left.sourceMessageIds());
        ids.addAll(right.sourceMessageIds());
        return new NormalizedTxn(left.accountLast4(), left.occurredAt(), left.direction(), left.amount(),
                left.category(), left.merchant(), new ArrayList<>(ids).stream().sorted().toList());
    }
}
