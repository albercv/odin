package com.e2d.odin.application.shared;

import com.e2d.odin.domain.shared.ValidationException;
import java.util.Locale;

public final class Validation {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private Validation() {
    }

    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Falta " + field);
        }
        return value.trim();
    }

    public static String normalizeEmail(String email) {
        return requireText(email, "el correo").toLowerCase(Locale.ROOT);
    }

    public static String requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("La contraseña necesita al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        return password;
    }
}
