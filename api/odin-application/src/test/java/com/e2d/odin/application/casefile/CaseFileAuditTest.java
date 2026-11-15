package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.identity.Actor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Las acciones de expediente del administrador dejan una entrada cada una; las del trabajador, ninguna aquí. */
class CaseFileAuditTest {

    private final CaseFileWorld w = new CaseFileWorld();
    private final Actor admin = Actors.companyAdmin(w.company);
    private final Document caseDoc = w.caseIn(w.project);

    private AuditEntry only(AuditAction action) {
        assertThat(w.audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(action));
        AuditEntry e = w.audit.entries.get(0);
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorUserId()).contains(admin.userId());
        assertThat(e.actorRole()).contains("COMPANY_ADMIN");
        assertThat(e.company()).contains(w.company);
        assertThat(e.project()).contains(w.project);
        return e;
    }

    private AuditEntry.Target caseFile() {
        return new AuditEntry.Target("CASE_FILE", caseDoc.id().value().toString(), caseDoc.title());
    }

    @Test
    void adminAddRecordsOneEntryWithCountAndIds() {
        Document one = w.documentIn(w.project);
        Document two = w.documentIn(w.project);

        w.addToCaseFiles().execute(admin, w.company,
                List.of(new CaseFileAssignment(caseDoc.id(), Set.of(one.id(), two.id()))));

        AuditEntry e = only(AuditAction.CASE_FILE_DOCUMENTS_ADDED);
        assertThat(e.target()).contains(caseFile());
        assertThat(e.details()).containsEntry("count", 2);
        assertThat(((List<?>) e.details().get("ids")).stream().map(Object::toString).toList()).containsExactlyInAnyOrder(one.id().value().toString(),
                two.id().value().toString());
    }

    @Test
    void adminRemoveRecordsTheDocumentId() {
        Document one = w.documentIn(w.project);
        w.addToCaseFiles().execute(admin, w.company, List.of(new CaseFileAssignment(caseDoc.id(), Set.of(one.id()))));
        w.audit.entries.clear();

        w.removeFromCaseFile().execute(admin, w.company, caseDoc.id(), one.id());

        AuditEntry e = only(AuditAction.CASE_FILE_DOCUMENT_REMOVED);
        assertThat(e.target()).contains(caseFile());
        assertThat(e.details()).containsEntry("documentId", one.id().value().toString());
    }

    @Test
    void adminUploadToTheCaseFileRecordsExactlyOneUploadEntryEvenForADuplicate() {
        var upload = new UploadDocument(w.docs, w.orgs, new InMemoryOriginalFileStore(),
                new StubFormatDetector(DocumentFormat.PDF), new UploadSettings(100), w.ids, w.clock, w.audit);
        var toCase = new UploadToCaseFile(upload, w.addToCaseFiles(), w.files, w.ids, w.clock, w.audit);

        UploadOutcome first = toCase.executeAsAdmin(admin, caseDoc, "Acta", "a.pdf", "contenido".getBytes());
        AuditEntry e = only(AuditAction.DOCUMENT_UPLOADED);
        assertThat(e.target().orElseThrow().id()).isEqualTo(first.document().id().value().toString());
        assertThat(e.details()).containsEntry("caseId", caseDoc.id().value().toString())
                .containsEntry("duplicate", false).containsEntry("sizeBytes", 9);

        w.audit.entries.clear();
        toCase.executeAsAdmin(admin, caseDoc, "Acta", "a.pdf", "contenido".getBytes());
        assertThat(w.audit.entries).singleElement().satisfies(x -> assertThat(x.details())
                .containsEntry("duplicate", true));
    }
}
