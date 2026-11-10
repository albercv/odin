package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UnauthorizedException;
import java.util.Locale;

public class LogInAdmin {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordHasher passwords;
    private final AdminSessions sessions;

    public LogInAdmin(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            AdminSessions sessions) {
        this.users = users;
        this.memberships = memberships;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    public NewSession execute(String email, String password) {
        User user = email == null || password == null ? null
                : users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).filter(u -> canLogIn(u, password))
                        .orElse(null);
        if (user == null) {
            throw new UnauthorizedException(ResolveActor.INVALID_CREDENTIALS);
        }
        return new NewSession(sessions.open(user.id()), user.id());
    }

    private boolean canLogIn(User user, String password) {
        return user.active() && administersSomething(user)
                && user.passwordHash().filter(hash -> passwords.matches(password, hash)).isPresent();
    }

    private boolean administersSomething(User user) {
        return user.groupAdmin() || !UserAdministration.companiesAdministeredBy(memberships, user.id()).isEmpty();
    }
}
