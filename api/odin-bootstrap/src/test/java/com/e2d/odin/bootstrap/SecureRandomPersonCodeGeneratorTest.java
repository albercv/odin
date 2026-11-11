package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureRandomPersonCodeGeneratorTest {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    @Test
    void codesHaveFourCharactersOfTheAlphabet() {
        SecureRandomPersonCodeGenerator generator = new SecureRandomPersonCodeGenerator();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 2000; i++) {
            String code = generator.next();
            assertThat(code).hasSize(4).matches("[" + ALPHABET + "]{4}");
            seen.add(code);
        }
        assertThat(seen.size()).isGreaterThan(1900);
    }
}
