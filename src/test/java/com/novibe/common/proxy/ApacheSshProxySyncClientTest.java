package com.novibe.common.proxy;

import com.novibe.common.exception.ProcessException;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
