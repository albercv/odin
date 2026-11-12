package com.e2d.odin.application.port;

/** El servicio lector respondió 415 o 422: el fichero está dañado o no se puede leer. */
public class UnreadableDocumentException extends RuntimeException {

    public UnreadableDocumentException(String message) {
        super(message);
    }
}
