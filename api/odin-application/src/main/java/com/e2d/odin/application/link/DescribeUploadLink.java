package com.e2d.odin.application.link;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.organization.ProjectLabel;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Instant;
import java.util.Optional;

/** Lo que la página de subida dice antes de subir: en qué proyecto, y en qué caso si lo hay, entrará el documento. */
public class DescribeUploadLink {

    private final ResolveLink resolve;
    private final OrganizationRepository organizations;

    public DescribeUploadLink(ResolveLink resolve, OrganizationRepository organizations) {
        this.resolve = resolve;
        this.organizations = organizations;
    }

    public UploadLinkInfo execute(String token, String clientAddress) {
        ResolvedLink resolved = resolve.execute(token, clientAddress, AccessLinkKind.UPLOAD);
        Instant expiresAt = resolved.link().expiresAt();
        ProjectLabel label = organizations.findCompany(resolved.link().companyId()).map(Company::projectLabel)
                .orElse(ProjectLabel.DEFAULT);
        return switch (resolved.target()) {
            case LinkTarget.ProjectTarget p ->
                    new UploadLinkInfo(UploadTargetKind.PROJECT, Optional.empty(), p.project().name(), label, expiresAt);
            case LinkTarget.CaseTarget c -> new UploadLinkInfo(UploadTargetKind.CASE,
                    Optional.of(c.caseDocument().title()), projectNameOf(c.caseDocument()), label, expiresAt);
            case LinkTarget.OriginalTarget o -> throw new NotFoundException("Enlace no encontrado");
        };
    }

    private String projectNameOf(Document caseDocument) {
        return organizations.findProject(caseDocument.companyId(), caseDocument.projectId().orElseThrow())
                .map(Project::name).orElseThrow(() -> new NotFoundException("Enlace no encontrado"));
    }
}
