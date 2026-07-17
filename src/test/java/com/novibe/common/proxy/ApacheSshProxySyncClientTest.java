package com.novibe.common.proxy;

import com.novibe.common.exception.ProcessException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ApacheSshProxySyncClientTest {

    @Test
    void createsOnlyServerAcceptedIncomingPath() {
        String path = ApacheSshProxySyncClient.createRemoteTempPath();

        assertTrue(Pattern.matches("/var/lib/dnsconf-proxy/incoming/allowlist\\.[A-Za-z0-9]{16,128}", path));
    }

    @Test
    void acceptsOnly64HexStageToken() {
        String valid = "a".repeat(64);

        assertEquals(valid, ApacheSshProxySyncClient.parseTransactionToken(valid + "\n"));
        assertThrows(ProcessException.class, () -> ApacheSshProxySyncClient.parseTransactionToken("short-token"));
        assertThrows(ProcessException.class, () -> ApacheSshProxySyncClient.parseTransactionToken("g".repeat(64)));
    }

    @Test
    void requiresOnlyContractVersionOne() {
        ApacheSshProxySyncClient.requireContractVersion("1\n");

        assertThrows(ProcessException.class, () -> ApacheSshProxySyncClient.requireContractVersion("2"));
        assertThrows(ProcessException.class, () -> ApacheSshProxySyncClient.requireContractVersion("1\nstatus"));
    }

    @Test
    void mapsServerExitCodesWithoutUsingServerOutput() {
        assertEquals("Proxy rejected the staged allowlist", ApacheSshProxySyncClient.remoteCommandFailure(2).getMessage());
        assertEquals("Proxy transaction is busy or stale", ApacheSshProxySyncClient.remoteCommandFailure(6).getMessage());
        assertEquals("Proxy remote command failed", ApacheSshProxySyncClient.remoteCommandFailure(99).getMessage());
    }

    @Test
    void uploadsRootOwned0600RegularFileBeforeStage() throws Exception {
        SftpClient sftp = mock(SftpClient.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        SftpClient.Attributes uploaded = mock(SftpClient.Attributes.class);
        String path = ApacheSshProxySyncClient.createRemoteTempPath();
        byte[] content = "example.com\n".getBytes();
        when(sftp.write(eq(path), any(SftpClient.OpenMode[].class))).thenReturn(stream);
        when(sftp.stat(path)).thenReturn(uploaded);
        when(uploaded.isRegularFile()).thenReturn(true);
        when(uploaded.getPermissions()).thenReturn(0600);
        when(uploaded.getUserId()).thenReturn(0);

        ApacheSshProxySyncClient.uploadAllowlist(sftp, path, content);

        assertArrayEquals(content, stream.toByteArray());
        ArgumentCaptor<SftpClient.Attributes> permissions = ArgumentCaptor.forClass(SftpClient.Attributes.class);
        verify(sftp).setStat(eq(path), permissions.capture());
        assertEquals(0600, permissions.getValue().getPermissions());
        verify(sftp).stat(path);
    }

    @Test
    void rejectsUploadWithoutRootOwnershipOr0600() throws Exception {
        SftpClient sftp = mock(SftpClient.class);
        SftpClient.Attributes uploaded = mock(SftpClient.Attributes.class);
        String path = ApacheSshProxySyncClient.createRemoteTempPath();
        when(sftp.write(eq(path), any(SftpClient.OpenMode[].class))).thenReturn(new ByteArrayOutputStream());
        when(sftp.stat(path)).thenReturn(uploaded);
        when(uploaded.isRegularFile()).thenReturn(true);
        when(uploaded.getPermissions()).thenReturn(0644);
        when(uploaded.getUserId()).thenReturn(0);

        assertThrows(ProcessException.class, () -> ApacheSshProxySyncClient.uploadAllowlist(sftp, path, new byte[]{1}));
    }

    @Test
    void defaultApacheSshClientExposesSshEd25519() throws Exception {
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.start();

            assertTrue(BuiltinSignatures.ed25519.isSupported());
            assertTrue(client.getSignatureFactories().stream()
                    .anyMatch(factory -> "ssh-ed25519".equals(factory.getName())));
        }
    }

    @Test
    void reportsSafeSshFailureCategoriesAndNeverIncludesCauseText() {
        IOException sensitiveCause = new IOException("password=never-log-this");
        ProcessException connectFailure = ApacheSshProxySyncClient.sshFailure(
                ApacheSshProxySyncClient.SshFailureCategory.CONNECT,
                sensitiveCause
        );

        assertEquals(
                "Proxy SSH connect failed (IOException)",
                connectFailure.getMessage()
        );
        assertEquals(
                "Proxy SSH host-key mismatch failed (IOException)",
                ApacheSshProxySyncClient.sshFailure(
                        ApacheSshProxySyncClient.SshFailureCategory.HOST_KEY_MISMATCH,
                        sensitiveCause
                ).getMessage()
        );
        assertEquals(
                "Proxy SSH authentication failed (IOException)",
                ApacheSshProxySyncClient.sshFailure(
                        ApacheSshProxySyncClient.SshFailureCategory.AUTHENTICATION,
                        sensitiveCause
                ).getMessage()
        );
        assertEquals("Proxy SSH command timeout", ApacheSshProxySyncClient.commandTimeoutFailure().getMessage());
        assertFalse(connectFailure.getMessage().contains("never-log-this"));
        assertNull(connectFailure.getCause());
    }

    @Test
    void rejectsAnUnpinnedHostKeyBeforeAuthenticationCanBegin() {
        AtomicReference<ApacheSshProxySyncClient.HostKeyVerificationStatus> status = new AtomicReference<>(
                ApacheSshProxySyncClient.HostKeyVerificationStatus.NOT_VERIFIED
        );

        boolean accepted = ApacheSshProxySyncClient.trackingPinnedVerifier(
                (session, remoteAddress, serverKey) -> false,
                status
        ).verifyServerKey(null, null, null);

        assertFalse(accepted);
        assertEquals(ApacheSshProxySyncClient.HostKeyVerificationStatus.REJECTED, status.get());
    }

    @Test
    void waitsForAnAsynchronousPinnedHostKeyCallbackBeforeClassifyingItAsMismatch() {
        AtomicReference<ApacheSshProxySyncClient.HostKeyVerificationStatus> status = new AtomicReference<>(
                ApacheSshProxySyncClient.HostKeyVerificationStatus.NOT_VERIFIED
        );
        AtomicLong nanoseconds = new AtomicLong();

        ApacheSshProxySyncClient.HostKeyVerificationWaitResult result = ApacheSshProxySyncClient.awaitHostKeyVerification(
                status,
                () -> true,
                Duration.ofSeconds(1),
                Duration.ofMillis(10),
                nanoseconds::get,
                pause -> {
                    nanoseconds.addAndGet(pause.toNanos());
                    status.set(ApacheSshProxySyncClient.HostKeyVerificationStatus.VERIFIED);
                }
        );

        assertEquals(ApacheSshProxySyncClient.HostKeyVerificationWaitResult.VERIFIED, result);
    }

    @Test
    void reportsHostKeyVerificationTimeoutOnlyAfterTheBoundedWaitExpires() {
        AtomicReference<ApacheSshProxySyncClient.HostKeyVerificationStatus> status = new AtomicReference<>(
                ApacheSshProxySyncClient.HostKeyVerificationStatus.NOT_VERIFIED
        );
        AtomicLong nanoseconds = new AtomicLong();

        ApacheSshProxySyncClient.HostKeyVerificationWaitResult result = ApacheSshProxySyncClient.awaitHostKeyVerification(
                status,
                () -> true,
                Duration.ofMillis(10),
                Duration.ofMillis(5),
                nanoseconds::get,
                pause -> nanoseconds.addAndGet(pause.toNanos())
        );

        assertEquals(ApacheSshProxySyncClient.HostKeyVerificationWaitResult.TIMED_OUT, result);
    }
}
