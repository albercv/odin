package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.Set;

public class AddMember {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final PasswordHasher passwords;
    private final IdGenerator ids;

    public AddMember(UserRepository users, MembershipRepository memberships, OrganizationRepository organizations,
            PasswordHasher passwords, IdGenerator ids) {
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.passwords = passwords;
        this.ids = ids;
    }

    public MemberAdded execute(Actor actor, CompanyId companyId, NewMember newMember) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        Set<Role> roles = UserAdministration.requireValidRoles(newMember.roles());
        boolean administrator = roles.contains(Role.COMPANY_ADMIN);
        requireAllowedRoles(actor, administrator, newMember.password());
        Optional<String> email = newMember.email().filter(e -> !e.isBlank()).map(Validation::normalizeEmail);
        Optional<User> existing = email.flatMap(users::findByEmail);
        User person = existing.map(user -> requireJoinable(user, companyId))
                .orElseGet(() -> newPerson(newMember, email));
        User ready = administrator ? UserAdministration.readyToAdminister(person, newMember.password(), passwords)
                : person;
        if (existing.isEmpty() || !ready.equals(person)) {
            users.save(ready);
        }
        Membership membership = new Membership(ready.id(), companyId, roles, true);
        memberships.save(membership);
        return new MemberAdded(ready, membership, existing.isPresent());
    }

    private static void requireAllowedRoles(Actor actor, boolean administrator, Optional<String> password) {
        if (administrator) {
            UserAdministration.requireGroupAdminForAdministration(actor);
        } else if (password.filter(p -> !p.isBlank()).isPresent()) {
            throw new ValidationException(UserAdministration.WORKER_HAS_NO_PASSWORD);
        }
    }

    private User requireJoinable(User user, CompanyId companyId) {
        if (user.groupAdmin()) {
            throw new ValidationException("El administrador del grupo no pertenece a ninguna empresa");
        }
        if (memberships.find(user.id(), companyId).isPresent()) {
            throw new ConflictException("Esa persona ya pertenece a esta empresa");
        }
        return user;
    }

    private User newPerson(NewMember newMember, Optional<String> email) {
        return new User(new UserId(ids.newId()), Validation.requireText(newMember.displayName(), "el nombre"), email,
                false, true, Optional.empty());
    }
}
