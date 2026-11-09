package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListAccessKeysTest {

    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final ListAccessKeys list = new ListAccessKeys(keys, users);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());

    ListAccessKeysTest() {
        users.save(new User(workerId, "Wanda", Optional.empty(), Role.WORKER, Optional.of(company), true,
                Optional.empty()));
        keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), workerId, "portátil", "abcdefgh", "h:x",
                Instant.parse("2026-11-09T10:00:00Z"), Optional.empty()));
    }

    @Test
    void otherCompanyAdminCannotListKeys() {
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), workerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownCompanyAdminListsKeys() {
        assertThat(list.execute(Actors.companyAdmin(company), workerId)).hasSize(1);
    }
}
