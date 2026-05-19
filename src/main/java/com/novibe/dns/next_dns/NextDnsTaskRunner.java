package com.novibe.dns.next_dns;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.config.NextDnsRewriteExclusionConfig;
import com.novibe.dns.next_dns.config.NextDnsRewriteExclusionParser;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.service.NextDnsDenyService;
import com.novibe.dns.next_dns.service.NextDnsRewriteService;
import com.novibe.dns.next_dns.service.WildcardDomainMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.novibe.common.config.EnvironmentVariables.BLOCK;
import static com.novibe.common.config.EnvironmentVariables.NEXTDNS_REWRITE_EXCLUSIONS;
import static com.novibe.common.config.EnvironmentVariables.REDIRECT;

@Service
@RequiredArgsConstructor
public class NextDnsTaskRunner extends DnsTaskRunner {

    private final NextDnsRewriteExclusionParser nextDnsRewriteExclusionParser;
    private final NextDnsRewriteService nextDnsRewriteService;
    private final NextDnsDenyService nextDnsDenyService;

    @Override
    public void greetingMessage() {
        Log.global("Setting up Profile " + dnsProfile.number() + " (NextDNS)");
        Log.common("""
                Script behaviour: old BLOCK/REDIRECT settings are about to be updated via provided BLOCK/REDIRECT sources.
                - if no sources provided, then all NextDNS settings will be removed.
                - each line is mapped to an IP–domain pair; lines that cannot be parsed are skipped.
                - if provided only one type of sources, related settings will be updated; another type remain untouched.
                - if EXCLUDE_REDIRECT domains provided, they will be removed from existing redirect rules and won't be added with new ones.
                - if NEXTDNS_REWRITE_EXCLUSIONS provided, matching new rewrite rules are skipped; cleanupExisting controls existing ones.
                NextDNS api rate limiter reset config: 60 seconds after the last request""");
    }

    @Override
    protected void process() {
        List<String> blockSources = EnvParser.parse(BLOCK);
        List<String> rewriteSources = EnvParser.parse(REDIRECT);
        Optional<NextDnsRewriteExclusionConfig> exclusionConfig = Optional.empty();
        Optional<WildcardDomainMatcher> exclusionMatcher = Optional.empty();

        if (!rewriteSources.isEmpty()) {
            exclusionConfig = nextDnsRewriteExclusionParser.parse(NEXTDNS_REWRITE_EXCLUSIONS);
            exclusionMatcher = exclusionConfig
                    .map(NextDnsRewriteExclusionConfig::patterns)
                    .map(WildcardDomainMatcher::new);
        }

        if (!blockSources.isEmpty()) {
            Log.step("Obtain block lists from %s sources".formatted(blockSources.size()));
            List<String> blocks = blockListsLoader.fetchWebsites(blockSources);
            Log.step("Prepare denylist");
            List<String> filteredBlocklist = nextDnsDenyService.omitExistingDenys(blocks);
            Log.common("Prepared %s domains to block".formatted(filteredBlocklist.size()));
            Log.step("Save denylist");
            nextDnsDenyService.saveDenyList(filteredBlocklist);
        } else {
            Log.common("No block sources provided; skipping denylist update");
        }

        if (!rewriteSources.isEmpty()) {

            Log.step("Obtain rewrite lists from %s sources".formatted(rewriteSources.size()));
            List<HostsOverrideListsLoader.BypassRoute> overrides = overrideListsLoader.fetchWebsites(rewriteSources);

            exclusionConfig.ifPresent(config -> Log.common(
                    "Loaded %s NextDNS rewrite exclusion patterns. Existing cleanup: %s"
                            .formatted(config.patterns().size(), config.cleanupExisting())
            ));

            Log.step("Prepare rewrites");
            Map<String, CreateRewriteDto> requests = nextDnsRewriteService.buildNewRewrites(overrides, exclusionMatcher);
            List<CreateRewriteDto> createRewriteDtos = nextDnsRewriteService.cleanupOutdatedAndExcluded(
                    requests,
                    exclusionMatcher,
                    exclusionConfig.map(NextDnsRewriteExclusionConfig::cleanupExisting).orElse(false)
            );

            Log.step("Save rewrites");
            nextDnsRewriteService.saveRewrites(createRewriteDtos);
        } else {
            Log.common("No rewrite sources provided; skipping rewrite update");
        }

        if (blockSources.isEmpty() && rewriteSources.isEmpty()) {
            Log.step("Remove settings");
            nextDnsDenyService.removeAll();
            nextDnsRewriteService.removeAll();
        }
    }

    @Override
    protected void finishMessage() {
        Log.global("Profile " + dnsProfile.number() + " (NextDNS) set up successfully");
    }
}
