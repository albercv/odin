package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Instant;
import java.util.Collection;

/** Desmarcar un caso: lo frena lo que el administrador ve en el expediente, y lo que ya no se ve se retira con él. */
public final class CaseFileGuard {

    private CaseFileGuard() {
    }

    public static void requireNoVisibleEntries(CaseFileRepository caseFiles, CompanyId companyId,
            Collection<DocumentId> caseIds) {
        boolean anyVisible = caseIds.stream().anyMatch(
                id -> !caseFiles.entries(CompanyScope.of(companyId), ProjectVisibility.all(), id).isEmpty());
        if (anyVisible) {
            throw new ConflictException("Quita antes los documentos del expediente");
        }
    }

    /** Las relaciones con documentos retirados no se pueden quitar a mano: salen con la ficha, con su REMOVED. */
    public static void removeHiddenEntries(CaseFileRepository caseFiles, CompanyId companyId,
            Collection<DocumentId> caseIds, Actor actor, Instant at) {
        caseIds.forEach(id -> caseFiles.removeHiddenEntries(companyId, id, actor.userId(), at));
    }
}
