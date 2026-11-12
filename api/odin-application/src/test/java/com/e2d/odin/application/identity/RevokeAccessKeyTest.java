package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevokeAccessKeyTest {

    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final MutableClock clock = new MutableClock();
    private final RevokeAccessKey revoke = new RevokeAccessKey(keys, users, clock);
    private final CompanyId company = Fixtures.company(new InMemoryOrganizationRepository());
    private final AccessKeyId keyId = new AccessKeyId(UUID.randomUUID());

    RevokeAccessKeyTest() {
        UserId workerId = new UserId(UUID.randomUUID());
        People.member(users, memberships, workerId, "Wanda", Optional.empty(), Role.WORKER, company, true,
                Optional.empty());
        keys.save(new AccessKey(keyId, workerId, company, "portátil", "abcdefgh", "h:secreto", clock.instant(),
                Optional.empty()));
    }

    @Test
    void otherCompanyAdminGetsNotFoundLikeForAMissingKey() {
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> revoke.execute(Actors.companyAdmin(other), keyId))
                .isInstanceOf(NotFoundException.class).hasMessage("Clave no encontrada");
        assertThatThrownBy(() -> revoke.execute(Actors.companyAdmin(other), new AccessKeyId(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class).hasMessage("Clave no encontrada");
        assertThat(keys.findById(keyId).orElseThrow().revokedAt()).isEmpty();
    }

    @Test
    void workerCannotRevoke() {
        assertThatThrownBy(() -> revoke.execute(Actors.worker(company), keyId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(keys.findById(keyId).orElseThrow().revokedAt()).isEmpty();
    }

    @Test
    void ownCompanyAdminCanRevoke() {
        revoke.execute(Actors.companyAdmin(company), keyId);

        assertThat(keys.findById(keyId).orElseThrow().revokedAt()).isPresent();
    }
}
