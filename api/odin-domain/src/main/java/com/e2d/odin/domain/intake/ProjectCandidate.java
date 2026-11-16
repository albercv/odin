package com.e2d.odin.domain.intake;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Un nombre de la lista de proyectos del lote. {@code aliases}: grafías normalizadas que la clasificación también busca. */
public record ProjectCandidate(CompanyId companyId, IntakeBatchId batchId, String name, CandidateOrigin origin,
        int documentCount, DecisionState state, Optional<ProjectId> projectId, List<String> aliases) {

    public ProjectCandidate {
        aliases = List.copyOf(aliases);
    }

    public ProjectCandidate(CompanyId companyId, IntakeBatchId batchId, String name, CandidateOrigin origin,
            int documentCount, DecisionState state, Optional<ProjectId> projectId) {
        this(companyId, batchId, name, origin, documentCount, state, projectId, List.of());
    }

    public ProjectCandidate confirm(ProjectId project) {
        requireProposed();
        return new ProjectCandidate(companyId, batchId, name, origin, documentCount, DecisionState.CONFIRMED,
                Optional.of(project), aliases);
    }

    public ProjectCandidate reject() {
        requireProposed();
        return new ProjectCandidate(companyId, batchId, name, origin, documentCount, DecisionState.REJECTED,
                Optional.empty(), aliases);
    }

    /** El mismo candidato con otro nombre; el anterior, normalizado, pasa a ser alias. */
    public ProjectCandidate renamed(String newName, String oldKey) {
        requireProposed();
        return new ProjectCandidate(companyId, batchId, newName, origin, documentCount, state, projectId,
                withAlias(aliases, oldKey, ProjectRules.normalize(newName)));
    }

    /** Este candidato con el otro dentro: sus alias, su nombre (normalizado) y sus documentos pasan a este. */
    public ProjectCandidate absorbing(ProjectCandidate other) {
        requireProposed();
        other.requireProposed();
        Set<String> all = new LinkedHashSet<>(aliases);
        all.addAll(other.aliases);
        List<String> merged = withAlias(List.copyOf(all), ProjectRules.normalize(other.name),
                ProjectRules.normalize(name));
        return new ProjectCandidate(companyId, batchId, name, origin, documentCount + other.documentCount, state,
                projectId, merged);
    }

    private static List<String> withAlias(List<String> aliases, String alias, String own) {
        Set<String> all = new LinkedHashSet<>(aliases);
        all.add(alias);
        all.remove(own);
        return List.copyOf(all);
    }

    private void requireProposed() {
        if (state != DecisionState.PROPOSED) {
            throw new ConflictException("El candidato ya está decidido");
        }
    }
}
