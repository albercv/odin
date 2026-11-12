package com.e2d.odin.application.organization;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Set;

public final class ProjectMembers {

    private ProjectMembers() {
    }

    /** Solo los trabajadores pertenecen a un proyecto. */
    public static void requireWorker(Set<Role> roles) {
        if (!roles.contains(Role.WORKER)) {
            throw new ValidationException("Solo un trabajador de la empresa puede ser miembro");
        }
    }
}
