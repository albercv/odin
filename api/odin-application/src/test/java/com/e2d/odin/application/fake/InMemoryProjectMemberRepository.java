package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.organization.Project;
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

    /** Ayuda de las pruebas: usa la empresa con la que se creó el proyecto. */
    public void add(ProjectId projectId, UserId userId) {
        add(organizations.companyOf(projectId), projectId, userId);
    }

    public List<UserId> listMembers(ProjectId projectId) {
        return listMembers(organizations.companyOf(projectId), projectId);
    }

    @Override
    public void add(CompanyId companyId, ProjectId projectId, UserId userId) {
        if (companyId != null && companyId.equals(organizations.companyOf(projectId))) {
            members.computeIfAbsent(projectId, id -> new LinkedHashSet<>()).add(userId);
        }
    }

    @Override
    public void remove(CompanyId companyId, ProjectId projectId, UserId userId) {
        if (companyId != null && companyId.equals(organizations.companyOf(projectId))) {
            members.getOrDefault(projectId, Set.of()).remove(userId);
        }
    }

    @Override
    public void removeAllOf(CompanyId companyId, UserId userId) {
        members.forEach((projectId, users) -> {
            if (companyId.equals(organizations.companyOf(projectId))) {
                users.remove(userId);
            }
        });
    }

    @Override
    public List<UserId> listMembers(CompanyId companyId, ProjectId projectId) {
        boolean ofCompany = companyId != null && companyId.equals(organizations.companyOf(projectId));
        return ofCompany ? List.copyOf(members.getOrDefault(projectId, Set.of())) : List.of();
    }

    @Override
    public Set<ProjectId> projectsOf(CompanyId companyId, UserId userId) {
        return memberOf(organizations.listProjects(companyId), userId);
    }

    @Override
    public Set<ProjectId> deletedProjectsOf(CompanyId companyId, UserId userId) {
        return memberOf(organizations.listDeletedProjects(companyId), userId);
    }

    private Set<ProjectId> memberOf(List<Project> projects, UserId userId) {
        return projects.stream().map(Project::id)
                .filter(id -> members.getOrDefault(id, Set.of()).contains(userId)).collect(Collectors.toSet());
    }
}
