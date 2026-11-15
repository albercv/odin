package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.link.OpenOriginal;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Una entrada por acción de administración sobre un documento: nunca su texto ni su contenido. */
class DocumentAuditTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryCaseFileRepository files = new InMemoryCaseFileRepository(docs);
    private final InMemoryOriginalFileStore store = new InMemoryOriginalFileStore();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final MutableClock clock = new MutableClock();
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);

    private AuditEntry only(AuditAction action) {
        assertThat(audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(action));
        AuditEntry e = audit.entries.get(0);
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorUserId()).contains(admin.userId());
        assertThat(e.actorRole()).contains("COMPANY_ADMIN");
        assertThat(e.company()).contains(company);
        return e;
    }

    private static AuditEntry.Target document(Document d) {
        return new AuditEntry.Target("DOCUMENT", d.id().value().toString(), d.title());
    }

    @Test
    void uploadRecordsTitleSizeAndHashButNotTheContent() {
        ProjectId project = Fixtures.project(orgs, company);
        var upload = new UploadDocument(docs, orgs, store, new StubFormatDetector(DocumentFormat.PDF),
                new UploadSettings(100), new SequenceIdGenerator(), clock, audit);

        UploadOutcome outcome = upload.execute(admin, company, Optional.of(project), "Contrato", "c.pdf",
                "secreto del contrato".getBytes(), false);

        AuditEntry e = only(AuditAction.DOCUMENT_UPLOADED);
        assertThat(e.project()).contains(project);
        assertThat(e.target()).contains(document(outcome.document()));
        assertThat(e.details()).containsEntry("sizeBytes", 20).containsEntry("sha256", outcome.document().sha256())
                .containsEntry("duplicate", false);
        assertThat(e.details().toString()).doesNotContain("secreto");
    }

    @Test
    void retryRecordsTheDocument() {
        Document d = Fixtures.document(docs, company, DocumentStatus.FAILED);

        new RetryDocument(docs, audit).execute(admin, d.id());

        assertThat(only(AuditAction.DOCUMENT_RETRIED).target()).contains(document(d));
    }

    @Test
    void withdrawAndRestoreEachRecordOneEntryAndANoOpRecordsNone() {
        Document d = Fixtures.readyDocument(docs, company, "texto privado");
        var withdraw = new WithdrawDocument(docs, clock, audit);
        var restore = new RestoreDocument(docs, audit);

        restore.execute(admin, d.id());
        assertThat(audit.entries).isEmpty();
        withdraw.execute(admin, d.id());
        withdraw.execute(admin, d.id());
        assertThat(only(AuditAction.DOCUMENT_WITHDRAWN).target()).contains(document(d));

        audit.entries.clear();
        restore.execute(admin, d.id());
        assertThat(only(AuditAction.DOCUMENT_RESTORED).target()).contains(document(d));
    }

    @Test
    void supersedeMarkAndClearRecordTheReplacementId() {
        Document old = Fixtures.readyDocument(docs, company, "viejo");
        Document replacement = Fixtures.readyDocument(docs, company, "nuevo");
        var supersede = new SupersedeDocument(docs, clock, audit);

        supersede.mark(admin, old.id(), replacement.id());
        AuditEntry marked = only(AuditAction.DOCUMENT_SUPERSEDED);
        assertThat(marked.target()).contains(document(old));
        assertThat(marked.details()).containsEntry("replacementId", replacement.id().value().toString());

        audit.entries.clear();
        supersede.unmark(admin, old.id());
        assertThat(only(AuditAction.DOCUMENT_SUPERSEDED_CLEARED).target()).contains(document(old));
    }

    @Test
    void updateRecordsTheCaseMark() {
        Document d = Fixtures.readyDocument(docs, company, "caso");

        new UpdateDocument(docs, files, clock, audit).execute(admin, d.id(), true);

        AuditEntry e = only(AuditAction.DOCUMENT_CASE_MARK_CHANGED);
        assertThat(e.target()).contains(document(d));
        assertThat(e.details()).containsEntry("caseDocument", true);
    }

    @Test
    void bulkCaseMarksRecordOneEntryWithCountAndIdsCappedAt100() {
        List<DocumentId> ids = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            ids.add(Fixtures.readyDocument(docs, company, "x").id());
        }

        new MarkCaseDocuments(docs, files, clock, audit).execute(admin, company, Set.copyOf(ids), true);

        AuditEntry e = only(AuditAction.DOCUMENTS_CASE_MARK_CHANGED);
        assertThat(e.details()).containsEntry("count", 120).containsEntry("caseDocument", true)
                .containsEntry("truncated", true);
        assertThat((List<?>) e.details().get("ids")).hasSize(100);
    }

    @Test
    void adminOriginalDownloadRecordsADoneEntryWithoutContent() {
        String sha = store.store("bytes del original".getBytes());
        Document d = Fixtures.readyDocument(docs, company, "texto");
        docs.save(new Document(d.id(), company, Optional.empty(), d.title(), d.filename(), d.format(), sha, d.status(),
                Optional.empty(), true, d.uploadedBy(), d.uploadedAt(), Optional.empty(), 0, Optional.empty(),
                Optional.empty()));

        new OpenOriginal(null, store, docs, audit).execute(admin, d.id());

        AuditEntry e = only(AuditAction.DOCUMENT_ORIGINAL_DOWNLOADED);
        assertThat(e.target().orElseThrow().id()).isEqualTo(d.id().value().toString());
        assertThat(e.details()).isEmpty();
    }

    @Test
    void noDocumentEntryCarriesPageTextOrContent() {
        Document d = Fixtures.readyDocument(docs, company, "TEXTO-SECRETO");
        new UpdateDocument(docs, files, clock, audit).execute(admin, d.id(), true);
        new WithdrawDocument(docs, clock, audit).execute(admin, d.id());

        assertThat(audit.entries).isNotEmpty().allSatisfy(e -> {
            assertThat(e.toString()).doesNotContain("TEXTO-SECRETO");
            assertThat(e.details().keySet()).doesNotContain("text", "content", "body");
        });
    }
}
