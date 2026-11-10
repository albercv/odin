package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.InstallationToken;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Group;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.GroupId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public class SetUpInstallation {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final IdGenerator ids;
    private final InstallationSettings settings;

    public SetUpInstallation(OrganizationRepository organizations, UserRepository users, PasswordHasher passwords,
            IdGenerator ids, InstallationSettings settings) {
        this.organizations = organizations;
        this.users = users;
        this.passwords = passwords;
        this.ids = ids;
        this.settings = settings;
    }

    public UserId execute(String setupToken, String groupName, String adminName, String adminEmail,
            String password) {
        InstallationToken.require(settings, setupToken);
        if (organizations.findGroup().isPresent()) {
            throw new ConflictException("La instalación ya está configurada");
        }
        String name = Validation.requireText(groupName, "el nombre del grupo");
        String email = Validation.normalizeEmail(adminEmail);
        Validation.requirePassword(password);
        organizations.saveGroup(new Group(new GroupId(ids.newId()), name));
        UserId id = new UserId(ids.newId());
        users.save(new User(id, Validation.requireText(adminName, "el nombre"), Optional.of(email),
                Role.GROUP_ADMIN, Optional.empty(), true, Optional.of(passwords.hash(password))));
        return id;
    }
}
