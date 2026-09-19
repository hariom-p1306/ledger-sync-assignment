package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bank transaction alert emails.
 *
 * Not written yet. The corpus contains them and they are currently all dropped.
 */
public final class EmailParser implements MessageParser {

    private static final Pattern DATE = Pattern.compile("(?m)^Date:\\s*(?<date>.+)$");
    private static final Pattern TRANSACTION = Pattern.compile(
            "account ending\\s+(?<acct>\\d{4})\\s+has been\\s+(?<dir>debited|credited)\\s+with\\s+"
                    + "(?:INR|Rs\\.?)\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\.\\s*"
                    + "Merchant / Remarks:\\s*(?<merchant>.+?)(?:\\r?\\n|Transaction reference:)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        Matcher date = DATE.matcher(m.body());
        Matcher tx = TRANSACTION.matcher(m.body());
        if (!date.find() || !tx.find()) return Optional.empty();
        OffsetDateTime occurred = Dates.email(date.group("date"));
        BigDecimal amount = Amounts.parse(tx.group("amount"));
        if (occurred == null || amount == null) return Optional.empty();
        Direction direction = "debited".equalsIgnoreCase(tx.group("dir"))
                ? Direction.DEBIT : Direction.CREDIT;
        return Optional.of(new ParsedTxn(tx.group("acct"), occurred, direction, amount,
                tx.group("merchant").trim(), Amounts.statedBalance(m.body()), m.messageId()));
    }
}
