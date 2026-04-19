package com.novibe.common.util;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DataParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EOL = Pattern.compile("\\r?\\n");
    private static final int LOG_PREVIEW_LIMIT = 80;

    public record HostsLine(String ip, String domain) {
    }

    public static Optional<HostsLine> parseHostsLine(String line) {
        String sanitizedLine = stripInlineComment(line).strip();
        if (sanitizedLine.isBlank()) {
            return Optional.empty();
        }

        String[] columns = WHITESPACE.split(sanitizedLine, 3);
        if (columns.length < 2 || columns[0].isBlank() || columns[1].isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new HostsLine(columns[0], columns[1]));
    }

    public static boolean hasMeaningfulContent(String line) {
        return !stripInlineComment(line).strip().isBlank();
    }

    public static Stream<String> splitByEol(String data) {
        return EOL.splitAsStream(data);
    }

    public static String removeWWW(String domain) {
        if (domain.startsWith("www.")) {
            return domain.substring("www.".length());
        }
        return domain;
    }

    public static String summarizeForLog(String line) {
        String sanitized = stripInlineComment(line).strip();
        if (sanitized.length() <= LOG_PREVIEW_LIMIT) {
            return sanitized;
        }
        return sanitized.substring(0, LOG_PREVIEW_LIMIT - 3) + "...";
    }

    private static String stripInlineComment(String line) {
        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) {
            return line.substring(0, commentIndex).strip();
        }
        return line;
    }
}
