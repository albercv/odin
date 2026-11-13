package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListCaseFileEventsTest {

    private final CaseFileWorld w = new CaseFileWorld();
    private final ListCaseFileEvents list = new ListCaseFileEvents(w.docs, w.files);

    @Test
    void adminSeesTheHistory() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        w.addToCaseFiles().execute(w.worker, List.of(new CaseFileAssignment(caseDoc.id(), Set.of(doc.id()))));

        assertThat(list.execute(Actors.companyAdmin(w.company), w.company, caseDoc.id())).hasSize(1);
    }

    @Test
    void eventsCarryTheDocumentTitleAndNullWhenWithdrawn() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        Document gone = w.documentIn(w.project);
        w.addToCaseFiles().execute(w.worker, List.of(new CaseFileAssignment(caseDoc.id(), Set.of(doc.id(), gone.id()))));
        w.docs.save(gone.withWithdrawal(java.util.Optional.of(Fixtures.NOW), java.util.Optional.of(w.worker.userId())));

        var lines = list.execute(Actors.companyAdmin(w.company), w.company, caseDoc.id());

        assertThat(lines).filteredOn(l -> l.event().documentId().equals(doc.id())).singleElement()
                .satisfies(l -> assertThat(l.documentTitle()).isEqualTo(doc.title()));
        assertThat(lines).filteredOn(l -> l.event().documentId().equals(gone.id())).singleElement()
                .satisfies(l -> assertThat(l.documentTitle()).isNull());
    }

    @Test
    void workerIsForbidden() {
        assertThatThrownBy(() -> list.execute(w.worker, w.company, new DocumentId(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(Fixtures.company(w.orgs)), w.company,
                new DocumentId(UUID.randomUUID()))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void notACaseIsNotFound() {
        Document plain = w.documentIn(w.project);

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(w.company), w.company, plain.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void withdrawnCaseIsNotFound() {
        Document caseDoc = w.caseIn(w.project);
        w.docs.save(caseDoc.withWithdrawal(java.util.Optional.of(Fixtures.NOW), java.util.Optional.of(w.worker.userId())));

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(w.company), w.company, caseDoc.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownCaseIsNotFound() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(w.company), w.company,
                new DocumentId(UUID.randomUUID()))).isInstanceOf(NotFoundException.class);
    }
}
