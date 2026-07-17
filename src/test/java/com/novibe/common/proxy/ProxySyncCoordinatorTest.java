package com.novibe.common.proxy;

import com.novibe.common.data_sources.RedirectSourceSnapshot;
import com.novibe.common.data_sources.RedirectSourceSnapshot.ProxyAllowlist;
import com.novibe.common.exception.ProcessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxySyncCoordinatorTest {

    private final RecordingClient client = new RecordingClient();
    private final ProxySyncCoordinator coordinator = new ProxySyncCoordinator(client);
    private final ProxyConfiguration configuration = new ProxyConfiguration(
            true, "198.51.100.7", Set.of(), "password", "198.51.100.7 ssh-ed25519 AAAA"
    );
    private final ProxyAllowlist allowlist = new RedirectSourceSnapshot(List.of(
            new com.novibe.common.data_sources.RedirectSourceRecord("1.1.1.1", "example.com", "example.com")
    ), 0).allowlist();

    @Test
    void stagesBeforeProviderMutationsAndCommitsAfterSuccess() {
        coordinator.run(configuration, allowlist, () -> client.events.add("providers"));

        assertEquals(List.of("version", "stage", "renew", "providers", "renew", "commit"), client.events);
    }

    @Test
    void abortsStagedTransactionWhenProviderFails() {
        assertThrows(ProcessException.class, () -> coordinator.run(configuration, allowlist, () -> {
            client.events.add("providers");
            throw new ProcessException("provider failed");
        }));

        assertEquals(List.of("version", "stage", "renew", "providers", "abort"), client.events);
    }

    private static class RecordingClient implements ProxySyncClient {
        private final List<String> events = new ArrayList<>();

        @Override
        public void verifyCompatibleContract(ProxyConfiguration configuration) {
            events.add("version");
        }

        @Override
        public Transaction stage(ProxyConfiguration configuration, ProxyAllowlist allowlist) {
            events.add("stage");
            return new Transaction("valid-token-123456");
        }

        @Override
        public void renew(ProxyConfiguration configuration, Transaction transaction) {
            events.add("renew");
        }

        @Override
        public void commit(ProxyConfiguration configuration, Transaction transaction) {
            events.add("commit");
        }

        @Override
        public void abort(ProxyConfiguration configuration, Transaction transaction) {
            events.add("abort");
        }
    }
}
