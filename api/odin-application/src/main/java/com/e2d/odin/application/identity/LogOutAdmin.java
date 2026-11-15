package com.e2d.odin.application.identity;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditContext;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;

public class LogOutAdmin {

    private final AdminSessions sessions;
    private final AuditLog audit;
    private final AuditContext context;

    public LogOutAdmin(AdminSessions sessions, AuditLog audit, AuditContext context) {
        this.sessions = sessions;
        this.audit = audit;
        this.context = context;
    }

    public void execute(Actor actor, String token) {
        audit.record(AuditSupport.withClient(AuditEntry.by(actor, AuditAction.LOGOUT), context.current()));
        sessions.close(token);
    }
}
