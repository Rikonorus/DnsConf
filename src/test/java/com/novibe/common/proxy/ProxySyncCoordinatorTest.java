package com.novibe.common.proxy;

import com.novibe.common.data_sources.RedirectSourceSnapshot;
import com.novibe.common.data_sources.RedirectSourceSnapshot.ProxyAllowlist;
import com.novibe.common.exception.ProcessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

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
    void abortsStagedTransactionWhenRateLimitDeadlinePreventsCompletion() {
        assertThrows(ProcessException.class, () -> coordinator.run(configuration, allowlist, () -> {
            client.events.add("providers");
            throw new ProcessException("NextDNS rate-limit deadline exceeded");
        }));

        assertEquals(List.of("version", "stage", "renew", "providers", "abort"), client.events);
    }

    @Test
    void renewsTheLeasePeriodicallyWhileProviderUpdatesAreStillRunning() throws Exception {
        RecordingClient shortLeaseClient = new RecordingClient();
        ProxySyncCoordinator shortLeaseCoordinator = new ProxySyncCoordinator(
                shortLeaseClient, java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(10)
        );
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch finishProvider = new CountDownLatch(1);

        Thread run = Thread.ofVirtual().start(() -> shortLeaseCoordinator.run(configuration, allowlist, () -> {
            shortLeaseClient.events.add("providers");
            providerStarted.countDown();
            finishProvider.await();
        }));

        org.junit.jupiter.api.Assertions.assertTrue(providerStarted.await(1, TimeUnit.SECONDS));
        awaitRenewalCount(shortLeaseClient, 2);
        finishProvider.countDown();
        run.join(1_000);

        org.junit.jupiter.api.Assertions.assertFalse(run.isAlive());
        org.junit.jupiter.api.Assertions.assertTrue(
                shortLeaseClient.events.stream().filter("renew"::equals).count() >= 3
        );
        assertEquals("commit", shortLeaseClient.events.getLast());
    }

    private void awaitRenewalCount(RecordingClient client, int count) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadlineNanos) {
            if (client.events.stream().filter("renew"::equals).count() >= count) return;
            Thread.sleep(5);
        }
        throw new AssertionError("Expected periodic proxy lease renewal");
    }

    private static class RecordingClient implements ProxySyncClient {
        private final List<String> events = new CopyOnWriteArrayList<>();

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
