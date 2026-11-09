package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Group;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryOrganizationRepository implements OrganizationRepository {

    private Group group;
    private final Map<CompanyId, Company> companies = new LinkedHashMap<>();
    private final Map<ProjectId, Project> projects = new LinkedHashMap<>();

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
}
