package com.novibe.common.data_sources;

/** One valid, redirectable hosts entry from the immutable source snapshot. */
public record RedirectSourceRecord(String ip, String sourceHostname, String effectiveHostname) {
}
