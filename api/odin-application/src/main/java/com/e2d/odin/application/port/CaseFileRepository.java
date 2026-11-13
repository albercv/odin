package com.e2d.odin.application.port;

import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface CaseFileRepository {

    /** Devuelve {@code false} si el documento ya estaba en el expediente. */
    boolean add(CaseFileEntry entry);

    /**
     * Añade las relaciones nuevas y, de cada una, su evento, todo en una transacción: si algo falla no queda nada.
     * Devuelve cuántas eran nuevas; las que ya estaban no dejan evento.
     */
    int addAll(List<CaseFileEntry> entries, Function<CaseFileEntry, CaseFileEvent> eventFor);

    /**
     * Guarda un documento nuevo y lo añade al expediente con su ADDED, todo en una transacción: si algo falla no queda
     * ni el documento. {@code ConflictException} si ya hay otro igual (mismo fichero en el mismo proyecto).
     */
    void addNewDocument(Document document, CaseFileEntry entry, CaseFileEvent event);

    /** Quita la relación del evento y lo registra, juntos; {@code false} si la relación no existía. */
    boolean removeAndRecord(CaseFileEvent event);

    boolean remove(CompanyId companyId, DocumentId caseId, DocumentId documentId);

    void record(CaseFileEvent event);

    /** Solo las relaciones cuyos dos documentos ve la persona. */
    List<CaseFileEntry> entries(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId);

    List<CaseFileEvent> events(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId);

    int countEntries(CompanyId companyId, DocumentId caseId);

    /**
     * Desmarca los casos todo o nada, en una transacción con los casos bloqueados: si alguno tiene relaciones visibles,
     * {@code ConflictException} («Quita antes los documentos del expediente»); si falta alguno, 404; si no, quita las
     * relaciones ocultas (de documentos retirados) con su REMOVED a nombre de {@code actor} y quita la marca.
     */
    int unmarkCases(CompanyId companyId, Set<DocumentId> caseIds, UserId actor, Instant at);

    /** Páginas guardadas de cada documento visible, en una sola consulta; los que no tienen páginas no salen. */
    Map<DocumentId, Integer> pageCounts(CompanyScope scope, ProjectVisibility visibility, Collection<DocumentId> ids);
}
