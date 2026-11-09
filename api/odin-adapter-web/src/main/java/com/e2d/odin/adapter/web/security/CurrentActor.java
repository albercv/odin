package com.e2d.odin.adapter.web.security;

/** Nombre del atributo de la petición con el Actor resuelto; los controladores lo reciben con @RequestAttribute. */
public final class CurrentActor {

    public static final String ATTRIBUTE = "odin.actor";

    private CurrentActor() {
    }
}
