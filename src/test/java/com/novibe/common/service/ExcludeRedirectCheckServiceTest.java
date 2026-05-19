package com.novibe.common.service;

import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcludeRedirectCheckServiceTest {

    @Test
    void matchesExactDomainAndSubdomainWithBoundaryOnly() {
        ExcludeRedirectSettingsLoader loader = mock(ExcludeRedirectSettingsLoader.class);
        when(loader.loadIgnoredDomains()).thenReturn(List.of("instagram.com"));

        ExcludeRedirectCheckService service = new ExcludeRedirectCheckService(loader);

        assertTrue(service.shouldExclude("instagram.com"));
        assertTrue(service.shouldExclude("a.instagram.com"));
        assertFalse(service.shouldExclude("notinstagram.com"));
    }
}
