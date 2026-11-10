package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MembershipRepository {

    void save(Membership membership);

    Optional<Membership> find(UserId userId, CompanyId companyId);

    List<Membership> listByCompany(CompanyId companyId);

    List<Membership> listByUser(UserId userId);

    void delete(UserId userId, CompanyId companyId, Instant at);
}
