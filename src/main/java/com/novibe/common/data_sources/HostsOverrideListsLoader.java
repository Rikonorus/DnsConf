package com.novibe.common.data_sources;

import com.novibe.common.util.DataParser;
import com.novibe.common.util.DataParser.HostsLine;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

@Service
public class HostsOverrideListsLoader extends ListLoader<HostsOverrideListsLoader.BypassRoute> {

    public record BypassRoute(String ip, String website) {
    }

    @Override
    protected String listType() {
        return "Override";
    }

    @Override
    protected Predicate<HostsLine> filterRelatedLines() {
        return line -> !HostsBlockListsLoader.isBlockIp(line.ip());
    }

    @Override
    protected BypassRoute toObject(HostsLine line) {
        return new BypassRoute(line.ip(), DataParser.removeWWW(line.domain()));
    }

}
