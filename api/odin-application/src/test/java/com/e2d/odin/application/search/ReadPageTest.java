package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class ReadPageTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final ReadPage readPage = new ReadPage(orgs, docs);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    @Test
    void readsPageOfOwnDocument() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");

        PageView view = readPage.execute(Actors.worker(companyA), d.id(), 1);

        assertThat(view.document()).isEqualTo(d);
        assertThat(view.page().text()).isEqualTo("texto");
    }

    @Test
    void otherCompanyDocumentIsNotFound() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");

        assertThatThrownBy(() -> readPage.execute(Actors.worker(companyB), d.id(), 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void missingPageIsNotFound() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");

        assertThatThrownBy(() -> readPage.execute(Actors.worker(companyA), d.id(), 2))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requiresSearchCapabilityAndWorker() {
        CompanyId noSearch = Fixtures.companyWith(orgs, EnumSet.of(Capability.SAVE_KNOWLEDGE_CARD));
        Document d = Fixtures.readyDocument(docs, noSearch, "texto");

        assertThatThrownBy(() -> readPage.execute(Actors.worker(noSearch), d.id(), 1))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> readPage.execute(Actors.companyAdmin(noSearch), d.id(), 1))
                .isInstanceOf(ForbiddenException.class);
    }
}
