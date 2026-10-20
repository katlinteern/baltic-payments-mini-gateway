package com.baltic.gateway.payments.domain.payee;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Verification of Payee: ignores case, diacritics, punctuation and word order. */
public final class PayeeNameMatcher {
    private static final double CLOSE_MATCH_SIMILARITY = 0.85;
    private static final int MIN_PARTIAL_TOKEN_LENGTH = 3;

    private PayeeNameMatcher() {}

    public static NameCheckResult check(String providedName, String accountHolderName) {
        if (accountHolderName == null || accountHolderName.isBlank()) return NameCheckResult.NOT_AVAILABLE;
        Set<String> provided = tokens(providedName);
        Set<String> holder = tokens(accountHolderName);
        if (provided.isEmpty() || holder.isEmpty()) return NameCheckResult.NO_MATCH;
        if (provided.equals(holder)) return NameCheckResult.MATCH;
        boolean partial = holder.containsAll(provided) && provided.stream().anyMatch(t -> t.length() >= MIN_PARTIAL_TOKEN_LENGTH);
        if (partial) return NameCheckResult.CLOSE_MATCH;
        String left = String.join(" ", provided);
        String right = String.join(" ", holder);
        return similarity(left, right) >= CLOSE_MATCH_SIMILARITY ? NameCheckResult.CLOSE_MATCH : NameCheckResult.NO_MATCH;
    }

    private static Set<String> tokens(String name) {
        if (name == null) return Set.of();
        String folded = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (folded.isEmpty()) return Set.of();
        return Arrays.stream(folded.split(" ")).collect(Collectors.toCollection(TreeSet::new));
    }

    private static double similarity(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 1.0 : 1.0 - (double) levenshtein(a, b) / longest;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
