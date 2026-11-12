package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListAccessKeysTest {

    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final ListAccessKeys list = new ListAccessKeys(keys, memberships);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());

    ListAccessKeysTest() {
        People.member(users, memberships, workerId, "Wanda", Optional.empty(), Role.WORKER, company, true,
                Optional.empty());
        keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), workerId, company, "portátil", "abcdefgh", "h:x",
                Instant.parse("2026-11-09T10:00:00Z"), Optional.empty()));
    }

    @Test
    void otherCompanyAdminCannotListKeys() {
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), company, workerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownCompanyAdminListsKeys() {
        assertThat(list.execute(Actors.companyAdmin(company), company, workerId)).hasSize(1);
    }

    @Test
    void listsOnlyTheKeysOfThatCompanysMembership() {
        CompanyId second = new CompanyId(UUID.randomUUID());
        memberships.save(new Membership(workerId, second, Set.of(Role.WORKER), true));
        keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), workerId, second, "otra", "ijklmnop", "h:y",
                Instant.parse("2026-11-09T11:00:00Z"), Optional.empty()));

        assertThat(list.execute(Actors.companyAdmin(company), company, workerId)).extracting(AccessKey::label)
                .containsExactly("portátil");
    }

    @Test
    void personWhoDoesNotBelongToTheCompanyIsNotFound() {
        CompanyId second = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(second), second, workerId))
                .isInstanceOf(NotFoundException.class);
    }
}
