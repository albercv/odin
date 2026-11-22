package com.e2d.odin.domain.context;

/** Un texto literal de un documento con su página: la frase de un vínculo, el encabezado o el mejor fragmento. */
public record PageText(int page, String text) {
}
