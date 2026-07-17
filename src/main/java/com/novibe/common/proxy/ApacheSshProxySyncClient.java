package com.novibe.common.proxy;

import com.novibe.common.data_sources.RedirectSourceSnapshot.ProxyAllowlist;
import com.novibe.common.exception.ProcessException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
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
import java.util.Set;
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
            withSession(configuration, session -> {
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    uploadAllowlist(sftp, remotePath, allowlist.bytes());
                }
                return null;
            });
            return new Transaction(parseTransactionToken(execute(configuration, COMMAND + " stage " + remotePath)));
        } catch (ProcessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw safeFailure("Proxy allowlist stage failed", exception);
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
            withSession(configuration, session -> {
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
        return withSession(configuration, session -> {
            try (ClientChannel channel = session.createExecChannel(command);
                 ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                 ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {
                channel.setOut(stdout);
                channel.setErr(stderr);
                channel.open().verify(COMMAND_TIMEOUT);
                Set<ClientChannelEvent> events = channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), COMMAND_TIMEOUT);
                if (!events.contains(ClientChannelEvent.CLOSED)) {
                    throw new ProcessException("Proxy remote command timed out");
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
        if (!"1".equals(commandOutput.strip())) {
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

    private <T> T withSession(ProxyConfiguration configuration, SessionWork<T> work) {
        Path knownHostsFile = null;
        try {
            knownHostsFile = writeKnownHosts(configuration.knownHosts());
            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.setServerKeyVerifier(new KnownHostsServerKeyVerifier(
                        RejectAllServerKeyVerifier.INSTANCE, knownHostsFile
                ));
                client.start();
                try (ClientSession session = client.connect(USER, configuration.redirectTarget(), SSH_PORT)
                        .verify(CONNECT_TIMEOUT).getSession()) {
                    // Host-key verification happens during connect, before this identity is added.
                    session.addPasswordIdentity(configuration.rootPassword());
                    session.auth().verify(AUTH_TIMEOUT);
                    return work.execute(session);
                } finally {
                    client.stop();
                }
            }
        } catch (ProcessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw safeFailure("Proxy SSH operation failed", exception);
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

    private ProcessException safeFailure(String message, Exception cause) {
        return new ProcessException(SensitiveValueRedactor.redact(message), cause);
    }

    @FunctionalInterface
    private interface SessionWork<T> {
        T execute(ClientSession session) throws Exception;
    }
}
