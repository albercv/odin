package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.NotFoundException;
import org.junit.jupiter.api.Test;

class RemoveFromCaseFileTest {

    private final CaseFileWorld w = new CaseFileWorld();

    private void put(Document caseDoc, Document doc) {
        w.files.add(new CaseFileEntry(w.company, caseDoc.id(), doc.id(), w.worker.userId(), w.clock.instant()));
    }

    @Test
    void removeKeepsDocumentAndRecordsRemoved() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        put(caseDoc, doc);
        Actor other = Actors.worker(w.company);
        w.members.add(w.project, other.userId());

        w.removeFromCaseFile().execute(other, caseDoc.id(), doc.id());

        assertThat(w.files.countEntries(w.company, caseDoc.id())).isZero();
        assertThat(w.docs.get(doc.id())).isPresent();
        assertThat(w.files.allEvents()).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(CaseFileAction.REMOVED);
            assertThat(e.actorId()).isEqualTo(other.userId());
        });
    }

    @Test
    void removeNotFoundWhenNotInTheCaseFile() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);

        assertThatThrownBy(() -> w.removeFromCaseFile().execute(w.worker, caseDoc.id(), doc.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(w.files.allEvents()).isEmpty();
    }

    @Test
    void removeNotFoundWhenProjectInvisible() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        put(caseDoc, doc);

        assertThatThrownBy(() -> w.removeFromCaseFile().execute(Actors.worker(w.company), caseDoc.id(), doc.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(w.files.countEntries(w.company, caseDoc.id())).isEqualTo(1);
    }
}
