package com.e2d.odin.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

    private static final CompanyId COMPANY = new CompanyId(UUID.randomUUID());

    private static Actor actor(Role role, Optional<CompanyId> company) {
        return new Actor(new UserId(UUID.randomUUID()), "Ana", role, company);
    }

    @Test
    void groupAdminScopeIsAll() {
        Actor admin = actor(Role.GROUP_ADMIN, Optional.empty());

        assertThat(admin.isGroupAdmin()).isTrue();
        assertThat(admin.scope()).isEqualTo(CompanyScope.all());
    }

    @Test
    void workerScopeIsOwnCompany() {
        Actor worker = actor(Role.WORKER, Optional.of(COMPANY));

        assertThat(worker.scope()).isEqualTo(CompanyScope.of(COMPANY));
        assertThat(worker.requireCompany()).isEqualTo(COMPANY);
    }

    @Test
    void requireCompanyFailsForGroupAdmin() {
        Actor admin = actor(Role.GROUP_ADMIN, Optional.empty());

        assertThatThrownBy(admin::requireCompany).isInstanceOf(ForbiddenException.class);
    }
}
