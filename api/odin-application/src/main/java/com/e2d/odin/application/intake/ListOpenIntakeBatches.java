package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Los lotes de la empresa que no están cerrados y los cerrados que aún tienen documentos sin ubicar, el más reciente
 * primero: el panel abre el primero y deja elegir entre ellos.
 */
public class ListOpenIntakeBatches {

    private final IntakeRepository intake;
    private final DocumentRepository documents;

    public ListOpenIntakeBatches(IntakeRepository intake, DocumentRepository documents) {
        this.intake = intake;
        this.documents = documents;
    }

    public List<IntakeBatch> execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Stream<IntakeBatch> withPending = intake.findClosed(companyId).stream()
                .filter(b -> !documents.intakePendingDocuments(companyId, b.id()).isEmpty());
        return Stream.concat(intake.findOpen(companyId).stream(), withPending)
                .sorted(Comparator.comparing(IntakeBatch::createdAt).reversed()).toList();
    }
}
