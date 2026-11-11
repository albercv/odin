package com.e2d.odin.application.port;

public interface PersonCodeGenerator {

    /** Un código de persona de 4 caracteres, sin garantía de que esté libre. */
    String next();
}
