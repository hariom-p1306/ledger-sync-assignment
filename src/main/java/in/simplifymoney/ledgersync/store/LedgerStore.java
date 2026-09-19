package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.List;

/**
 * Where transactions live.
 *
 * Note what this interface does NOT promise: that saving the same transaction
 * twice results in one row.
 */
public interface LedgerStore {

    void save(NormalizedTxn txn);

    List<NormalizedTxn> all();

    /** Replaces the physical representation with a canonical ledger snapshot. */
    default void replaceAll(List<NormalizedTxn> transactions) {
        throw new UnsupportedOperationException("store does not support replacement");
    }

    long count();
}
