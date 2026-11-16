package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.intake.ProjectRules;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Renombrar un candidato PROPOSED y juntar varios en uno, con la lista abierta. El nombre que se deja de usar queda como
 * alias (la clasificación también lo busca). Renombrar a un nombre que ya tiene otro candidato PROPOSED los junta.
 */
public class EditProjectCandidates {

    private final IntakeRepository intake;
    private final OrganizationRepository organizations;

    public EditProjectCandidates(IntakeRepository intake, OrganizationRepository organizations) {
        this.intake = intake;
        this.organizations = organizations;
    }

    public List<ProjectCandidate> rename(Actor actor, CompanyId companyId, IntakeBatchId batchId, String name,
            String newName) {
        IntakeBatches.requireListOpen(intake, actor, companyId, batchId);
        String clean = newName == null ? "" : newName.strip();
        String newKey = ProjectRules.normalize(clean);
        if (newKey.isEmpty()) {
            throw new ValidationException("El nuevo nombre no puede estar vacío");
        }
        List<ProjectCandidate> all = intake.candidates(companyId, batchId);
        ProjectCandidate candidate = find(all, name);
        requireProposed(candidate);
        Optional<ProjectCandidate> same = all.stream().filter(c -> !c.name().equals(name))
                .filter(c -> ProjectRules.normalize(c.name()).equals(newKey) || c.aliases().contains(newKey)).findFirst();
        if (same.isPresent()) {
            requireProposed(same.get());
            join(companyId, batchId, same.get(), List.of(candidate));
        } else {
            if (!ProjectRules.normalize(name).equals(newKey) && projectNameTaken(companyId, newKey)) {
                throw new ConflictException("Ya existe un proyecto llamado «" + clean + "»: únelo a él");
            }
            intake.deleteCandidate(companyId, batchId, name);
            intake.save(candidate.renamed(clean, ProjectRules.normalize(name)));
        }
        return intake.candidates(companyId, batchId);
    }

    public List<ProjectCandidate> merge(Actor actor, CompanyId companyId, IntakeBatchId batchId, List<String> names,
            String into) {
        IntakeBatches.requireListOpen(intake, actor, companyId, batchId);
        Set<String> distinct = names == null ? Set.of() : new HashSet<>(names);
        if (distinct.isEmpty() || into == null || distinct.contains(into)) {
            throw new ValidationException("Elige uno o más candidatos y otro en el que juntarlos");
        }
        List<ProjectCandidate> all = intake.candidates(companyId, batchId);
        ProjectCandidate target = find(all, into);
        requireProposed(target);
        List<ProjectCandidate> sources = distinct.stream().map(n -> find(all, n)).toList();
        sources.forEach(EditProjectCandidates::requireProposed);
        join(companyId, batchId, target, sources);
        return intake.candidates(companyId, batchId);
    }

    private void join(CompanyId companyId, IntakeBatchId batchId, ProjectCandidate target, List<ProjectCandidate> sources) {
        ProjectCandidate joined = target;
        for (ProjectCandidate source : sources) {
            joined = joined.absorbing(source);
            intake.deleteCandidate(companyId, batchId, source.name());
        }
        intake.save(joined);
    }

    private static ProjectCandidate find(List<ProjectCandidate> all, String name) {
        return all.stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new NotFoundException("Candidato no encontrado: " + name));
    }

    private static void requireProposed(ProjectCandidate c) {
        if (c.state() != DecisionState.PROPOSED) {
            throw new ConflictException("El candidato ya está decidido: " + c.name());
        }
    }

    private boolean projectNameTaken(CompanyId companyId, String key) {
        return organizations.listProjects(companyId).stream().map(Project::name)
                .anyMatch(existing -> ProjectRules.normalize(existing).equals(key));
    }
}
