package com.e2d.odin.application.port;

import com.e2d.odin.domain.shared.DocumentId;
import java.util.Optional;

/** Un documento leído y pendiente de un lote, con lo que la clasificación necesita: su carpeta, su texto y si lo leyó el OCR. */
public record IntakeDocument(DocumentId id, Optional<String> path, String text, boolean scan) {
}
