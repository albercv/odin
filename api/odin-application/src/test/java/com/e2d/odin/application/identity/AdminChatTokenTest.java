package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.shared.UnauthorizedException;
import org.junit.jupiter.api.Test;

class AdminChatTokenTest {

    @Test
    void workerKeyFormatIsNeverAnAdminChatToken() {
        assertThat(AdminChatToken.parse("odin_abcdefgh_secreto")).isEmpty();
        assertThat(AdminChatToken.parse("odin_adm_abcdefgh_secreto")).isEmpty();
        assertThat(AdminChatToken.isAdminChatToken("odin_abcdefgh_secreto")).isFalse();
    }

    @Test
    void adminChatTokenIsNeverAWorkerKey() {
        String token = AdminChatToken.format("abcdefgh", "secreto");
        ResolveActor workers = new ResolveActor(new InMemoryAccessKeyRepository(), new InMemoryUserRepository(),
                new InMemoryMembershipRepository(), null, new FakeSecretHasher(), null);

        assertThat(AdminChatToken.parse(token)).hasValueSatisfying(p -> {
            assertThat(p.prefix()).isEqualTo("abcdefgh");
            assertThat(p.secret()).isEqualTo("secreto");
        });
        assertThatThrownBy(() -> workers.fromAccessKey(token)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> workers.fromAccessKey("odin-adm_a_b_c")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void prefixWithUnderscoreStillParsesByPosition() {
        assertThat(AdminChatToken.parse("odin-adm_ab_defgh_sec_ret")).hasValueSatisfying(
                p -> assertThat(p.prefix()).isEqualTo("ab_defgh"));
    }
}
