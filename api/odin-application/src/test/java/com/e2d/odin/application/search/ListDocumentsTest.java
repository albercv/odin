package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListDocumentsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final ListDocuments list = new ListDocuments(orgs, members, docs);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    @Test
    void capsLimitAt500() {
        IntStream.range(0, 510).forEach(i -> Fixtures.document(docs, companyA, DocumentStatus.READY));

        assertThat(list.execute(Actors.worker(companyA), Optional.empty(), 0, Optional.of(1000))).hasSize(500);
        assertThat(list.execute(Actors.worker(companyA), Optional.empty(), 0, Optional.empty())).hasSize(100);
    }

    @Test
    void listsOnlyReadyDocuments() {
        Document ready = Fixtures.document(docs, companyA, DocumentStatus.READY);
        for (DocumentStatus other : List.of(DocumentStatus.RECEIVED, DocumentStatus.PROCESSING, DocumentStatus.FAILED,
                DocumentStatus.PENDING_READER)) {
            Fixtures.document(docs, companyA, other);
        }

        assertThat(list.execute(Actors.worker(companyA), Optional.empty(), 0, Optional.empty())).containsExactly(ready);
    }

    @Test
    void workerOfOtherCompanyNeverSeesCompanyADocuments() {
        Document a = Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThat(list.execute(Actors.worker(companyB), Optional.empty(), 0, Optional.empty())).doesNotContain(a);
        assertThat(docs.lastListScope()).isEqualTo(CompanyScope.of(companyB));
    }

    @Test
    void workerOfOtherCompanyCannotUseProjectOfCompanyA() {
        var projectOfA = Fixtures.project(orgs, companyA);

        assertThatThrownBy(() -> list.execute(Actors.worker(companyB), Optional.of(projectOfA), 0, Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminCannotUseWorkerListing() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(companyA), Optional.empty(), 0, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unassignedProjectIsInvisible() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        Document inClosed = Fixtures.documentIn(docs, companyA, closed, "texto");
        Document withoutProject = Fixtures.readyDocument(docs, companyA, "libre");
        Actor worker = Actors.worker(companyA);

        assertThat(list.execute(worker, Optional.empty(), 0, Optional.empty())).containsExactly(withoutProject);
        assertThatThrownBy(() -> list.execute(worker, Optional.of(closed), 0, Optional.empty()))
                .isInstanceOf(NotFoundException.class).hasMessage("Proyecto no encontrado");

        members.add(closed, worker.userId());
        assertThat(list.execute(worker, Optional.empty(), 0, Optional.empty()))
                .containsExactlyInAnyOrder(inClosed, withoutProject);
        assertThat(list.execute(worker, Optional.of(closed), 0, Optional.empty())).containsExactly(inClosed);
    }

    @Test
    void companyWideProjectDocumentsAreVisibleToEveryWorker() {
        ProjectId open = Fixtures.companyWideProject(orgs, companyA);
        Document inOpen = Fixtures.documentIn(docs, companyA, open, "texto");

        assertThat(list.execute(Actors.worker(companyA), Optional.of(open), 0, Optional.empty()))
                .containsExactly(inOpen);
    }

    @Test
    void documentOfDeletedProjectStaysVisibleToWhoCouldSeeIt() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        Document inClosed = Fixtures.documentIn(docs, companyA, closed, "texto");
        Actor member = Actors.worker(companyA);
        members.add(closed, member.userId());
        orgs.deleteProject(companyA, closed, Fixtures.NOW);

        assertThat(list.execute(member, Optional.empty(), 0, Optional.empty())).containsExactly(inClosed);
        assertThat(list.execute(Actors.worker(companyA), Optional.empty(), 0, Optional.empty())).isEmpty();
        assertThatThrownBy(() -> list.execute(member, Optional.of(closed), 0, Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }
}
