package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;

public class LogOutAdmin {

    private final AdminSessions sessions;

    public LogOutAdmin(AdminSessions sessions) {
        this.sessions = sessions;
    }

    public void execute(String token) {
        sessions.close(token);
    }
}
