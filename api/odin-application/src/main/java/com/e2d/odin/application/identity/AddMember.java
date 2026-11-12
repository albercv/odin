package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.PersonCodeGenerator;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.organization.ProjectMembers;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.Set;

public class AddMember {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final PasswordHasher passwords;
    private final IdGenerator ids;
    private final PersonCodeGenerator personCodes;

    public AddMember(UserRepository users, MembershipRepository memberships, OrganizationRepository organizations,
            ProjectMemberRepository projectMembers, PasswordHasher passwords, IdGenerator ids,
            PersonCodeGenerator personCodes) {
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.passwords = passwords;
        this.ids = ids;
        this.personCodes = personCodes;
    }

    public MemberAdded execute(Actor actor, CompanyId companyId, NewMember newMember) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Company company = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        UserAdministration.requireNameOrLastName(newMember.firstName());
        UserAdministration.requireNameOrLastName(newMember.lastName());
        Set<Role> roles = UserAdministration.requireValidRoles(newMember.roles());
        boolean administrator = roles.contains(Role.COMPANY_ADMIN);
        if (administrator) {
            UserAdministration.requireGroupAdminForAdministration(actor);
        }
        UserAdministration.requirePasswordOnlyToAdminister(newMember.password(), administrator);
        requireAssignableProjects(companyId, roles, newMember.projectIds());
        Optional<String> email = newMember.email().filter(e -> !e.isBlank()).map(Validation::normalizeEmail);
        Optional<User> existing = email.flatMap(users::findByEmail);
        User person = existing.map(user -> requireJoinable(actor, user, companyId, roles))
                .orElseGet(() -> newPerson(newMember, email));
        User ready = administrator ? UserAdministration.readyToAdminister(person, newMember.password(), passwords)
                : person;
        if (existing.isEmpty() || !ready.equals(person)) {
            users.save(ready);
        }
        Membership membership = new Membership(ready.id(), companyId, roles, true);
        memberships.save(membership);
        newMember.projectIds().forEach(projectId -> projectMembers.add(companyId, projectId, ready.id()));
        return new MemberAdded(ready, membership, company, existing.isPresent(),
                administrator && existing.flatMap(User::passwordHash).isPresent());
    }

    private void requireAssignableProjects(CompanyId companyId, Set<Role> roles, Set<ProjectId> projectIds) {
        if (projectIds.isEmpty()) {
            return;
        }
        ProjectMembers.requireWorker(roles);
        for (ProjectId projectId : projectIds) {
            organizations.findProject(companyId, projectId)
                    .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        }
    }

    private User requireJoinable(Actor actor, User user, CompanyId companyId, Set<Role> roles) {
        UserAdministration.requireGroupAdminToActOn(actor, user);
        UserAdministration.requireNotCompanyAdminForGroupAdmin(user, roles);
        if (memberships.find(user.id(), companyId).isPresent()) {
            throw new ConflictException("Esa persona ya pertenece a esta empresa");
        }
        return user;
    }

    private User newPerson(NewMember newMember, Optional<String> email) {
        return new User(new UserId(ids.newId()), UserAdministration.requireNameOrLastName(newMember.firstName()),
                Optional.of(UserAdministration.requireNameOrLastName(newMember.lastName())), PersonCodes.next(personCodes, users), email, false,
                true, Optional.empty());
    }
}
