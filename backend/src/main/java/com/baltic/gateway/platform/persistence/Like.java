package com.baltic.gateway.platform.persistence;

import java.util.Locale;

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
