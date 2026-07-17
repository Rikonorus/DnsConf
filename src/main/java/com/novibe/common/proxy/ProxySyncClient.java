package com.novibe.common.proxy;

import com.novibe.common.data_sources.RedirectSourceSnapshot.ProxyAllowlist;

public interface ProxySyncClient {

    void verifyCompatibleContract(ProxyConfiguration configuration);

    Transaction stage(ProxyConfiguration configuration, ProxyAllowlist allowlist);

    void renew(ProxyConfiguration configuration, Transaction transaction);

    void commit(ProxyConfiguration configuration, Transaction transaction);

    void abort(ProxyConfiguration configuration, Transaction transaction);

    record Transaction(String token) {
    }
}
