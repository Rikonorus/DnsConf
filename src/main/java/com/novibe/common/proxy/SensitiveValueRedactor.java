package com.novibe.common.proxy;

import java.util.ArrayList;
import java.util.List;

/** Minimal last-resort output protection for errors from third-party clients. */
public final class SensitiveValueRedactor {

    private static volatile List<String> values = List.of();

    private SensitiveValueRedactor() {
    }

    public static void configure(ProxyConfiguration configuration) {
        if (!configuration.enabled()) {
            values = List.of();
            return;
        }
        List<String> protectedValues = new ArrayList<>();
        protectedValues.add(configuration.redirectTarget());
        protectedValues.addAll(configuration.previousRedirectTargets());
        protectedValues.add(configuration.rootPassword());
        protectedValues.add(configuration.knownHosts());
        values = protectedValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
    }

    public static String redact(String message) {
        if (message == null) return "Unexpected failure";
        String redacted = message;
        for (String value : values) {
            redacted = redacted.replace(value, "[redacted]");
        }
        return redacted;
    }
}
