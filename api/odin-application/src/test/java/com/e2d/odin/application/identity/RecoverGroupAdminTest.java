package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoverGroupAdminTest {

    private static final String TOKEN = "token-secreto";
    private static final String NEW = "clave-nueva-1234";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final FakePasswordHasher passwords = new FakePasswordHasher();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final UserId adminId = new UserId(UUID.randomUUID());

    private RecoverGroupAdmin recover(String configuredToken) {
        return new RecoverGroupAdmin(users, passwords, sessions, new InstallationSettings(configuredToken));
    }

    private void saveGroupAdmin(boolean active) {
        People.groupAdmin(users, adminId, "Gema", Optional.of("g@demo.test"), active,
                Optional.of("pw:vieja-clave-123"));
    }

    @Test
    void validTokenSetsPasswordReactivatesAndClosesSessions() {
        saveGroupAdmin(false);
        String session = sessions.open(adminId);

        recover(TOKEN).execute(TOKEN, NEW);

        User admin = users.findById(adminId).orElseThrow();
        assertThat(admin.passwordHash()).contains(passwords.hash(NEW));
        assertThat(admin.active()).isTrue();
        assertThat(sessions.resolve(session)).isEmpty();
    }

    @Test
    void invalidTokenIsForbidden() {
        saveGroupAdmin(true);

        assertThatThrownBy(() -> recover(TOKEN).execute("otro", NEW)).isInstanceOf(ForbiddenException.class)
                .hasMessage("Código de instalación no válido");
        assertThat(users.findById(adminId).orElseThrow().passwordHash()).contains("pw:vieja-clave-123");
    }

    @Test
    void emptyConfiguredTokenIsForbidden() {
        saveGroupAdmin(true);

        assertThatThrownBy(() -> recover("").execute("", NEW)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void notConfiguredInstallationIsConflict() {
        assertThatThrownBy(() -> recover(TOKEN).execute(TOKEN, NEW)).isInstanceOf(ConflictException.class)
                .hasMessage("La instalación no está configurada");
    }

    @Test
    void shortPasswordIsRejected() {
        saveGroupAdmin(true);

        assertThatThrownBy(() -> recover(TOKEN).execute(TOKEN, "corta")).isInstanceOf(ValidationException.class);
        assertThat(users.findById(adminId).orElseThrow().passwordHash()).contains("pw:vieja-clave-123");
    }
}
