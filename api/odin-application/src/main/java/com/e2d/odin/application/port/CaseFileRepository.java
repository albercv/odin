package com.e2d.odin.application.port;

import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface CaseFileRepository {

    /** Devuelve {@code false} si el documento ya estaba en el expediente. */
    boolean add(CaseFileEntry entry);

    /**
     * Añade las relaciones nuevas y, de cada una, su evento, todo en una transacción: si algo falla no queda nada.
     * Devuelve cuántas eran nuevas; las que ya estaban no dejan evento.
     */
    int addAll(List<CaseFileEntry> entries, Function<CaseFileEntry, CaseFileEvent> eventFor);

    /** Quita la relación del evento y lo registra, juntos; {@code false} si la relación no existía. */
    boolean removeAndRecord(CaseFileEvent event);

    boolean remove(CompanyId companyId, DocumentId caseId, DocumentId documentId);

    void record(CaseFileEvent event);

    /** Solo las relaciones cuyos dos documentos ve la persona. */
    List<CaseFileEntry> entries(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId);

    List<CaseFileEvent> events(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId);

    int countEntries(CompanyId companyId, DocumentId caseId);

    /**
     * Quita las relaciones del caso que ya no se ven (por un documento retirado) y deja un REMOVED de cada una, a
     * nombre de {@code actor}, en una transacción. Devuelve cuántas quitó.
     */
    int removeHiddenEntries(CompanyId companyId, DocumentId caseId, UserId actor, Instant at);

    /** Páginas guardadas de cada documento visible, en una sola consulta; los que no tienen páginas no salen. */
    Map<DocumentId, Integer> pageCounts(CompanyScope scope, ProjectVisibility visibility, Collection<DocumentId> ids);
}
