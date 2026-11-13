package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Guarda relaciones y eventos en memoria; la visibilidad de los documentos la comprueba el caso de uso. */
public class InMemoryCaseFileRepository implements CaseFileRepository {

    private final List<CaseFileEntry> entries = new ArrayList<>();
    private final List<CaseFileEvent> events = new ArrayList<>();

    @Override
    public boolean add(CaseFileEntry entry) {
        boolean exists = entries.stream().anyMatch(e -> sameRelation(e, entry.caseId(), entry.documentId()));
        return !exists && entries.add(entry);
    }

    @Override
    public int addAll(List<CaseFileEntry> toAdd, Function<CaseFileEntry, CaseFileEvent> eventFor) {
        int added = 0;
        for (CaseFileEntry entry : toAdd) {
            if (add(entry)) {
                record(eventFor.apply(entry));
                added++;
            }
        }
        return added;
    }

    @Override
    public boolean remove(CompanyId companyId, DocumentId caseId, DocumentId documentId) {
        return entries.removeIf(e -> e.companyId().equals(companyId) && sameRelation(e, caseId, documentId));
    }

    @Override
    public boolean removeAndRecord(CaseFileEvent event) {
        boolean removed = remove(event.companyId(), event.caseId(), event.documentId());
        if (removed) {
            record(event);
        }
        return removed;
    }

    @Override
    public void record(CaseFileEvent event) {
        events.add(event);
    }

    @Override
    public List<CaseFileEntry> entries(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId) {
        return entries.stream().filter(e -> scope.includes(e.companyId()) && e.caseId().equals(caseId)).toList();
    }

    @Override
    public List<CaseFileEvent> events(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId) {
        return events.stream().filter(e -> scope.includes(e.companyId()) && e.caseId().equals(caseId)).toList();
    }

    @Override
    public int countEntries(CompanyId companyId, DocumentId caseId) {
        return (int) entries.stream().filter(e -> e.companyId().equals(companyId) && e.caseId().equals(caseId))
                .count();
    }

    public List<CaseFileEvent> allEvents() {
        return List.copyOf(events);
    }

    private static boolean sameRelation(CaseFileEntry e, DocumentId caseId, DocumentId documentId) {
        return e.caseId().equals(caseId) && e.documentId().equals(documentId);
    }
}
