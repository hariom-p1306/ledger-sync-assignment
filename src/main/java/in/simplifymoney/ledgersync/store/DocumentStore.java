package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The document store the ledger is moving to. NOT IMPLEMENTED - this is yours.
 *
 * These three methods are the only access patterns this service has. Design
 * your documents so the engine can serve them directly. We are not going to
 * tell you what the documents should look like; that decision is the point of
 * the exercise.
 *
 * For each method your submission must report, at 100,000 transactions, how
 * many items the engine EXAMINED versus how many it RETURNED. Both DynamoDB
 * (ScannedCount vs Count) and MongoDB (totalDocsExamined vs nReturned) give you
 * this directly. Put the numbers in your README.
 */
public interface DocumentStore {

    /** Q1: one account's transactions for one month, newest first. */
    List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month);

    /** Q2: running totals per category for an account, for its whole history. */
    Map<Category, BigDecimal> categoryTotals(String accountLast4);

    /** Q3: which transaction, if any, did this message produce? */
    Optional<NormalizedTxn> byMessageId(String messageId);

    void save(NormalizedTxn txn);

    /** Required by the backfill and exact consistency audit. */
    default List<NormalizedTxn> all() {
        throw new UnsupportedOperationException("document store does not expose an audit stream");
    }
}
