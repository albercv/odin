package com.e2d.odin.application.link;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

/**
 * La comprobación única de «puede subir a este proyecto», al pedir el enlace y en cada uso: la capacidad
 * UPLOAD_DOCUMENTS, y un proyecto que existe, está vivo y la persona ve. Un proyecto que no se ve, no existe o está
 * borrado es el mismo 404.
 */
final class ProjectUploadAccess {

    private ProjectUploadAccess() {
    }

    static Project require(Actor actor, OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            ProjectId projectId) {
        var granted = WorkerAccess.require(actor, organizations, projectMembers, Capability.UPLOAD_DOCUMENTS);
        WorkerAccess.requireProject(granted.visibility(), Optional.of(projectId));
        return organizations.findProject(granted.companyId(), projectId)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
    }
}
