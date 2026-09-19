package in.simplifymoney.ledgersync.store;

/**
 * Moves everything already in the SQL store into the document store.
 *
 * NOT IMPLEMENTED - this is yours.
 *
 * Two things to know before you start:
 *  - the SQL store is not clean. It has been running without a uniqueness
 *    guarantee for a long time
 *  - this will be run more than once, including after a partial failure
 */
public final class Backfill {

    private final SqlLedgerStore source;
    private final DocumentStore target;

    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }

    public Result run() {
        long read = 0;
        long written = 0;
        long skipped = 0;
        java.util.Set<String> known = new java.util.HashSet<>();
        for (var transaction : target.all()) known.add(TransactionKeys.financial(transaction));
        for (var transaction : source.all()) {
            read++;
            String key = TransactionKeys.financial(transaction);
            if (!known.add(key)) {
                // save still merges new source evidence, but it does not create a new document.
                target.save(transaction);
                skipped++;
            } else {
                target.save(transaction);
                written++;
            }
        }
        return new Result(read, written, skipped);
    }

    public record Result(long read, long written, long skipped) {}
}
