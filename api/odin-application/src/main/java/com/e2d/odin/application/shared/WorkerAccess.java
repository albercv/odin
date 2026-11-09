package com.e2d.odin.application.shared;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

public final class WorkerAccess {

    private WorkerAccess() {
    }

    public static CompanyId require(Actor actor, OrganizationRepository organizations, Capability capability) {
        AccessPolicy.requireWorker(actor);
        CompanyId companyId = actor.requireCompany();
        var company = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        AccessPolicy.requireCapability(company, capability);
        return companyId;
    }

    public static void requireProject(OrganizationRepository organizations, CompanyId companyId,
            Optional<ProjectId> projectId) {
        projectId.ifPresent(id -> organizations.findProject(companyId, id)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado")));
    }

    public static int cap(Optional<Integer> requested, int defaultLimit, int maxLimit) {
        return Math.max(1, Math.min(requested.orElse(defaultLimit), maxLimit));
    }
}
