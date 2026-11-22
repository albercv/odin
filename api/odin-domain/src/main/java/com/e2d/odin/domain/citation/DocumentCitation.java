package com.e2d.odin.domain.citation;

/** Una cita con verbo de cambio: la página y la frase literal que la sostienen. */
public record DocumentCitation(LinkRelation relation, String citedIdentifier, int page, String quote) {
}
