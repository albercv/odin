package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
    public Optional<User> findGroupAdmin() {
        return users.values().stream().filter(u -> u.role() == Role.GROUP_ADMIN).findFirst();
    }

    @Override
    public void delete(UserId id, Instant at) {
        users.remove(id);
    }

    @Override
    public List<User> listByCompany(CompanyId companyId) {
        return users.values().stream().filter(u -> u.companyId().filter(companyId::equals).isPresent()).toList();
    }
}
