package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.PasswordHasher;

public class FakePasswordHasher implements PasswordHasher {

    @Override
    public String hash(String password) {
        return "pw:" + password;
    }

    @Override
    public boolean matches(String password, String hash) {
        return hash(password).equals(hash);
    }
}
