package org.opcoach.mailmcp.mail;

import org.junit.jupiter.api.Test;
import org.opcoach.mailmcp.config.MailLimits;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MailQueryParserTest {

    @Test
    void parsesDateRangeAndUidCursor() {
        MailQueryParser parser = new MailQueryParser(MailLimits.DEFAULTS);

        SearchMessagesQuery query = parser.search(Map.of(
                "since", "2024-02-01",
                "until", "2024-02-29",
                "limit", 5,
                "beforeUid", "42"
        ));

        assertEquals("2024-02-01", query.since().toString());
        assertEquals("2024-02-29", query.until().toString());
        assertEquals(5, query.limit());
        assertEquals(42L, query.beforeUid());
    }

    @Test
    void rejectsInvertedDateRange() {
        MailQueryParser parser = new MailQueryParser(MailLimits.DEFAULTS);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.search(Map.of(
                "since", "2024-03-01",
                "until", "2024-02-29"
        )));

        assertEquals("since must be on or before until.", exception.getMessage());
    }

    @Test
    void rejectsInvalidUidCursor() {
        MailQueryParser parser = new MailQueryParser(MailLimits.DEFAULTS);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.search(Map.of(
                "beforeUid", "0"
        )));

        assertEquals("beforeUid must be a positive IMAP integer.", exception.getMessage());
    }
}
