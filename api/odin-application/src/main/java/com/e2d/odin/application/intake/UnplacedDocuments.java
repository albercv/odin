package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Los documentos pendientes del lote sin propuesta de proyecto por decidir: no tienen otra salida que la decisión directa. */
final class UnplacedDocuments {

    private UnplacedDocuments() {
    }

    static List<Document> find(IntakeRepository intake, DocumentRepository documents, CompanyId companyId,
            IntakeBatchId batchId) {
        Set<DocumentId> withProposal = intake.proposals(companyId, batchId).stream()
                .filter(p -> p.state() == DecisionState.PROPOSED && placement(p))
                .map(IntakeProposal::documentId).collect(Collectors.toSet());
        return documents.intakePendingDocuments(companyId, batchId).stream()
                .filter(d -> !withProposal.contains(d.id())).toList();
    }

    private static boolean placement(IntakeProposal p) {
        return p.kind() == ProposalKind.PROJECT || p.kind() == ProposalKind.COMPANY_WIDE;
    }
}
