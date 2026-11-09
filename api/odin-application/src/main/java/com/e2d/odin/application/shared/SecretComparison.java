package com.e2d.odin.application.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SecretComparison {

    private SecretComparison() {
    }

    public static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
