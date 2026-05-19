package com.novibe;


import com.novibe.common.DnsTaskRunner;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.ProcessException;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static java.util.Objects.nonNull;


public class App {

    public static ApplicationContext commonContext;

    static void main() {

        List<DnsProfile> dnsProfiles = EnvParser.parseProfiles();

        String commonsBasePackage = "com.novibe.common";
        commonContext = new AnnotationConfigApplicationContext(commonsBasePackage);
        AnnotationConfigApplicationContext currentContext = null;
        boolean hasErrors = false;

        for (DnsProfile dnsProfile : dnsProfiles) {
            try {
                currentContext = loadProfileContext(dnsProfile);

                DnsTaskRunner runner = currentContext.getBean(DnsTaskRunner.class);
                runner.run();

            } catch (ProcessException processException) {
                hasErrors = true;
                Log.fail("Process Exception on profile " + dnsProfile.number());
                Log.fail(processException.getMessage());
            } catch (Exception exception) {
                hasErrors = true;
                Log.fail("Unexpected exception on profile " + dnsProfile.number());
                exception.printStackTrace(System.out);
            } finally {
                if (nonNull(currentContext)) currentContext.close();
            }
        }

        if (hasErrors) {
            System.exit(1);
        }
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
