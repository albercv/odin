package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import java.util.List;
import java.util.Optional;
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
        int seen = ownerDocs.findById(scope, documentOfA.id()).stream().toList().size();
        seen += ownerDocs.list(scope, Optional.empty(), 0, 10).size();
        seen += ownerDocs.findPage(scope, documentOfA.id(), 1).stream().toList().size();
        seen += ownerCards.findById(scope, pendingOfA.id()).stream().toList().size();
        seen += ownerCards.listByStatus(scope, CardStatus.PENDING).size();
        return seen + ownerSearch.search(new SearchQuery(List.of("filtración"), scope, Optional.empty(), 10))
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
}
