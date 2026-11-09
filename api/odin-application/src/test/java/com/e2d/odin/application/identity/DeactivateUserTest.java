package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeactivateUserTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final DeactivateUser deactivate = new DeactivateUser(users);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());

    DeactivateUserTest() {
        users.save(new User(workerId, "Wanda", Optional.empty(), Role.WORKER, Optional.of(company), true,
                Optional.empty()));
    }

    @Test
    void otherCompanyAdminCannotDeactivate() {
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> deactivate.execute(Actors.companyAdmin(other), workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(workerId).orElseThrow().active()).isTrue();
    }

    @Test
    void workerCannotDeactivate() {
        assertThatThrownBy(() -> deactivate.execute(Actors.worker(company), workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(workerId).orElseThrow().active()).isTrue();
    }

    @Test
    void ownCompanyAdminCanDeactivate() {
        deactivate.execute(Actors.companyAdmin(company), workerId);

        assertThat(users.findById(workerId).orElseThrow().active()).isFalse();
    }
}
