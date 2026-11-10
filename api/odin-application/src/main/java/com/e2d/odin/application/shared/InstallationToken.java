package com.e2d.odin.application.shared;

import com.e2d.odin.domain.shared.ForbiddenException;

public final class InstallationToken {

    private InstallationToken() {
    }

    public static void require(InstallationSettings settings, String token) {
        boolean valid = token != null && !token.isEmpty() && !settings.setupToken().isEmpty()
                && SecretComparison.equal(token, settings.setupToken());
        if (!valid) {
            throw new ForbiddenException("Código de instalación no válido");
        }
    }
}
