package com.e2d.odin.adapter.web.security;

/** Nombre del atributo de la petición con el Actor resuelto; los controladores lo reciben con @RequestAttribute. */
public final class CurrentActor {

    public static final String ATTRIBUTE = "odin.actor";
    /** La clave de administrador del chat resuelta, solo en las rutas que la aceptan. */
    public static final String CHAT_ACCESS = "odin.chatAccess";

    private CurrentActor() {
    }
}
