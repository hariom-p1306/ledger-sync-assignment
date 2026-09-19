package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.RawMessage;
import java.util.List;
import java.util.Optional;

/** Picks the parser for a message. Add yours here. */
public final class Parsers {

    private final List<MessageParser> parsers;

    public Parsers() {
        this(List.of(new HdfcSmsParser(), new IciciSmsParser(), new EmailParser()));
    }

    public Parsers(List<MessageParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public Optional<ParsedTxn> parse(RawMessage m) {
        for (MessageParser p : parsers) {
            if (p.supports(m)) return p.parse(m);
        }
        return Optional.empty();
    }
}
