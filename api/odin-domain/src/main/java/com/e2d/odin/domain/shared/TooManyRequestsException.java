package com.e2d.odin.domain.shared;

/** Se ha agotado un tope de uso; la API responde 429. */
public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
