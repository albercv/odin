package com.e2d.odin.domain.audit;

/** Lista cerrada de lo que se registra; cada tarea de la monitorización añade las suyas. */
public enum AuditAction {
    AUDIT_ENABLED,
    AUDIT_DISABLED,
    AUDIT_SETTINGS_UPDATED,
    REAUTHENTICATION_FAILED
}
