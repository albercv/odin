package com.e2d.odin.application.identity;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditContext;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UnauthorizedException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class LogInAdmin {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordHasher passwords;
    private final AdminSessions sessions;
    private final AuditLog audit;
    private final AuditContext context;

    public LogInAdmin(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            AdminSessions sessions, AuditLog audit, AuditContext context) {
        this.audit = audit;
        this.context = context;
        this.users = users;
        this.memberships = memberships;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    public NewSession execute(String email, String password) {
        Optional<User> found = email == null || password == null ? Optional.empty()
                : users.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        User user = found.filter(u -> canLogIn(u, password)).orElse(null);
        if (user == null) {
            audit.recordDenied(AuditSupport.withClient(failedEntry(found), context.current()));
            throw new UnauthorizedException(ResolveActor.INVALID_CREDENTIALS);
        }
        NewSession session = new NewSession(sessions.open(user.id()), user.id());
        audit.record(AuditSupport.withClient(AuditEntry.by(actorOf(user), AuditAction.LOGIN_SUCCEEDED),
                context.current()));
        return session;
    }

    /** Sin el correo escrito: solo si la persona existe, y si no, que era desconocida. */
    private AuditEntry failedEntry(Optional<User> found) {
        return found.map(u -> AuditEntry.by(actorOf(u), AuditAction.LOGIN_FAILED).denied().with("knownUser", true))
                .orElseGet(() -> AuditEntry.byUnknownPerson(AuditAction.LOGIN_FAILED).denied()
                        .with("knownUser", false));
    }

    private Actor actorOf(User user) {
        Role role = user.groupAdmin() ? Role.GROUP_ADMIN
                : administersSomething(user) ? Role.COMPANY_ADMIN : Role.WORKER;
        return new Actor(user.id(), user.displayName(), role, Set.of());
    }

    private boolean canLogIn(User user, String password) {
        return user.active() && administersSomething(user)
                && user.passwordHash().filter(hash -> passwords.matches(password, hash)).isPresent();
    }

    private boolean administersSomething(User user) {
        return user.groupAdmin() || !UserAdministration.companiesAdministeredBy(memberships, user.id()).isEmpty();
    }
}
