package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CryptoAdaptersTest {

    @Test
    void secretHashIsSha256Hex() {
        assertThat(new Sha256SecretHasher().hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void passwordHashUsesBcryptCost12AndMatchesOnlyTheRightPassword() {
        BcryptPasswordHasher hasher = new BcryptPasswordHasher();
        String hash = hasher.hash("clave-muy-larga-1");
        assertThat(hash).startsWith("$2a$12$");
        assertThat(hasher.matches("clave-muy-larga-1", hash)).isTrue();
        assertThat(hasher.matches("otra-clave-larga", hash)).isFalse();
    }

    @Test
    void tokensAreBase64UrlWithoutPaddingAndDistinct() {
        SecureRandomTokenGenerator tokens = new SecureRandomTokenGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String token = tokens.randomToken(32);
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            seen.add(token);
        }
        assertThat(seen).hasSize(20);
    }

    @Test
    void idsAreRandomUuids() {
        RandomUuidGenerator ids = new RandomUuidGenerator();
        UUID first = ids.newId();
        assertThat(first.version()).isEqualTo(4);
        assertThat(ids.newId()).isNotEqualTo(first);
    }
}
