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
import com.e2d.odin.domain.shared.ValidationException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MarkCaseDocumentsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryCaseFileRepository files = new InMemoryCaseFileRepository();
    private final MarkCaseDocuments mark = new MarkCaseDocuments(docs, files);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    private boolean isCase(Document d) {
        return docs.get(d.id()).orElseThrow().caseDocument();
    }

    @Test
    void marksBatchOfDocuments() {
        Document one = Fixtures.readyDocument(docs, companyA, "uno");
        Document two = Fixtures.readyDocument(docs, companyA, "dos");
        Document untouched = Fixtures.readyDocument(docs, companyA, "tres");

        int updated = mark.execute(Actors.companyAdmin(companyA), companyA, Set.of(one.id(), two.id()), true);

        assertThat(updated).isEqualTo(2);
        assertThat(isCase(one)).isTrue();
        assertThat(isCase(two)).isTrue();
        assertThat(isCase(untouched)).isFalse();
    }

    @Test
    void unmarksBatch() {
        Document one = Fixtures.readyDocument(docs, companyA, "uno");
        mark.execute(Actors.groupAdmin(), companyA, Set.of(one.id()), true);

        mark.execute(Actors.groupAdmin(), companyA, Set.of(one.id()), false);

        assertThat(isCase(one)).isFalse();
    }

    @Test
    void emptyOrMoreThanFiveHundredIsRejected() {
        Set<DocumentId> tooMany = new HashSet<>();
        IntStream.range(0, MarkCaseDocuments.MAX_DOCUMENTS + 1).forEach(i -> tooMany.add(new DocumentId(UUID.randomUUID())));

        assertThatThrownBy(() -> mark.execute(Actors.companyAdmin(companyA), companyA, Set.of(), true))
                .isInstanceOf(ValidationException.class).hasMessage("Elige entre 1 y 500 documentos");
        assertThatThrownBy(() -> mark.execute(Actors.companyAdmin(companyA), companyA, tooMany, true))
                .isInstanceOf(ValidationException.class).hasMessage("Elige entre 1 y 500 documentos");
        assertThatThrownBy(() -> mark.execute(Actors.companyAdmin(companyA), companyA, null, true))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void foreignDocumentInBatchChangesNothingAndIsNotFound() {
        Document own = Fixtures.readyDocument(docs, companyA, "propio");
        Document foreign = Fixtures.readyDocument(docs, companyB, "ajeno");

        assertThatThrownBy(() -> mark.execute(Actors.companyAdmin(companyA), companyA,
                Set.of(own.id(), foreign.id()), true)).isInstanceOf(NotFoundException.class)
                .hasMessage("Documento no encontrado");
        assertThatThrownBy(() -> mark.execute(Actors.companyAdmin(companyA), companyA,
                Set.of(own.id(), new DocumentId(UUID.randomUUID())), true)).isInstanceOf(NotFoundException.class);

        assertThat(isCase(own)).isFalse();
        assertThat(isCase(foreign)).isFalse();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        Document own = Fixtures.readyDocument(docs, companyA, "propio");

        assertThatThrownBy(() -> mark.execute(Actors.companyAdmin(companyB), companyA, Set.of(own.id()), true))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> mark.execute(Actors.worker(companyA), companyA, Set.of(own.id()), true))
                .isInstanceOf(ForbiddenException.class);
        assertThat(isCase(own)).isFalse();
    }

    @Test
    void unmarkCaseWithEntriesConflicts() {
        Document caseDoc = Fixtures.readyDocument(docs, companyA, "caso");
        mark.execute(Actors.companyAdmin(companyA), companyA, Set.of(caseDoc.id()), true);
        files.add(new CaseFileEntry(companyA, caseDoc.id(), Fixtures.readyDocument(docs, companyA, "x").id(),
                new UserId(UUID.randomUUID()), Fixtures.NOW));

        assertThatThrownBy(() -> mark.execute(Actors.companyAdmin(companyA), companyA, Set.of(caseDoc.id()), false))
                .isInstanceOf(ConflictException.class).hasMessage("Quita antes los documentos del expediente");
        assertThat(isCase(caseDoc)).isTrue();
    }
}
