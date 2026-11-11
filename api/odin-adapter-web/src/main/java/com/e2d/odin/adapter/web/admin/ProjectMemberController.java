package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.organization.AddProjectMember;
import com.e2d.odin.application.organization.ListProjectMembers;
import com.e2d.odin.application.organization.RemoveProjectMember;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quién ve un proyecto cerrado. */
@RestController
@RequestMapping("/v1/admin/companies/{companyId}/projects/{projectId}/members")
public class ProjectMemberController {

    private final ListProjectMembers listMembers;
    private final AddProjectMember addMember;
    private final RemoveProjectMember removeMember;

    public ProjectMemberController(ListProjectMembers listMembers, AddProjectMember addMember,
            RemoveProjectMember removeMember) {
        this.listMembers = listMembers;
        this.addMember = addMember;
        this.removeMember = removeMember;
    }

    @GetMapping
    public List<Responses.ProjectMemberView> list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID projectId) {
        return listMembers.execute(actor, new CompanyId(companyId), new ProjectId(projectId)).stream()
                .map(Responses.ProjectMemberView::from).toList();
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID projectId, @PathVariable UUID userId) {
        addMember.execute(actor, new CompanyId(companyId), new ProjectId(projectId), new UserId(userId));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID projectId, @PathVariable UUID userId) {
        removeMember.execute(actor, new CompanyId(companyId), new ProjectId(projectId), new UserId(userId));
    }
}
