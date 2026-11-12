package com.e2d.odin.application.shared;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class WorkerAccess {

    /**
     * La empresa del trabajador y sus proyectos, calculados una vez por petición. {@code visibility} son los
     * proyectos vivos que ve (listas y filtros); {@code content} añade los borrados que veía, porque borrar un
     * proyecto no oculta sus documentos ni sus fichas.
     */
    public record Granted(CompanyId companyId, ProjectVisibility visibility, ProjectVisibility content) {
    }

    private WorkerAccess() {
    }

    /** Exige la capacidad y, además, las otras que se pidan (los casos parecidos necesitan también la búsqueda). */
    public static Granted require(Actor actor, OrganizationRepository organizations,
            ProjectMemberRepository projectMembers, Capability capability, Capability... alsoRequired) {
        AccessPolicy.requireWorker(actor);
        CompanyId companyId = actor.requireCompany();
        var company = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        AccessPolicy.requireCapability(company, capability);
        for (Capability other : alsoRequired) {
            AccessPolicy.requireCapability(company, other);
        }
        ProjectVisibility visibility = visibleProjects(actor, companyId, organizations.listProjects(companyId),
                projectMembers);
        return new Granted(companyId, visibility, withDeleted(visibility, actor, companyId, organizations,
                projectMembers));
    }

    /** Los de su empresa visibles para toda ella más aquellos de los que es miembro. */
    public static ProjectVisibility visibleProjects(Actor actor, CompanyId companyId, List<Project> alive,
            ProjectMemberRepository projectMembers) {
        Set<ProjectId> visible = new HashSet<>(projectMembers.projectsOf(companyId, actor.userId()));
        alive.stream().filter(p -> !p.restricted()).map(Project::id).forEach(visible::add);
        return ProjectVisibility.of(visible);
    }

    public static ProjectVisibility withDeleted(ProjectVisibility alive, Actor actor, CompanyId companyId,
            OrganizationRepository organizations, ProjectMemberRepository projectMembers) {
        Set<ProjectId> content = new HashSet<>(alive.projects());
        content.addAll(projectMembers.deletedProjectsOf(companyId, actor.userId()));
        organizations.listDeletedProjects(companyId).stream().filter(p -> !p.restricted()).map(Project::id)
                .forEach(content::add);
        return ProjectVisibility.of(content);
    }

    public static void requireProject(ProjectVisibility visibility, Optional<ProjectId> projectId) {
        projectId.filter(id -> !visibility.includes(id)).ifPresent(id -> {
            throw new NotFoundException("Proyecto no encontrado");
        });
    }

    public static int cap(Optional<Integer> requested, int defaultLimit, int maxLimit) {
        return Math.max(1, Math.min(requested.orElse(defaultLimit), maxLimit));
    }
}
