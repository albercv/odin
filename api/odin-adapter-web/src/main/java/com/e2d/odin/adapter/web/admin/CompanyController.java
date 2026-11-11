package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.organization.CreateCompany;
import com.e2d.odin.application.organization.CreateProject;
import com.e2d.odin.application.organization.DeleteProject;
import com.e2d.odin.application.organization.ListCompanies;
import com.e2d.odin.application.organization.CompanyChanges;
import com.e2d.odin.application.organization.ListProjects;
import com.e2d.odin.application.organization.ProjectChanges;
import com.e2d.odin.application.organization.SetCompanyCapabilities;
import com.e2d.odin.application.organization.UpdateCompany;
import com.e2d.odin.application.organization.UpdateProject;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/companies")
public class CompanyController {

    private final CreateCompany createCompany;
    private final ListCompanies listCompanies;
    private final UpdateCompany updateCompany;
    private final SetCompanyCapabilities setCapabilities;
    private final CreateProject createProject;
    private final ListProjects listProjects;
    private final UpdateProject updateProject;
    private final DeleteProject deleteProject;

    public CompanyController(CreateCompany createCompany, ListCompanies listCompanies, UpdateCompany updateCompany,
            SetCompanyCapabilities setCapabilities, CreateProject createProject, ListProjects listProjects,
            UpdateProject updateProject, DeleteProject deleteProject) {
        this.createCompany = createCompany;
        this.listCompanies = listCompanies;
        this.updateCompany = updateCompany;
        this.setCapabilities = setCapabilities;
        this.createProject = createProject;
        this.listProjects = listProjects;
        this.updateProject = updateProject;
        this.deleteProject = deleteProject;
    }

    @GetMapping
    public List<Responses.CompanyView> list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor) {
        return listCompanies.execute(actor).stream().map(Responses.CompanyView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.CompanyView create(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.NewCompany request) {
        return Responses.CompanyView.from(createCompany.execute(actor, request.name(), request.code()));
    }

    @PatchMapping("/{companyId}")
    public Responses.CompanyView update(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.UpdateCompany request) {
        CompanyChanges changes = new CompanyChanges(Optional.ofNullable(request.name()),
                Optional.ofNullable(request.code()));
        return Responses.CompanyView.from(updateCompany.execute(actor, new CompanyId(companyId), changes));
    }

    @PutMapping("/{companyId}/capabilities")
    public Responses.CompanyView capabilities(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.Capabilities request) {
        if (request.capabilities() == null) {
            throw new ValidationException("Faltan las capacidades");
        }
        return Responses.CompanyView.from(
                setCapabilities.execute(actor, new CompanyId(companyId), request.capabilities()));
    }

    @GetMapping("/{companyId}/projects")
    public List<Responses.ProjectView> projects(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId) {
        return listProjects.execute(actor, new CompanyId(companyId)).stream().map(Responses.ProjectView::from)
                .toList();
    }

    @PostMapping("/{companyId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.ProjectView createProject(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.Name request) {
        return Responses.ProjectView.from(createProject.execute(actor, new CompanyId(companyId), request.name()));
    }

    @PatchMapping("/{companyId}/projects/{projectId}")
    public Responses.ProjectView updateProject(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID projectId,
            @RequestBody Requests.UpdateProject request) {
        ProjectChanges changes = new ProjectChanges(Optional.ofNullable(request.name()),
                Optional.ofNullable(request.restricted()));
        return Responses.ProjectView.from(updateProject.execute(actor, new CompanyId(companyId),
                new ProjectId(projectId), changes));
    }

    @DeleteMapping("/{companyId}/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID projectId) {
        deleteProject.execute(actor, new CompanyId(companyId), new ProjectId(projectId));
    }
}
