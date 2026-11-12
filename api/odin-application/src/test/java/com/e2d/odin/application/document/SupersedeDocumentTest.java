package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupersedeDocumentTest {

    private static final String NOT_FOUND = "Documento no encontrado";
    private static final String REPLACEMENT_NOT_FOUND = "Documento que lo sustituye no encontrado";
    private static final String NOT_READY =
            "El documento que lo sustituye aún no está listo. Márcalo cuando termine de procesarse.";
    private static final String OTHER_PROJECT = "El documento que lo sustituye tiene que estar en el mismo proyecto "
            + "o sin proyecto, para que lo vea todo el que ve este.";
    private static final String CIRCULAR = "Ese documento ya está sustituido, directa o indirectamente, por este.";

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final MutableClock clock = new MutableClock();
    private final SupersedeDocument supersede = new SupersedeDocument(docs, clock);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);
    private final Actor adminA = Actors.companyAdmin(companyA);

    private Document ready(CompanyId company) {
        return Fixtures.document(docs, company, DocumentStatus.READY);
    }

    private Document readyIn(ProjectId project) {
        return Fixtures.documentIn(docs, companyA, project, "texto");
    }

    private Document reread(Document d) {
        return docs.get(d.id()).orElseThrow();
    }

    @Test
    void marksAndReturnsTheDocumentWithItsReplacement() {
        Document old = ready(companyA);
        Document current = ready(companyA);

        DocumentEntry entry = supersede.mark(adminA, old.id(), current.id());

        assertThat(entry.document().supersededBy()).contains(current.id());
        assertThat(entry.document().supersededAt()).contains(clock.instant());
        assertThat(entry.supersededBy()).contains(DocumentRef.of(current));
        assertThat(entry.replacement()).contains(DocumentRef.of(current));
        assertThat(reread(old).supersededBy()).contains(current.id());
    }

    @Test
    void changingTheMarkPointsToTheNewReplacementAndDate() {
        Document old = ready(companyA);
        Document first = ready(companyA);
        Document second = ready(companyA);
        supersede.mark(adminA, old.id(), first.id());
        clock.advance(Duration.ofHours(1));

        DocumentEntry entry = supersede.mark(adminA, old.id(), second.id());

        assertThat(entry.document().supersededBy()).contains(second.id());
        assertThat(entry.document().supersededAt()).contains(clock.instant());
    }

    @Test
    void unmarkClearsAndIsIdempotent() {
        Document old = ready(companyA);
        supersede.mark(adminA, old.id(), ready(companyA).id());

        supersede.unmark(adminA, old.id());
        supersede.unmark(adminA, old.id());

        assertThat(reread(old).superseded()).isFalse();
        assertThat(reread(old).supersededAt()).isEmpty();
    }

    @Test
    void unknownDocumentIsNotFound() {
        DocumentId unknown = new DocumentId(UUID.randomUUID());

        assertThatThrownBy(() -> supersede.mark(adminA, unknown, ready(companyA).id()))
                .isInstanceOf(NotFoundException.class).hasMessage(NOT_FOUND);
        assertThatThrownBy(() -> supersede.unmark(adminA, unknown)).isInstanceOf(NotFoundException.class)
                .hasMessage(NOT_FOUND);
    }

    @Test
    void documentOfAnotherCompanyIsNotFound() {
        Document old = ready(companyA);
        Document replacement = ready(companyA);
        Actor adminB = Actors.companyAdmin(companyB);
        supersede.mark(adminA, old.id(), replacement.id());

        assertThatThrownBy(() -> supersede.mark(adminB, old.id(), ready(companyB).id()))
                .isInstanceOf(NotFoundException.class).hasMessage(NOT_FOUND);
        assertThatThrownBy(() -> supersede.unmark(adminB, old.id())).isInstanceOf(NotFoundException.class)
                .hasMessage(NOT_FOUND);
        assertThat(reread(old).supersededBy()).contains(replacement.id());
    }

    @Test
    void selfReplacementIsRejected() {
        Document old = ready(companyA);

        assertThatThrownBy(() -> supersede.mark(adminA, old.id(), old.id())).isInstanceOf(ValidationException.class)
                .hasMessage("Un documento no puede sustituirse a sí mismo");
    }

    @Test
    void unknownReplacementIsNotFound() {
        Document old = ready(companyA);

        assertThatThrownBy(() -> supersede.mark(adminA, old.id(), new DocumentId(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class).hasMessage(REPLACEMENT_NOT_FOUND);
    }

    @Test
    void replacementOfAnotherCompanyIsNotFoundEvenForGroupAdmin() {
        Document old = ready(companyA);
        Document foreign = ready(companyB);

        for (Actor actor : new Actor[] {adminA, Actors.groupAdmin()}) {
            assertThatThrownBy(() -> supersede.mark(actor, old.id(), foreign.id()))
                    .isInstanceOf(NotFoundException.class).hasMessage(REPLACEMENT_NOT_FOUND);
        }
        assertThat(reread(old).superseded()).isFalse();
    }

    @Test
    void replacementNotReadyIsConflict() {
        Document old = ready(companyA);
        Document processing = Fixtures.document(docs, companyA, DocumentStatus.PROCESSING);

        assertThatThrownBy(() -> supersede.mark(adminA, old.id(), processing.id()))
                .isInstanceOf(ConflictException.class).hasMessage(NOT_READY);
        assertThat(reread(old).superseded()).isFalse();
    }

    @Test
    void replacementInAnotherProjectIsConflict() {
        ProjectId projectOne = Fixtures.project(orgs, companyA);
        ProjectId projectTwo = Fixtures.project(orgs, companyA);
        Document old = readyIn(projectOne);

        assertThatThrownBy(() -> supersede.mark(adminA, old.id(), readyIn(projectTwo).id()))
                .isInstanceOf(ConflictException.class).hasMessage(OTHER_PROJECT);
        assertThatThrownBy(() -> supersede.mark(adminA, ready(companyA).id(), readyIn(projectTwo).id()))
                .isInstanceOf(ConflictException.class).hasMessage(OTHER_PROJECT);
        assertThat(reread(old).superseded()).isFalse();
    }

    @Test
    void replacementInSameProjectOrWithoutProjectIsAccepted() {
        ProjectId project = Fixtures.project(orgs, companyA);
        Document old = readyIn(project);

        assertThat(supersede.mark(adminA, old.id(), readyIn(project).id()).document().superseded()).isTrue();
        assertThat(supersede.mark(adminA, old.id(), ready(companyA).id()).document().superseded()).isTrue();
    }

    @Test
    void notReadyIsReportedBeforeAnotherProject() {
        ProjectId project = Fixtures.project(orgs, companyA);
        Document old = ready(companyA);
        Document processing = Fixtures.document(docs, companyA, DocumentStatus.PROCESSING);
        docs.save(new Document(processing.id(), companyA, Optional.of(project), processing.title(),
                processing.filename(), processing.format(), processing.sha256(), processing.status(),
                Optional.empty(), true, processing.uploadedBy(), processing.uploadedAt(), Optional.empty(), 0,
                Optional.empty(), Optional.empty()));

        assertThatThrownBy(() -> supersede.mark(adminA, old.id(), processing.id()))
                .isInstanceOf(ConflictException.class).hasMessage(NOT_READY);
    }

    @Test
    void directAndIndirectCyclesAreConflicts() {
        Document a = ready(companyA);
        Document b = ready(companyA);
        Document c = ready(companyA);
        supersede.mark(adminA, a.id(), b.id());

        assertThatThrownBy(() -> supersede.mark(adminA, b.id(), a.id())).isInstanceOf(ConflictException.class)
                .hasMessage(CIRCULAR);
        supersede.mark(adminA, b.id(), c.id());
        assertThatThrownBy(() -> supersede.mark(adminA, c.id(), a.id())).isInstanceOf(ConflictException.class)
                .hasMessage(CIRCULAR);
        assertThat(reread(b).supersededBy()).contains(c.id());
        assertThat(reread(c).superseded()).isFalse();
    }

    @Test
    void workerIsForbidden() {
        Document old = ready(companyA);
        Document current = ready(companyA);
        Actor worker = Actors.worker(companyA);

        assertThatThrownBy(() -> supersede.mark(worker, old.id(), current.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> supersede.unmark(worker, old.id())).isInstanceOf(ForbiddenException.class);
        assertThat(reread(old).superseded()).isFalse();
    }

    @Test
    void groupAdminMarksInAnyCompany() {
        Document old = ready(companyB);
        Document current = ready(companyB);

        DocumentEntry entry = supersede.mark(Actors.groupAdmin(), old.id(), current.id());

        assertThat(entry.document().supersededBy()).contains(current.id());
        supersede.unmark(Actors.groupAdmin(), old.id());
        assertThat(reread(old).superseded()).isFalse();
    }
}
