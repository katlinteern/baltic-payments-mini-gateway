package com.baltic.gateway.platform.persistence;

import java.util.Locale;

/** Builds case-insensitive "contains" patterns for {@code LIKE ... ESCAPE '\'} so user input is never a wildcard. */
public final class Like {
    public static final char ESCAPE = '\\';

    private Like() {}

    /** Compare against {@code lower(column)}. */
    public static String contains(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
