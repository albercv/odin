package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequencePersonCodeGenerator;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddGroupAdminTest {

    private static final String PASSWORD = "clave-larga-123";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakePasswordHasher passwords = new FakePasswordHasher();
    private final AddGroupAdmin add = new AddGroupAdmin(users, passwords, new SequenceIdGenerator(),
            new SequencePersonCodeGenerator("7F3K", "9ZQ2"));

    private static NewGroupAdmin person(String email, String password) {
        return new NewGroupAdmin("Gael", "Ruiz", email, Optional.ofNullable(password));
    }

    @Test
    void createsANewPersonWithItsOwnCode() {
        GroupAdminAdded added = add.execute(Actors.groupAdmin(), person(" G@Demo.test ", PASSWORD));

        assertThat(added.alreadyExisted()).isFalse();
        assertThat(added.keptPassword()).isFalse();
        User admin = users.findById(added.admin().id()).orElseThrow();
        assertThat(admin.groupAdmin()).isTrue();
        assertThat(admin.active()).isTrue();
        assertThat(admin.displayName()).isEqualTo("Gael Ruiz");
        assertThat(admin.email()).contains("g@demo.test");
        assertThat(admin.personCode()).isEqualTo("7F3K");
        assertThat(admin.passwordHash()).contains(passwords.hash(PASSWORD));
    }

    @Test
    void existingPersonGetsTheMarkAndKeepsNameAndCode() {
        User wanda = new User(new UserId(UUID.randomUUID()), "Wanda", Optional.of("Pérez"), "WAN1",
                Optional.of("w@demo.test"), false, true, Optional.of("pw:suya"));
        users.save(wanda);

        GroupAdminAdded added = add.execute(Actors.groupAdmin(), person("W@demo.test", null));

        assertThat(added.alreadyExisted()).isTrue();
        User stored = users.findById(wanda.id()).orElseThrow();
        assertThat(stored.groupAdmin()).isTrue();
        assertThat(stored.displayName()).isEqualTo("Wanda Pérez");
        assertThat(stored.personCode()).isEqualTo("WAN1");
    }

    @Test
    void passwordOnlyWhenMissing() {
        User withPassword = new User(new UserId(UUID.randomUUID()), "Ana", Optional.of("Gil"), "ANA1",
                Optional.of("ana@demo.test"), false, true, Optional.of("pw:suya"));
        User without = new User(new UserId(UUID.randomUUID()), "Beto", Optional.of("Gil"), "BET1",
                Optional.of("beto@demo.test"), false, true, Optional.empty());
        users.save(withPassword);
        users.save(without);

        GroupAdminAdded kept = add.execute(Actors.groupAdmin(), person("ana@demo.test", PASSWORD));
        assertThat(kept.keptPassword()).isTrue();
        assertThat(users.findById(withPassword.id()).orElseThrow().passwordHash()).contains("pw:suya");

        assertThatThrownBy(() -> add.execute(Actors.groupAdmin(), person("beto@demo.test", null)))
                .isInstanceOf(ValidationException.class).hasMessage("Hace falta una contraseña para administrar");
        assertThat(users.findById(without.id()).orElseThrow().groupAdmin()).isFalse();

        GroupAdminAdded given = add.execute(Actors.groupAdmin(), person("beto@demo.test", PASSWORD));
        assertThat(given.keptPassword()).isFalse();
        assertThat(users.findById(without.id()).orElseThrow().passwordHash()).contains(passwords.hash(PASSWORD));
    }

    @Test
    void emailIsRequired() {
        assertThatThrownBy(() -> add.execute(Actors.groupAdmin(), person(null, PASSWORD)))
                .isInstanceOf(ValidationException.class).hasMessage("El administrador necesita correo");
        assertThatThrownBy(() -> add.execute(Actors.groupAdmin(), person("  ", PASSWORD)))
                .isInstanceOf(ValidationException.class).hasMessage("El administrador necesita correo");
    }

    @Test
    void companyAdminIsForbidden() {
        assertThatThrownBy(() -> add.execute(Actors.companyAdmin(new CompanyId(UUID.randomUUID())),
                person("g@demo.test", PASSWORD))).isInstanceOf(ForbiddenException.class);
        assertThat(users.findByEmail("g@demo.test")).isEmpty();
    }

    @Test
    void aPersonWhoIsAlreadyGroupAdminIsConflict() {
        People.groupAdmin(users, new UserId(UUID.randomUUID()), "Gema", Optional.of("g@demo.test"), true,
                Optional.of("pw:x"));

        assertThatThrownBy(() -> add.execute(Actors.groupAdmin(), person("g@demo.test", PASSWORD)))
                .isInstanceOf(ConflictException.class).hasMessage("Esa persona ya es administradora del grupo");
    }
}
