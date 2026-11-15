package com.e2d.odin.bootstrap;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditEntry;

/** Para las pruebas de atomicidad que no miran el registro. */
final class NoAudit implements AuditLog {

    @Override
    public void record(AuditEntry entry) {
    }

    @Override
    public void recordDenied(AuditEntry entry) {
    }
}
