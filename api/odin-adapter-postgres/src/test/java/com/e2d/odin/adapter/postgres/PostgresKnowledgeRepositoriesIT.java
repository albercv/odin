package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.knowledge.CardReview;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class PostgresKnowledgeRepositoriesIT extends AbstractPostgresIT {

    private KnowledgeCard card(CompanyId company, UserId author, String title) {
        KnowledgeCard c = new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.empty(), title, "Cuerpo",
                Optional.empty(), author, NOW, CardStatus.PENDING, Optional.empty());
        cards.save(c);
        return c;
    }

    @Test
    void savesAndFindsCardsWithinScope() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        KnowledgeCard c = card(a, newUser(a), "Ficha");
        assertThat(cards.findById(CompanyScope.of(a), c.id())).contains(c);
        assertThat(cards.findById(CompanyScope.all(), c.id())).contains(c);
        assertThat(cards.findById(CompanyScope.of(b), c.id())).isEmpty();
    }

    @Test
    void reviewAndCorrectionArePersisted() {
        CompanyId a = newCompany();
        UserId reviewer = newUser(a);
        KnowledgeCard c = card(a, newUser(a), "Ficha");
        CardReview review = new CardReview(reviewer, ReviewDecision.CORRECT, NOW.plusSeconds(5), Optional.of("nota"));
        KnowledgeCard corrected = c.correct("Cuerpo corregido", review);
        cards.save(corrected);
        assertThat(cards.findById(CompanyScope.of(a), c.id())).contains(corrected);
        KnowledgeCard noNote = c.approve(new CardReview(reviewer, ReviewDecision.APPROVE, NOW, Optional.empty()));
        cards.save(noNote);
        assertThat(cards.findById(CompanyScope.of(a), c.id())).contains(noNote);
    }

    @Test
    void listByStatusFiltersStatusAndScopeInInsertionOrder() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId ua = newUser(a);
        KnowledgeCard one = card(a, ua, "Uno");
        KnowledgeCard two = card(a, ua, "Dos");
        card(b, newUser(b), "Ajena");
        KnowledgeCard approved = card(a, ua, "Aprobada")
                .approve(new CardReview(ua, ReviewDecision.APPROVE, NOW, Optional.empty()));
        cards.save(approved);
        assertThat(cards.listByStatus(CompanyScope.of(a), CardStatus.PENDING)).containsExactly(one, two);
        assertThat(cards.listByStatus(CompanyScope.of(a), CardStatus.APPROVED)).containsExactly(approved);
        assertThat(cards.listByStatus(CompanyScope.of(b), CardStatus.APPROVED)).isEmpty();
        assertThat(cards.listByStatus(CompanyScope.all(), CardStatus.PENDING)).hasSize(3);
    }

    @Test
    void questionLogStoresTextAndAuthorOnlyForItsCompany() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId author = newUser(a);
        QuestionRecord q = new QuestionRecord(UUID.randomUUID(), a, author, "¿Cuánto cuesta?", 2, NOW);
        new PostgresQuestionLog(tx).record(q);
        assertThat(questionTexts(CompanyScope.of(a))).containsExactly("¿Cuánto cuesta?");
        assertThat(questionTexts(CompanyScope.of(b))).isEmpty();
        assertThat(hitCounts()).containsExactly(2);
    }

    private List<String> questionTexts(CompanyScope scope) {
        return tx.inScope(scope, (JdbcClient jdbc) -> jdbc.sql("SELECT text FROM question_record")
                .query(String.class).list());
    }

    private List<Integer> hitCounts() {
        return tx.inScope(CompanyScope.all(), (JdbcClient jdbc) -> jdbc.sql("SELECT hit_count FROM question_record")
                .query(Integer.class).list());
    }
}
