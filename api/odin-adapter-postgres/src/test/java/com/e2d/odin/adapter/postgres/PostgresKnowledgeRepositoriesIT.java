package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.knowledge.CardReview;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        assertThat(cards.findById(CompanyScope.of(a), ProjectVisibility.all(), c.id())).contains(c);
        assertThat(cards.findById(CompanyScope.all(), ProjectVisibility.all(), c.id())).contains(c);
        assertThat(cards.findById(CompanyScope.of(b), ProjectVisibility.all(), c.id())).isEmpty();
    }

    @Test
    void reviewAndCorrectionArePersisted() {
        CompanyId a = newCompany();
        UserId reviewer = newUser(a);
        KnowledgeCard c = card(a, newUser(a), "Ficha");
        CardReview review = new CardReview(reviewer, ReviewDecision.CORRECT, NOW.plusSeconds(5), Optional.of("nota"));
        KnowledgeCard corrected = c.correct("Cuerpo corregido", review);
        cards.save(corrected);
        assertThat(cards.findById(CompanyScope.of(a), ProjectVisibility.all(), c.id())).contains(corrected);
        KnowledgeCard noNote = c.approve(new CardReview(reviewer, ReviewDecision.APPROVE, NOW, Optional.empty()));
        cards.save(noNote);
        assertThat(cards.findById(CompanyScope.of(a), ProjectVisibility.all(), c.id())).contains(noNote);
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
        assertThat(cards.listByStatus(CompanyScope.of(a), ProjectVisibility.all(), CardStatus.PENDING)).containsExactly(one, two);
        assertThat(cards.listByStatus(CompanyScope.of(a), ProjectVisibility.all(), CardStatus.APPROVED)).containsExactly(approved);
        assertThat(cards.listByStatus(CompanyScope.of(b), ProjectVisibility.all(), CardStatus.APPROVED)).isEmpty();
        assertThat(cards.listByStatus(CompanyScope.all(), ProjectVisibility.all(), CardStatus.PENDING)).hasSize(3);
    }

    @Test
    void questionLogStoresTextAndAuthorOnlyForItsCompany() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId author = newUser(a);
        QuestionRecord q = new QuestionRecord(UUID.randomUUID(), a, author, "¿Cuánto cuesta?", 2, NOW, QuestionKind.SEARCH);
        new PostgresQuestionLog(tx).record(q);
        assertThat(questionTexts(CompanyScope.of(a))).containsExactly("¿Cuánto cuesta?");
        assertThat(questionTexts(CompanyScope.of(b))).isEmpty();
        assertThat(hitCounts()).containsExactly(2);
    }

    @Test
    void storesKind() {
        CompanyId a = newCompany();
        UserId author = newUser(a);
        PostgresQuestionLog log = new PostgresQuestionLog(tx);
        log.record(new QuestionRecord(UUID.randomUUID(), a, author, "busca", 1, NOW, QuestionKind.SEARCH));
        log.record(new QuestionRecord(UUID.randomUUID(), a, author, "casos", 3, NOW, QuestionKind.SIMILAR_CASES));

        List<String> kinds = tx.inScope(CompanyScope.of(a), (JdbcClient jdbc) -> jdbc
                .sql("SELECT kind FROM question_record ORDER BY text").query(String.class).list());

        assertThat(kinds).containsExactly("SEARCH", "SIMILAR_CASES");
    }

    @Test
    void kindOnlyAcceptsKnownValues() {
        CompanyId a = newCompany();
        UserId author = newUser(a);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.runInScope(CompanyScope.of(a), jdbc -> jdbc.sql("""
                INSERT INTO question_record (id, company_id, author_id, text, hit_count, asked_at, kind)
                VALUES (:id, :company, :author, 'x', 0, now(), 'OTRA')""").param("id", UUID.randomUUID())
                .param("company", a.value()).param("author", author.value()).update()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private List<String> questionTexts(CompanyScope scope) {
        return tx.inScope(scope, (JdbcClient jdbc) -> jdbc.sql("SELECT text FROM question_record")
                .query(String.class).list());
    }

    private List<Integer> hitCounts() {
        return tx.inScope(CompanyScope.all(), (JdbcClient jdbc) -> jdbc.sql("SELECT hit_count FROM question_record")
                .query(Integer.class).list());
    }

    @Test
    void filtersByVisibleProjects() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        ProjectId visible = newProject(a, "Visible");
        ProjectId hidden = newProject(a, "Oculto");
        KnowledgeCard free = card(a, ua, "Libre");
        KnowledgeCard inVisible = cardIn(a, ua, visible, "En visible");
        KnowledgeCard inHidden = cardIn(a, ua, hidden, "En oculta");
        ProjectVisibility some = ProjectVisibility.of(Set.of(visible));
        CompanyScope scope = CompanyScope.of(a);

        assertThat(cards.listByStatus(scope, some, CardStatus.PENDING)).containsExactly(free, inVisible);
        assertThat(cards.listByStatus(scope, ProjectVisibility.of(Set.of()), CardStatus.PENDING))
                .containsExactly(free);
        assertThat(cards.listByStatus(scope, ProjectVisibility.all(), CardStatus.PENDING))
                .containsExactly(free, inVisible, inHidden);
        assertThat(cards.findById(scope, some, inHidden.id())).isEmpty();
        assertThat(cards.findById(scope, some, inVisible.id())).contains(inVisible);
        assertThat(cards.findById(scope, some, free.id())).contains(free);
        assertThat(cards.findById(scope, ProjectVisibility.all(), inHidden.id())).contains(inHidden);
    }

    private KnowledgeCard cardIn(CompanyId company, UserId author, ProjectId project, String title) {
        KnowledgeCard c = new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.of(project), title,
                "Cuerpo", Optional.empty(), author, NOW, CardStatus.PENDING, Optional.empty());
        cards.save(c);
        return c;
    }
}
