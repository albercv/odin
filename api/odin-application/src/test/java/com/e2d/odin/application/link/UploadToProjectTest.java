package com.e2d.odin.application.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Subir a un proyecto por enlace: sin expediente, con duplicados solo dentro de lo que la persona ve. */
class UploadToProjectTest {

    private static final String HOME = LinkWorld.HOME;

    private final LinkWorld w = new LinkWorld();
    private final String token = LinkWorld.token(w.issueUpload().forProject(w.worker, w.project));

    private UploadReceipt send(String name, String text) {
        return w.uploadByLink().execute(token, HOME, name, text.getBytes());
    }

    private long inProject(ProjectId project) {
        return w.docs.all().stream().filter(d -> d.projectId().equals(Optional.of(project))).count();
    }

    private Document sameContentIn(ProjectId project, String text) {
        String sha = w.store.store(text.getBytes());
        Document base = Fixtures.documentIn(w.docs, w.company, project, text);
        Document d = new Document(base.id(), w.company, Optional.of(project), base.title(), base.filename(),
                base.format(), sha, base.status(), Optional.empty(), true, base.uploadedBy(), base.uploadedAt(),
                Optional.empty(), 0, Optional.empty(), Optional.empty());
        w.docs.save(d);
        return d;
    }

    @Test
    void issuedLinkTargetsTheProjectAndNoCase() {
        assertThat(w.links.all()).singleElement().satisfies(l -> {
            assertThat(l.projectId()).contains(w.project);
            assertThat(l.caseId()).isEmpty();
            assertThat(l.userId()).isEqualTo(w.worker.userId());
        });
    }

    @Test
    void uploadCreatesTheDocumentInTheProjectWithTheLinkOwnerAsAuthor() {
        UploadReceipt receipt = send("acta.pdf", "uno");

        assertThat(receipt.filename()).isEqualTo("acta.pdf");
        assertThat(receipt.duplicate()).isFalse();
        Document created = w.uploaded("acta.pdf");
        assertThat(created.projectId()).contains(w.project);
        assertThat(created.uploadedBy()).isEqualTo(w.worker.userId());
        assertThat(created.caseDocument()).isFalse();
        assertThat(w.caseFiles.allEvents()).isEmpty();
    }

    @Test
    void sameFileTwiceGivesOneDocumentAndTheSecondSaysItWasThere() {
        assertThat(send("a.pdf", "mismo").duplicate()).isFalse();
        assertThat(send("b.pdf", "mismo").duplicate()).isTrue();

        assertThat(inProject(w.project)).isEqualTo(1);
    }

    @Test
    void duplicateInAProjectTheWorkerDoesNotSeeCreatesANewDocument() {
        ProjectId hidden = Fixtures.project(w.orgs, w.company);
        Document elsewhere = sameContentIn(hidden, "ajeno");

        assertThat(send("a.pdf", "ajeno").duplicate()).isFalse();

        assertThat(inProject(w.project)).isEqualTo(1);
        assertThat(w.docs.get(elsewhere.id())).isPresent();
    }

    @Test
    void parallelIdenticalUploadLinksTheDocumentTheOtherOneCreated() {
        w.docs.raceWith(mine -> w.docs.save(new Document(new com.e2d.odin.domain.shared.DocumentId(
                java.util.UUID.randomUUID()), w.company, mine.projectId(), "otro", "otro.pdf", mine.format(),
                mine.sha256(), mine.status(), Optional.empty(), false, w.worker.userId(), mine.uploadedAt(),
                Optional.empty(), 0, Optional.empty(), Optional.empty())));

        assertThat(send("a.pdf", "mismo").duplicate()).isTrue();

        assertThat(inProject(w.project)).isEqualTo(1);
    }

    @Test
    void withoutTheCapabilityIssuingIsForbidden() {
        w.setCapabilities(Capability.CASE_FILES, Capability.SEARCH_KNOWLEDGE);

        assertThatThrownBy(() -> w.issueUpload().forProject(w.worker, w.project))
                .isInstanceOf(com.e2d.odin.domain.shared.ForbiddenException.class);
    }

    @Test
    void closedProjectWithoutMembershipIsTheSameNotFoundAsAMissingOne() {
        ProjectId closed = Fixtures.project(w.orgs, w.company);
        var missing = new ProjectId(java.util.UUID.randomUUID());

        assertThatThrownBy(() -> w.issueUpload().forProject(w.worker, closed)).isInstanceOf(NotFoundException.class)
                .hasMessage("Proyecto no encontrado");
        assertThatThrownBy(() -> w.issueUpload().forProject(w.worker, missing)).isInstanceOf(NotFoundException.class)
                .hasMessage("Proyecto no encontrado");
    }

    @Test
    void companyWideProjectNeedsNoMembership() {
        ProjectId open = Fixtures.companyWideProject(w.orgs, w.company);

        assertThat(w.issueUpload().forProject(w.worker, open).url()).startsWith("http://odin.local/subir/");
    }

    @Test
    void deletedProjectCannotIssue() {
        w.orgs.deleteProject(w.company, w.project, w.clock.instant());

        assertThatThrownBy(() -> w.issueUpload().forProject(w.worker, w.project))
                .isInstanceOf(NotFoundException.class).hasMessage("Proyecto no encontrado");
    }

    @Test
    void personWhoLeftTheProjectGetsNotFoundOnTheNextUpload() {
        send("a.pdf", "uno");
        w.members.remove(w.company, w.project, w.worker.userId());

        assertThatThrownBy(() -> send("b.pdf", "dos")).isInstanceOf(NotFoundException.class)
                .hasMessage("Enlace no encontrado");
        assertThat(inProject(w.project)).isEqualTo(1);
    }

    @Test
    void projectDeletedAfterIssuingMakesTheNextUseNotFound() {
        w.orgs.deleteProject(w.company, w.project, w.clock.instant());

        assertThatThrownBy(() -> send("a.pdf", "uno")).isInstanceOf(NotFoundException.class)
                .hasMessage("Enlace no encontrado");
        assertThatThrownBy(() -> new DescribeUploadLink(w.resolve(), w.orgs).execute(token, HOME))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void capabilityRemovedAfterIssuingMakesTheNextUseNotFound() {
        w.setCapabilities(Capability.CASE_FILES, Capability.SEARCH_KNOWLEDGE);

        assertThatThrownBy(() -> send("a.pdf", "uno")).isInstanceOf(NotFoundException.class)
                .hasMessage("Enlace no encontrado");
    }

    @Test
    void caseLinksKeepWorkingWithCaseFilesAndWithoutUploadDocuments() {
        Document caseDoc = w.caseIn(w.project);
        String caseToken = LinkWorld.token(w.issueUpload().execute(w.worker, caseDoc.id()));
        w.setCapabilities(Capability.CASE_FILES, Capability.SEARCH_KNOWLEDGE);

        UploadReceipt receipt = w.uploadByLink().execute(caseToken, HOME, "c.pdf", "caso".getBytes());

        assertThat(receipt.duplicate()).isFalse();
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(1);
    }

    @Test
    void describeSaysProjectKindWithoutCaseTitle() {
        UploadLinkInfo info = new DescribeUploadLink(w.resolve(), w.orgs).execute(token, HOME);

        assertThat(info.kind()).isEqualTo(UploadTargetKind.PROJECT);
        assertThat(info.caseTitle()).isEmpty();
        assertThat(info.projectName()).isEqualTo("Proyecto");
        assertThat(info.expiresAt()).isEqualTo(w.clock.instant().plus(java.time.Duration.ofMinutes(15)));
    }
}
