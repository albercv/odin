package com.e2d.odin.application.link;

import com.e2d.odin.domain.shared.ValidationException;
import java.time.Duration;

/** La dirección de Odín en la red local, la duración de los enlaces y el tope de ficheros de uno de subida. */
public record LinkSettings(String publicUrl, Duration ttl) {

    public static final int MAX_UPLOADS = 20;
    static final String MISSING_URL = "Falta configurar la dirección de Odín en la red local; avisa al administrador";

    public LinkSettings {
        publicUrl = publicUrl == null ? "" : publicUrl.strip().replaceAll("/+$", "");
    }

    /** Sin dirección configurada no se inventa ninguna. */
    String urlOf(String path) {
        if (publicUrl.isEmpty()) {
            throw new ValidationException(MISSING_URL);
        }
        return publicUrl + path;
    }
}
