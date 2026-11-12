package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> users = new LinkedHashMap<>();
    private final Set<String> personCodes = new HashSet<>();

    @Override
    public void save(User u) {
        users.put(u.id(), u);
        personCodes.add(u.personCode());
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.values().stream().filter(u -> u.email().filter(email::equalsIgnoreCase).isPresent()).findFirst();
    }

    @Override
    public List<User> findAllById(Collection<UserId> ids) {
        return users.values().stream().filter(u -> ids.contains(u.id())).toList();
    }

    @Override
    public List<User> listGroupAdmins() {
        return users.values().stream().filter(User::groupAdmin).toList();
    }

    @Override
    public Optional<User> findGroupAdminByEmail(String email) {
        return listGroupAdmins().stream().filter(u -> u.email().filter(email::equalsIgnoreCase).isPresent())
                .findFirst();
    }

    @Override
    public boolean personCodeExists(String personCode) {
        return personCodes.contains(personCode);
    }

    @Override
    public void delete(UserId id, Instant at) {
        users.remove(id);
    }
}
