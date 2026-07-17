package com.novibe.common.data_sources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedirectSourceSnapshotTest {

    @Test
    void allowlistKeepsSourceWwwAndEffectiveRewriteHostname() {
        RedirectSourceSnapshot snapshot = new RedirectSourceSnapshot(List.of(
                new RedirectSourceRecord("1.2.3.4", "www.example.com", "example.com"),
                new RedirectSourceRecord("5.6.7.8", "api.example.com", "api.example.com")
        ), 0);

        RedirectSourceSnapshot.ProxyAllowlist allowlist = snapshot.allowlist();

        assertEquals(List.of("api.example.com", "example.com", "www.example.com"), allowlist.domains());
        assertEquals("api.example.com\nexample.com\nwww.example.com\n", new String(allowlist.bytes()));
        assertEquals(List.of(
                new HostsOverrideListsLoader.BypassRoute("1.2.3.4", "example.com"),
                new HostsOverrideListsLoader.BypassRoute("5.6.7.8", "api.example.com")
        ), snapshot.bypassRoutes());
    }

    @Test
    void emptySnapshotCannotBecomeProxyAllowlist() {
        RedirectSourceSnapshot snapshot = new RedirectSourceSnapshot(List.of(), 0);

        assertThrows(RuntimeException.class, snapshot::allowlist);
    }
}
