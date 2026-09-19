package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Used by SelfCheck and by tests. Keeps everything it is given. */
public final class InMemoryLedgerStore implements LedgerStore {

    private final List<NormalizedTxn> rows = new ArrayList<>();

    @Override public void save(NormalizedTxn txn) { rows.add(txn); }

    @Override public List<NormalizedTxn> all() { return Collections.unmodifiableList(rows); }

    @Override public void replaceAll(List<NormalizedTxn> transactions) {
        rows.clear();
        rows.addAll(transactions);
    }

    @Override public long count() { return rows.size(); }
}
