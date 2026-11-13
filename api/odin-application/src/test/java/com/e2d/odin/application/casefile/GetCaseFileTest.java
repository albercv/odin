package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCaseFileTest {

    private final CaseFileWorld w = new CaseFileWorld();

    private void put(Document caseDoc, Document doc, Actor by) {
        w.files.add(new CaseFileEntry(w.company, caseDoc.id(), doc.id(), by.userId(), w.clock.instant()));
    }

    @Test
    void getCaseFileListsDocumentsWithWhoAndWhen() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        put(caseDoc, doc, w.worker);

        var view = w.getCaseFile().execute(w.worker, caseDoc.id());

        assertThat(view.caseDocument().document().id()).isEqualTo(caseDoc.id());
        assertThat(view.documents()).singleElement().satisfies(d -> {
            assertThat(d.document().document().id()).isEqualTo(doc.id());
            assertThat(d.addedBy()).isEqualTo(w.worker.userId());
            assertThat(d.addedAt()).isEqualTo(w.clock.instant());
        });
    }

    @Test
    void getCaseFileHidesUnreadDocuments() {
        Document caseDoc = w.caseIn(w.project);
        Document unread = Fixtures.document(w.docs, w.company, DocumentStatus.PENDING_READER);
        put(caseDoc, unread, w.worker);

        assertThat(w.getCaseFile().execute(w.worker, caseDoc.id()).documents()).isEmpty();
    }

    @Test
    void getCaseFileShowsSupersededWithReplacement() {
        Document caseDoc = w.caseIn(w.project);
        Document old = w.documentIn(w.project);
        Document current = w.documentIn(w.project);
        w.docs.markSuperseded(w.company, old.id(), current.id(), w.clock.instant());
        put(caseDoc, old, w.worker);

        var shown = w.getCaseFile().execute(w.worker, caseDoc.id()).documents().get(0).document();

        assertThat(shown.document().superseded()).isTrue();
        assertThat(shown.replacement()).hasValueSatisfying(r -> assertThat(r.id()).isEqualTo(current.id()));
    }

    @Test
    void getCaseFileNotFoundWhenNotCase() {
        Document plain = w.documentIn(w.project);

        assertThatThrownBy(() -> w.getCaseFile().execute(w.worker, plain.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCaseFileNotFoundWhenUnknown() {
        assertThatThrownBy(() -> w.getCaseFile().execute(w.worker, new DocumentId(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCaseFileNotFoundWhenProjectInvisible() {
        Document caseDoc = w.caseIn(w.project);
        Actor outsider = Actors.worker(w.company);

        assertThatThrownBy(() -> w.getCaseFile().execute(outsider, caseDoc.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void workerWithoutCaseFilesCapabilityForbidden() {
        var other = Fixtures.companyWith(w.orgs, EnumSet.of(Capability.SEARCH_KNOWLEDGE));
        Actor limited = Actors.worker(other);

        assertThatThrownBy(() -> w.getCaseFile().execute(limited, new DocumentId(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminSeesTheCaseFileOfTheirCompanyAndOnlyThat() {
        Document caseDoc = w.caseIn(w.project);
        Actor admin = Actors.companyAdmin(w.company);

        assertThat(w.getCaseFile().execute(admin, w.company, caseDoc.id()).caseDocument().document().id())
                .isEqualTo(caseDoc.id());
        assertThatThrownBy(() -> w.getCaseFile().execute(Actors.companyAdmin(Fixtures.company(w.orgs)), w.company,
                caseDoc.id())).isInstanceOf(ForbiddenException.class);
    }
}
