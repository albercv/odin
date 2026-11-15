package com.e2d.odin.application.identity;

import java.time.Instant;
import java.util.UUID;

/** La clave en claro solo existe aquí: se muestra una vez y no se guarda. */
public record IssuedAdminChatKey(UUID id, String prefix, String token, Instant expiresAt) {
}
