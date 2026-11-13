package com.e2d.odin.application.link;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.organization.Project;

/** Lo que la página de subida dice antes de subir: en qué caso y en qué proyecto entrará el documento. */
public class DescribeUploadLink {

    private static final String GENERAL = "General";

    private final ResolveLink resolve;
    private final OrganizationRepository organizations;

    public DescribeUploadLink(ResolveLink resolve, OrganizationRepository organizations) {
        this.resolve = resolve;
        this.organizations = organizations;
    }

    public UploadLinkInfo execute(String token, String clientAddress) {
        ResolvedLink resolved = resolve.execute(token, clientAddress, AccessLinkKind.UPLOAD);
        var caseDocument = resolved.target();
        String project = caseDocument.projectId()
                .flatMap(id -> organizations.findProject(caseDocument.companyId(), id)).map(Project::name)
                .orElse(GENERAL);
        return new UploadLinkInfo(caseDocument.title(), project, resolved.link().expiresAt());
    }
}
