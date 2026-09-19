package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.HdfcSmsParser;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/** Reproduces INC-2026-09-11 without special-casing its merchant. */
class IncidentRegressionTest {
    @Test
    void parsesWholeRupeeTransactionNotLaterAvailableBalance() {
        RawMessage message = new RawMessage("incident-water-can", "sms", HdfcSmsParser.SENDER,
                OffsetDateTime.parse("2026-07-04T07:19:00+05:30"), "device",
                "Rs.5 debited from a/c **4821 on 04-07-26 at 07:19 to UPI/WATER CAN. "
                        + "Avl Bal: Rs.92,213.10. Not you? Call 18002586161");

        var transaction = new HdfcSmsParser().parse(message);
        assertTrue(transaction.isPresent());
        assertEquals(new BigDecimal("5.00"), transaction.get().amount());
        assertEquals(new BigDecimal("92213.10"), transaction.get().statedBalance());
    }
}
