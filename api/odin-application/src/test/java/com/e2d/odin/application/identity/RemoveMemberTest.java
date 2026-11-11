package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemoveMemberTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryOrganizationRepository projects = new InMemoryOrganizationRepository();
    private final InMemoryProjectMemberRepository projectMembers = new InMemoryProjectMemberRepository(projects);
    private final RemoveMember remove = new RemoveMember(memberships, keys, projectMembers, sessions,
            new MutableClock());
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final CompanyId other = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final UserId adminId = new UserId(UUID.randomUUID());

    RemoveMemberTest() {
        People.member(users, memberships, workerId, "Wanda", Optional.empty(), Role.WORKER, company, true,
                Optional.empty());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
    }

    @Test
    void removesOnlyThatCompanysMembership() {
        memberships.save(new Membership(workerId, other, Set.of(Role.WORKER), true));

        remove.execute(Actors.companyAdmin(company), company, workerId);

        assertThat(memberships.find(workerId, company)).isEmpty();
        assertThat(memberships.find(workerId, other)).isPresent();
    }

    @Test
    void personWithNoMembershipLeftStillExists() {
        remove.execute(Actors.companyAdmin(company), company, workerId);

        assertThat(memberships.listByUser(workerId)).isEmpty();
        assertThat(users.findById(workerId)).isPresent();
    }

    @Test
    void cannotRemoveOneself() {
        Actor self = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Set.of(company));

        assertThatThrownBy(() -> remove.execute(self, company, adminId))
                .isInstanceOf(ValidationException.class).hasMessage("No puedes quitarte de la empresa");
        assertThat(memberships.find(adminId, company)).isPresent();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        assertThatThrownBy(() -> remove.execute(Actors.companyAdmin(other), company, workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> remove.execute(Actors.worker(company), company, workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(memberships.find(workerId, company)).isPresent();
    }

    @Test
    void companyAdminCannotRemoveACompanyAdminMembership() {
        assertThatThrownBy(() -> remove.execute(Actors.companyAdmin(company), company, adminId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo el administrador del grupo da o quita la administración de una empresa");
        assertThat(memberships.find(adminId, company)).isPresent();
    }

    @Test
    void groupAdminRemovingTheOnlyAdminMembershipClosesSessions() {
        String token = sessions.open(adminId);

        remove.execute(Actors.groupAdmin(), company, adminId);

        assertThat(memberships.find(adminId, company)).isEmpty();
        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void removingAMembershipOfAnotherCompanyIsNotFound() {
        assertThatThrownBy(() -> remove.execute(Actors.companyAdmin(other), other, workerId))
                .isInstanceOf(NotFoundException.class);
        assertThat(memberships.find(workerId, company)).isPresent();
    }

    private AccessKey keyOf(UserId userId, CompanyId companyId, String prefix, String secret) {
        AccessKey key = new AccessKey(new AccessKeyId(UUID.randomUUID()), userId, companyId, "portátil", prefix,
                "h:" + secret, Instant.parse("2026-11-09T10:00:00Z"), Optional.empty());
        keys.save(key);
        return key;
    }

    @Test
    void removingRevokesTheMembershipsKeys() {
        memberships.save(new Membership(workerId, other, Set.of(Role.WORKER), true));
        AccessKey inCompany = keyOf(workerId, company, "abcdefgh", "uno");
        AccessKey inOther = keyOf(workerId, other, "ijklmnop", "dos");

        remove.execute(Actors.companyAdmin(company), company, workerId);

        assertThat(keys.findById(inCompany.id()).orElseThrow().revokedAt()).isPresent();
        assertThat(keys.findById(inOther.id()).orElseThrow().revokedAt()).isEmpty();
    }

    @Test
    void reAddedPersonDoesNotGetOldKeysBack() {
        FakeSecretHasher secrets = new FakeSecretHasher();
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        organizations.saveCompany(new Company(company, "A", EnumSet.allOf(Capability.class)));
        UserId personId = new UserId(UUID.randomUUID());
        People.member(users, memberships, personId, "Pili", Optional.of("p@demo.test"), Role.WORKER, company, true,
                Optional.empty());
        memberships.save(new Membership(personId, other, Set.of(Role.WORKER), true));
        keyOf(personId, company, "abcdefgh", "uno");
        keyOf(personId, other, "ijklmnop", "dos");
        ResolveActor resolve = new ResolveActor(keys, users, memberships, sessions, secrets);

        remove.execute(Actors.companyAdmin(company), company, personId);
        new AddMember(users, memberships, organizations, projectMembers,
                new FakePasswordHasher(), new SequenceIdGenerator())
                .execute(Actors.companyAdmin(company), company,
                        new NewMember("Pili", Optional.of("p@demo.test"), Set.of(Role.WORKER), Optional.empty(),
                                Set.of()));

        assertThatThrownBy(() -> resolve.fromAccessKey("odin_abcdefgh_uno")).isInstanceOf(UnauthorizedException.class);
        assertThat(resolve.fromAccessKey("odin_ijklmnop_dos").companies()).containsExactly(other);
    }

    @Test
    void removingDropsTheProjectsOfThatCompanyOnly() {
        memberships.save(new Membership(workerId, other, Set.of(Role.WORKER), true));
        ProjectId inCompany = Fixtures.project(projects, company);
        ProjectId inOther = Fixtures.project(projects, other);
        projectMembers.add(inCompany, workerId);
        projectMembers.add(inOther, workerId);

        remove.execute(Actors.companyAdmin(company), company, workerId);

        assertThat(projectMembers.projectsOf(company, workerId)).isEmpty();
        assertThat(projectMembers.projectsOf(other, workerId)).containsExactly(inOther);
    }

    @Test
    void removedAndReAddedPersonHasNoProjects() {
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        organizations.saveCompany(new Company(company, "A", EnumSet.allOf(Capability.class)));
        UserId pili = new UserId(UUID.randomUUID());
        People.member(users, memberships, pili, "Pili", Optional.of("pili@demo.test"), Role.WORKER, company, true,
                Optional.empty());
        ProjectId project = Fixtures.project(projects, company);
        projectMembers.add(project, pili);

        remove.execute(Actors.companyAdmin(company), company, pili);
        MemberAdded again = new AddMember(users, memberships, organizations, projectMembers,
                new FakePasswordHasher(), new SequenceIdGenerator()).execute(Actors.companyAdmin(company), company,
                new NewMember("Pili", Optional.of("pili@demo.test"), Set.of(Role.WORKER), Optional.empty(),
                        Set.of()));

        assertThat(again.alreadyExisted()).isTrue();
        assertThat(again.user().id()).isEqualTo(pili);
        assertThat(projectMembers.projectsOf(company, pili)).isEmpty();
    }
}
