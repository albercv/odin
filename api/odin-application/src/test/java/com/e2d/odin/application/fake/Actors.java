package com.e2d.odin.application.fake;

import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;

public final class Actors {

    private Actors() {
    }

    public static Actor groupAdmin() {
        return new Actor(new UserId(UUID.randomUUID()), "Gema", Role.GROUP_ADMIN, Optional.empty());
    }

    public static Actor companyAdmin(CompanyId company) {
        return new Actor(new UserId(UUID.randomUUID()), "Carlos", Role.COMPANY_ADMIN, Optional.of(company));
    }

    public static Actor worker(CompanyId company) {
        return new Actor(new UserId(UUID.randomUUID()), "Wanda", Role.WORKER, Optional.of(company));
    }
}
