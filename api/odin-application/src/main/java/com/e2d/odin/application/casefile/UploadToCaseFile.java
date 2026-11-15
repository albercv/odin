package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Sube un fichero al proyecto del caso y lo añade a su expediente. Los duplicados se buscan solo en ese proyecto y
 * entre lo general: el mismo fichero en otro proyecto da un documento nuevo. El documento nuevo, su entrada y el
 * ADDED se guardan juntos; si el fichero ya estaba, se enlaza el que había.
 */
public class UploadToCaseFile {

    private final UploadDocument upload;
    private final AddToCaseFiles addToCaseFiles;
    private final CaseFileRepository caseFiles;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog audit;

    public UploadToCaseFile(UploadDocument upload, AddToCaseFiles addToCaseFiles, CaseFileRepository caseFiles,
            IdGenerator ids, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.upload = upload;
        this.addToCaseFiles = addToCaseFiles;
        this.caseFiles = caseFiles;
        this.ids = ids;
        this.clock = clock;
    }

    /** La subida del administrador: una entrada con quién, qué documento y a qué expediente. El enlace usa {@link #execute}. */
    public UploadOutcome executeAsAdmin(Actor actor, Document caseDocument, String title, String filename,
            byte[] content) {
        UploadOutcome stored = execute(actor, caseDocument, title, filename, content);
        audit.record(stored.auditEntry(actor, content.length).with("caseId", caseDocument.id().value().toString()));
        return stored;
    }

    /** Sin entrada de registro: la comparten el administrador (que usa executeAsAdmin) y el enlace de subida. */
    public UploadOutcome execute(Actor actor, Document caseDocument, String title, String filename, byte[] content) {
        UploadOutcome stored = upload.executeForCase(actor, caseDocument.companyId(), caseDocument.projectId(), title,
                filename, content, created -> addNew(actor, caseDocument, created));
        // el fichero de la propia ficha ya está en Odín y no es parte de su expediente: nunca un error
        if (stored.duplicate() && !stored.document().id().equals(caseDocument.id())) {
            var assignments = List.of(new CaseFileAssignment(caseDocument.id(), Set.of(stored.document().id())));
            if (actor.role() == Role.WORKER) {
                addToCaseFiles.execute(actor, assignments);
            } else {
                addToCaseFiles.apply(actor, caseDocument.companyId(), assignments);
            }
        }
        return stored;
    }

    private void addNew(Actor actor, Document caseDocument, Document created) {
        Instant now = clock.instant();
        caseFiles.addNewDocument(created,
                new CaseFileEntry(caseDocument.companyId(), caseDocument.id(), created.id(), actor.userId(), now),
                new CaseFileEvent(ids.newId(), caseDocument.companyId(), caseDocument.id(), created.id(),
                        CaseFileAction.ADDED, actor.userId(), now));
    }
}
