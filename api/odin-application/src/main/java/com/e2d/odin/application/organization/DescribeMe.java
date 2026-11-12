package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.List;

public class DescribeMe {

    private final OrganizationRepository organizations;

    private final ProjectMemberRepository projectMembers;

    private final TopicRepository topics;

    private final TaggingRepository tagging;

    public DescribeMe(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            TopicRepository topics, TaggingRepository tagging) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.topics = topics;
        this.tagging = tagging;
    }

    public MeView execute(Actor actor) {
        CompanyId companyId = actor.requireCompany();
        Company company = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        List<Project> alive = organizations.listProjects(companyId);
        var visibility = WorkerAccess.visibleProjects(actor, companyId, alive, projectMembers);
        return new MeView(actor.userId(), actor.displayName(), actor.role(), company,
                alive.stream().filter(p -> visibility.includes(p.id())).toList(), topics.list(companyId),
                tagging.pendingCount(companyId, visibility));
    }
}
