package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;

/** La sesión existía y ha caducado; lleva de quién era para dejar constancia. */
public class SessionExpiredException extends UnauthorizedException {

    public enum Reason { IDLE, ABSOLUTE }

    private final UserId userId;
    private final Reason reason;

    public SessionExpiredException(String message, UserId userId, Reason reason) {
        super(message);
        this.userId = userId;
        this.reason = reason;
    }

    public UserId userId() {
        return userId;
    }

    public Reason reason() {
        return reason;
    }
}
