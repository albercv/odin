package com.e2d.odin.application.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UploadByLinkTest {

    private static final String HOME = LinkWorld.HOME;

    private final LinkWorld w = new LinkWorld();
    private final Document caseDoc = w.caseIn(w.project);
    private final String token = LinkWorld.token(w.issueUpload().execute(w.worker, caseDoc.id()));

    private UploadReceipt send(String name, String text) {
        return w.uploadByLink().execute(token, HOME, name, text.getBytes());
    }

    private long inCaseProject() {
        return w.docs.all().stream().filter(d -> d.projectId().equals(Optional.of(w.project)) && !d.caseDocument())
                .count();
    }

    @Test
    void uploadCreatesTheDocumentInTheCaseProjectAndAddsItToTheFile() {
        assertThat(send("acta.pdf", "uno").filename()).isEqualTo("acta.pdf");

        Document created = w.docs.all().stream().filter(d -> d.filename().equals("acta.pdf")).findFirst().orElseThrow();
        assertThat(created.projectId()).contains(w.project);
        assertThat(created.uploadedBy()).isEqualTo(w.worker.userId());
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(1);
        assertThat(w.caseFiles.allEvents()).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(CaseFileAction.ADDED);
            assertThat(e.actorId()).isEqualTo(w.worker.userId());
        });
    }

    @Test
    void sameFileTwiceLinksOnce() {
        send("a.pdf", "mismo");
        send("b.pdf", "mismo");

        assertThat(inCaseProject()).isEqualTo(1);
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(1);
    }

    @Test
    void duplicateInAnotherProjectCreatesANewDocument() {
        ProjectId other = Fixtures.project(w.orgs, w.company);
        w.members.add(other, w.worker.userId());
        Document elsewhere = sameContentIn(other, "ajeno");

        send("a.pdf", "ajeno");

        assertThat(inCaseProject()).isEqualTo(1);
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(1);
        assertThat(w.docs.get(elsewhere.id())).isPresent();
    }

    @Test
    void duplicateInTheCaseProjectOrGeneralIsLinkedWithoutCopy() {
        Document general = sameContentIn(null, "general");
        Document inProject = sameContentIn(w.project, "proyecto");

        send("g.pdf", "general");
        send("p.pdf", "proyecto");

        assertThat(w.docs.all()).hasSize(3);
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(2);
        assertThat(general.id()).isNotEqualTo(inProject.id());
    }

    private Document sameContentIn(ProjectId project, String text) {
        String sha = w.store.store(text.getBytes());
        Document base = project == null ? Fixtures.readyDocument(w.docs, w.company, text)
                : Fixtures.documentIn(w.docs, w.company, project, text);
        Document d = new Document(base.id(), w.company, Optional.ofNullable(project), base.title(), base.filename(),
                base.format(), sha, base.status(), Optional.empty(), true, base.uploadedBy(), base.uploadedAt(),
                Optional.empty(), 0, Optional.empty(), Optional.empty());
        w.docs.save(d);
        return d;
    }

    @Test
    void theTwentyFirstFileIsRefused() {
        for (int i = 0; i < LinkSettings.MAX_UPLOADS; i++) {
            send("f" + i + ".pdf", "contenido " + i);
        }

        assertThatThrownBy(() -> send("extra.pdf", "uno más")).isInstanceOf(ConflictException.class);
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(LinkSettings.MAX_UPLOADS);
    }

    @Test
    void expiredLinkIsNotFound() {
        w.clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> send("a.pdf", "uno")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void linkIsBoundToTheFirstAddress() {
        send("a.pdf", "uno");

        assertThatThrownBy(() -> w.uploadByLink().execute(token, "192.168.1.99", "b.pdf", "dos".getBytes()))
                .isInstanceOf(NotFoundException.class);
        assertThat(w.links.all()).singleElement().satisfies(l -> assertThat(l.boundAddress()).contains(HOME));
    }

    @Test
    void unknownTokenAndBlankTokenAreTheSameNotFound() {
        assertThatThrownBy(() -> w.uploadByLink().execute("nope", HOME, "a.pdf", "x".getBytes()))
                .isInstanceOf(NotFoundException.class).hasMessage("Enlace no encontrado");
        assertThatThrownBy(() -> w.uploadByLink().execute(null, HOME, "a.pdf", "x".getBytes()))
                .isInstanceOf(NotFoundException.class).hasMessage("Enlace no encontrado");
    }

    @Test
    void personWhoLeftTheProjectGetsNotFound() {
        w.members.remove(w.company, w.project, w.worker.userId());

        assertThatThrownBy(() -> send("a.pdf", "uno")).isInstanceOf(NotFoundException.class)
                .hasMessage("Enlace no encontrado");
    }

    @Test
    void inactiveMembershipGetsNotFound() {
        w.memberships.delete(w.worker.userId(), w.company, w.clock.instant());

        assertThatThrownBy(() -> send("a.pdf", "uno")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anOriginalTokenCannotUpload() {
        Document doc = Fixtures.documentIn(w.docs, w.company, w.project, "texto");
        String original = LinkWorld.token(w.issueOriginal().execute(w.worker, doc.id(), Optional.empty()));

        assertThatThrownBy(() -> w.uploadByLink().execute(original, HOME, "a.pdf", "x".getBytes()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void describeGivesCaseAndProjectNames() {
        UploadLinkInfo info = new DescribeUploadLink(w.resolve(), w.orgs).execute(token, HOME);

        assertThat(info.caseTitle()).isEqualTo(caseDoc.title());
        assertThat(info.projectName()).isEqualTo("Proyecto");
        assertThat(info.expiresAt()).isEqualTo(w.clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    void failureInTheEntryStepLeavesNoDocument() {
        w.caseFiles.failTheEntryStep();

        assertThatThrownBy(() -> send("a.pdf", "uno")).isInstanceOf(IllegalStateException.class);

        assertThat(w.docs.all()).containsExactly(caseDoc);
        assertThat(w.caseFiles.allEvents()).isEmpty();
    }

    @Test
    void parallelIdenticalUploadLinksTheDocumentTheOtherOneCreated() {
        w.caseFiles.raceWith(mine -> w.docs.save(new Document(new com.e2d.odin.domain.shared.DocumentId(
                java.util.UUID.randomUUID()), w.company, mine.projectId(), "otro", "otro.pdf", mine.format(),
                mine.sha256(), mine.status(), Optional.empty(), false, w.worker.userId(), mine.uploadedAt(),
                Optional.empty(), 0, Optional.empty(), Optional.empty())));

        assertThat(send("a.pdf", "mismo").filename()).isEqualTo("a.pdf");

        assertThat(inCaseProject()).isEqualTo(1);
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(1);
    }

    @Test
    void aWithdrawnCopyInTheCaseProjectDoesNotBlockTheUpload() {
        Document old = sameContentIn(w.project, "retirado");
        w.docs.save(old.withWithdrawal(Optional.of(w.clock.instant()), Optional.of(w.worker.userId())));

        assertThat(send("nuevo.pdf", "retirado").filename()).isEqualTo("nuevo.pdf");

        assertThat(inCaseProject()).isEqualTo(2);
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isEqualTo(1);
    }

    @Test
    void linkOfACaseInADeletedProjectIsNotFoundToUploadOrDescribe() {
        w.orgs.deleteProject(w.company, w.project, w.clock.instant());

        assertThatThrownBy(() -> send("a.pdf", "uno")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> new DescribeUploadLink(w.resolve(), w.orgs).execute(token, HOME))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void uploadingTheCaseDocumentsOwnFileReturnsTheReceipt() {
        Document own = w.docs.get(caseDoc.id()).orElseThrow();
        w.store.store("texto".getBytes());
        w.docs.save(new Document(own.id(), w.company, own.projectId(), own.title(), own.filename(), own.format(),
                w.store.store("propio".getBytes()), own.status(), Optional.empty(), true, own.uploadedBy(),
                own.uploadedAt(), Optional.empty(), 0, Optional.empty(), Optional.empty(), java.util.List.of(), true));

        assertThat(send("ficha.pdf", "propio").filename()).isEqualTo("ficha.pdf");
        assertThat(w.caseFiles.countEntries(w.company, caseDoc.id())).isZero();
    }
}
