package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssueAccessKeyTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final IssueAccessKey issue = new IssueAccessKey(keys, users, memberships,
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SequenceIdGenerator(), new MutableClock(), audit);
    private final CompanyId company = new CompanyId(UUID.randomUUID());

    private User saveUser(Role role, boolean active) {
        return People.member(users, memberships, new UserId(UUID.randomUUID()), "Wanda", Optional.empty(), role,
                company, active, Optional.empty());
    }

    @Test
    void returnsPlainKeyOnceAndStoresHash() {
        User worker = saveUser(Role.WORKER, true);

        IssuedKey issued = issue.execute(Actors.companyAdmin(company), company, worker.id(), "portátil");

        AccessKey stored = keys.findById(issued.id()).orElseThrow();
        String secret = issued.plainKey().substring("odin_".length() + 8 + 1);
        assertThat(stored.secretHash()).isEqualTo("h:" + secret).doesNotContain(issued.plainKey());
        assertThat(stored.prefix()).isEqualTo(issued.prefix());
        assertThat(stored.label()).isEqualTo("portátil");
        assertThat(stored.companyId()).isEqualTo(company);
    }

    @Test
    void onlyForActiveWorkers() {
        var admin = Actors.groupAdmin();
        assertThatThrownBy(() -> issue.execute(admin, company, saveUser(Role.COMPANY_ADMIN, true).id(), "x"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> issue.execute(admin, company, saveUser(Role.WORKER, false).id(), "x"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void keyFormatIsOdinPrefixSecret() {
        User worker = saveUser(Role.WORKER, true);

        IssuedKey issued = issue.execute(Actors.groupAdmin(), company, worker.id(), "x");

        assertThat(issued.plainKey()).matches("odin_[A-Za-z0-9_-]{8}_[A-Za-z0-9_-]{43}");
        assertThat(issued.plainKey()).startsWith("odin_" + issued.prefix() + "_");
    }

    @Test
    void otherCompanyAdminIsForbidden() {
        User worker = saveUser(Role.WORKER, true);
        var foreign = Actors.companyAdmin(new CompanyId(UUID.randomUUID()));

        assertThatThrownBy(() -> issue.execute(foreign, company, worker.id(), "x")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void personWhoIsAdminAndWorkerGetsAKey() {
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Carlos", Optional.of("Pérez"), People.nextCode(), Optional.of("c@demo.test"), false, true, Optional.of("pw:x")));
        memberships.save(new Membership(id, company, Set.of(Role.COMPANY_ADMIN, Role.WORKER), true));

        assertThat(issue.execute(Actors.companyAdmin(company), company, id, "portátil").plainKey()).isNotBlank();
    }

    @Test
    void keyBelongsToTheMembershipOfTheCompanyInThePath() {
        User worker = saveUser(Role.WORKER, true);
        CompanyId second = new CompanyId(UUID.randomUUID());
        memberships.save(new Membership(worker.id(), second, Set.of(Role.WORKER), true));

        IssuedKey issued = issue.execute(Actors.companyAdmin(second), second, worker.id(), "otro");

        assertThat(keys.findById(issued.id()).orElseThrow().companyId()).isEqualTo(second);
    }

    @Test
    void personWhoDoesNotBelongToTheCompanyIsNotFound() {
        User worker = saveUser(Role.WORKER, true);
        CompanyId second = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> issue.execute(Actors.companyAdmin(second), second, worker.id(), "x"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deactivatedPersonCannotGetAKey() {
        User worker = saveUser(Role.WORKER, true);
        users.save(worker.withActive(false));

        assertThatThrownBy(() -> issue.execute(Actors.groupAdmin(), company, worker.id(), "x"))
                .isInstanceOf(ValidationException.class);
    }
}
