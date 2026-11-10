package com.e2d.odin.adapter.web.setup;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.LoginRateLimiter;
import com.e2d.odin.application.identity.RecoverGroupAdmin;
import com.e2d.odin.application.identity.SetUpInstallation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetupController {

    private final SetUpInstallation setUp;
    private final RecoverGroupAdmin recoverGroupAdmin;
    private final LoginRateLimiter limiter;

    public SetupController(SetUpInstallation setUp, RecoverGroupAdmin recoverGroupAdmin, LoginRateLimiter limiter) {
        this.setUp = setUp;
        this.recoverGroupAdmin = recoverGroupAdmin;
        this.limiter = limiter;
    }

    @PostMapping("/v1/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.UserRef setup(@RequestBody Requests.Setup request) {
        return new Responses.UserRef(setUp.execute(request.setupToken(), request.groupName(), request.adminName(),
                request.adminEmail(), request.password()).value());
    }

    @PostMapping("/v1/setup/admin-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recoverAdmin(@RequestBody Requests.RecoverAdmin request, HttpServletRequest http) {
        limiter.check("setup", http.getRemoteAddr());
        recoverGroupAdmin.execute(request.setupToken(), request.newPassword());
    }
}
