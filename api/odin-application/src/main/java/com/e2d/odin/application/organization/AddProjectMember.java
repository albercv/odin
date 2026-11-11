package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Set;

public class AddProjectMember {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final ProjectMemberRepository projectMembers;

    public AddProjectMember(OrganizationRepository organizations, MembershipRepository memberships,
            ProjectMemberRepository projectMembers) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.projectMembers = projectMembers;
    }

    public void execute(Actor actor, CompanyId companyId, ProjectId projectId, UserId userId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findProject(companyId, projectId)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        Set<Role> roles = memberships.find(userId, companyId).filter(Membership::active).map(Membership::roles)
                .orElse(Set.of());
        ProjectMembers.requireWorker(roles);
        projectMembers.add(projectId, userId);
    }
}
