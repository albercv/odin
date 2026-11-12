package com.e2d.odin.application.port;

/** El etiquetador no responde o no se puede conectar: el documento espera sin gastar intentos. */
public class TaggerUnavailableException extends RuntimeException {

    public TaggerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
