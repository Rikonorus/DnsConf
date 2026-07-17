package com.novibe.common.proxy;

import com.novibe.common.data_sources.RedirectSourceSnapshot.ProxyAllowlist;
import com.novibe.common.exception.ProcessException;
import com.novibe.common.util.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Owns the stage -> provider updates -> commit transaction boundary. */
@Service
@RequiredArgsConstructor
public class ProxySyncCoordinator {

    private static final Duration PROVIDER_PHASE_TIMEOUT = Duration.ofMinutes(15);

    private final ProxySyncClient proxySyncClient;

    public void run(ProxyConfiguration configuration, ProxyAllowlist allowlist, ThrowingWork providerUpdates) {
        if (!configuration.enabled()) {
            runWithoutProxy(providerUpdates);
            return;
        }

        Log.common("Prepared %s exact proxy allowlist domains (SHA-256 %s)".formatted(
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
            runProviderUpdates(providerUpdates);

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

    private void runProviderUpdates(ThrowingWork providerUpdates) throws Exception {
        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> future = executor.submit(() -> {
                providerUpdates.run();
                return null;
            });
            try {
                future.get(PROVIDER_PHASE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw new ProcessException("DNS provider phase exceeded its timeout", exception);
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
