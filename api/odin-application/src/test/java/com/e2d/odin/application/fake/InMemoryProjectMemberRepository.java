package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryProjectMemberRepository implements ProjectMemberRepository {

    private final InMemoryOrganizationRepository organizations;
    private final Map<ProjectId, Set<UserId>> members = new LinkedHashMap<>();

    public InMemoryProjectMemberRepository(InMemoryOrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Override
    public void add(ProjectId projectId, UserId userId) {
        members.computeIfAbsent(projectId, id -> new LinkedHashSet<>()).add(userId);
    }

    @Override
    public void remove(ProjectId projectId, UserId userId) {
        members.getOrDefault(projectId, Set.of()).remove(userId);
    }

    @Override
    public List<UserId> listMembers(ProjectId projectId) {
        return List.copyOf(members.getOrDefault(projectId, Set.of()));
    }

    @Override
    public Set<ProjectId> projectsOf(CompanyId companyId, UserId userId) {
        return organizations.listProjects(companyId).stream().map(p -> p.id())
                .filter(id -> members.getOrDefault(id, Set.of()).contains(userId)).collect(Collectors.toSet());
    }
}
