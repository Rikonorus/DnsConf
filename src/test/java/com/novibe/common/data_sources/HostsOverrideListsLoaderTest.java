package com.novibe.common.data_sources;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostsOverrideListsLoaderTest {

    @Test
    void fetchWebsitesSkipsMalformedRowsAndSupportsFlexibleWhitespace() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = stringResponse(loadFixture());

        doReturn(response).when(client).send(any(HttpRequest.class), argThat(handler -> true));

        HostsOverrideListsLoader loader = new HostsOverrideListsLoader();
        loader.setClient(client);

        List<HostsOverrideListsLoader.BypassRoute> routes = loader.fetchWebsites(List.of("https://example.com/hosts"));

        assertEquals(4, routes.size());
        assertTrue(routes.contains(new HostsOverrideListsLoader.BypassRoute("157.240.245.174", "instagram.com")));
        assertTrue(routes.contains(new HostsOverrideListsLoader.BypassRoute("157.240.245.174", "b.i.instagram.com")));
        assertTrue(routes.contains(new HostsOverrideListsLoader.BypassRoute("8.8.8.8", "google.com")));
        assertTrue(routes.contains(new HostsOverrideListsLoader.BypassRoute("1.2.3.4", "example.com")));
    }

    private String loadFixture() throws Exception {
        Path path = Path.of(Objects.requireNonNull(getClass().getResource("/geohidedns-malformed-sample.hosts")).toURI());
        return Files.readString(path);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> stringResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(body);
        return response;
    }
}
