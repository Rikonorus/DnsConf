package com.novibe;


import com.novibe.common.DnsTaskRunner;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.data_sources.RedirectSourceSnapshotProvider;
import com.novibe.common.exception.ProcessException;
import com.novibe.common.proxy.ProxyConfiguration;
import com.novibe.common.proxy.ProxySyncCoordinator;
import com.novibe.common.proxy.SensitiveValueRedactor;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.NextDnsTaskRunner;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.ArrayList;


public class App {

    public static AnnotationConfigApplicationContext commonContext;

    public static void main(String[] args) {
        int exitCode = 0;
        List<AnnotationConfigApplicationContext> profileContexts = new ArrayList<>();
        try {
            List<DnsProfile> dnsProfiles = EnvParser.parseProfiles();
            ProxyConfiguration proxyConfiguration = ProxyConfiguration.fromEnvironment(dnsProfiles);
            SensitiveValueRedactor.configure(proxyConfiguration);

            commonContext = new AnnotationConfigApplicationContext();
            commonContext.scan("com.novibe.common");
            commonContext.registerBean(ProxyConfiguration.class, () -> proxyConfiguration);
            commonContext.refresh();

            for (DnsProfile profile : dnsProfiles) {
                profileContexts.add(loadProfileContext(profile));
            }
            preflightProfileContexts(profileContexts);

            ProxySyncCoordinator coordinator = commonContext.getBean(ProxySyncCoordinator.class);
            if (proxyConfiguration.enabled()) {
                RedirectSourceSnapshotProvider sourceProvider = commonContext.getBean(RedirectSourceSnapshotProvider.class);
                coordinator.run(proxyConfiguration, sourceProvider.load().allowlist(),
                        () -> runAllProfilesFailFast(profileContexts));
            } else {
                runProfilesIndependently(profileContexts);
            }
        } catch (Exception exception) {
            exitCode = 1;
            Log.fail(SensitiveValueRedactor.redact(safeMessage(exception)));
        } finally {
            for (AnnotationConfigApplicationContext context : profileContexts) {
                context.close();
            }
            if (commonContext != null) commonContext.close();
        }
        if (exitCode != 0) System.exit(exitCode);
    }

    private static void runAllProfilesFailFast(List<AnnotationConfigApplicationContext> profileContexts) {
        for (AnnotationConfigApplicationContext context : profileContexts) {
            DnsTaskRunner runner = context.getBean(DnsTaskRunner.class);
            runner.run();
        }
    }

    private static void preflightProfileContexts(List<AnnotationConfigApplicationContext> profileContexts) {
        for (AnnotationConfigApplicationContext context : profileContexts) {
            DnsTaskRunner runner = context.getBean(DnsTaskRunner.class);
            if (runner instanceof NextDnsTaskRunner nextDnsTaskRunner) {
                nextDnsTaskRunner.validateConfiguration();
            }
        }
    }

    private static void runProfilesIndependently(List<AnnotationConfigApplicationContext> profileContexts) {
        ProcessException failure = null;
        for (AnnotationConfigApplicationContext context : profileContexts) {
            DnsTaskRunner runner = context.getBean(DnsTaskRunner.class);
            try {
                runner.run();
            } catch (Exception exception) {
                failure = new ProcessException("DNS profile update failed", exception);
                Log.fail(SensitiveValueRedactor.redact(safeMessage(exception)));
            }
        }
        if (failure != null) throw failure;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Application failed" : message;
    }

    private static @NonNull AnnotationConfigApplicationContext loadProfileContext(DnsProfile dnsProfile) {
        String dnsBasePackage = switch (dnsProfile.dnsProvider()) {
            case "CLOUDFLARE" -> "com.novibe.dns.cloudflare";
            case "NEXTDNS" -> "com.novibe.dns.next_dns";
            default ->
                    throw new ProcessException("Unsupported DNS provider! Must be CLOUDFLARE or NEXTDNS. Was: " + dnsProfile.dnsProvider());

        };
        AnnotationConfigApplicationContext currentContext = new AnnotationConfigApplicationContext();
        currentContext.setParent(commonContext);
        currentContext.scan(dnsBasePackage);
        currentContext.registerBean("DnsProfile", DnsProfile.class, () -> dnsProfile);
        currentContext.refresh();
        return currentContext;
    }

}
