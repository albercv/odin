package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresMembershipRepositoryIT extends AbstractPostgresIT {

    private UserId person() {
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Persona", Optional.empty(), false, true, Optional.empty()));
        return id;
    }

    private static Membership membership(UserId user, CompanyId company, Role... roles) {
        return new Membership(user, company, Set.of(roles), true);
    }

    @Test
    void savesAndFinds() {
        CompanyId company = newCompany();
        Membership m = membership(person(), company, Role.COMPANY_ADMIN, Role.WORKER);
        memberships.save(m);
        assertThat(memberships.find(m.userId(), company)).contains(m);

        memberships.save(m.withActive(false));
        assertThat(memberships.find(m.userId(), company)).contains(m.withActive(false));
    }

    @Test
    void findsNothingInAnotherCompany() {
        Membership m = membership(person(), newCompany(), Role.WORKER);
        memberships.save(m);
        assertThat(memberships.find(m.userId(), newCompany())).isEmpty();
    }

    @Test
    void listsByCompanyAndByUser() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId both = person();
        Membership bothInA = membership(both, a, Role.WORKER);
        Membership bothInB = membership(both, b, Role.COMPANY_ADMIN);
        Membership otherInA = membership(person(), a, Role.WORKER);
        memberships.save(bothInA);
        memberships.save(bothInB);
        memberships.save(otherInA);

        assertThat(memberships.listByCompany(a)).containsExactly(bothInA, otherInA);
        assertThat(memberships.listByCompany(b)).containsExactly(bothInB);
        assertThat(memberships.listByUser(both)).containsExactly(bothInA, bothInB);
    }

    @Test
    void deletedMembershipIsExcludedFromReads() {
        CompanyId company = newCompany();
        Membership m = membership(person(), company, Role.WORKER);
        memberships.save(m);
        memberships.delete(m.userId(), company, Instant.now());

        assertThat(memberships.find(m.userId(), company)).isEmpty();
        assertThat(memberships.listByCompany(company)).isEmpty();
        assertThat(memberships.listByUser(m.userId())).isEmpty();
    }

    @Test
    void savingAgainAfterDeleteRestoresIt() {
        CompanyId company = newCompany();
        Membership m = membership(person(), company, Role.WORKER);
        memberships.save(m);
        memberships.delete(m.userId(), company, Instant.now());
        memberships.save(m);

        assertThat(memberships.find(m.userId(), company)).contains(m);
    }

    @Test
    void secondDeleteKeepsFirstTimestamp() throws SQLException {
        CompanyId company = newCompany();
        Membership m = membership(person(), company, Role.WORKER);
        memberships.save(m);
        Instant first = Instant.parse("2026-11-01T10:00:00Z");
        memberships.delete(m.userId(), company, first);
        memberships.delete(m.userId(), company, Instant.parse("2026-11-02T10:00:00Z"));

        try (var c = Db.owner().getConnection(); var st = c.prepareStatement(
                "SELECT deleted_at FROM membership WHERE user_id = ? AND company_id = ?")) {
            st.setObject(1, m.userId().value());
            st.setObject(2, company.value());
            var rs = st.executeQuery();
            rs.next();
            assertThat(rs.getTimestamp(1).toInstant()).isEqualTo(first);
        }
    }
}
