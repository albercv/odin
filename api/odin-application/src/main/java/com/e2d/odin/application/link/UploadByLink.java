package com.e2d.odin.application.link;

import com.e2d.odin.application.casefile.UploadToCaseFile;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.document.UploadToProject;
import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.ConflictException;

/** Sube un fichero al proyecto del enlace (y, si es de un caso, a su expediente), con la persona del enlace como autora. */
public class UploadByLink {

    private final ResolveLink resolve;
    private final AccessLinkRepository links;
    private final UploadToCaseFile uploadToCaseFile;
    private final UploadToProject uploadToProject;

    public UploadByLink(ResolveLink resolve, AccessLinkRepository links, UploadToCaseFile uploadToCaseFile,
            UploadToProject uploadToProject) {
        this.resolve = resolve;
        this.links = links;
        this.uploadToCaseFile = uploadToCaseFile;
        this.uploadToProject = uploadToProject;
    }

    /**
     * El hueco del tope se reserva antes de guardar nada, con una sola orden atómica; un fichero que luego se rechaza
     * (formato, tamaño) también lo gasta, para que probar ficheros no dé más de 20 intentos por enlace. El título es
     * el nombre del fichero.
     */
    public UploadReceipt execute(String token, String clientAddress, String filename, byte[] content) {
        ResolvedLink resolved = resolve.execute(token, clientAddress, AccessLinkKind.UPLOAD);
        if (!links.reserveUpload(resolved.link(), LinkSettings.MAX_UPLOADS)) {
            throw new ConflictException("Este enlace ya admitió " + LinkSettings.MAX_UPLOADS + " ficheros");
        }
        UploadOutcome stored = switch (resolved.target()) {
            case LinkTarget.CaseTarget c ->
                    uploadToCaseFile.execute(resolved.actor(), c.caseDocument(), filename, filename, content);
            case LinkTarget.ProjectTarget p ->
                    uploadToProject.execute(resolved.actor(), p.project(), filename, filename, content);
            case LinkTarget.OriginalTarget o -> throw new IllegalStateException("Un enlace de subida no lleva a un original");
        };
        return new UploadReceipt(filename, stored.duplicate());
    }
}
