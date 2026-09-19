package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Stable financial identity, deliberately independent of a phone upload ID. */
final class TransactionIdentity {
    private TransactionIdentity() {}

    static String of(ParsedTxn t) {
        return t.accountLast4() + "|" + t.occurredAt().toInstant()
                + "|" + t.direction() + "|" + t.amount().toPlainString() + "|" + merchant(t.merchant());
    }

    static String of(NormalizedTxn t) {
        return t.accountLast4() + "|" + t.occurredAt().toInstant()
                + "|" + t.direction() + "|" + t.amount().toPlainString() + "|" + merchant(t.merchant());
    }

    static String merchant(String merchant) {
        return merchant == null ? "" : merchant.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
