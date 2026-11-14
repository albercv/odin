package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import java.util.Optional;

/**
 * Sube un fichero a un proyecto, sin expediente. Quien llama (ResolveLink) ya comprobó los permisos de la persona.
 * Los duplicados se buscan solo en ese proyecto y entre lo general; si otra subida idéntica gana el índice único por
 * un instante, se enlaza el documento que creó.
 */
public class UploadToProject {

    private final UploadDocument upload;
    private final DocumentRepository documents;

    public UploadToProject(UploadDocument upload, DocumentRepository documents) {
        this.upload = upload;
        this.documents = documents;
    }

    public UploadOutcome execute(Actor actor, Project project, String title, String filename, byte[] content) {
        return upload.executeForCase(actor, project.companyId(), Optional.of(project.id()), title, filename, content,
                documents::save);
    }
}
