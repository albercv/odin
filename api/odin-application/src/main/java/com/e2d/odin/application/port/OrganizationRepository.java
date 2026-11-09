package com.e2d.odin.application.port;

import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Group;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    Optional<Group> findGroup();

    void saveGroup(Group group);

    void saveCompany(Company company);

    Optional<Company> findCompany(CompanyId id);

    List<Company> listCompanies(CompanyScope scope);

    void saveProject(Project project);

    Optional<Project> findProject(CompanyId companyId, ProjectId id);

    List<Project> listProjects(CompanyId companyId);
}
