package com.e2d.odin.domain.organization;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;

/** Un proyecto restringido solo lo ven sus miembros; uno abierto, todos los trabajadores de la empresa. */
public record Project(ProjectId id, CompanyId companyId, String name, boolean restricted) {

    public Project(ProjectId id, CompanyId companyId, String name) {
        this(id, companyId, name, false);
    }

    public Project withName(String newName) {
        return new Project(id, companyId, newName, restricted);
    }

    public Project withRestricted(boolean newRestricted) {
        return new Project(id, companyId, name, newRestricted);
    }
}
