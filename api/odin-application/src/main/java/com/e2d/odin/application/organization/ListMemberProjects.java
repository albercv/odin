package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Set;

/** Los proyectos de una empresa de los que una persona es miembro. */
public class ListMemberProjects {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final ProjectMemberRepository projectMembers;

    public ListMemberProjects(OrganizationRepository organizations, MembershipRepository memberships,
            ProjectMemberRepository projectMembers) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.projectMembers = projectMembers;
    }

    public List<Project> execute(Actor actor, CompanyId companyId, UserId userId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        memberships.find(userId, companyId)
                .orElseThrow(() -> new NotFoundException("Esa persona no pertenece a esta empresa"));
        Set<ProjectId> memberOf = projectMembers.projectsOf(companyId, userId);
        return organizations.listProjects(companyId).stream().filter(p -> memberOf.contains(p.id())).toList();
    }
}
