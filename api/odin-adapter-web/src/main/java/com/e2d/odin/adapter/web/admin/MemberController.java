package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.identity.AddMember;
import com.e2d.odin.application.identity.IssueAccessKey;
import com.e2d.odin.application.identity.ListAccessKeys;
import com.e2d.odin.application.identity.ListMembers;
import com.e2d.odin.application.identity.MemberChanges;
import com.e2d.odin.application.identity.NewMember;
import com.e2d.odin.application.identity.RemoveMember;
import com.e2d.odin.application.identity.UpdateMember;
import com.e2d.odin.application.organization.ListMemberProjects;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Las pertenencias de una empresa: quién está en ella, con qué papeles y con qué claves. */
@RestController
@RequestMapping("/v1/admin/companies/{companyId}/members")
public class MemberController {

    private final ListMembers listMembers;
    private final AddMember addMember;
    private final UpdateMember updateMember;
    private final RemoveMember removeMember;
    private final IssueAccessKey issueKey;
    private final ListAccessKeys listKeys;
    private final ListMemberProjects listMemberProjects;

    public MemberController(ListMembers listMembers, AddMember addMember, UpdateMember updateMember,
            RemoveMember removeMember, IssueAccessKey issueKey, ListAccessKeys listKeys,
            ListMemberProjects listMemberProjects) {
        this.listMembers = listMembers;
        this.addMember = addMember;
        this.updateMember = updateMember;
        this.removeMember = removeMember;
        this.issueKey = issueKey;
        this.listKeys = listKeys;
        this.listMemberProjects = listMemberProjects;
    }

    @GetMapping
    public List<Responses.MemberView> list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId) {
        return listMembers.execute(actor, new CompanyId(companyId)).stream().map(Responses.MemberView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.MemberAdded add(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.AddMember request) {
        NewMember member = new NewMember(request.firstName(), request.lastName(), Optional.ofNullable(request.email()),
                request.roles(), Optional.ofNullable(request.password()), projectIds(request));
        return Responses.MemberAdded.from(addMember.execute(actor, new CompanyId(companyId), member));
    }

    private static Set<ProjectId> projectIds(Requests.AddMember request) {
        return request.projectIds() == null ? Set.of()
                : request.projectIds().stream().map(ProjectId::new).collect(Collectors.toSet());
    }

    @GetMapping("/{userId}/projects")
    public List<Responses.ProjectView> projects(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID userId) {
        return listMemberProjects.execute(actor, new CompanyId(companyId), new UserId(userId)).stream()
                .map(Responses.ProjectView::from).toList();
    }

    @PatchMapping("/{userId}")
    public Responses.MemberView update(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID userId, @RequestBody Requests.UpdateMember request) {
        MemberChanges changes = new MemberChanges(Optional.ofNullable(request.roles()),
                Optional.ofNullable(request.active()), Optional.ofNullable(request.password()));
        return Responses.MemberView.from(
                updateMember.execute(actor, new CompanyId(companyId), new UserId(userId), changes));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID userId) {
        removeMember.execute(actor, new CompanyId(companyId), new UserId(userId));
    }

    @GetMapping("/{userId}/keys")
    public List<Responses.KeyView> keys(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID userId) {
        return listKeys.execute(actor, new CompanyId(companyId), new UserId(userId)).stream()
                .map(Responses.KeyView::from).toList();
    }

    @PostMapping("/{userId}/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.IssuedKeyView issue(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID userId, @RequestBody Requests.KeyLabel request) {
        return Responses.IssuedKeyView.from(
                issueKey.execute(actor, new CompanyId(companyId), new UserId(userId), request.label()));
    }
}
