package com.e2d.odin.adapter.web.setup;

import com.e2d.odin.adapter.web.dto.Responses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/v1/health")
    public Responses.Health health() {
        return new Responses.Health("ok");
    }
}
