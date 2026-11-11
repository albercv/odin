package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.PersonCodeGenerator;
import com.e2d.odin.application.port.UserRepository;

final class PersonCodes {

    static final int MAX_ATTEMPTS = 5;
    private static final System.Logger LOG = System.getLogger(PersonCodes.class.getName());

    private PersonCodes() {
    }

    /** Un código que ninguna persona, ni borrada, tiene aún; si en 5 intentos no lo encuentra, falla. */
    static String next(PersonCodeGenerator generator, UserRepository users) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = generator.next();
            if (!users.personCodeExists(code)) {
                return code;
            }
        }
        LOG.log(System.Logger.Level.ERROR, "No se encontró un código de persona libre en {0} intentos", MAX_ATTEMPTS);
        throw new IllegalStateException("No se pudo generar un código de persona único");
    }
}
