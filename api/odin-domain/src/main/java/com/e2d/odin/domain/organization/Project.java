package com.e2d.odin.domain.organization;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;

/** {@code restricted} es verdadero por defecto: solo lo ven sus miembros. Falso: visible para toda la empresa. */
public record Project(ProjectId id, CompanyId companyId, String name, boolean restricted) {

    public Project withName(String newName) {
        return new Project(id, companyId, newName, restricted);
    }

    public Project withRestricted(boolean newRestricted) {
        return new Project(id, companyId, name, newRestricted);
    }
}
