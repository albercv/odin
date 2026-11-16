package com.e2d.odin.application.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditActorKind;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Emitir un enlace deja la entrada de la persona; usarlo, la del enlace (actor LINK, {@code via} = su id). */
class LinkAuditTest {

    private static final String HOME = LinkWorld.HOME;

    private final LinkWorld w = new LinkWorld();
    private final Document caseDoc = w.caseIn(w.project);

    private Document storedDocument() {
        Document base = Fixtures.documentIn(w.docs, w.company, w.project, "t");
        Document d = new Document(base.id(), w.company, base.projectId(), "Informe", "informe.pdf",
                DocumentFormat.PDF, w.store.store("original".getBytes()), base.status(), Optional.empty(), true,
                base.uploadedBy(), base.uploadedAt(), Optional.empty(), 0, Optional.empty(), Optional.empty());
        w.docs.save(d);
        return d;
    }

    private AccessLink only(String token) {
        return w.links.findByTokenHash(w.secrets.hash(token)).orElseThrow();
    }

    private AuditEntry single(AuditAction action) {
        assertThat(w.audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(action));
        return w.audit.entries.get(0);
    }

    private void assertByWorker(AuditEntry e) {
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorKind()).isEqualTo(AuditActorKind.PERSON);
        assertThat(e.actorUserId()).contains(w.worker.userId());
        assertThat(e.actorRole()).contains("WORKER");
        assertThat(e.company()).contains(w.company);
    }

    private void assertByLink(AuditEntry e, AccessLink link) {
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorKind()).isEqualTo(AuditActorKind.LINK);
        assertThat(e.actorUserId()).isEmpty();
        assertThat(e.via()).contains(link.id().toString());
        assertThat(e.details()).containsEntry("linkOwnerId", w.worker.userId().value().toString());
        assertThat(e.company()).contains(w.company);
    }

    @Test
    void issuingACaseUploadLinkRecordsOneEntryWithTheLinkId() {
        String token = LinkWorld.token(w.issueUpload().execute(w.worker, caseDoc.id()));

        AuditEntry e = single(AuditAction.UPLOAD_LINK_ISSUED);
        assertByWorker(e);
        assertThat(e.project()).contains(w.project);
        assertThat(e.target()).contains(new AuditEntry.Target("CASE_FILE", caseDoc.id().value().toString(), caseDoc.title()));
        assertThat(e.details()).containsEntry("linkId", only(token).id().toString());
        assertThat(e.toString()).doesNotContain(token);
    }

    @Test
    void issuingAProjectUploadLinkRecordsOneEntryWithTheLinkId() {
        String token = LinkWorld.token(w.issueUpload().forProject(w.worker, w.project));

        AuditEntry e = single(AuditAction.UPLOAD_LINK_ISSUED);
        assertByWorker(e);
        assertThat(e.project()).contains(w.project);
        assertThat(e.target().orElseThrow().kind()).isEqualTo("PROJECT");
        assertThat(e.target().orElseThrow().id()).isEqualTo(w.project.value().toString());
        assertThat(e.details()).containsEntry("linkId", only(token).id().toString());
    }

    @Test
    void issuingAnOriginalLinkRecordsOneEntryWithTheLinkId() {
        Document doc = w.caseIn(w.project);
        String token = LinkWorld.token(w.issueOriginal().execute(w.worker, doc.id(), Optional.of(2)));

        AuditEntry e = single(AuditAction.ORIGINAL_LINK_ISSUED);
        assertByWorker(e);
        assertThat(e.target().orElseThrow().kind()).isEqualTo("DOCUMENT");
        assertThat(e.target().orElseThrow().id()).isEqualTo(doc.id().value().toString());
        assertThat(e.details()).containsEntry("linkId", only(token).id().toString());
    }

    @Test
    void aFailedIssueWritesNothing() {
        assertThatThrownBy(() -> w.issueUpload().execute(w.stranger(), caseDoc.id()))
                .isInstanceOf(NotFoundException.class);

        assertThat(w.audit.entries).isEmpty();
    }

    @Test
    void uploadByLinkToACaseRecordsOneLinkEntry() {
        String token = LinkWorld.token(w.issueUpload().execute(w.worker, caseDoc.id()));
        w.audit.entries.clear();

        w.uploadByLink().execute(token, HOME, "acta.pdf", "contenido".getBytes());

        AuditEntry e = single(AuditAction.DOCUMENT_UPLOADED);
        assertByLink(e, only(token));
        assertThat(e.project()).contains(w.project);
        assertThat(e.target().orElseThrow().id()).isEqualTo(w.uploaded("acta.pdf").id().value().toString());
        assertThat(e.details()).containsEntry("duplicate", false).containsEntry("sizeBytes", 9)
                .containsEntry("sha256", w.uploaded("acta.pdf").sha256())
                .containsEntry("caseId", caseDoc.id().value().toString());
        assertThat(e.toString()).doesNotContain("contenido");
    }

    @Test
    void uploadByLinkToAProjectRecordsOneLinkEntry() {
        String token = LinkWorld.token(w.issueUpload().forProject(w.worker, w.project));
        w.audit.entries.clear();

        w.uploadByLink().execute(token, HOME, "plano.pdf", "datos".getBytes());

        AuditEntry e = single(AuditAction.DOCUMENT_UPLOADED);
        assertByLink(e, only(token));
        assertThat(e.project()).contains(w.project);
        assertThat(e.details()).containsEntry("duplicate", false).doesNotContainKey("caseId");
    }

    @Test
    void aDuplicateUploadByLinkRecordsExactlyOneEntryMarkedDuplicate() {
        String token = LinkWorld.token(w.issueUpload().execute(w.worker, caseDoc.id()));
        w.uploadByLink().execute(token, HOME, "a.pdf", "mismo".getBytes());
        w.audit.entries.clear();

        w.uploadByLink().execute(token, HOME, "b.pdf", "mismo".getBytes());

        AuditEntry e = single(AuditAction.DOCUMENT_UPLOADED);
        assertByLink(e, only(token));
        assertThat(e.details()).containsEntry("duplicate", true);
    }

    @Test
    void openingAnOriginalByLinkRecordsOneEntryWithoutContent() {
        Document doc = storedDocument();
        String token = LinkWorld.token(w.issueOriginal().execute(w.worker, doc.id(), Optional.empty()));
        w.audit.entries.clear();

        w.openOriginal().execute(token, HOME);

        AuditEntry e = single(AuditAction.DOCUMENT_ORIGINAL_DOWNLOADED);
        assertByLink(e, only(token));
        assertThat(e.target().orElseThrow().id()).isEqualTo(doc.id().value().toString());
        assertThat(e.details().keySet()).containsExactly("linkOwnerId");
    }

    @Test
    void aRejectedLinkWritesNothing() {
        String token = LinkWorld.token(w.issueUpload().execute(w.worker, caseDoc.id()));
        w.uploadByLink().execute(token, HOME, "primero.pdf", "uno".getBytes());
        w.audit.entries.clear();

        assertThatThrownBy(() -> w.uploadByLink().execute(token, "10.0.0.9", "x.pdf", "x".getBytes()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> w.uploadByLink().execute("inventado", HOME, "x.pdf", "x".getBytes()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> w.openOriginal().execute(token, HOME)).isInstanceOf(NotFoundException.class);

        assertThat(w.audit.entries).isEmpty();
        assertThat(w.audit.denied).isEmpty();
    }
}
