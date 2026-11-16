package com.e2d.odin.application.identity;

import java.util.Optional;

/**
 * Formato {@code odin-adm_<prefijo de 8>_<secreto>}. El guion tras «odin» hace imposible que el analizador de la
 * clave de trabajador ({@code odin_...}) la acepte, y el de aquí nunca acepta una {@code odin_...}.
 */
public final class AdminChatToken {

    public record Parsed(String prefix, String secret) {
    }

    public static final int PREFIX_LENGTH = 8;
    private static final String START = "odin-adm_";
    private static final int SECRET_START = START.length() + PREFIX_LENGTH + 1;

    private AdminChatToken() {
    }

    public static String format(String prefix, String secret) {
        return START + prefix + "_" + secret;
    }

    public static boolean isAdminChatToken(String token) {
        return token != null && token.startsWith(START);
    }

    public static Optional<Parsed> parse(String token) {
        boolean wellFormed = isAdminChatToken(token) && token.length() > SECRET_START
                && token.charAt(SECRET_START - 1) == '_';
        return wellFormed ? Optional.of(new Parsed(token.substring(START.length(), SECRET_START - 1),
                token.substring(SECRET_START))) : Optional.empty();
    }
}
