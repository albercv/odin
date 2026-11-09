package com.e2d.odin.adapter.web.setup;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.application.identity.SetUpInstallation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetupController {

    private final SetUpInstallation setUp;

    public SetupController(SetUpInstallation setUp) {
        this.setUp = setUp;
    }

    @PostMapping("/v1/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.UserRef setup(@RequestBody Requests.Setup request) {
        return new Responses.UserRef(setUp.execute(request.setupToken(), request.groupName(), request.adminName(),
                request.adminEmail(), request.password()).value());
    }
}
