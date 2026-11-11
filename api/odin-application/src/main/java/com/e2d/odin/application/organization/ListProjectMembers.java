package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        List<UserId> order = projectMembers.listMembers(companyId, projectId);
        Map<UserId, User> people = users.findAllById(order).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
        return order.stream().filter(people::containsKey)
                .map(id -> new ProjectMemberView(id, people.get(id).displayName())).toList();
    }
}
