package com.e2d.odin.application.port;

/** Lo que responde el etiquetador a un tramo, con los tokens que ocupó el texto y el contexto que tenía. */
public record TaggerAnswer(String text, int promptTokens, int contextTokens, String taggerVersion) {

    /** El texto ocupó todo el contexto: el modelo no lo leyó entero. */
    public boolean truncated() {
        return promptTokens >= contextTokens;
    }
}
