package com.e2d.odin.domain.search;

import java.util.Locale;

public final class QuoteMatcher {

    private QuoteMatcher() {
    }

    public static boolean containsLiteral(String pageText, String quote) {
        if (pageText == null || quote == null || quote.isBlank()) {
            return false;
        }
        return normalize(pageText).contains(normalize(quote));
    }

    private static String normalize(String text) {
        return text.replaceAll("[\\s\\p{Z}]+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
