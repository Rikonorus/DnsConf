package com.novibe.common.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataParserTest {

    @Test
    void parseHostsLineReturnsEmptyForIpOnlyLine() {
        assertTrue(DataParser.parseHostsLine("103.27.157.38").isEmpty());
    }

    @Test
    void parseHostsLineHandlesFlexibleWhitespaceAndInlineComments() {
        Optional<DataParser.HostsLine> parsedLine = DataParser.parseHostsLine("8.8.8.8\tgoogle.com   # comment");

        assertTrue(parsedLine.isPresent());
        assertEquals("8.8.8.8", parsedLine.get().ip());
        assertEquals("google.com", parsedLine.get().domain());
    }

    @Test
    void hasMeaningfulContentIgnoresCommentOnlyLines() {
        assertFalse(DataParser.hasMeaningfulContent("   # only a comment"));
    }
}
