package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.Optional;

/**
 * Qué ve quien toca un expediente: el trabajador, los proyectos que ve de su empresa; el administrador, todos los de
 * su empresa. Un documento que no se ve es un 404, igual que uno que no existe.
 */
public record CaseFileAccess(CompanyScope scope, ProjectVisibility visibility) {

    /** Para leer: el trabajador ve también el contenido de los proyectos borrados que veía. */
    public static CaseFileAccess of(Actor actor, Optional<CompanyId> adminCompany, OrganizationRepository organizations,
            ProjectMemberRepository projectMembers) {
        return access(actor, adminCompany, organizations, projectMembers, false);
    }

    /** Para escribir: el trabajador solo toca proyectos vivos; en un proyecto borrado no se añade ni se enlaza nada. */
    public static CaseFileAccess forWrite(Actor actor, Optional<CompanyId> adminCompany,
            OrganizationRepository organizations, ProjectMemberRepository projectMembers) {
        return access(actor, adminCompany, organizations, projectMembers, true);
    }

    private static CaseFileAccess access(Actor actor, Optional<CompanyId> adminCompany,
            OrganizationRepository organizations, ProjectMemberRepository projectMembers, boolean write) {
        if (actor.role() == Role.WORKER) {
            var granted = WorkerAccess.require(actor, organizations, projectMembers, Capability.CASE_FILES);
            return new CaseFileAccess(CompanyScope.of(granted.companyId()),
                    write ? granted.visibility() : granted.content());
        }
        adminCompany.ifPresentOrElse(company -> AccessPolicy.requireAdminOf(actor, company),
                () -> AccessPolicy.requireAdmin(actor));
        return new CaseFileAccess(adminCompany.map(CompanyScope::of).orElseGet(actor::scope),
                ProjectVisibility.all());
    }

    public Document document(DocumentRepository documents, DocumentId id) {
        return documents.findById(scope, visibility, id).filter(d -> d.withdrawnAt().isEmpty())
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
    }

    public Document caseDocument(DocumentRepository documents, DocumentId id) {
        return documents.findById(scope, visibility, id).filter(d -> d.caseDocument() && d.withdrawnAt().isEmpty())
                .orElseThrow(() -> new NotFoundException("Caso no encontrado"));
    }

    Optional<DocumentEntry> entry(DocumentRepository documents, DocumentId id) {
        return documents.findEntry(scope, visibility, id);
    }
}
