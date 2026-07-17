package com.novibe.common.util;

import com.novibe.common.proxy.SensitiveValueRedactor;

public class Log {

    public static void global(String msg) {
        IO.println(Color.YELLOW_BOLD + "\n#==#==# " + Color.GREEN_BOLD + safe(msg) + Color.YELLOW_BOLD + " #==#==#\n" + Color.RESET);
    }

    public static void step(String msg) {
        IO.println(Color.BLUE_BOLD + "\n--- " + safe(msg) + Color.RESET);
    }

    public static void io(String msg) {
        IO.println(Color.YELLOW + ">>> " + Color.PURPLE + safe(msg) + Color.RESET);
    }

    public static void fail(String msg) {
        IO.println("\n" + Color.YELLOW_BOLD + "!!!" + Color.RED + " " + safe(msg) + Color.RESET);
    }

    public static void common(String msg) {
        IO.println(safe(msg));
    }

    public static void progress(String msg) {
        IO.print(safe(msg) + "\r");
    }

    private static String safe(String message) {
        return SensitiveValueRedactor.redact(message);
    }

    private static class Color {

        public static final String RESET = "\033[0m";

        public static final String RED = "\033[0;31m";
        public static final String YELLOW = "\033[0;33m";
        public static final String PURPLE = "\033[0;35m";
        public static final String BLUE_BOLD = "\033[1;34m";
        public static final String YELLOW_BOLD = "\033[1;93m";
        public static final String GREEN_BOLD = "\033[1;92m";
    }

}
