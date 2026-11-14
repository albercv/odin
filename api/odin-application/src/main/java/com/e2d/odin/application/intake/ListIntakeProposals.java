package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Las propuestas por decidir del lote, agrupadas por tipo, confianza (primero las seguras) y proyecto, cada una con su título y su motivo. */
public class ListIntakeProposals {

    private record Key(ProposalKind kind, Optional<ProjectId> project, ProposalConfidence confidence) {
    }

    private static final UUID NO_PROJECT = new UUID(0, 0);

    private final IntakeRepository intake;
    private final DocumentRepository documents;

    public ListIntakeProposals(IntakeRepository intake, DocumentRepository documents) {
        this.intake = intake;
        this.documents = documents;
    }

    public List<IntakeProposalGroup> execute(Actor actor, CompanyId companyId, IntakeBatchId batchId,
            ProposalFilter filter) {
        IntakeBatches.require(intake, actor, companyId, batchId);
        Map<DocumentId, String> titles = new java.util.HashMap<>(documents.intakeTitles(companyId, batchId));
        List<IntakeProposal> proposed = intake.proposals(companyId, batchId).stream()
                .filter(p -> p.state() == DecisionState.PROPOSED).filter(filter::matches).toList();
        titles.putAll(documents.titlesOf(companyId, proposed.stream().flatMap(p -> p.targetDocumentId().stream())
                .filter(t -> !titles.containsKey(t)).distinct().toList()));
        Map<Key, List<IntakeProposalItem>> groups = new LinkedHashMap<>();
        proposed.stream().map(p -> new IntakeProposalItem(p, titles.getOrDefault(p.documentId(), ""),
                        p.targetDocumentId().map(titles::get)))
                .sorted(Comparator.comparing(IntakeProposalItem::title).thenComparing(i -> i.proposal().id()))
                .forEach(i -> groups.computeIfAbsent(key(i.proposal()), k -> new java.util.ArrayList<>()).add(i));
        return groups.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Key, List<IntakeProposalItem>> e) -> e.getKey().kind())
                        .thenComparing(e -> e.getKey().confidence())
                        .thenComparing(e -> e.getKey().project().map(ProjectId::value).orElse(NO_PROJECT)))
                .map(e -> new IntakeProposalGroup(e.getKey().kind(), e.getKey().confidence(), e.getKey().project(),
                        e.getValue())).toList();
    }

    private static Key key(IntakeProposal p) {
        return new Key(p.kind(), p.targetProjectId(), p.confidence());
    }
}
