package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import org.junit.jupiter.api.Test;

class VerifyCitationTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final VerifyCitation verify = new VerifyCitation(orgs, docs);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    @Test
    void trueForLiteralQuote() {
        Document d = Fixtures.readyDocument(docs, companyA, "La franquicia\nes de 300 euros");

        assertThat(verify.execute(Actors.worker(companyA), d.id(), 1, "FRANQUICIA es   de 300")).isTrue();
    }

    @Test
    void falseForChangedQuoteOrMissingPage() {
        Document d = Fixtures.readyDocument(docs, companyA, "La franquicia es de 300 euros");

        assertThat(verify.execute(Actors.worker(companyA), d.id(), 1, "fránquicia")).isFalse();
        assertThat(verify.execute(Actors.worker(companyA), d.id(), 9, "franquicia")).isFalse();
    }

    @Test
    void falseForOtherCompanyDocument() {
        Document d = Fixtures.readyDocument(docs, companyA, "La franquicia es de 300 euros");

        assertThat(verify.execute(Actors.worker(companyB), d.id(), 1, "franquicia")).isFalse();
    }

    @Test
    void adminCannotVerify() {
        Document d = Fixtures.readyDocument(docs, companyA, "texto");

        assertThatThrownBy(() -> verify.execute(Actors.companyAdmin(companyA), d.id(), 1, "texto"))
                .isInstanceOf(ForbiddenException.class);
    }
}
