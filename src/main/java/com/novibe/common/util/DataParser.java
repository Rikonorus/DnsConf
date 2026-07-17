package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.IDN;
import java.util.Locale;
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
            if (isValidIP(ip)) {
                return new HostsLine(ip, domain);
            }
        }
        return null;
    }

    /**
     * Parses only a complete hosts IP/hostname pair while preserving the
     * hostname before the legacy www-removal rule is applied.
     */
    public static @Nullable HostsPair parseHostsPair(String line) {
        String sanitizedLine = stripInlineComment(line).strip();
        if (sanitizedLine.isBlank()) return null;
        String[] columns = WHITESPACE.split(sanitizedLine);
        if (columns.length != 2 || !isValidIP(columns[0])) return null;
        return new HostsPair(columns[0], columns[1]);
    }

    /** Returns lower-case ASCII IDN hostname or null for non-hostname input. */
    public static @Nullable String normalizeHostname(String hostname) {
        if (hostname == null) return null;
        String candidate = hostname.strip();
        while (candidate.endsWith(".")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isBlank() || candidate.contains(":")) return null;
        try {
            String ascii = IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (ascii.length() > 253 || isValidIP(ascii)) return null;
            String[] labels = ascii.split("\\.", -1);
            if (labels.length < 2) return null;
            for (String label : labels) {
                if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")
                        || !label.matches("[a-z0-9-]+")) {
                    return null;
                }
            }
            return ascii;
        } catch (Exception ignored) {
            return null;
        }
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

    public record HostsPair(String ip, String hostname) {
    }
}
