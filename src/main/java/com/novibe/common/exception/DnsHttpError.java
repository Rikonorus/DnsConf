package com.novibe.common.exception;

import lombok.Getter;

import java.net.http.HttpResponse;

@Getter
public class DnsHttpError extends RuntimeException {

    private final int code;

    public DnsHttpError(HttpResponse<?> response, com.novibe.common.base_structures.Jsonable requestPayload) {
        super("DNS provider request failed with HTTP " + response.statusCode());
        this.code = response.statusCode();
    }

}
