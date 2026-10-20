package com.baltic.gateway.platform.persistence;

import java.util.Locale;

/** Builds "contains" patterns for LIKE ... ESCAPE so user input is never a wildcard; compare against lower(column). */
public final class Like {
    public static final char ESCAPE = '\\';

    private Like() {}

    public static String contains(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
