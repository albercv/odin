package com.e2d.odin.domain.topic;

import java.text.Normalizer;
import java.util.Locale;

/** Compara nombres de tema sin tildes, sin mayúsculas y con los espacios juntos, como cotejaba lo medido. */
public final class TopicNames {

    private TopicNames() {
    }

    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").strip();
    }

    public static boolean contains(String name, String other) {
        return normalize(name).contains(normalize(other));
    }
}
