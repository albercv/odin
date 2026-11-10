package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> users = new LinkedHashMap<>();

    @Override
    public void save(User u) {
        users.put(u.id(), u);
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
        return ids.stream().map(users::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<User> findGroupAdmin() {
        return users.values().stream().filter(u -> u.groupAdmin()).findFirst();
    }

    @Override
    public void delete(UserId id, Instant at) {
        users.remove(id);
    }
}
