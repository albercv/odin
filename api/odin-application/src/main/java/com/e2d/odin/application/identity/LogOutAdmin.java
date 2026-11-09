package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.application.port.SecretHasher;

public class LogOutAdmin {

    private final AdminSessionRepository sessions;
    private final SecretHasher secrets;

    public LogOutAdmin(AdminSessionRepository sessions, SecretHasher secrets) {
        this.sessions = sessions;
        this.secrets = secrets;
    }

    public void execute(String token) {
        if (token != null) {
            sessions.delete(secrets.hash(token));
        }
    }
}
