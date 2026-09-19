package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses bank-stated balances as evidence and reports, rather than invents, gaps. */
public final class ReconciliationService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private ReconciliationService() {}

    public static List<Map<String, Object>> findDiscrepancies(List<RawMessage> messages) {
        Map<String, ParsedTxn> events = new LinkedHashMap<>();
        Parsers parsers = new Parsers();
        for (RawMessage message : messages) {
            parsers.parse(message).ifPresent(event -> events.putIfAbsent(key(event), event));
        }
        Map<String, List<ParsedTxn>> byAccount = new LinkedHashMap<>();
        for (ParsedTxn event : events.values()) byAccount.computeIfAbsent(event.accountLast4(), ignored -> new ArrayList<>()).add(event);

        List<Map<String, Object>> out = new ArrayList<>();
        for (var entry : byAccount.entrySet()) {
            List<ParsedTxn> transactions = entry.getValue();
            transactions.sort(Comparator.comparing(ParsedTxn::occurredAt));
            ParsedTxn priorBalance = null;
            for (ParsedTxn current : transactions) {
                if (current.statedBalance() == null) continue;
                if (priorBalance != null) {
                    BigDecimal knownChange = ZERO;
                    for (ParsedTxn between : transactions) {
                        if (between.occurredAt().isAfter(priorBalance.occurredAt())
                                && !between.occurredAt().isAfter(current.occurredAt())) {
                            knownChange = knownChange.add(between.direction() == Direction.DEBIT
                                    ? between.amount().negate() : between.amount());
                        }
                    }
                    BigDecimal statedChange = current.statedBalance().subtract(priorBalance.statedBalance());
                    BigDecimal unexplained = statedChange.subtract(knownChange).setScale(2);
                    if (unexplained.signum() != 0) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("account_last4", current.accountLast4());
                        row.put("occurred_at", current.occurredAt().toString());
                        row.put("amount", unexplained.abs().toPlainString());
                        row.put("note", "bank balance changed " + unexplained.toPlainString()
                                + " beyond parsed transactions between stated balance alerts");
                        out.add(row);
                    }
                }
                priorBalance = current;
            }
        }
        return out;
    }

    private static String key(ParsedTxn t) {
        return t.accountLast4() + "|" + t.occurredAt().toInstant() + "|" + t.direction() + "|"
                + t.amount().toPlainString() + "|" + t.merchant().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim();
    }
}
