package com.novibe.common.service;

import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import com.novibe.common.util.DataParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ExcludeRedirectCheckService {

    private final List<String> ignoringList;

    public ExcludeRedirectCheckService(ExcludeRedirectSettingsLoader excludeRedirectSettingsLoader) {
        ignoringList = excludeRedirectSettingsLoader.loadIgnoredDomains();
    }

    public boolean shouldExclude(String domain) {
        String normalizedDomain = DataParser.removeWWW(domain.toLowerCase(Locale.ROOT).strip());
        for (String ignored : ignoringList) {
            if (normalizedDomain.equals(ignored) || normalizedDomain.endsWith("." + ignored)) {
                return true;
            }
        }
        return false;
    }

}
