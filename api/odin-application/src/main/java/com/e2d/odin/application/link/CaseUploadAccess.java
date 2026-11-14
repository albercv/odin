package com.e2d.odin.application.link;

import com.e2d.odin.application.casefile.CaseFileAccess;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.Optional;

/**
 * La comprobación única de «puede subir a este expediente», al pedir el enlace y en cada uso: la ficha existe, se ve y
 * está en un proyecto vivo. Un caso sin proyecto (D-111) es el mismo 404 que uno que no existe: lo subido entraría en
 * lo general, a la vista de toda la empresa.
 */
final class CaseUploadAccess {

    private CaseUploadAccess() {
    }

    static Document require(Actor actor, OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, DocumentId caseId) {
        return Optional.of(CaseFileAccess.forWrite(actor, Optional.empty(), organizations, projectMembers)
                .caseDocument(documents, caseId)).filter(d -> d.projectId().isPresent())
                .orElseThrow(() -> new NotFoundException("Caso no encontrado"));
    }
}
