package com.e2d.odin.application.intake;

import com.e2d.odin.application.intake.ProjectDecision.Action;
import com.e2d.odin.application.organization.CreateProject;
import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El administrador confirma, une o rechaza candidatos (D2: ninguno cambia nada hasta que lo decide). Crear usa
 * {@link CreateProject}; unir enlaza un proyecto que ya existe y no crea ninguno. Todo o nada: se valida todo antes
 * de escribir.
 */
public class ConfirmProjectCandidates {

    private final IntakeRepository intake;
    private final OrganizationRepository organizations;
    private final CreateProject createProject;
    private final AuditLog audit;

    public ConfirmProjectCandidates(IntakeRepository intake, OrganizationRepository organizations,
            CreateProject createProject, AuditLog audit) {
        this.audit = audit;
        this.intake = intake;
        this.organizations = organizations;
        this.createProject = createProject;
    }

    public List<ProjectCandidate> execute(Actor actor, CompanyId companyId, IntakeBatchId batchId,
            List<ProjectDecision> decisions) {
        IntakeBatches.requireListOpen(intake, actor, companyId, batchId);
        Map<String, ProjectCandidate> candidates = intake.candidates(companyId, batchId).stream()
                .collect(Collectors.toMap(ProjectCandidate::name, Function.identity()));
        decisions.forEach(d -> validate(companyId, candidates, d));
        requireDistinct(decisions);
        for (ProjectDecision d : decisions) {
            ProjectCandidate candidate = candidates.get(d.name());
            intake.save(switch (d.action()) {
                case CREATE -> candidate.confirm(createProject.execute(actor, companyId, d.name()).id());
                case MERGE -> candidate.confirm(d.projectId().orElseThrow());
                case REJECT -> candidate.reject();
            });
        }
        audit.record(AuditSupport.onIntake(AuditEntry.by(actor, AuditAction.INTAKE_PROJECTS_DECIDED), companyId, batchId)
                .with("count", decisions.size()).with("created", countOf(decisions, Action.CREATE))
                .with("merged", countOf(decisions, Action.MERGE)).with("rejected", countOf(decisions, Action.REJECT)));
        return intake.candidates(companyId, batchId);
    }

    private static int countOf(List<ProjectDecision> decisions, Action action) {
        return (int) decisions.stream().filter(d -> d.action() == action).count();
    }

    private void validate(CompanyId companyId, Map<String, ProjectCandidate> candidates, ProjectDecision d) {
        ProjectCandidate candidate = candidates.get(d.name());
        if (candidate == null) {
            throw new NotFoundException("Candidato no encontrado: " + d.name());
        }
        if (candidate.state() != DecisionState.PROPOSED) {
            throw new ConflictException("El candidato ya está decidido: " + d.name());
        }
        if (d.action() == Action.MERGE) {
            requireProject(companyId, d);
        } else if (d.action() == Action.CREATE && projectNameTaken(companyId, d.name())) {
            throw new ConflictException("Ya existe un proyecto llamado «" + d.name() + "»: únelo a él");
        }
    }

    private void requireProject(CompanyId companyId, ProjectDecision d) {
        if (d.projectId().isEmpty()) {
            throw new ValidationException("Falta el proyecto al que unir «" + d.name() + "»");
        }
        organizations.findProject(companyId, d.projectId().get())
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
    }

    private boolean projectNameTaken(CompanyId companyId, String name) {
        return organizations.listProjects(companyId).stream().map(Project::name)
                .anyMatch(existing -> existing.equalsIgnoreCase(name));
    }

    private static void requireDistinct(List<ProjectDecision> decisions) {
        Set<String> seen = new HashSet<>();
        if (!decisions.stream().allMatch(d -> seen.add(d.name()))) {
            throw new ValidationException("Un candidato solo puede decidirse una vez por petición");
        }
    }
}
