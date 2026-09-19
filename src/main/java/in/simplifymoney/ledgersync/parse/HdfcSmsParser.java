package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HDFC Bank SMS.
 *
 * Two shapes are in production. The older one is a single sentence; the newer
 * one is multi-line and was rolled out partway through the window we have data
 * for. Both are handled here.
 *
 * Card messages ("spent on HDFC Bank Card x3310") are handled too - they quote
 * an available limit rather than an available balance.
 */
public final class HdfcSmsParser implements MessageParser {

    public static final String SENDER = "AD-HDFCBK-S";

    private static final Pattern V1 = Pattern.compile(
            "(?:Rs\\.?|INR)\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]{1,2})?) "
                    + "(?<dir>debited from|credited to) a/c \\*\\*(?<acct>\\d{4}) "
                    + "on (?<when>\\d{2}-\\d{2}-\\d{2} at \\d{2}:\\d{2}) "
                    + "(?:to|by) (?<merchant>[^.]+)\\.");

    private static final Pattern V2 = Pattern.compile(
            "^(?<dir>Sent|Received)\\s+INR\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]{1,2})?).*?\\n(?:To|From): (?<merchant>.+?)\\n"
                    + "On: (?<when>\\d{2} \\w{3} \\d{2} \\d{2}:\\d{2})\\n"
                    + "A/c: XX(?<acct>\\d{4})",
            Pattern.DOTALL);

    private static final Pattern CARD = Pattern.compile(
            "(?:Rs\\.?|INR)\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]{1,2})?) spent on HDFC Bank Card x(?<acct>\\d{4}) at (?<merchant>.+?) "
                    + "on (?<when>\\d{2}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\.");

    @Override
    public boolean supports(RawMessage m) {
        return "sms".equals(m.channel()) && SENDER.equals(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        String body = m.body();

        Matcher v1 = V1.matcher(body);
        if (v1.find()) {
            Direction d = v1.group("dir").startsWith("debited")
                    ? Direction.DEBIT : Direction.CREDIT;
            return build(m, v1.group("acct"), v1.group("when").replace(" at ", " "),
                    d, v1.group("merchant"), v1.group("amount"));
        }

        Matcher v2 = V2.matcher(body);
        if (v2.find()) {
            Direction d = "Sent".equals(v2.group("dir"))
                    ? Direction.DEBIT : Direction.CREDIT;
            return build(m, v2.group("acct"), v2.group("when"), d, v2.group("merchant"), v2.group("amount"));
        }

        Matcher card = CARD.matcher(body);
        if (card.find()) {
            return build(m, card.group("acct"), card.group("when"),
                    Direction.DEBIT, card.group("merchant"), card.group("amount"));
        }

        return Optional.empty();
    }

    private Optional<ParsedTxn> build(RawMessage m, String acct, String when,
                                      Direction dir, String merchant, String rawAmount) {
        BigDecimal amount = Amounts.parse(rawAmount);
        OffsetDateTime at = Dates.ist(when);
        if (amount == null || at == null) return Optional.empty();
        return Optional.of(new ParsedTxn(acct, at, dir, amount, merchant.trim(),
                Amounts.statedBalance(m.body()), m.messageId()));
    }
}
