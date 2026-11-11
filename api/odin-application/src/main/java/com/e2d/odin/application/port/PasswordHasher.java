package com.e2d.odin.application.port;

public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String hash);
}
