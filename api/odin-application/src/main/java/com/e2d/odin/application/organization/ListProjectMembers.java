package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;

public class ListProjectMembers {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final ProjectMemberRepository projectMembers;

    public ListProjectMembers(OrganizationRepository organizations, UserRepository users,
            ProjectMemberRepository projectMembers) {
        this.organizations = organizations;
        this.users = users;
        this.projectMembers = projectMembers;
    }

    public List<ProjectMemberView> execute(Actor actor, CompanyId companyId, ProjectId projectId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findProject(companyId, projectId)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        return users.findAllById(projectMembers.listMembers(companyId, projectId)).stream()
                .map(user -> new ProjectMemberView(user.id(), user.displayName())).toList();
    }
}
