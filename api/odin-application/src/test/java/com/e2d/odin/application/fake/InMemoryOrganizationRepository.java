package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Group;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryOrganizationRepository implements OrganizationRepository {

    private Group group;
    private final Map<CompanyId, Company> companies = new LinkedHashMap<>();
    private final Map<ProjectId, Project> projects = new LinkedHashMap<>();
    private final Map<ProjectId, Project> deletedProjects = new LinkedHashMap<>();

    @Override
    public Optional<Group> findGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public void saveGroup(Group g) {
        group = g;
    }

    @Override
    public void saveCompany(Company c) {
        companies.put(c.id(), c);
    }

    @Override
    public Optional<Company> findCompany(CompanyId id) {
        return Optional.ofNullable(companies.get(id));
    }

    @Override
    public Optional<Company> findCompanyByCode(String code) {
        return companies.values().stream().filter(c -> c.code().equals(code)).findFirst();
    }

    @Override
    public List<Company> listCompanies(CompanyScope scope) {
        return companies.values().stream().filter(c -> scope.includes(c.id())).toList();
    }

    @Override
    public void saveProject(Project p) {
        projects.put(p.id(), p);
    }

    @Override
    public Optional<Project> findProject(CompanyId companyId, ProjectId id) {
        return Optional.ofNullable(projects.get(id)).filter(p -> p.companyId().equals(companyId));
    }

    @Override
    public List<Project> listProjects(CompanyId companyId) {
        return projects.values().stream().filter(p -> p.companyId().equals(companyId)).toList();
    }

    @Override
    public void deleteProject(CompanyId companyId, ProjectId id, Instant at) {
        findProject(companyId, id).ifPresent(p -> deletedProjects.put(id, projects.remove(id)));
    }

    @Override
    public List<Project> listDeletedProjects(CompanyId companyId) {
        return deletedProjects.values().stream().filter(p -> p.companyId().equals(companyId)).toList();
    }

    /** Ayuda de las pruebas: la empresa de un proyecto, esté o no borrado. */
    public CompanyId companyOf(ProjectId id) {
        Project project = projects.getOrDefault(id, deletedProjects.get(id));
        return project == null ? null : project.companyId();
    }
}
