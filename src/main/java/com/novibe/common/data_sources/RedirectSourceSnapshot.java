package com.novibe.common.data_sources;

import com.novibe.common.exception.ProcessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record RedirectSourceSnapshot(List<RedirectSourceRecord> records, int malformedRecords) {

    public List<HostsOverrideListsLoader.BypassRoute> bypassRoutes() {
        List<HostsOverrideListsLoader.BypassRoute> routes = new ArrayList<>();
        Set<String> seenEffectiveHostnames = new LinkedHashSet<>();
        for (RedirectSourceRecord record : records) {
            if (seenEffectiveHostnames.add(record.effectiveHostname())) {
                routes.add(new HostsOverrideListsLoader.BypassRoute(record.ip(), record.effectiveHostname()));
            }
        }
        return routes;
    }

    public ProxyAllowlist allowlist() {
        Set<String> domains = new LinkedHashSet<>();
        for (RedirectSourceRecord record : records) {
            domains.add(record.sourceHostname());
            domains.add(record.effectiveHostname());
        }
        List<String> ordered = domains.stream().sorted(Comparator.naturalOrder()).toList();
        if (ordered.isEmpty()) {
            throw new ProcessException("Proxy allowlist cannot be empty");
        }
        String content = String.join("\n", ordered) + "\n";
        return new ProxyAllowlist(ordered, content.getBytes(StandardCharsets.UTF_8), sha256(content));
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ProxyAllowlist(List<String> domains, byte[] bytes, String sha256) {
        public ProxyAllowlist {
            domains = List.copyOf(domains);
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
