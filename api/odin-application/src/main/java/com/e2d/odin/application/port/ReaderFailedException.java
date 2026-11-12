package com.e2d.odin.application.port;

/** Respuesta 500, tiempo agotado o respuesta que no cumple el contrato. */
public class ReaderFailedException extends RuntimeException {

    public ReaderFailedException(String message) {
        super(message);
    }

    public ReaderFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
