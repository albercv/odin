package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.SecretHasher;

public class FakeSecretHasher implements SecretHasher {

    @Override
    public String hash(String secret) {
        return "h:" + secret;
    }
}
