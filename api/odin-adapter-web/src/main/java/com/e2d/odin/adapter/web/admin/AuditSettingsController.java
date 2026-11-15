package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.audit.GetAuditSettings;
import com.e2d.odin.application.audit.UpdateAuditSettings;
import com.e2d.odin.domain.identity.Actor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El interruptor del registro de actividad: solo el administrador del grupo, y cambiarlo pide la contraseña. */
@RestController
@RequestMapping("/v1/admin/audit-settings")
public class AuditSettingsController {

    private final GetAuditSettings get;
    private final UpdateAuditSettings update;

    public AuditSettingsController(GetAuditSettings get, UpdateAuditSettings update) {
        this.get = get;
        this.update = update;
    }

    @GetMapping
    public Responses.AuditSettingsView get(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor) {
        return Responses.AuditSettingsView.from(get.execute(actor));
    }

    @PutMapping
    public Responses.AuditSettingsView update(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @RequestBody Requests.UpdateAuditSettings request) {
        return Responses.AuditSettingsView.from(update.execute(actor, request.password(), request.enabled(),
                request.usagePolicyReference(), request.chatEnabled()));
    }
}
