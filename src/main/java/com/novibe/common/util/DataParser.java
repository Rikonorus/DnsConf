package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DataParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EOL = Pattern.compile("\\r?\\n");
    private static final int LOG_PREVIEW_LIMIT = 80;

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

    public static @Nullable HostsLine parseHostsLine(String line) {
        String sanitizedLine = stripInlineComment(line).strip();
        if (sanitizedLine.isBlank()) {
            return null;
        }
        String[] columns = WHITESPACE.split(sanitizedLine, 3);
        if (columns.length == 1) {
            String value = columns[0];
            return isValidIP(value) ? HostsLine.ipOnly(value) : HostsLine.domainOnly(removeWWW(value));
        } else if (columns.length == 2) {
            String ip = columns[0];
            String domain = removeWWW(columns[1]);
            return isValidIP(ip) ? new HostsLine(ip, domain) : null;
        }
        return null;
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

    private static boolean isValidIP(String ip) {
        try {
            return !InetAddress.ofLiteral(ip).getHostAddress().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
