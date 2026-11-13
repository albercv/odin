package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddToCaseFilesTest {

    private final CaseFileWorld w = new CaseFileWorld();

    private int add(Actor actor, Document caseDoc, Document... docs) {
        var ids = Set.of(java.util.Arrays.stream(docs).map(Document::id).toArray(DocumentId[]::new));
        return w.addToCaseFiles().execute(actor, List.of(new CaseFileAssignment(caseDoc.id(), ids)));
    }

    @Test
    void addRejectsOtherProjectDocument() {
        Document caseDoc = w.caseIn(w.project);
        Document foreign = w.documentIn(otherProject());

        assertThatThrownBy(() -> add(w.worker, caseDoc, foreign)).isInstanceOf(NotFoundException.class)
                .hasMessage("Documento no encontrado");
        assertThat(w.files.countEntries(w.company, caseDoc.id())).isZero();
    }

    private ProjectId otherProject() {
        ProjectId other = Fixtures.project(w.orgs, w.company);
        w.members.add(other, w.worker.userId());
        return other;
    }

    @Test
    void addAcceptsGeneralDocument() {
        Document caseDoc = w.caseIn(w.project);
        Document general = Fixtures.readyDocument(w.docs, w.company, "general");

        assertThat(add(w.worker, caseDoc, general)).isEqualTo(1);
    }

    @Test
    void addAllOrNothing() {
        Document caseDoc = w.caseIn(w.project);
        Document good = w.documentIn(w.project);
        Document bad = w.documentIn(otherProject());

        assertThatThrownBy(() -> add(w.worker, caseDoc, good, bad)).isInstanceOf(NotFoundException.class);

        assertThat(w.files.countEntries(w.company, caseDoc.id())).isZero();
        assertThat(w.files.allEvents()).isEmpty();
    }

    @Test
    void addBatchSeveralCases() {
        Document caseA = w.caseIn(w.project);
        Document caseB = w.caseIn(w.project);
        Document one = w.documentIn(w.project);
        Document two = w.documentIn(w.project);

        int added = w.addToCaseFiles().execute(w.worker, List.of(
                new CaseFileAssignment(caseA.id(), Set.of(one.id(), two.id())),
                new CaseFileAssignment(caseB.id(), Set.of(one.id()))));

        assertThat(added).isEqualTo(3);
        assertThat(w.files.countEntries(w.company, caseA.id())).isEqualTo(2);
        assertThat(w.files.countEntries(w.company, caseB.id())).isEqualTo(1);
    }

    @Test
    void addRecordsAddedEventWithActor() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);

        add(w.worker, caseDoc, doc);

        assertThat(w.files.allEvents()).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(CaseFileAction.ADDED);
            assertThat(e.actorId()).isEqualTo(w.worker.userId());
            assertThat(e.caseId()).isEqualTo(caseDoc.id());
            assertThat(e.documentId()).isEqualTo(doc.id());
        });
    }

    @Test
    void addExistingPairNoNewEvent() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        add(w.worker, caseDoc, doc);

        assertThat(add(w.worker, caseDoc, doc)).isZero();

        assertThat(w.files.allEvents()).hasSize(1);
    }

    @Test
    void addRejectsTheCaseItself() {
        Document caseDoc = w.caseIn(w.project);

        assertThatThrownBy(() -> add(w.worker, caseDoc, caseDoc)).isInstanceOf(ValidationException.class);
    }

    @Test
    void addRejectsNotACase() {
        Document plain = w.documentIn(w.project);
        Document doc = w.documentIn(w.project);

        assertThatThrownBy(() -> add(w.worker, plain, doc)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void addRejectsInvisibleCaseAndUnknownDocument() {
        Document caseDoc = w.caseIn(w.project);
        Actor outsider = Actors.worker(w.company);
        Document doc = w.documentIn(w.project);

        assertThatThrownBy(() -> add(outsider, caseDoc, doc)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> w.addToCaseFiles().execute(w.worker, List.of(new CaseFileAssignment(caseDoc.id(),
                Set.of(new DocumentId(UUID.randomUUID())))))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void addRejectsEmptyBatch() {
        assertThatThrownBy(() -> w.addToCaseFiles().execute(w.worker, List.of()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void adminAddsWithTheirOwnIdentity() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        Actor admin = Actors.companyAdmin(w.company);

        int added = w.addToCaseFiles().execute(admin, w.company,
                List.of(new CaseFileAssignment(caseDoc.id(), Set.of(doc.id()))));

        assertThat(added).isEqualTo(1);
        assertThat(w.files.allEvents().get(0).actorId()).isEqualTo(admin.userId());
    }
}
