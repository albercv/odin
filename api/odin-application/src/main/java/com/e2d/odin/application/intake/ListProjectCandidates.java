package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.CandidateOrigin;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.intake.ProjectRules;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** La lista de proyectos del lote, y el descubrimiento de candidatos en los textos ya leídos cuando no hay lista importada. */
public class ListProjectCandidates {

    private final IntakeRepository intake;
    private final DocumentRepository documents;

    public ListProjectCandidates(IntakeRepository intake, DocumentRepository documents) {
        this.intake = intake;
        this.documents = documents;
    }

    public List<ProjectCandidate> execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatches.require(intake, actor, companyId, batchId);
        return intake.candidates(companyId, batchId);
    }

    /**
     * Propone (o actualiza el recuento de) los candidatos {@code DISCOVERED} con las reglas del estudio. Lo que el
     * administrador ya decidió no se toca, y con una lista importada no se descubre nada.
     */
    public List<ProjectCandidate> discover(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatches.requireListOpen(intake, actor, companyId, batchId);
        List<ProjectCandidate> existing = intake.candidates(companyId, batchId);
        if (existing.stream().anyMatch(c -> c.origin() == CandidateOrigin.IMPORTED)) {
            return existing;
        }
        Map<String, ProjectCandidate> byKey = existing.stream()
                .collect(Collectors.toMap(c -> ProjectRules.normalize(c.name()), Function.identity()));
        Set<String> aliases = existing.stream().flatMap(c -> c.aliases().stream()).collect(Collectors.toSet());
        for (ProjectRules.Discovered found : ProjectRules.discover(documents.intakeTexts(companyId, batchId))) {
            ProjectCandidate current = byKey.get(ProjectRules.normalize(found.name()));
            if (current == null && aliases.contains(ProjectRules.normalize(found.name()))) {
                continue; // ya se renombró o se juntó: no resucita
            }
            if (current == null) {
                intake.save(new ProjectCandidate(companyId, batchId, found.name(), CandidateOrigin.DISCOVERED,
                        found.documentCount(), DecisionState.PROPOSED, Optional.empty()));
            } else if (current.state() == DecisionState.PROPOSED) {
                intake.save(new ProjectCandidate(companyId, batchId, current.name(), current.origin(),
                        found.documentCount(), current.state(), current.projectId(), current.aliases()));
            }
        }
        return intake.candidates(companyId, batchId);
    }
}
