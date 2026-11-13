package com.e2d.odin.application.link;

import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.casefile.CaseFileAssignment;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.ConflictException;
import java.util.List;
import java.util.Set;

/** Sube un fichero al proyecto del caso y lo añade a su expediente, con la persona del enlace como autora. */
public class UploadByLink {

    private final ResolveLink resolve;
    private final AccessLinkRepository links;
    private final UploadDocument upload;
    private final AddToCaseFiles addToCaseFiles;

    public UploadByLink(ResolveLink resolve, AccessLinkRepository links, UploadDocument upload,
            AddToCaseFiles addToCaseFiles) {
        this.resolve = resolve;
        this.links = links;
        this.upload = upload;
        this.addToCaseFiles = addToCaseFiles;
    }

    /**
     * El hueco del tope se reserva antes de guardar nada, con una sola orden atómica; un fichero que luego se rechaza
     * (formato, tamaño) también lo gasta, para que probar ficheros no dé más de 20 intentos por enlace.
     */
    public UploadReceipt execute(String token, String clientAddress, String filename, byte[] content) {
        ResolvedLink resolved = resolve.execute(token, clientAddress, AccessLinkKind.UPLOAD);
        if (!links.reserveUpload(resolved.link(), LinkSettings.MAX_UPLOADS)) {
            throw new ConflictException("Este enlace ya admitió " + LinkSettings.MAX_UPLOADS + " ficheros");
        }
        var caseDocument = resolved.target();
        UploadOutcome stored = upload.executeForLink(resolved.actor(), caseDocument.companyId(),
                caseDocument.projectId(), filename, content);
        addToCaseFiles.execute(resolved.actor(),
                List.of(new CaseFileAssignment(caseDocument.id(), Set.of(stored.document().id()))));
        return new UploadReceipt(filename);
    }
}
