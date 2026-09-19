package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ICICI Bank SMS.
 *
 * TODO(ops): this only reads the "Dear Customer, Acct XX.... is debited with"
 * shape. There is at least one other ICICI format in the corpus that falls
 * straight through and is lost. Finish this.
 */
public final class IciciSmsParser implements MessageParser {

    public static final String SENDER = "VM-ICICIB-T";

    private static final Pattern V1 = Pattern.compile(
            "Acct XX(?<acct>\\d{4}) is (?<dir>debited|credited) with (?:INR|Rs\\.?)\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]{1,2})?) "
                    + "on (?<when>\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2})\\. "
                    + "Info: (?<merchant>[^.]+)\\.");

    private static final Pattern V2 = Pattern.compile(
            "ICICI Bank Acct XX(?<acct>\\d{4}) (?<dir>Dr|Cr) INR\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]{1,2})?) "
                    + "on (?<when>\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2});\\s*"
                    + "(?<merchant>.+?)\\s+ref no");

    @Override
    public boolean supports(RawMessage m) {
        return "sms".equals(m.channel()) && SENDER.equals(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        Matcher v1 = V1.matcher(m.body());
        if (v1.find()) return build(m, v1.group("acct"), v1.group("when"),
                "debited".equals(v1.group("dir")) ? Direction.DEBIT : Direction.CREDIT,
                v1.group("amount"), v1.group("merchant"));
        Matcher v2 = V2.matcher(m.body());
        if (v2.find()) return build(m, v2.group("acct"), v2.group("when"),
                "Dr".equals(v2.group("dir")) ? Direction.DEBIT : Direction.CREDIT,
                v2.group("amount"), v2.group("merchant"));
        return Optional.empty();
    }

    private Optional<ParsedTxn> build(RawMessage m, String account, String when,
                                      Direction direction, String rawAmount, String merchant) {
        BigDecimal amount = Amounts.parse(rawAmount);
        OffsetDateTime at = Dates.ist(when);
        if (amount == null || at == null) return Optional.empty();
        return Optional.of(new ParsedTxn(account, at, direction, amount, merchant.trim(),
                Amounts.statedBalance(m.body()), m.messageId()));
    }
}
