package com.novibe.common.util;

import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.config.EnvironmentVariables;
import com.novibe.common.exception.ProcessException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Objects.isNull;

public class EnvParser {

    public static List<String> parse(String envValue) {
        if (isNull(envValue)) return List.of();
        envValue = envValue.strip();
        if (envValue.isEmpty()) return List.of();
        return Arrays.asList(envValue.strip().split(","));
    }

    public static List<DnsProfile> parseProfiles() {
        require("DNS", EnvironmentVariables.DNS);
        require("CLIENT_ID", EnvironmentVariables.CLIENT_ID);
        require("AUTH_SECRET", EnvironmentVariables.AUTH_SECRET);
        List<String> dnsList = parse(EnvironmentVariables.DNS);
        List<String> clientIdList = parse(EnvironmentVariables.CLIENT_ID);
        List<String> secretList = parse(EnvironmentVariables.AUTH_SECRET);

        if (clientIdList.size() != secretList.size()) {
            throw new ProcessException("CLIENT_ID values amount and AUTH_SECRET values amount must be equal, but were %s and %s"
                    .formatted(clientIdList.size(), secretList.size()));
        }
        int profilesAmount = clientIdList.size();

        if (dnsList.size() == 1) {
            String[] dnsFiller = new String[profilesAmount];
            Arrays.fill(dnsFiller, dnsList.getFirst());
            dnsList = Arrays.asList(dnsFiller);
        } else if (dnsList.size() != profilesAmount) {
            throw new ProcessException("DNS values amount must be equal to CLIENT_ID values amount or contain exactly one provider");
        }
        ArrayList<DnsProfile> dnsProfiles = new ArrayList<>();
        for (int i = 0; i < profilesAmount; i++) {
            String provider = dnsList.get(i).toUpperCase();
            if (!"NEXTDNS".equals(provider) && !"CLOUDFLARE".equals(provider)) {
                throw new ProcessException("Unsupported DNS provider. Must be CLOUDFLARE or NEXTDNS");
            }
            DnsProfile dnsProfile = DnsProfile.builder()
                    .dnsProvider(provider)
                    .clientId(clientIdList.get(i))
                    .authSecret(secretList.get(i))
                    .number(i + 1)
                    .build();
            dnsProfiles.add(dnsProfile);
        }
        return dnsProfiles;
    }

    private static void require(String name, String value) {
        if (isNull(value) || value.isBlank()) {
            throw new ProcessException("Mandatory environment variable is not provided: " + name);
        }
    }

}
