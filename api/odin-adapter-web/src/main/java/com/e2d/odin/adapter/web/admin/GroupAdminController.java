package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.identity.AddGroupAdmin;
import com.e2d.odin.application.identity.ListGroupAdmins;
import com.e2d.odin.application.identity.NewGroupAdmin;
import com.e2d.odin.application.identity.RevokeGroupAdmin;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Los administradores del grupo: solo los gestiona un administrador del grupo. */
@RestController
@RequestMapping("/v1/admin/group-admins")
public class GroupAdminController {

    private final ListGroupAdmins listGroupAdmins;
    private final AddGroupAdmin addGroupAdmin;
    private final RevokeGroupAdmin revokeGroupAdmin;

    public GroupAdminController(ListGroupAdmins listGroupAdmins, AddGroupAdmin addGroupAdmin,
            RevokeGroupAdmin revokeGroupAdmin) {
        this.listGroupAdmins = listGroupAdmins;
        this.addGroupAdmin = addGroupAdmin;
        this.revokeGroupAdmin = revokeGroupAdmin;
    }

    @GetMapping
    public List<Responses.GroupAdminView> list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor) {
        return listGroupAdmins.execute(actor).stream().map(Responses.GroupAdminView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.GroupAdminAdded add(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.AddGroupAdmin request) {
        return Responses.GroupAdminAdded.from(addGroupAdmin.execute(actor, new NewGroupAdmin(request.firstName(),
                request.lastName(), request.email(), Optional.ofNullable(request.password()))));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID userId) {
        revokeGroupAdmin.execute(actor, new UserId(userId));
    }
}
