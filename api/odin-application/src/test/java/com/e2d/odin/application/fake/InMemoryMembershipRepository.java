package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryMembershipRepository implements MembershipRepository {

    private record Key(UserId userId, CompanyId companyId) {
    }

    private final Map<Key, Membership> memberships = new LinkedHashMap<>();

    @Override
    public void save(Membership m) {
        memberships.put(new Key(m.userId(), m.companyId()), m);
    }

    @Override
    public Optional<Membership> find(UserId userId, CompanyId companyId) {
        return Optional.ofNullable(memberships.get(new Key(userId, companyId)));
    }

    @Override
    public List<Membership> listByCompany(CompanyId companyId) {
        return memberships.values().stream().filter(m -> m.companyId().equals(companyId)).toList();
    }

    @Override
    public List<Membership> listByUser(UserId userId) {
        return memberships.values().stream().filter(m -> m.userId().equals(userId)).toList();
    }

    @Override
    public void delete(UserId userId, CompanyId companyId, Instant at) {
        memberships.remove(new Key(userId, companyId));
    }
}
