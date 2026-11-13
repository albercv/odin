package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateDocumentTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryCaseFileRepository files = new InMemoryCaseFileRepository();
    private final UpdateDocument update = new UpdateDocument(docs, files);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    @Test
    void marksAndUnmarksCase() {
        Document d = Fixtures.readyDocument(docs, companyA, "caso");

        var marked = update.execute(Actors.companyAdmin(companyA), d.id(), true);
        assertThat(marked.document().caseDocument()).isTrue();
        assertThat(docs.get(d.id()).orElseThrow().caseDocument()).isTrue();

        var unmarked = update.execute(Actors.groupAdmin(), d.id(), false);
        assertThat(unmarked.document().caseDocument()).isFalse();
        assertThat(docs.get(d.id()).orElseThrow().caseDocument()).isFalse();
    }

    @Test
    void unknownDocumentIsNotFound() {
        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(companyA), new DocumentId(UUID.randomUUID()), true))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminOfAnotherCompanyIsNotFound() {
        Document d = Fixtures.readyDocument(docs, companyA, "caso");

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(companyB), d.id(), true))
                .isInstanceOf(NotFoundException.class).hasMessage("Documento no encontrado");
        assertThat(docs.get(d.id()).orElseThrow().caseDocument()).isFalse();
    }

    @Test
    void workerIsForbiddenEvenForAnUnknownDocument() {
        assertThatThrownBy(() -> update.execute(Actors.worker(companyA), new DocumentId(UUID.randomUUID()), true))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void workerIsForbidden() {
        Document d = Fixtures.readyDocument(docs, companyA, "caso");

        assertThatThrownBy(() -> update.execute(Actors.worker(companyA), d.id(), true))
                .isInstanceOf(ForbiddenException.class);
        assertThat(docs.get(d.id()).orElseThrow().caseDocument()).isFalse();
    }

    @Test
    void unmarkCaseWithEntriesConflicts() {
        Document caseDoc = Fixtures.readyDocument(docs, companyA, "caso");
        update.execute(Actors.companyAdmin(companyA), caseDoc.id(), true);
        files.add(new CaseFileEntry(companyA, caseDoc.id(), Fixtures.readyDocument(docs, companyA, "x").id(),
                new UserId(UUID.randomUUID()), Fixtures.NOW));

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(companyA), caseDoc.id(), false))
                .isInstanceOf(ConflictException.class).hasMessage("Quita antes los documentos del expediente");
        assertThat(docs.get(caseDoc.id()).orElseThrow().caseDocument()).isTrue();
    }
}
