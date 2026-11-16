package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/** Comprobaciones reutilizables de lo que nunca debe llevar el registro. */
final class AuditAssertions {

    private static final List<String> FORBIDDEN_KEYS = List.of("password", "contrasena", "text", "content", "question",
            "answer", "body", "title", "email", "name", "key", "token", "secret", "quote");

    private AuditAssertions() {
    }

    /** Ninguna clave de {@code details}, a cualquier nivel, es de contenido, contraseña, clave, nombre o correo. */
    static void assertNoForbiddenFields(JsonNode details) {
        details.properties().forEach(field -> {
            String key = field.getKey().toLowerCase(Locale.ROOT);
            assertThat(FORBIDDEN_KEYS.stream().filter(key::contains)).as("campo prohibido %s", key).isEmpty();
            if (field.getValue().isObject()) {
                assertNoForbiddenFields(field.getValue());
            }
        });
    }
}
