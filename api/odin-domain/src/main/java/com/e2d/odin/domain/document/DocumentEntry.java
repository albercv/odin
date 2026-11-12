package com.e2d.odin.domain.document;

import java.util.Optional;

/** Un documento leído con lo que depende de quien lee: su sustituto directo y el vigente del final de su cadena,
 *  cada uno solo si lo ve. */
public record DocumentEntry(Document document, Optional<DocumentRef> supersededBy, Optional<DocumentRef> replacement) {
}
