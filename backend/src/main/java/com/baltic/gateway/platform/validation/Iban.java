package com.baltic.gateway.platform.validation;

import java.util.Locale;

public final class Iban {
    private Iban() {}

    public static String normalize(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
