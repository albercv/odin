package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.PersonCodeGenerator;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

/** Da la marca de administrador del grupo a una persona que ya existe o la crea. Solo el administrador del grupo. */
public class AddGroupAdmin {

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final IdGenerator ids;
    private final PersonCodeGenerator personCodes;

    public AddGroupAdmin(UserRepository users, PasswordHasher passwords, IdGenerator ids,
            PersonCodeGenerator personCodes) {
        this.users = users;
        this.passwords = passwords;
        this.ids = ids;
        this.personCodes = personCodes;
    }

    public GroupAdminAdded execute(Actor actor, NewGroupAdmin request) {
        AccessPolicy.requireGroupAdmin(actor);
        String email = Optional.ofNullable(request.email()).filter(e -> !e.isBlank()).map(Validation::normalizeEmail)
                .orElseThrow(() -> new ValidationException(UserAdministration.ADMINISTRATOR_NEEDS_EMAIL));
        Optional<User> existing = users.findByEmail(email);
        existing.filter(User::groupAdmin).ifPresent(user -> {
            throw new ConflictException("Esa persona ya es administradora del grupo");
        });
        User person = existing.orElseGet(() -> newPerson(request, email));
        User admin = UserAdministration.readyToAdminister(person, request.password(), passwords).withGroupAdmin(true);
        users.save(admin);
        return new GroupAdminAdded(admin, existing.isPresent(), existing.flatMap(User::passwordHash).isPresent());
    }

    private User newPerson(NewGroupAdmin request, String email) {
        return new User(new UserId(ids.newId()), UserAdministration.requireNameOrLastName(request.firstName()),
                Optional.of(UserAdministration.requireNameOrLastName(request.lastName())),
                PersonCodes.next(personCodes, users), Optional.of(email), true, true, Optional.empty());
    }
}
