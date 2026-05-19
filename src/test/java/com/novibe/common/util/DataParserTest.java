package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataParserTest {

    @Test
    void parseHostsLineReturnsIpOnlyLineForIpOnlyInput() {
        HostsLine parsedLine = DataParser.parseHostsLine("103.27.157.38");

        assertNotNull(parsedLine);
        assertEquals("103.27.157.38", parsedLine.ip());
        assertEquals(null, parsedLine.domain());
    }

    @Test
    void parseHostsLineHandlesFlexibleWhitespaceAndInlineComments() {
        HostsLine parsedLine = DataParser.parseHostsLine("8.8.8.8\tgoogle.com   # comment");

        assertNotNull(parsedLine);
        assertEquals("8.8.8.8", parsedLine.ip());
        assertEquals("google.com", parsedLine.domain());
    }

    @Test
    void hasMeaningfulContentIgnoresCommentOnlyLines() {
        assertFalse(DataParser.hasMeaningfulContent("   # only a comment"));
    }
}
