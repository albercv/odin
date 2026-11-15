package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.Map;

public class UpdateCompany {

    private final OrganizationRepository organizations;

    private final AuditLog audit;

    public UpdateCompany(OrganizationRepository organizations, AuditLog audit) {
        this.organizations = organizations;
        this.audit = audit;
    }

    public Company execute(Actor actor, CompanyId id, CompanyChanges changes) {
        AccessPolicy.requireGroupAdmin(actor);
        Company company = organizations.findCompany(id).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        Company named = changes.name().map(n -> company.withName(Validation.requireText(n, "el nombre")))
                .orElse(company);
        Company coded = changes.code().map(named::withCode).orElse(named);
        Company updated = changes.projectLabel().map(coded::withProjectLabel).orElse(coded).withCodeReviewed(true);
        CompanyCodes.requireFree(organizations, updated.code(), id);
        organizations.saveCompany(updated);
        AuditEntry entry = AuditEntry.by(actor, AuditAction.COMPANY_UPDATED).onCompany(id)
                .on("COMPANY", id.value().toString(), updated.name())
                .with("nameChanged", !company.name().equals(updated.name()))
                .with("codeChanged", !company.code().equals(updated.code()));
        if (!company.projectLabel().equals(updated.projectLabel())) {
            entry = entry.with("before", Map.of("projectLabel", company.projectLabel()))
                    .with("after", Map.of("projectLabel", updated.projectLabel()));
        }
        audit.record(entry);
        return updated;
    }
}
