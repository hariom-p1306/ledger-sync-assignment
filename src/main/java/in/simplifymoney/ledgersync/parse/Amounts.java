package in.simplifymoney.ledgersync.parse;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rupee amounts as banks write them.
 *
 * Handles the prefixes we see in practice - "Rs.", "Rs ", "INR " - and strips
 * the thousands separators before handing back a BigDecimal.
 */
public final class Amounts {

    private Amounts() {}

    private static final Pattern AMOUNT =
            Pattern.compile("(?:Rs\\.?|INR)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern BALANCE = Pattern.compile(
            "(?:Avl\\s*Bal|Available\\s*Balance|BalAvl)\\s*:?\\s*"
                    + "(?:Rs\\.?|INR)\\s*([0-9,]+\\.[0-9]{2})",
            Pattern.CASE_INSENSITIVE);

    /**
     * Legacy helper retained for compatibility. Parsers must prefer an amount
     * captured beside their transaction verb; a balance is also a rupee figure.
     */
    public static BigDecimal first(String body) {
        Matcher m = AMOUNT.matcher(body);
        if (!m.find()) return null;
        return toDecimal(m.group(1));
    }

    /** The balance the bank quoted, if it quoted one. */
    public static BigDecimal statedBalance(String body) {
        Matcher m = BALANCE.matcher(body);
        if (!m.find()) return null;
        return toDecimal(m.group(1));
    }

    /** Converts a parser's context-specific numeric capture to paisa precision. */
    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return toDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal toDecimal(String raw) {
        return new BigDecimal(raw.replace(",", "")).setScale(2);
    }
}
