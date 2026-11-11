package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import org.junit.jupiter.api.Test;

class VerifyCitationTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final VerifyCitation verify = new VerifyCitation(orgs, members, docs);
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
    void notReadyDocumentIsNotFound() {
        Document ready = Fixtures.readyDocument(docs, companyA, "La franquicia es de 300 euros");
        docs.save(ready.markFailed("roto"));

        assertThatThrownBy(() -> verify.execute(Actors.worker(companyA), ready.id(), 1, "franquicia"))
                .isInstanceOf(NotFoundException.class);
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

    @Test
    void unassignedProjectIsInvisible() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        Document d = Fixtures.documentIn(docs, companyA, closed, "La franquicia es de 300 euros");
        Actor worker = Actors.worker(companyA);

        assertThat(verify.execute(worker, d.id(), 1, "franquicia")).isFalse();

        members.add(closed, worker.userId());
        assertThat(verify.execute(worker, d.id(), 1, "franquicia")).isTrue();
    }

    @Test
    void citationOfDeletedProjectStaysVerifiableByWhoCouldSeeIt() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        Document d = Fixtures.documentIn(docs, companyA, closed, "La franquicia es de 300 euros");
        Actor member = Actors.worker(companyA);
        members.add(closed, member.userId());
        orgs.deleteProject(companyA, closed, Fixtures.NOW);

        assertThat(verify.execute(member, d.id(), 1, "franquicia")).isTrue();
        assertThat(verify.execute(Actors.worker(companyA), d.id(), 1, "franquicia")).isFalse();
    }
}
