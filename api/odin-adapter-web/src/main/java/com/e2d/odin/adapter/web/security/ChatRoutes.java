package com.e2d.odin.adapter.web.security;

/** Las rutas donde vale la clave de administrador para el chat. En ninguna otra se acepta. */
final class ChatRoutes {

    private static final String ACTIVITY = "/v1/activity";
    private static final String ME = "/v1/me";

    private ChatRoutes() {
    }

    static boolean accepts(String path) {
        return chatOnly(path) || ME.equals(path);
    }

    /** Solo la clave del chat; una clave de trabajador aquí es un 403. */
    static boolean chatOnly(String path) {
        return ACTIVITY.equals(path);
    }
}
