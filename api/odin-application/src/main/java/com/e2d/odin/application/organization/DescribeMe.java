package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;

public class DescribeMe {

    private final OrganizationRepository organizations;

    private final ProjectMemberRepository projectMembers;

    public DescribeMe(OrganizationRepository organizations, ProjectMemberRepository projectMembers) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
    }

    public MeView execute(Actor actor) {
        CompanyId companyId = actor.requireCompany();
        Company company = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        var visibility = WorkerAccess.visibleProjects(actor, companyId, organizations, projectMembers);
        return new MeView(actor.userId(), actor.displayName(), actor.role(), company,
                organizations.listProjects(companyId).stream().filter(p -> visibility.includes(p.id())).toList());
    }
}
