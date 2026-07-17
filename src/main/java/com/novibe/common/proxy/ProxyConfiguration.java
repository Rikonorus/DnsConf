package com.novibe.common.proxy;

import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.ProcessException;
import com.novibe.common.util.EnvParser;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * All proxy-mode inputs are validated before the application opens a provider
 * context or loads a source. Values in this object must never be logged.
 */
public record ProxyConfiguration(
        boolean enabled,
        String redirectTarget,
        Set<String> previousRedirectTargets,
        String rootPassword,
        String knownHosts
) {

    public static ProxyConfiguration disabled() {
        return new ProxyConfiguration(false, null, Set.of(), null, null);
    }

    public static ProxyConfiguration fromEnvironment(List<DnsProfile> profiles) {
        String target = System.getenv("REDIRECT_TARGET");
        if (target == null || target.isBlank()) {
            return disabled();
        }

        target = validatePublicIpv4(target, "REDIRECT_TARGET");
        String password = System.getenv("PROXY_VPS_ROOT_PASSWORD");
        String hosts = System.getenv("PROXY_VPS_SSH_KNOWN_HOSTS");
        if (password == null || password.isBlank()) {
            throw new ProcessException("Proxy mode requires PROXY_VPS_ROOT_PASSWORD");
        }
        if (hosts == null || hosts.isBlank() || !isTrustedKnownHostsEntry(hosts, target)) {
            throw new ProcessException("Proxy mode requires a valid PROXY_VPS_SSH_KNOWN_HOSTS entry");
        }
        if (EnvParser.parse(System.getenv("REDIRECT")).isEmpty()) {
            throw new ProcessException("Proxy mode requires at least one REDIRECT source");
        }
        if (profiles.stream().noneMatch(profile -> "NEXTDNS".equals(profile.dnsProvider()))) {
            throw new ProcessException("Proxy mode requires at least one NEXTDNS profile");
        }

        Set<String> previousTargets = new LinkedHashSet<>();
        for (String value : EnvParser.parse(System.getenv("PROXY_PREVIOUS_REDIRECT_TARGETS"))) {
            String previous = validatePublicIpv4(value, "PROXY_PREVIOUS_REDIRECT_TARGETS");
            if (previous.equals(target) || !previousTargets.add(previous)) {
                throw new ProcessException("Proxy previous redirect targets must be unique and different from the current target");
            }
        }
        return new ProxyConfiguration(true, target, Set.copyOf(previousTargets), password, hosts);
    }

    private static boolean isTrustedKnownHostsEntry(String value, String target) {
        String[] fields = value.strip().split("\\s+");
        if (fields.length != 3 || !target.equals(fields[0]) || !"ssh-ed25519".equals(fields[1])) {
            return false;
        }
        try {
            java.util.Base64.getDecoder().decode(fields[2]);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String validatePublicIpv4(String value, String variableName) {
        String candidate = value.strip();
        try {
            InetAddress parsed = InetAddress.ofLiteral(candidate);
            if (!(parsed instanceof Inet4Address ipv4) || !candidate.equals(ipv4.getHostAddress()) || !isPublicUnicast(ipv4)) {
                throw new IllegalArgumentException();
            }
            return candidate;
        } catch (Exception ignored) {
            throw new ProcessException(variableName + " must contain a public IPv4 literal");
        }
    }

    private static boolean isPublicUnicast(Inet4Address address) {
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
        if (first == 100 && second >= 64 && second <= 127) return false;
        if (first == 169 && second == 254) return false;
        if (first == 172 && second >= 16 && second <= 31) return false;
        if (first == 192 && (second == 0 || second == 168)) return false;
        if (first == 198 && (second == 18 || second == 19 || second == 51)) return false;
        return !(first == 203 && second == 0 && Byte.toUnsignedInt(bytes[2]) == 113);
    }
}
