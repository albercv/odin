package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import java.util.List;
import java.util.Optional;

public class ListCompanyDocuments {

    private final DocumentRepository documents;

    public ListCompanyDocuments(DocumentRepository documents) {
        this.documents = documents;
    }

    public List<Document> execute(Actor actor, CompanyId companyId, int offset, int limit) {
        AccessPolicy.requireAdminOf(actor, companyId);
        int capped = WorkerAccess.cap(Optional.of(limit), 100, 500);
        return documents.list(CompanyScope.of(companyId), Optional.empty(), Math.max(0, offset), capped);
    }
}
