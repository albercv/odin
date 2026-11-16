package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditReader;
import com.e2d.odin.domain.audit.AuditCursor;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.audit.AuditRow;
import com.e2d.odin.domain.identity.Actor;
import java.util.List;
import java.util.Optional;

/** Una página de 50 del registro de actividad, siempre por fecha e id descendentes. */
public class ListAuditActivity {

    public static final int PAGE_SIZE = 50;

    private final AuditReader reader;

    public ListAuditActivity(AuditReader reader) {
        this.reader = reader;
    }

    public AuditPage execute(Actor actor, AuditFilter filter, Optional<String> cursor) {
        return execute(actor, filter, cursor, PAGE_SIZE);
    }

    /** Una página más corta que la normal, para quien tiene un tope de filas. */
    public AuditPage execute(Actor actor, AuditFilter filter, Optional<String> cursor, int pageSize) {
        List<AuditRow> rows = reader.find(ActivityScope.of(actor, filter), filter, cursor.map(AuditCursor::decode),
                pageSize + 1);
        if (rows.size() <= pageSize) {
            return new AuditPage(rows, Optional.empty());
        }
        List<AuditRow> items = rows.subList(0, pageSize);
        AuditRow last = items.get(pageSize - 1);
        return new AuditPage(items, Optional.of(new AuditCursor(last.occurredAt(), last.id()).encode()));
    }
}
