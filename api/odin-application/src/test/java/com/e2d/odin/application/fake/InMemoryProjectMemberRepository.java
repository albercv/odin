package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryProjectMemberRepository implements ProjectMemberRepository {

    private record Link(ProjectId projectId, UserId userId) {
    }

    private final InMemoryOrganizationRepository organizations;
    private final Set<Link> links = new LinkedHashSet<>();

    public InMemoryProjectMemberRepository(InMemoryOrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Override
    public void add(ProjectId projectId, UserId userId) {
        links.add(new Link(projectId, userId));
    }

    @Override
    public void remove(ProjectId projectId, UserId userId) {
        links.remove(new Link(projectId, userId));
    }

    @Override
    public List<UserId> listMembers(ProjectId projectId) {
        return links.stream().filter(l -> l.projectId().equals(projectId)).map(Link::userId).toList();
    }

    @Override
    public Set<ProjectId> restrictedProjectsOf(CompanyId companyId, UserId userId) {
        return links.stream().filter(l -> l.userId().equals(userId))
                .map(l -> organizations.findProject(companyId, l.projectId())).flatMap(java.util.Optional::stream)
                .filter(Project::restricted).map(Project::id).collect(Collectors.toSet());
    }
}
