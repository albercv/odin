package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditActorKind;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Las acciones de expediente de la persona trabajadora dejan una entrada cada una, con su rol. */
class WorkerCaseFileAuditTest {

    private final CaseFileWorld w = new CaseFileWorld();
    private final Document caseDoc = w.caseIn(w.project);
    private final Document doc = w.documentIn(w.project);

    private AuditEntry only(AuditAction action) {
        assertThat(w.audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(action));
        AuditEntry e = w.audit.entries.get(0);
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorKind()).isEqualTo(AuditActorKind.PERSON);
        assertThat(e.actorUserId()).contains(w.worker.userId());
        assertThat(e.actorRole()).contains("WORKER");
        assertThat(e.company()).contains(w.company);
        assertThat(e.project()).contains(w.project);
        assertThat(e.target()).contains(new AuditEntry.Target("CASE_FILE", caseDoc.id().value().toString(), caseDoc.title()));
        return e;
    }

    @Test
    void workerAddRecordsOneEntryWithCountAndIds() {
        w.addToCaseFiles().execute(w.worker, List.of(new CaseFileAssignment(caseDoc.id(), Set.of(doc.id()))));

        AuditEntry e = only(AuditAction.CASE_FILE_DOCUMENTS_ADDED);
        assertThat(e.details()).containsEntry("count", 1).containsEntry("ids", List.of(doc.id().value().toString()));
    }

    @Test
    void workerRemoveRecordsOneEntryWithTheDocumentId() {
        w.addToCaseFiles().execute(w.worker, List.of(new CaseFileAssignment(caseDoc.id(), Set.of(doc.id()))));
        w.audit.entries.clear();

        w.removeFromCaseFile().execute(w.worker, caseDoc.id(), doc.id());

        assertThat(only(AuditAction.CASE_FILE_DOCUMENT_REMOVED).details())
                .containsEntry("documentId", doc.id().value().toString());
    }

    @Test
    void aFailedRemoveWritesNothing() {
        assertThatThrownBy(() -> w.removeFromCaseFile().execute(w.worker, caseDoc.id(), doc.id()))
                .isInstanceOf(NotFoundException.class);

        assertThat(w.audit.entries).isEmpty();
    }
}
