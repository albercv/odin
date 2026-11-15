package com.e2d.odin.domain.organization;

import com.e2d.odin.domain.shared.ValidationException;
import java.util.regex.Pattern;

/** Cómo llama una empresa a sus proyectos; {@code feminine} concuerda el texto («Nueva comunidad»). */
public record ProjectLabel(String singular, String plural, boolean feminine) {

    public static final ProjectLabel DEFAULT = new ProjectLabel("proyecto", "proyectos", false);

    private static final Pattern VALID_WORD = Pattern.compile("[\\p{L} -]{2,30}");

    public static ProjectLabel of(String singular, String plural, boolean feminine) {
        return new ProjectLabel(normalize(singular), normalize(plural), feminine);
    }

    private static String normalize(String word) {
        String trimmed = word == null ? "" : word.trim().replaceAll("\\s+", " ");
        if (!VALID_WORD.matcher(trimmed).matches()) {
            throw new ValidationException("El nombre de los proyectos tiene de 2 a 30 letras, espacios o guiones");
        }
        boolean acronym = trimmed.equals(trimmed.toUpperCase()) && !trimmed.equals(trimmed.toLowerCase());
        return acronym ? trimmed : Character.toLowerCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
