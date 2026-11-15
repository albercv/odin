package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequencePersonCodeGenerator;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import org.junit.jupiter.api.Test;

class SetUpInstallationTest {

    private static final String PASSWORD = "una-clave-larga-1";

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final SetUpInstallation setUp = new SetUpInstallation(organizations, users, new FakePasswordHasher(),
            new SequenceIdGenerator(), new SequencePersonCodeGenerator("7F3K"), new InstallationSettings("token-secreto"), audit);

    @Test
    void createsGroupAndFirstAdmin() {
        UserId id = setUp.execute("token-secreto", "Grupo Demo", "Gema", "Gema@Demo.test", PASSWORD);

        User admin = users.findById(id).orElseThrow();
        assertThat(organizations.findGroup()).get().extracting("name").isEqualTo("Grupo Demo");
        assertThat(admin.groupAdmin()).isTrue();
        assertThat(memberships.listByUser(id)).isEmpty();
        assertThat(admin.email()).contains("gema@demo.test");
        assertThat(admin.passwordHash()).contains("pw:" + PASSWORD);
    }

    @Test
    void firstAdminGetsAGeneratedPersonCode() {
        UserId id = setUp.execute("token-secreto", "Grupo Demo", "Gema", "g@demo.test", PASSWORD);

        assertThat(users.findById(id).orElseThrow().personCode()).isEqualTo("7F3K");
    }

    @Test
    void rejectsWrongToken() {
        assertThatThrownBy(() -> setUp.execute("otro", "G", "Gema", "g@demo.test", PASSWORD))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setUp.execute("", "G", "Gema", "g@demo.test", PASSWORD))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findGroup()).isEmpty();
    }

    @Test
    void rejectsSecondSetup() {
        setUp.execute("token-secreto", "G", "Gema", "g@demo.test", PASSWORD);

        assertThatThrownBy(() -> setUp.execute("token-secreto", "G2", "Otra", "o@demo.test", PASSWORD))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> setUp.execute("token-secreto", "G", "Gema", "g@demo.test", "corta"))
                .isInstanceOf(ValidationException.class);
    }
}
