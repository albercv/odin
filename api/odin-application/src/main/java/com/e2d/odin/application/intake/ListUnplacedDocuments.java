package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.List;

/** Los documentos pendientes del lote que ninguna propuesta de proyecto resuelve, también los que fallaron al leerse. */
public class ListUnplacedDocuments {

    private final IntakeRepository intake;
    private final DocumentRepository documents;

    public ListUnplacedDocuments(IntakeRepository intake, DocumentRepository documents) {
        this.intake = intake;
        this.documents = documents;
    }

    public List<Document> execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatches.require(intake, actor, companyId, batchId);
        return UnplacedDocuments.find(intake, documents, companyId, batchId);
    }
}
