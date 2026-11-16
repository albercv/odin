package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.AuditReader;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.audit.AuditRow;
import com.e2d.odin.domain.audit.ChainHead;
import com.e2d.odin.domain.identity.Actor;
import java.util.List;
import java.util.Optional;

/** Hasta {@value #MAX_ROWS} filas con los mismos filtros; la exportación deja su propia entrada. */
public class ExportAuditActivity {

    public static final int MAX_ROWS = 10_000;

    public record Export(Optional<ChainHead> head, List<AuditRow> rows, boolean truncated) {
    }

    private final AuditReader reader;
    private final AuditLog audit;

    public ExportAuditActivity(AuditReader reader, AuditLog audit) {
        this.reader = reader;
        this.audit = audit;
    }

    public Export execute(Actor actor, AuditFilter filter) {
        List<AuditRow> rows = reader.find(ActivityScope.of(actor, filter), filter, Optional.empty(), MAX_ROWS + 1);
        Optional<ChainHead> head = reader.head();
        AuditEntry entry = AuditEntry.by(actor, AuditAction.ACTIVITY_EXPORTED).on("AUDIT_LOG", "main", null)
                .with("filters", filter.asDetails());
        audit.record(filter.company().map(entry::onCompany).orElse(entry));
        boolean truncated = rows.size() > MAX_ROWS;
        return new Export(head, truncated ? rows.subList(0, MAX_ROWS) : rows, truncated);
    }
}
