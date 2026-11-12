package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminDocumentsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);
    private final ListCompanyDocuments list = new ListCompanyDocuments(docs);
    private final GetDocument get = new GetDocument(docs);
    private final RetryDocument retry = new RetryDocument(docs);

    @Test
    void listsOwnCompanyDocumentsOnly() {
        Document a = Fixtures.document(docs, companyA, DocumentStatus.READY);
        Fixtures.document(docs, companyB, DocumentStatus.READY);

        assertThat(list.execute(Actors.companyAdmin(companyA), companyA, 0, 10)).containsExactly(a);
    }

    @Test
    void otherCompanyAdminCannotList() {
        Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(companyB), companyA, 0, 10))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void groupAdminListsAnyCompany() {
        Document a = Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThat(list.execute(Actors.groupAdmin(), companyA, 0, 10)).isEqualTo(List.of(a));
    }

    @Test
    void getsOwnDocument() {
        Document a = Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThat(get.execute(Actors.companyAdmin(companyA), a.id())).isEqualTo(a);
    }

    @Test
    void otherCompanyAdminCannotGet() {
        Document a = Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThatThrownBy(() -> get.execute(Actors.companyAdmin(companyB), a.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void workerCannotGetAsAdmin() {
        Document a = Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThatThrownBy(() -> get.execute(Actors.worker(companyA), a.id())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void retryRequeuesFailedAndResetsAttempts() {
        Document failed = Fixtures.document(docs, companyA, DocumentStatus.FAILED).markFailed("x");
        docs.save(new Document(failed.id(), failed.companyId(), failed.projectId(), failed.title(), failed.filename(),
                failed.format(), failed.sha256(), failed.status(), failed.failureReason(), true, failed.uploadedBy(),
                failed.uploadedAt(), failed.processingStartedAt(), 3, Optional.empty(), Optional.empty()));

        Document result = retry.execute(Actors.companyAdmin(companyA), failed.id());

        assertThat(result.status()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(result.attemptCount()).isZero();
        assertThat(docs.get(failed.id())).contains(result);
    }

    @Test
    void retryRejectsReadyDocument() {
        Document ready = Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThatThrownBy(() -> retry.execute(Actors.companyAdmin(companyA), ready.id()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void otherCompanyAdminCannotRetry() {
        Document failed = Fixtures.document(docs, companyA, DocumentStatus.FAILED);

        assertThatThrownBy(() -> retry.execute(Actors.companyAdmin(companyB), failed.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(docs.get(failed.id()).orElseThrow().status()).isEqualTo(DocumentStatus.FAILED);
    }
}
