package com.novibe.common.config;

public class EnvironmentVariables {

    public static final String DNS = System.getenv("DNS");

    public static final String CLIENT_ID = System.getenv("CLIENT_ID");

    public static final String AUTH_SECRET = System.getenv("AUTH_SECRET");

    public static final String BLOCK = System.getenv("BLOCK");

    public static final String REDIRECT = System.getenv("REDIRECT");

    public static final String EXCLUDE_REDIRECT = System.getenv("EXCLUDE_REDIRECT");

    public static final String NEXTDNS_REWRITE_EXCLUSIONS = System.getenv("NEXTDNS_REWRITE_EXCLUSIONS");

}
