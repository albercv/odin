package com.e2d.odin.application.port;

import com.e2d.odin.domain.audit.AuditEntry;

public interface AuditLog {

    /** En la transacción en curso: si el cambio se deshace, la entrada también. No escribe nada si el registro está apagado. */
    void record(AuditEntry entry);

    /**
     * En su propia transacción: queda aunque la del negocio se deshaga. No debe llamarse tras haber escrito otra entrada
     * en la transacción en curso, porque las dos esperarían al mismo bloqueo de la cadena.
     */
    void recordDenied(AuditEntry entry);
}
