package com.e2d.odin.application.organization;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.Set;

public class SetCompanyCapabilities {

    private final OrganizationRepository organizations;

    private final AuditLog audit;

    public SetCompanyCapabilities(OrganizationRepository organizations, AuditLog audit) {
        this.organizations = organizations;
        this.audit = audit;
    }

    public Company execute(Actor actor, CompanyId id, Set<Capability> capabilities) {
        AccessPolicy.requireGroupAdmin(actor);
        Company current = organizations.findCompany(id).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        Company updated = current.withCapabilities(capabilities);
        organizations.saveCompany(updated);
        audit.record(AuditEntry.by(actor, AuditAction.COMPANY_CAPABILITIES_CHANGED).onCompany(id)
                .on("COMPANY", id.value().toString(), updated.name())
                .with("before", AuditSupport.sorted(current.capabilities()))
                .with("after", AuditSupport.sorted(updated.capabilities())));
        return updated;
    }
}
