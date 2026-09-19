package in.simplifymoney.ledgersync.store;

import java.util.List;

/**
 * Proves the two stores agree, and says precisely where they do not.
 *
 * NOT IMPLEMENTED - this is yours.
 *
 * We will run your checker against a document store we have deliberately
 * altered. It has to find what we changed and name it. A checker that only
 * compares row counts will not.
 */
public final class ConsistencyChecker {

    private final SqlLedgerStore sql;
    private final DocumentStore documents;

    public ConsistencyChecker(SqlLedgerStore sql, DocumentStore documents) {
        this.sql = sql;
        this.documents = documents;
    }

    public List<Divergence> check() {
        java.util.Map<String, in.simplifymoney.ledgersync.model.NormalizedTxn> sqlRows = canonical(sql.all());
        java.util.Map<String, in.simplifymoney.ledgersync.model.NormalizedTxn> documentRows = canonical(documents.all());
        java.util.List<Divergence> result = new java.util.ArrayList<>();
        java.util.Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(sqlRows.keySet());
        keys.addAll(documentRows.keySet());
        for (String key : keys) {
            var left = sqlRows.get(key);
            var right = documentRows.get(key);
            if (left == null) result.add(new Divergence("unexpected transaction", "<missing>", describe(right)));
            else if (right == null) result.add(new Divergence("missing transaction", describe(left), "<missing>"));
            else if (!same(left, right)) result.add(new Divergence("transaction content differs", describe(left), describe(right)));
        }
        return result;
    }

    private static java.util.Map<String, in.simplifymoney.ledgersync.model.NormalizedTxn> canonical(
            java.util.List<in.simplifymoney.ledgersync.model.NormalizedTxn> rows) {
        java.util.Map<String, in.simplifymoney.ledgersync.model.NormalizedTxn> out = new java.util.LinkedHashMap<>();
        for (var row : rows) {
            String key = TransactionKeys.financial(row);
            var existing = out.get(key);
            if (existing == null) out.put(key, row);
            else {
                java.util.Set<String> evidence = new java.util.TreeSet<>(existing.sourceMessageIds());
                evidence.addAll(row.sourceMessageIds());
                out.put(key, new in.simplifymoney.ledgersync.model.NormalizedTxn(
                        existing.accountLast4(), existing.occurredAt(), existing.direction(), existing.amount(),
                        existing.category(), existing.merchant(), java.util.List.copyOf(evidence)));
            }
        }
        return out;
    }

    private static boolean same(in.simplifymoney.ledgersync.model.NormalizedTxn a,
                                in.simplifymoney.ledgersync.model.NormalizedTxn b) {
        return a.accountLast4().equals(b.accountLast4()) && a.occurredAt().equals(b.occurredAt())
                && a.direction() == b.direction() && a.amount().compareTo(b.amount()) == 0
                && a.category() == b.category() && a.merchant().equals(b.merchant())
                && new java.util.TreeSet<>(a.sourceMessageIds()).equals(new java.util.TreeSet<>(b.sourceMessageIds()));
    }

    private static String describe(in.simplifymoney.ledgersync.model.NormalizedTxn t) {
        return t.accountLast4() + " " + t.occurredAt() + " " + t.direction() + " " + t.amount()
                + " " + t.category() + " merchant=" + t.merchant() + " evidence=" + t.sourceMessageIds();
    }

    /** One place the two stores disagree. */
    public record Divergence(String what, String inSql, String inDocuments) {}
}
