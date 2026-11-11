package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El rol dueño se salta la RLS: el filtro de empresa del propio SQL tiene que bastar. */
class OwnerConnectionIsolationIT extends AbstractPostgresIT {

    private final ScopedTransactions ownerTx = new ScopedTransactions(Db.owner());
    private final PostgresDocumentRepository ownerDocs = new PostgresDocumentRepository(ownerTx);
    private final PostgresKnowledgeCardRepository ownerCards = new PostgresKnowledgeCardRepository(ownerTx);
    private final PostgresKnowledgeSearch ownerSearch = new PostgresKnowledgeSearch(ownerTx);

    private CompanyId a;
    private CompanyId b;
    private Document documentOfA;
    private KnowledgeCard pendingOfA;

    @BeforeEach
    void seedCompanyA() {
        a = newCompany();
        b = newCompany();
        documentOfA = newDocument(a, newUser(a), "Póliza de A", DocumentStatus.READY);
        docs.replacePages(documentOfA.id(),
                List.of(new DocumentPage(documentOfA.id(), 1, "filtración secreta de A", "lector", "1")));
        pendingOfA = card(CardStatus.PENDING);
        card(CardStatus.APPROVED);
    }

    private KnowledgeCard card(CardStatus status) {
        KnowledgeCard c = new KnowledgeCard(new CardId(UUID.randomUUID()), a, Optional.empty(), "Ficha de A",
                "filtración resuelta en A", Optional.empty(), newUser(a), NOW, status, Optional.empty());
        cards.save(c);
        return c;
    }

    private int readEverything(CompanyScope scope) {
        int seen = ownerDocs.findById(scope, ProjectVisibility.all(), documentOfA.id()).stream().toList().size();
        seen += ownerDocs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10).size();
        seen += ownerDocs.findPage(scope, ProjectVisibility.all(), documentOfA.id(), 1).stream().toList().size();
        seen += ownerCards.findById(scope, ProjectVisibility.all(), pendingOfA.id()).stream().toList().size();
        seen += ownerCards.listByStatus(scope, ProjectVisibility.all(), CardStatus.PENDING).size();
        return seen + ownerSearch.search(new SearchQuery(List.of("filtración"), scope, ProjectVisibility.all(), Optional.empty(), 10))
                .hits().size();
    }

    @Test
    void scopeOfBReadsNothingOfAThroughAConnectionThatBypassesRls() {
        assertThat(readEverything(CompanyScope.of(b))).isZero();
    }

    @Test
    void sameConnectionStillReadsAWithItsOwnScope() {
        assertThat(readEverything(CompanyScope.of(a))).isEqualTo(7);
        assertThat(readEverything(CompanyScope.all())).isEqualTo(7);
    }

    @Test
    void closedProjectIsHiddenFromEmptyVisibilityThroughAConnectionThatBypassesRls() {
        ProjectId project = newProject(a, "Cerrado");
        UserId user = newUser(a);
        Document closed = new Document(new DocumentId(UUID.randomUUID()), a, Optional.of(project), "Cerrado",
                "f.pdf", DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(),
                true, user, NOW, Optional.empty(), 0);
        docs.save(closed);
        docs.replacePages(closed.id(), List.of(new DocumentPage(closed.id(), 1, "zafiro cerrado", "lector", "1")));
        KnowledgeCard closedCard = new KnowledgeCard(new CardId(UUID.randomUUID()), a, Optional.of(project),
                "Ficha cerrada", "zafiro cerrado", Optional.empty(), user, NOW, CardStatus.PENDING,
                Optional.empty());
        cards.save(closedCard);
        CompanyScope scope = CompanyScope.of(a);
        ProjectVisibility none = ProjectVisibility.of(Set.of());

        assertThat(ownerDocs.findById(scope, none, closed.id())).isEmpty();
        assertThat(ownerDocs.list(scope, none, Optional.of(project), Optional.empty(), 0, 10)).isEmpty();
        assertThat(ownerDocs.findPage(scope, none, closed.id(), 1)).isEmpty();
        assertThat(ownerCards.findById(scope, none, closedCard.id())).isEmpty();
        assertThat(ownerCards.listByStatus(scope, none, CardStatus.PENDING))
                .extracting(KnowledgeCard::id).doesNotContain(closedCard.id());
        assertThat(ownerSearch.search(new SearchQuery(List.of("zafiro"), scope, none, Optional.empty(), 10))
                .hits()).isEmpty();
    }
}
