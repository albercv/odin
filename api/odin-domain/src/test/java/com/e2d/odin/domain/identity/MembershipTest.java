package com.e2d.odin.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipTest {

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final CompanyId COMPANY = new CompanyId(UUID.randomUUID());

    @Test
    void rolesCannotBeEmpty() {
        assertThatThrownBy(() -> new Membership(USER, COMPANY, Set.of(), true))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void groupAdminIsNotAMembershipRole() {
        assertThatThrownBy(() -> new Membership(USER, COMPANY, Set.of(Role.GROUP_ADMIN), true))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void exposesItsRoles() {
        Membership both = new Membership(USER, COMPANY, Set.of(Role.COMPANY_ADMIN, Role.WORKER), true);

        assertThat(both.isCompanyAdmin()).isTrue();
        assertThat(both.isWorker()).isTrue();
        assertThat(both.withActive(false).active()).isFalse();
    }
}
