package com.novibe.common.proxy;

import com.novibe.common.data_sources.RedirectSourceSnapshot.ProxyAllowlist;
import com.novibe.common.exception.ProcessException;
import com.novibe.common.util.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Owns the stage -> provider updates -> commit transaction boundary. */
@Service
public class ProxySyncCoordinator {

    private static final Duration PROVIDER_PHASE_TIMEOUT = Duration.ofMinutes(65);
    private static final Duration LEASE_RENEW_INTERVAL = Duration.ofMinutes(5);

    private final ProxySyncClient proxySyncClient;
    private final Duration providerPhaseTimeout;
    private final Duration leaseRenewInterval;

    @Autowired
    public ProxySyncCoordinator(ProxySyncClient proxySyncClient) {
        this(proxySyncClient, PROVIDER_PHASE_TIMEOUT, LEASE_RENEW_INTERVAL);
    }

    ProxySyncCoordinator(
            ProxySyncClient proxySyncClient,
            Duration providerPhaseTimeout,
            Duration leaseRenewInterval
    ) {
        this.proxySyncClient = proxySyncClient;
        this.providerPhaseTimeout = providerPhaseTimeout;
        this.leaseRenewInterval = leaseRenewInterval;
    }

    public void run(ProxyConfiguration configuration, ProxyAllowlist allowlist, ThrowingWork providerUpdates) {
        if (!configuration.enabled()) {
            runWithoutProxy(providerUpdates);
            return;
        }

        Log.common("Prepared %s proxy route roots (SHA-256 %s)".formatted(
                allowlist.domains().size(), allowlist.sha256()
        ));
        Log.step("Verify proxy allowlist contract");
        proxySyncClient.verifyCompatibleContract(configuration);

        ProxySyncClient.Transaction transaction = null;
        boolean commitAttempted = false;
        try {
            Log.step("Stage proxy allowlist");
            transaction = proxySyncClient.stage(configuration, allowlist);
            proxySyncClient.renew(configuration, transaction);

            Log.step("Update DNS profiles");
            runProviderUpdates(configuration, transaction, providerUpdates);

            proxySyncClient.renew(configuration, transaction);
            Log.step("Commit proxy allowlist");
            commitAttempted = true;
            proxySyncClient.commit(configuration, transaction);
        } catch (Exception exception) {
            if (transaction != null && !commitAttempted) {
                try {
                    proxySyncClient.abort(configuration, transaction);
                } catch (Exception ignored) {
                    // Lease expiry provides server-side recovery if abort cannot be delivered.
                }
            }
            if (exception instanceof ProcessException processException) throw processException;
            throw new ProcessException("Proxy synchronization failed", exception);
        }
    }

    private void runWithoutProxy(ThrowingWork providerUpdates) {
        try {
            providerUpdates.run();
        } catch (ProcessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProcessException("DNS profile update failed", exception);
        }
    }

    private void runProviderUpdates(
            ProxyConfiguration configuration,
            ProxySyncClient.Transaction transaction,
            ThrowingWork providerUpdates
    ) throws Exception {
        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> future = executor.submit(() -> {
                providerUpdates.run();
                return null;
            });
            long deadlineNanos = System.nanoTime() + providerPhaseTimeout.toNanos();
            long nextRenewNanos = System.nanoTime() + leaseRenewInterval.toNanos();
            try {
                while (true) {
                    long now = System.nanoTime();
                    long remainingToDeadline = deadlineNanos - now;
                    if (remainingToDeadline <= 0) {
                        future.cancel(true);
                        throw new ProcessException("DNS provider phase exceeded its timeout");
                    }
                    if (nextRenewNanos <= now) {
                        try {
                            proxySyncClient.renew(configuration, transaction);
                        } catch (Exception renewalFailure) {
                            future.cancel(true);
                            throw renewalFailure;
                        }
                        nextRenewNanos = System.nanoTime() + leaseRenewInterval.toNanos();
                        continue;
                    }
                    long waitNanos = Math.min(remainingToDeadline, nextRenewNanos - now);
                    try {
                        future.get(waitNanos, TimeUnit.NANOSECONDS);
                        return;
                    } catch (TimeoutException exception) {
                        if (System.nanoTime() >= deadlineNanos) {
                            future.cancel(true);
                            throw new ProcessException("DNS provider phase exceeded its timeout");
                        }
                        try {
                            proxySyncClient.renew(configuration, transaction);
                        } catch (Exception renewalFailure) {
                            future.cancel(true);
                            throw renewalFailure;
                        }
                        nextRenewNanos = System.nanoTime() + leaseRenewInterval.toNanos();
                    }
                }
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof Exception wrapped) throw wrapped;
                throw new ProcessException("DNS provider phase failed", cause);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingWork {
        void run() throws Exception;
    }
}
