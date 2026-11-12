package com.e2d.odin.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

    private static final CompanyId COMPANY = new CompanyId(UUID.randomUUID());

    private static Actor actor(Role role, Set<CompanyId> companies) {
        return new Actor(new UserId(UUID.randomUUID()), "Ana", role, companies);
    }

    @Test
    void groupAdminScopeIsAll() {
        Actor admin = actor(Role.GROUP_ADMIN, Set.of());

        assertThat(admin.isGroupAdmin()).isTrue();
        assertThat(admin.scope()).isEqualTo(CompanyScope.all());
    }

    @Test
    void workerScopeIsOwnCompany() {
        Actor worker = actor(Role.WORKER, Set.of(COMPANY));

        assertThat(worker.scope()).isEqualTo(CompanyScope.of(COMPANY));
        assertThat(worker.requireCompany()).isEqualTo(COMPANY);
    }

    @Test
    void requireCompanyFailsForGroupAdmin() {
        Actor admin = actor(Role.GROUP_ADMIN, Set.of());

        assertThatThrownBy(admin::requireCompany).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireCompanyFailsWithSeveralCompanies() {
        Actor admin = actor(Role.COMPANY_ADMIN, Set.of(COMPANY, new CompanyId(UUID.randomUUID())));

        assertThatThrownBy(admin::requireCompany).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void administersOnlyItsCompaniesUnlessGroupAdmin() {
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThat(actor(Role.COMPANY_ADMIN, Set.of(COMPANY)).administers(COMPANY)).isTrue();
        assertThat(actor(Role.COMPANY_ADMIN, Set.of(COMPANY)).administers(other)).isFalse();
        assertThat(actor(Role.WORKER, Set.of(COMPANY)).administers(COMPANY)).isFalse();
        assertThat(actor(Role.GROUP_ADMIN, Set.of()).administers(other)).isTrue();
    }
}
