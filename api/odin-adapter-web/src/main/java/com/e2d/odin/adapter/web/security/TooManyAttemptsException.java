package com.e2d.odin.adapter.web.security;

public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException() {
        super("Demasiados intentos; espera un minuto");
    }
}
