package com.e2d.odin.application.port;

/** El servicio lector no responde o no se puede conectar: el documento espera sin gastar intentos. */
public class ReaderUnavailableException extends RuntimeException {

    public ReaderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
