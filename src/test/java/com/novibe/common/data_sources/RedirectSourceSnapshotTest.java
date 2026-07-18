package com.novibe.common.data_sources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedirectSourceSnapshotTest {

    @Test
    void proxyAllowlistCollapsesExplicitRegistrableRootAndKeepsLegacyRoutesAvailable() {
        RedirectSourceSnapshot snapshot = new RedirectSourceSnapshot(List.of(
                new RedirectSourceRecord("1.2.3.4", "www.example.com", "example.com"),
                new RedirectSourceRecord("5.6.7.8", "api.example.com", "api.example.com")
        ), 0);

        RedirectSourceSnapshot.ProxyAllowlist allowlist = snapshot.proxyAllowlist();

        assertEquals(List.of("example.com"), allowlist.domains());
        assertEquals("example.com\n", new String(allowlist.bytes()));
        assertEquals(List.of(
                new HostsOverrideListsLoader.BypassRoute("1.2.3.4", "example.com"),
                new HostsOverrideListsLoader.BypassRoute("5.6.7.8", "api.example.com")
        ), snapshot.bypassRoutes());
    }

    @Test
    void proxyModeCollapsesOnlyToRegistrableRootsExplicitlyPresentInSource() {
        RedirectSourceSnapshot snapshot = new RedirectSourceSnapshot(List.of(
                new RedirectSourceRecord("1.1.1.1", "jetbrains.com", "jetbrains.com"),
                new RedirectSourceRecord("1.1.1.1", "api.jetbrains.com", "api.jetbrains.com"),
                new RedirectSourceRecord("1.1.1.1", "a.b.jetbrains.com", "a.b.jetbrains.com"),
                new RedirectSourceRecord("2.2.2.2", "elevenlabs.io", "elevenlabs.io"),
                new RedirectSourceRecord("2.2.2.2", "api.us.elevenlabs.io", "api.us.elevenlabs.io"),
                new RedirectSourceRecord("3.3.3.3", "a1961.g2.akamai.net", "a1961.g2.akamai.net"),
                new RedirectSourceRecord("4.4.4.4", "vendor.co.uk", "vendor.co.uk"),
                new RedirectSourceRecord("4.4.4.4", "api.vendor.co.uk", "api.vendor.co.uk")
        ), 0);

        assertEquals(List.of(
                new HostsOverrideListsLoader.BypassRoute("1.1.1.1", "jetbrains.com"),
                new HostsOverrideListsLoader.BypassRoute("2.2.2.2", "elevenlabs.io"),
                new HostsOverrideListsLoader.BypassRoute("3.3.3.3", "a1961.g2.akamai.net"),
                new HostsOverrideListsLoader.BypassRoute("4.4.4.4", "vendor.co.uk")
        ), snapshot.proxyBypassRoutes());

        assertEquals(List.of(
                "a1961.g2.akamai.net", "elevenlabs.io", "jetbrains.com", "vendor.co.uk"
        ), snapshot.proxyAllowlist().domains());
    }

    @Test
    void publicSuffixParserKeepsPrivateTenantBoundary() {
        assertEquals("tenant.github.io", RedirectSourceSnapshot.registrableRoot("api.tenant.github.io"));
        assertEquals("tenant.blogspot.com", RedirectSourceSnapshot.registrableRoot("api.tenant.blogspot.com"));
        assertEquals("vendor.co.uk", RedirectSourceSnapshot.registrableRoot("api.vendor.co.uk"));
    }

    @Test
    void emptySnapshotCannotBecomeProxyAllowlist() {
        RedirectSourceSnapshot snapshot = new RedirectSourceSnapshot(List.of(), 0);

        assertThrows(RuntimeException.class, snapshot::proxyAllowlist);
    }
}
