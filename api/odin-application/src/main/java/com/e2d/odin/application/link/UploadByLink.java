package com.e2d.odin.application.link;

import com.e2d.odin.application.casefile.UploadToCaseFile;
import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.ConflictException;

/** Sube un fichero al proyecto del caso y lo añade a su expediente, con la persona del enlace como autora. */
public class UploadByLink {

    private final ResolveLink resolve;
    private final AccessLinkRepository links;
    private final UploadToCaseFile uploadToCaseFile;

    public UploadByLink(ResolveLink resolve, AccessLinkRepository links, UploadToCaseFile uploadToCaseFile) {
        this.resolve = resolve;
        this.links = links;
        this.uploadToCaseFile = uploadToCaseFile;
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
        uploadToCaseFile.execute(resolved.actor(), resolved.target(), filename, filename, content);
        return new UploadReceipt(filename);
    }
}
