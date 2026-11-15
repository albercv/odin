package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminChatKey;

/** Quien llama con una clave de administrador para el chat: la persona (con el ámbito de la clave) y la clave. */
public record ChatAccess(Actor actor, AdminChatKey key) {
}
