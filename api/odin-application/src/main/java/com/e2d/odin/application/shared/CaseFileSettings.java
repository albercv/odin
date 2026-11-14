package com.e2d.odin.application.shared;

/** El tope de caracteres de cada tanda al leer un expediente entero. */
public record CaseFileSettings(int chunkChars) {

    public CaseFileSettings() {
        this(30_000);
    }
}
