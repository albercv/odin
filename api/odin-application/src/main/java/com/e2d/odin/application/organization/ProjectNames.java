package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

final class ProjectNames {

    private ProjectNames() {
    }

    static void requireFree(OrganizationRepository organizations, CompanyId companyId, String name,
            Optional<ProjectId> ignored) {
        boolean taken = organizations.listProjects(companyId).stream()
                .filter(p -> ignored.isEmpty() || !p.id().equals(ignored.get()))
                .anyMatch(p -> p.name().equalsIgnoreCase(name));
        if (taken) {
            throw new ConflictException("Ya existe un proyecto con ese nombre");
        }
    }
}
