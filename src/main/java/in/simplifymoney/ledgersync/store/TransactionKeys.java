package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Canonical key for the financial event, never for an individual upload. */
final class TransactionKeys {
    private TransactionKeys() {}

    static String financial(NormalizedTxn t) {
        return t.accountLast4() + "|" + t.occurredAt().toInstant()
                + "|" + t.direction() + "|" + t.amount().toPlainString() + "|" + merchant(t.merchant());
    }

    static String merchant(String merchant) {
        return merchant == null ? "" : merchant.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
