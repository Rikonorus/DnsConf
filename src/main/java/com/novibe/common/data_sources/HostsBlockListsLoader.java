package com.novibe.common.data_sources;

import com.novibe.common.base_structures.HostsLine;
import com.novibe.common.util.DataParser;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

import static java.util.Objects.nonNull;

@Service
public class HostsBlockListsLoader extends ListLoader<String> {

    private static final String[] BLOCK_IPS = {"0.0.0.0", "127.0.0.1", "::1"};
    private static final String[] LOCALHOST_NAME = {"localhost", "ip6-localhost"};

    @Override
    protected String listType() {
        return "Block";
    }

    @Override
    protected Predicate<HostsLine> filterRelatedLines() {
        return line -> line.hasDomainOnly()
                || (line.hasIpAndDomain() && isBlockIp(line.ip()) && !isLocalhost(line.domain()));
    }

    @Override
    protected String toObject(HostsLine line) {
        return line.domain();
    }

    public static boolean isBlock(String line) {
        HostsLine hostsLine = DataParser.parseHostsLine(line);
        return nonNull(hostsLine) && (hostsLine.hasDomainOnly()
                || (hostsLine.hasIpAndDomain() && isBlockIp(hostsLine.ip())));
    }

    public static boolean isBlockIp(String ip) {
        for (String blockIp : BLOCK_IPS) {
            if (blockIp.equals(ip)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalhost(String domain) {
        for (String localhost : LOCALHOST_NAME) {
            if (domain.equals(localhost))
                return true;
        }
        return false;
    }

}
