package com.e2d.odin.application.port;

/** Error del etiquetador, tiempo agotado o respuesta que no cumple el contrato. */
public class TaggerFailedException extends RuntimeException {

    public TaggerFailedException(String message) {
        super(message);
    }

    public TaggerFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
