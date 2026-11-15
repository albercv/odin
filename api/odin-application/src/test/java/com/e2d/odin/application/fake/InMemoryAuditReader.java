package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AuditReader;
import com.e2d.odin.domain.audit.AuditCursor;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.audit.AuditRow;
import com.e2d.odin.domain.audit.ChainHead;
import com.e2d.odin.domain.shared.CompanyScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Devuelve las filas que se le den, sin filtrar; lo que importa del ámbito se prueba contra la base real. */
public class InMemoryAuditReader implements AuditReader {

    public final List<AuditRow> rows = new ArrayList<>();
    public CompanyScope lastScope;

    @Override
    public List<AuditRow> find(CompanyScope scope, AuditFilter filter, Optional<AuditCursor> after, int limit) {
        lastScope = scope;
        return rows.stream().limit(limit).toList();
    }

    @Override
    public Optional<ChainHead> head() {
        return Optional.empty();
    }

    @Override
    public OptionalLong firstBroken() {
        return OptionalLong.empty();
    }

    @Override
    public boolean sealMatches(long id, String hash) {
        return false;
    }
}
