package com.e2d.odin.bootstrap;

import com.e2d.odin.application.port.PersonCodeGenerator;
import java.security.SecureRandom;

/** Cuatro caracteres al azar de un alfabeto sin 0, 1, I, L ni O, para que no se confundan al dictarlos. */
public class SecureRandomPersonCodeGenerator implements PersonCodeGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 4;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
