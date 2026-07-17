package com.novibe.dns.next_dns.service;

import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.service.ExcludeRedirectCheckService;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsRateLimitedApiProcessor;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class NextDnsRewriteService {

    private final NextDnsRewriteClient nextDnsRewriteClient;
    private final ExcludeRedirectCheckService excludeRedirectCheckService;

    public Map<String, CreateRewriteDto> buildNewRewrites(
            List<HostsOverrideListsLoader.BypassRoute> overrides,
            Optional<WildcardDomainMatcher> exclusionMatcher
    ) {
        return buildNewRewrites(overrides, exclusionMatcher, Optional.empty());
    }

    public Map<String, CreateRewriteDto> buildNewRewrites(
            List<HostsOverrideListsLoader.BypassRoute> overrides,
            Optional<WildcardDomainMatcher> exclusionMatcher,
            Optional<String> redirectTarget
    ) {
        Map<String, CreateRewriteDto> rewriteDtos = new HashMap<>();
        int excludedCounter = 0;
        int excludeRedirectCounter = 0;

        for (HostsOverrideListsLoader.BypassRoute route : overrides) {
            if (excludeRedirectCheckService.shouldExclude(route.website())) {
                excludeRedirectCounter++;
                continue;
            }

            if (matchesExclusion(exclusionMatcher, route.website())) {
                excludedCounter++;
                continue;
            }

            rewriteDtos.putIfAbsent(route.website(), new CreateRewriteDto(
                    route.website(), redirectTarget.orElse(route.ip())
            ));
        }

        if (excludedCounter > 0) {
            Log.io("Skipping %s rewrite candidates due to exclusion patterns".formatted(excludedCounter));
        }

        if (excludeRedirectCounter > 0) {
            Log.io("Skipping %s rewrite candidates due to EXCLUDE_REDIRECT".formatted(excludeRedirectCounter));
        }

        return rewriteDtos;
    }

    public List<CreateRewriteDto> cleanupOutdatedAndExcluded(
            Map<String, CreateRewriteDto> newRewriteRequests,
            Optional<WildcardDomainMatcher> exclusionMatcher,
            boolean cleanupExistingExcluded
    ) {
        return cleanupOutdatedAndExcluded(newRewriteRequests, exclusionMatcher, cleanupExistingExcluded, Optional.empty());
    }

    public List<CreateRewriteDto> cleanupOutdatedAndExcluded(
            Map<String, CreateRewriteDto> newRewriteRequests,
            Optional<WildcardDomainMatcher> exclusionMatcher,
            boolean cleanupExistingExcluded,
            Optional<ProxyRewriteTargets> proxyTargets
    ) {
        List<RewriteDto> existingRewrites = getExistingRewrites();
        java.util.Set<String> desiredDomains = new HashSet<>(newRewriteRequests.keySet());

        List<String> outdatedIds = new ArrayList<>();
        List<String> excludedIds = new ArrayList<>();
        List<String> excludeRedirectIds = new ArrayList<>();

        for (RewriteDto existingRewrite : existingRewrites) {
            String domain = existingRewrite.name();
            if (excludeRedirectCheckService.shouldExclude(domain)) {
                excludeRedirectIds.add(existingRewrite.id());
                newRewriteRequests.remove(domain);
                continue;
            }

            if (matchesExclusion(exclusionMatcher, domain)) {
                if (cleanupExistingExcluded) {
                    excludedIds.add(existingRewrite.id());
                    newRewriteRequests.remove(domain);
                } else if (proxyTargets.filter(targets -> targets.manages(existingRewrite.content())).isPresent()
                        && proxyTargets.get().needsMigration(existingRewrite.content())) {
                    outdatedIds.add(existingRewrite.id());
                    newRewriteRequests.put(domain, new CreateRewriteDto(domain, proxyTargets.get().currentTarget()));
                } else {
                    newRewriteRequests.remove(domain);
                }
                continue;
            }

            String oldIp = existingRewrite.content();
            CreateRewriteDto request = newRewriteRequests.get(domain);
            if (nonNull(request) && !request.content().equals(oldIp)) {
                outdatedIds.add(existingRewrite.id());
            } else {
                newRewriteRequests.remove(domain);
            }
        }
        if (proxyTargets.isPresent()) {
            ProxyRewriteTargets targets = proxyTargets.get();
            for (RewriteDto existingRewrite : existingRewrites) {
                if (!desiredDomains.contains(existingRewrite.name()) && targets.manages(existingRewrite.content())
                        && !excludeRedirectCheckService.shouldExclude(existingRewrite.name())
                        && !matchesExclusion(exclusionMatcher, existingRewrite.name())) {
                    outdatedIds.add(existingRewrite.id());
                }
            }
        }
        newRewriteRequests.keySet().removeIf(excludeRedirectCheckService::shouldExclude);

        if (!outdatedIds.isEmpty()) {
            Log.io("Removing %s outdated rewrites from NextDNS".formatted(outdatedIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(outdatedIds, nextDnsRewriteClient::deleteRewriteById);
        }

        if (!excludeRedirectIds.isEmpty()) {
            Log.io("Removing %s EXCLUDE_REDIRECT rewrites from NextDNS".formatted(excludeRedirectIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(excludeRedirectIds, nextDnsRewriteClient::deleteRewriteById);
        }

        if (!excludedIds.isEmpty()) {
            Log.io("Removing %s excluded rewrites from NextDNS".formatted(excludedIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(excludedIds, nextDnsRewriteClient::deleteRewriteById);
        }

        return newRewriteRequests.values().stream()
                .sorted(java.util.Comparator.comparing(CreateRewriteDto::name))
                .toList();
    }

    public List<RewriteDto> getExistingRewrites() {
        Log.io("Fetching existing rewrites from NextDNS");
        return nextDnsRewriteClient.fetchRewrites();
    }

    public void saveRewrites(List<CreateRewriteDto> createRewriteDtos) {
        Log.io("Saving %s new rewrites to NextDNS...".formatted(createRewriteDtos.size()));
        NextDnsRateLimitedApiProcessor.callApi(createRewriteDtos, nextDnsRewriteClient::saveRewrite);
    }

    public void removeAll() {
        Log.io("Fetching existing rewrites from NextDNS");
        List<RewriteDto> list = nextDnsRewriteClient.fetchRewrites();
        List<String> ids = list.stream().map(RewriteDto::id).toList();
        Log.io("Removing rewrites from NextDNS");
        NextDnsRateLimitedApiProcessor.callApi(ids, nextDnsRewriteClient::deleteRewriteById);
    }

    private boolean matchesExclusion(Optional<WildcardDomainMatcher> exclusionMatcher, String domain) {
        return exclusionMatcher.map(matcher -> matcher.matches(domain)).orElse(false);
    }

    public record ProxyRewriteTargets(String currentTarget, java.util.Set<String> previousTargets) {
        boolean manages(String content) {
            return currentTarget.equals(content) || previousTargets.contains(content);
        }

        boolean needsMigration(String content) {
            return previousTargets.contains(content);
        }
    }

}
