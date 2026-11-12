package com.e2d.odin.application.port;

/** El servicio lector respondió 413: el fichero pasa del tamaño o de las páginas que admite. */
public class DocumentTooLargeForReaderException extends RuntimeException {

    public DocumentTooLargeForReaderException(String message) {
        super(message);
    }
}
