package com.e2d.odin.bootstrap;

import com.e2d.odin.application.port.TokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public class SecureRandomTokenGenerator implements TokenGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
