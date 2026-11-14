package com.e2d.odin.domain.intake;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Una propuesta es un dato, no un efecto: confirmarla la decide una persona y los casos de uso hacen el cambio. */
public record IntakeProposal(UUID id, CompanyId companyId, IntakeBatchId batchId, DocumentId documentId,
        ProposalKind kind, Optional<ProjectId> targetProjectId, Optional<DocumentId> targetDocumentId,
        Optional<String> caseNumber, ProposalConfidence confidence, ProposalSource source, String reason,
        DecisionState state, Optional<UserId> decidedBy, Optional<Instant> decidedAt) {

    public static final int MAX_REASON = 300;

    public IntakeProposal {
        if (reason.length() > MAX_REASON) {
            throw new IllegalArgumentException("La razón admite " + MAX_REASON + " caracteres como máximo");
        }
    }

    /** Por decidir: sin decidir o con «no lo sé». Mantiene el lote abierto y se puede confirmar o rechazar. */
    public boolean open() {
        return state == DecisionState.PROPOSED || state == DecisionState.DONT_KNOW;
    }

    public IntakeProposal confirm(UserId by, Instant now) {
        return decide(DecisionState.CONFIRMED, by, now);
    }

    public IntakeProposal reject(UserId by, Instant now) {
        return decide(DecisionState.REJECTED, by, now);
    }

    /** «No lo sé»: el documento sigue pendiente y oculto, sin plazo (D-080), y queda quién lo dijo y cuándo. */
    public IntakeProposal dontKnow(UserId by, Instant now) {
        return decide(DecisionState.DONT_KNOW, by, now);
    }

    private IntakeProposal decide(DecisionState to, UserId by, Instant now) {
        if (!open()) {
            throw new ConflictException("La propuesta ya está decidida");
        }
        return new IntakeProposal(id, companyId, batchId, documentId, kind, targetProjectId, targetDocumentId,
                caseNumber, confidence, source, reason, to, Optional.of(by), Optional.of(now));
    }
}
