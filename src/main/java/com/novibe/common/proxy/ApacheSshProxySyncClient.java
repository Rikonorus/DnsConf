package com.novibe.common.proxy;

import com.novibe.common.data_sources.RedirectSourceSnapshot.ProxyAllowlist;
import com.novibe.common.exception.ProcessException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Direct SSH/SFTP implementation of the fixed VPS allowlist contract. */
@Service
public class ApacheSshProxySyncClient implements ProxySyncClient {

    private static final int SSH_PORT = 22;
    private static final String USER = "root";
    private static final String COMMAND = "/usr/local/sbin/dnsconf-proxy-allowlist";
    private static final String INCOMING_DIRECTORY = "/var/lib/dnsconf-proxy/incoming";
    private static final int ROOT_UID = 0;
    private static final int OWNER_READ_WRITE = 0600;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration HOST_KEY_POLL_INTERVAL = Duration.ofMillis(25);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern TOKEN = Pattern.compile("[0-9a-fA-F]{64}");

    @Override
    public void verifyCompatibleContract(ProxyConfiguration configuration) {
        requireContractVersion(execute(configuration, COMMAND + " version"));
    }

    @Override
    public Transaction stage(ProxyConfiguration configuration, ProxyAllowlist allowlist) {
        String remotePath = createRemoteTempPath();
        try {
            withSession(configuration, SshFailureCategory.SFTP, session -> {
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    uploadAllowlist(sftp, remotePath, allowlist.bytes());
                }
                return null;
            });
            return new Transaction(parseTransactionToken(execute(configuration, COMMAND + " stage " + remotePath)));
        } catch (ProcessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw safeFailure("Proxy allowlist stage failed");
        } finally {
            deleteRemoteFile(configuration, remotePath);
        }
    }

    @Override
    public void renew(ProxyConfiguration configuration, Transaction transaction) {
        execute(configuration, COMMAND + " renew " + transaction.token());
    }

    @Override
    public void commit(ProxyConfiguration configuration, Transaction transaction) {
        execute(configuration, COMMAND + " commit " + transaction.token());
    }

    @Override
    public void abort(ProxyConfiguration configuration, Transaction transaction) {
        execute(configuration, COMMAND + " abort " + transaction.token());
    }

    private void deleteRemoteFile(ProxyConfiguration configuration, String remotePath) {
        try {
            withSession(configuration, SshFailureCategory.SFTP, session -> {
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    sftp.remove(remotePath);
                } catch (IOException ignored) {
                    // The server may already have consumed the root-only incoming file.
                }
                return null;
            });
        } catch (Exception ignored) {
            // A server-side root-only cleanup/lease recovery remains the fallback.
        }
    }

    private String execute(ProxyConfiguration configuration, String command) {
        return withSession(configuration, SshFailureCategory.COMMAND, session -> {
            try (ClientChannel channel = session.createExecChannel(command);
                 ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                 ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {
                channel.setOut(stdout);
                channel.setErr(stderr);
                channel.open().verify(COMMAND_TIMEOUT);
                Set<ClientChannelEvent> events = channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), COMMAND_TIMEOUT);
                if (!events.contains(ClientChannelEvent.CLOSED)) {
                    throw commandTimeoutFailure();
                }
                Integer exitStatus = channel.getExitStatus();
                if (!Integer.valueOf(0).equals(exitStatus)) {
                    throw remoteCommandFailure(exitStatus);
                }
                return stdout.toString();
            }
        });
    }

    static String createRemoteTempPath() {
        return INCOMING_DIRECTORY + "/allowlist."
                + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    static String parseTransactionToken(String commandOutput) {
        String token = commandOutput.strip();
        if (!TOKEN.matcher(token).matches()) {
            throw new ProcessException("Proxy stage did not return a valid transaction token");
        }
        return token;
    }

    static void requireContractVersion(String commandOutput) {
        if (!"2".equals(commandOutput.strip())) {
            throw new ProcessException("Proxy allowlist contract is incompatible");
        }
    }

    static void uploadAllowlist(SftpClient sftp, String remotePath, byte[] allowlistBytes) throws IOException {
        try (OutputStream output = sftp.write(remotePath,
                SftpClient.OpenMode.Create,
                SftpClient.OpenMode.Truncate,
                SftpClient.OpenMode.Write)) {
            output.write(allowlistBytes);
        }
        SftpClient.Attributes permissions = new SftpClient.Attributes();
        permissions.setPermissions(OWNER_READ_WRITE);
        sftp.setStat(remotePath, permissions);

        SftpClient.Attributes uploaded = sftp.stat(remotePath);
        if (!uploaded.isRegularFile()
                || (uploaded.getPermissions() & 0777) != OWNER_READ_WRITE
                || uploaded.getUserId() != ROOT_UID) {
            throw new ProcessException("Proxy SFTP upload did not satisfy the required file ownership and permissions");
        }
    }

    static ProcessException remoteCommandFailure(Integer exitStatus) {
        return switch (exitStatus) {
            case 2 -> new ProcessException("Proxy rejected the staged allowlist");
            case 3 -> new ProcessException("Proxy allowlist contract is incompatible");
            case 4 -> new ProcessException("Proxy rejected the generated runtime configuration");
            case 5 -> new ProcessException("Proxy could not atomically apply the allowlist");
            case 6 -> new ProcessException("Proxy transaction is busy or stale");
            default -> new ProcessException("Proxy remote command failed");
        };
    }

    private <T> T withSession(
            ProxyConfiguration configuration,
            SshFailureCategory operation,
            SessionWork<T> work
    ) {
        Path knownHostsFile = null;
        try {
            try {
                knownHostsFile = writeKnownHosts(configuration.knownHosts());
            } catch (Exception exception) {
                throw sshFailure(SshFailureCategory.KNOWN_HOSTS, exception);
            }
            try (SshClient client = SshClient.setUpDefaultClient()) {
                AtomicReference<HostKeyVerificationStatus> hostKeyStatus = new AtomicReference<>(
                        HostKeyVerificationStatus.NOT_VERIFIED
                );
                KnownHostsServerKeyVerifier knownHostsVerifier = new KnownHostsServerKeyVerifier(
                        RejectAllServerKeyVerifier.INSTANCE, knownHostsFile
                );
                configureStrictPinnedHostKeyAlgorithm(client);
                client.setServerKeyVerifier(trackingPinnedVerifier(knownHostsVerifier, hostKeyStatus));
                client.start();
                try (ClientSession session = connect(client, configuration, hostKeyStatus)) {
                    authenticate(session, configuration.rootPassword());
                    try {
                        return work.execute(session);
                    } catch (ProcessException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw sshFailure(operation, exception);
                    }
                } finally {
                    client.stop();
                }
            }
        } catch (ProcessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw sshFailure(SshFailureCategory.CONNECT, exception);
        } finally {
            if (knownHostsFile != null) {
                try {
                    Files.deleteIfExists(knownHostsFile);
                } catch (IOException ignored) {
                    // OS temporary-file cleanup is sufficient if a runner is interrupted here.
                }
            }
        }
    }

    private ClientSession connect(
            SshClient client,
            ProxyConfiguration configuration,
            AtomicReference<HostKeyVerificationStatus> hostKeyStatus
    ) {
        try {
            ClientSession session = client.connect(USER, configuration.redirectTarget(), SSH_PORT)
                    .verify(CONNECT_TIMEOUT)
                    .getSession();
            try {
                requireVerifiedHostKey(session, hostKeyStatus);
                return session;
            } catch (ProcessException exception) {
                session.close(false);
                throw exception;
            }
        } catch (ProcessException exception) {
            throw exception;
        } catch (Exception exception) {
            SshFailureCategory category = hostKeyStatus.get() == HostKeyVerificationStatus.REJECTED
                    ? SshFailureCategory.HOST_KEY_MISMATCH
                    : SshFailureCategory.CONNECT;
            throw sshFailure(category, exception);
        }
    }

    private void requireVerifiedHostKey(
            ClientSession session,
            AtomicReference<HostKeyVerificationStatus> hostKeyStatus
    ) {
        HostKeyVerificationWaitResult result = awaitHostKeyVerification(
                hostKeyStatus,
                () -> !session.isClosing() && !session.isClosed(),
                CONNECT_TIMEOUT,
                HOST_KEY_POLL_INTERVAL,
                System::nanoTime,
                Thread::sleep
        );
        switch (result) {
            case VERIFIED -> {
                return;
            }
            case REJECTED -> throw new ProcessException("Proxy SSH host-key mismatch failed (HostKeyRejected)");
            case SESSION_CLOSED -> throw new ProcessException("Proxy SSH host-key verification failed (ConnectionClosed)");
            case TIMED_OUT -> throw new ProcessException("Proxy SSH host-key verification timeout");
            case INTERRUPTED -> {
                Thread.currentThread().interrupt();
                throw new ProcessException("Proxy SSH host-key verification interrupted");
            }
        }
    }

    static HostKeyVerificationWaitResult awaitHostKeyVerification(
            AtomicReference<HostKeyVerificationStatus> hostKeyStatus,
            BooleanSupplier sessionOpen,
            Duration timeout,
            Duration pollInterval,
            LongSupplier nanoTime,
            DurationSleeper sleeper
    ) {
        long deadline = nanoTime.getAsLong() + timeout.toNanos();
        while (true) {
            switch (hostKeyStatus.get()) {
                case VERIFIED:
                    return HostKeyVerificationWaitResult.VERIFIED;
                case REJECTED:
                    return HostKeyVerificationWaitResult.REJECTED;
                case NOT_VERIFIED:
                    break;
            }
            if (!sessionOpen.getAsBoolean()) {
                return HostKeyVerificationWaitResult.SESSION_CLOSED;
            }
            long remainingNanos = deadline - nanoTime.getAsLong();
            if (remainingNanos <= 0) {
                return HostKeyVerificationWaitResult.TIMED_OUT;
            }
            try {
                sleeper.sleep(Duration.ofNanos(Math.min(remainingNanos, pollInterval.toNanos())));
            } catch (InterruptedException exception) {
                return HostKeyVerificationWaitResult.INTERRUPTED;
            }
        }
    }

    private void authenticate(ClientSession session, String rootPassword) {
        try {
            // The password is added only after the strict pinned host key was verified during connect.
            session.addPasswordIdentity(rootPassword);
            session.auth().verify(AUTH_TIMEOUT);
        } catch (Exception exception) {
            throw sshFailure(SshFailureCategory.AUTHENTICATION, exception);
        }
    }

    static ServerKeyVerifier trackingPinnedVerifier(
            ServerKeyVerifier delegate,
            AtomicReference<HostKeyVerificationStatus> hostKeyStatus
    ) {
        return (session, remoteAddress, serverKey) -> {
            try {
                boolean verified = delegate.verifyServerKey(session, remoteAddress, serverKey);
                hostKeyStatus.set(verified ? HostKeyVerificationStatus.VERIFIED : HostKeyVerificationStatus.REJECTED);
                return verified;
            } catch (RuntimeException exception) {
                hostKeyStatus.set(HostKeyVerificationStatus.REJECTED);
                throw exception;
            }
        };
    }

    static void configureStrictPinnedHostKeyAlgorithm(SshClient client) {
        if (!BuiltinSignatures.ed25519.isSupported()) {
            throw new ProcessException("Proxy SSH ssh-ed25519 support is unavailable");
        }
        client.setSignatureFactories(List.of(BuiltinSignatures.ed25519));
    }

    private Path writeKnownHosts(String knownHosts) throws IOException {
        Path file = Files.createTempFile("dnsconf-known-hosts-", ".tmp");
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            Files.writeString(file, knownHosts + "\n");
            return file;
        } catch (IOException exception) {
            Files.deleteIfExists(file);
            throw exception;
        }
    }

    static ProcessException sshFailure(SshFailureCategory category, Exception cause) {
        String className = cause.getClass().getSimpleName();
        if (className.isBlank()) {
            className = cause.getClass().getName();
        }
        return new ProcessException("Proxy SSH " + category.label + " failed (" + className + ")");
    }

    static ProcessException commandTimeoutFailure() {
        return new ProcessException("Proxy SSH command timeout");
    }

    private ProcessException safeFailure(String message) {
        return new ProcessException(SensitiveValueRedactor.redact(message));
    }

    enum SshFailureCategory {
        CONNECT("connect"),
        HOST_KEY_MISMATCH("host-key mismatch"),
        AUTHENTICATION("authentication"),
        COMMAND("command"),
        SFTP("SFTP"),
        KNOWN_HOSTS("known-hosts setup");

        private final String label;

        SshFailureCategory(String label) {
            this.label = label;
        }
    }

    enum HostKeyVerificationStatus {
        NOT_VERIFIED,
        VERIFIED,
        REJECTED
    }

    enum HostKeyVerificationWaitResult {
        VERIFIED,
        REJECTED,
        SESSION_CLOSED,
        TIMED_OUT,
        INTERRUPTED
    }

    @FunctionalInterface
    interface DurationSleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    @FunctionalInterface
    private interface SessionWork<T> {
        T execute(ClientSession session) throws Exception;
    }
}
