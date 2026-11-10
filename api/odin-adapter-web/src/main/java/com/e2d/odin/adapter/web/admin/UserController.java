package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.identity.ChangePassword;
import com.e2d.odin.application.identity.DeleteUser;
import com.e2d.odin.application.identity.IssueAccessKey;
import com.e2d.odin.application.identity.ListAccessKeys;
import com.e2d.odin.application.identity.ListUsers;
import com.e2d.odin.application.identity.NewUser;
import com.e2d.odin.application.identity.RegisterUser;
import com.e2d.odin.application.identity.RevokeAccessKey;
import com.e2d.odin.application.identity.UpdateUser;
import com.e2d.odin.application.identity.UserChanges;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final RegisterUser registerUser;
    private final ListUsers listUsers;
    private final UpdateUser updateUser;
    private final DeleteUser deleteUser;
    private final ChangePassword changePassword;
    private final IssueAccessKey issueKey;
    private final ListAccessKeys listKeys;
    private final RevokeAccessKey revokeKey;

    public UserController(RegisterUser registerUser, ListUsers listUsers, UpdateUser updateUser,
            DeleteUser deleteUser, ChangePassword changePassword, IssueAccessKey issueKey, ListAccessKeys listKeys,
            RevokeAccessKey revokeKey) {
        this.registerUser = registerUser;
        this.listUsers = listUsers;
        this.updateUser = updateUser;
        this.deleteUser = deleteUser;
        this.changePassword = changePassword;
        this.issueKey = issueKey;
        this.listKeys = listKeys;
        this.revokeKey = revokeKey;
    }

    @GetMapping("/v1/admin/companies/{companyId}/users")
    public List<Responses.UserView> list(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId) {
        return listUsers.execute(actor, new CompanyId(companyId)).stream().map(Responses.UserView::from).toList();
    }

    @PostMapping("/v1/admin/companies/{companyId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.UserView create(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.CreateUser request) {
        if (request.role() == null) {
            throw new ValidationException("Falta el rol");
        }
        NewUser user = new NewUser(request.displayName(), Optional.ofNullable(request.email()), request.role(),
                Optional.ofNullable(request.password()));
        return Responses.UserView.from(registerUser.execute(actor, new CompanyId(companyId), user));
    }

    @PatchMapping("/v1/admin/users/{userId}")
    public Responses.UserView update(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID userId, @RequestBody Requests.UpdateUser request) {
        UserChanges changes = new UserChanges(Optional.ofNullable(request.displayName()),
                Optional.ofNullable(request.email()), Optional.ofNullable(request.active()));
        return Responses.UserView.from(updateUser.execute(actor, new UserId(userId), changes));
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

    @GetMapping("/v1/admin/users/{userId}/keys")
    public List<Responses.KeyView> keys(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID userId) {
        return listKeys.execute(actor, new UserId(userId)).stream().map(Responses.KeyView::from).toList();
    }

    @PostMapping("/v1/admin/users/{userId}/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.IssuedKeyView issue(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID userId, @RequestBody Requests.KeyLabel request) {
        return Responses.IssuedKeyView.from(issueKey.execute(actor, new UserId(userId), request.label()));
    }

    @DeleteMapping("/v1/admin/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID keyId) {
        revokeKey.execute(actor, new AccessKeyId(keyId));
    }
}
