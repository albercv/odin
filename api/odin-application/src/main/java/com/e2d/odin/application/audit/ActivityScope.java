package com.e2d.odin.application.audit;

import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyScope;

final class ActivityScope {

    private ActivityScope() {
    }

    /** Solo administradores; el de empresa no puede pedir otra empresa y nunca ve filas sin empresa. */
    static CompanyScope of(Actor actor, AuditFilter filter) {
        AccessPolicy.requireAdmin(actor);
        filter.company().ifPresent(company -> AccessPolicy.requireAdminOf(actor, company));
        return actor.scope();
    }
}
