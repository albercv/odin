package com.e2d.odin.application.port;

import com.e2d.odin.domain.audit.AuditCursor;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.audit.AuditRow;
import com.e2d.odin.domain.audit.ChainHead;
import com.e2d.odin.domain.shared.CompanyScope;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Lectura del registro, de la más nueva a la más vieja, dentro del ámbito dado. */
public interface AuditReader {

    List<AuditRow> find(CompanyScope scope, AuditFilter filter, Optional<AuditCursor> after, int limit);

    Optional<ChainHead> head();

    /** El primer id cuya huella no sale de su fila o que no cuelga del anterior. */
    OptionalLong firstBroken();

    boolean sealMatches(long id, String hash);
}
