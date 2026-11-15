package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.identity.DescribeAdminChatKey;
import com.e2d.odin.application.identity.IssueAdminChatKey;
import com.e2d.odin.application.identity.IssuedAdminChatKey;
import com.e2d.odin.application.identity.RevokeAdminChatKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
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

/** La clave del administrador para consultar el registro desde su chat: emitir, ver la vigente y revocar. */
@RestController
@RequestMapping("/v1/admin/activity-keys")
public class ActivityKeyController {

    private final IssueAdminChatKey issue;
    private final DescribeAdminChatKey describe;
    private final RevokeAdminChatKey revoke;

    public ActivityKeyController(IssueAdminChatKey issue, DescribeAdminChatKey describe, RevokeAdminChatKey revoke) {
        this.issue = issue;
        this.describe = describe;
        this.revoke = revoke;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.IssuedActivityKeyView issue(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.IssueActivityKey request) {
        IssuedAdminChatKey key = issue.execute(actor, request.password(),
                Optional.ofNullable(request.companyId()).map(CompanyId::new));
        return new Responses.IssuedActivityKeyView(key.id(), key.prefix(), key.token(), key.expiresAt());
    }

    @GetMapping
    public Responses.ActivityKeysView current(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor) {
        var d = describe.execute(actor);
        return new Responses.ActivityKeysView(d.chatAvailable(),
                d.key().map(Responses.ActivityKeyView::from).orElse(null));
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID keyId) {
        revoke.execute(actor, keyId);
    }
}
