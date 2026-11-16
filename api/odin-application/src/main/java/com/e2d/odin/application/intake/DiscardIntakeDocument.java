package com.e2d.odin.application.intake;

import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.Optional;

/**
 * Descarta un documento pendiente del lote con {@link WithdrawDocument}. Sus propuestas por decidir pasan a rechazadas
 * en la misma transacción: no quedan congeladas.
 */
public class DiscardIntakeDocument {

    private final DocumentRepository documents;
    private final WithdrawDocument withdraw;
    private final IntakeEffects effects;

    public DiscardIntakeDocument(DocumentRepository documents, WithdrawDocument withdraw,
            IntakeEffects effects) {
        this.documents = documents;
        this.withdraw = withdraw;
        this.effects = effects;
    }

    public void execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, DocumentId documentId) {
        IntakeBatch batch = effects.unplacedEditable(actor, companyId, batchId);
        Document d = documents.findEntryIncludingWithdrawn(CompanyScope.of(companyId), ProjectVisibility.all(),
                documentId).map(DocumentEntry::document)
                .filter(x -> x.intakeBatchId().equals(Optional.of(batchId)))
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        if (!d.intakePending()) {
            throw new ConflictException("El documento ya está confirmado: retíralo desde Documentos");
        }
        // Antes de retirarlo: la RLS de las propuestas deja de verlas cuando su documento está retirado.
        effects.rejectUndecidedAbout(actor, batch, documentId);
        withdraw.execute(actor, documentId);
        effects.closeIfDone(batch);
    }
}
