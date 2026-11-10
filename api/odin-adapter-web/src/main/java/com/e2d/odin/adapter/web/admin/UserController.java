package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.identity.ChangePassword;
import com.e2d.odin.application.identity.DeleteUser;
import com.e2d.odin.application.identity.RevokeAccessKey;
import com.e2d.odin.application.identity.UpdateUser;
import com.e2d.odin.application.identity.UserChanges;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UpdateUser updateUser;
    private final DeleteUser deleteUser;
    private final ChangePassword changePassword;
    private final RevokeAccessKey revokeKey;

    public UserController(UpdateUser updateUser, DeleteUser deleteUser, ChangePassword changePassword,
            RevokeAccessKey revokeKey) {
        this.updateUser = updateUser;
        this.deleteUser = deleteUser;
        this.changePassword = changePassword;
        this.revokeKey = revokeKey;
    }

    @PatchMapping("/v1/admin/users/{userId}")
    public Responses.UserRef update(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID userId, @RequestBody Requests.UpdateUser request) {
        UserChanges changes = new UserChanges(Optional.ofNullable(request.displayName()),
                Optional.ofNullable(request.email()), Optional.ofNullable(request.active()));
        return new Responses.UserRef(updateUser.execute(actor, new UserId(userId), changes).id().value());
    }

    @DeleteMapping("/v1/admin/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID userId) {
        deleteUser.execute(actor, new UserId(userId));
    }

    @PutMapping("/v1/admin/users/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID userId,
            @RequestBody Requests.SetPassword request) {
        changePassword.execute(actor, new UserId(userId), Optional.ofNullable(request.currentPassword()),
                request.newPassword());
    }

    @DeleteMapping("/v1/admin/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID keyId) {
        revokeKey.execute(actor, new AccessKeyId(keyId));
    }
}
