package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateUserTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final UpdateUser update = new UpdateUser(users);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final UserId adminId = new UserId(UUID.randomUUID());
    private final Actor admin = Actors.companyAdmin(company);

    UpdateUserTest() {
        users.save(new User(workerId, "Wanda", Optional.of("w@demo.test"), Role.WORKER, Optional.of(company), true,
                Optional.empty()));
        users.save(new User(adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, Optional.of(company),
                true, Optional.of("pw:x")));
    }

    private static UserChanges name(String name) {
        return new UserChanges(Optional.of(name), Optional.empty(), Optional.empty());
    }

    private static UserChanges email(String email) {
        return new UserChanges(Optional.empty(), Optional.of(email), Optional.empty());
    }

    private static UserChanges active(boolean active) {
        return new UserChanges(Optional.empty(), Optional.empty(), Optional.of(active));
    }

    @Test
    void changesNameAndEmail() {
        User user = update.execute(admin, workerId,
                new UserChanges(Optional.of("Wanda Díaz"), Optional.of(" W2@Demo.test "), Optional.empty()));

        assertThat(user.displayName()).isEqualTo("Wanda Díaz");
        assertThat(user.email()).contains("w2@demo.test");
        assertThat(users.findById(workerId)).contains(user);
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> update.execute(admin, workerId, name(" "))).isInstanceOf(ValidationException.class);
    }

    @Test
    void duplicateEmailIsConflict() {
        assertThatThrownBy(() -> update.execute(admin, workerId, email("C@demo.test")))
                .isInstanceOf(ConflictException.class).hasMessage("Ya existe un usuario con ese correo");
    }

    @Test
    void sameEmailOnSameUserIsNotConflict() {
        User user = update.execute(admin, workerId, email("W@demo.test"));

        assertThat(user.email()).contains("w@demo.test");
    }

    @Test
    void blankEmailClearsWorkersEmail() {
        assertThat(update.execute(admin, workerId, email("")).email()).isEmpty();
    }

    @Test
    void blankEmailOnAdminIsRejected() {
        assertThatThrownBy(() -> update.execute(Actors.groupAdmin(), adminId, email(" ")))
                .isInstanceOf(ValidationException.class).hasMessage("El administrador necesita correo");
    }

    @Test
    void deactivatesAndReactivates() {
        assertThat(update.execute(admin, workerId, active(false)).active()).isFalse();
        assertThat(update.execute(admin, workerId, active(true)).active()).isTrue();
    }

    @Test
    void cannotDeactivateOneself() {
        Actor self = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Optional.of(company));

        assertThatThrownBy(() -> update.execute(self, adminId, active(false)))
                .isInstanceOf(ValidationException.class).hasMessage("No puedes desactivarte a ti mismo");
        assertThat(users.findById(adminId).orElseThrow().active()).isTrue();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        Actor stranger = Actors.companyAdmin(new CompanyId(UUID.randomUUID()));

        assertThatThrownBy(() -> update.execute(stranger, workerId, name("X"))).isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(workerId).orElseThrow().displayName()).isEqualTo("Wanda");
    }

    @Test
    void workerCannotUpdate() {
        assertThatThrownBy(() -> update.execute(Actors.worker(company), workerId, name("X")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletedUserIsNotFound() {
        users.delete(workerId, Instant.now());

        assertThatThrownBy(() -> update.execute(admin, workerId, name("X"))).isInstanceOf(NotFoundException.class);
    }
}
