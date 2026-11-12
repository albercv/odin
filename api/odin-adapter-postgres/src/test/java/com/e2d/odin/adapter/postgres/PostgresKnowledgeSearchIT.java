package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostgresKnowledgeSearchIT extends AbstractPostgresIT {

    private final PostgresKnowledgeSearch search = new PostgresKnowledgeSearch(tx);

    private Document page(CompanyId company, String title, DocumentStatus status, String... texts) {
        Document d = newDocument(company, newUser(company), title, status);
        int[] n = {0};
        docs.replacePages(d.id(), java.util.Arrays.stream(texts)
                .map(t -> new DocumentPage(d.id(), ++n[0], t, "lector", "1")).toList());
        return d;
    }

    private void card(CompanyId company, CardStatus status, String title, String body) {
        cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.empty(), title, body,
                Optional.empty(), newUser(company), NOW, status, Optional.empty()));
    }

    private List<SearchHit> find(CompanyScope scope, String... terms) {
        return search.search(new SearchQuery(List.of(terms), scope, ProjectVisibility.all(), Optional.empty(), 5)).hits();
    }

    @Test
    void findsAccentInsensitiveStem() {
        CompanyId c = newCompany();
        page(c, "Caso", DocumentStatus.READY, "Se detectó una filtración en la azotea del edificio.");
        List<SearchHit> hits = find(CompanyScope.of(c), "filtraciones", "FILTRACION");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).sourceType()).isEqualTo(SourceType.ORIGINAL_DOCUMENT);
        assertThat(hits.get(0).pageNumber()).contains(1);
        assertThat(hits.get(0).title()).isEqualTo("Caso");
        assertThat(hits.get(0).authorName()).isEqualTo("Persona Pérez");
    }

    @Test
    void identifierChannelRanksFirst() {
        CompanyId c = newCompany();
        page(c, "Muchas palabras", DocumentStatus.READY, "póliza franquicia daños agua seguro seguro seguro",
                "otra póliza distinta ATL-4417-2024 sin más");
        List<SearchHit> hits = find(CompanyScope.of(c), "póliza franquicia daños agua seguro", "ATL-4417-2024");
        assertThat(hits.get(0).pageNumber()).contains(2);
        assertThat(hits.get(0).rank()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void identifierWithTrailingPunctuationStillMatches() {
        CompanyId c = newCompany();
        page(c, "Doc", DocumentStatus.READY, "Referencia ATL-4417-2024 del contrato.");
        assertThat(find(CompanyScope.of(c), "ATL-4417-2024?")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"franquicia\tATL-4417-2024", "franquicia\nATL-4417-2024"})
    void identifierSeparatedByTabOrNewlineStillUsesTheExactChannel(String query) {
        CompanyId c = newCompany();
        page(c, "Doc", DocumentStatus.READY, "Referencia ATL-4417-2024 del contrato.");
        List<SearchHit> hits = find(CompanyScope.of(c), query);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).rank()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void pureNumbersAreNotBoosted() {
        CompanyId c = newCompany();
        page(c, "Doc", DocumentStatus.READY, "póliza seguro seguro daños 2025", "póliza seguro daños");
        List<SearchHit> hits = find(CompanyScope.of(c), "póliza seguro daños", "2025");
        assertThat(hits).isNotEmpty();
        assertThat(hits).allSatisfy(h -> assertThat(h.rank()).isLessThan(100));
    }

    @Test
    void identifierWithoutWordsStillMatchesByLiteral() {
        CompanyId c = newCompany();
        page(c, "Doc", DocumentStatus.READY, "Referencia ATL-4417-2024 del contrato.");
        assertThat(find(CompanyScope.of(c), "ATL-4417-2024")).hasSize(1);
    }

    @Test
    void excerptIsLiteral() {
        CompanyId c = newCompany();
        String text = "Primera línea del acta.\n\n  La   derrama   fue de 48.000 euros en total.\nÚltima línea. "
                + "x".repeat(2000) + " filtración final";
        page(c, "Acta", DocumentStatus.READY, text);
        card(c, CardStatus.APPROVED, "Derrama", "La derrama de fachada se aprobó.\nSe pagó en cuotas.");
        List<SearchHit> hits = find(CompanyScope.of(c), "derrama", "48.000", "filtraciones");
        assertThat(hits).hasSize(2);
        for (SearchHit hit : hits) {
            String source = hit.sourceType() == SourceType.KNOWLEDGE_CARD
                    ? "La derrama de fachada se aprobó.\nSe pagó en cuotas." : text;
            assertThat(QuoteMatcher.containsLiteral(source, hit.excerpt())).as(hit.excerpt()).isTrue();
        }
    }

    @Test
    void otherCompanyNeverReturned() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        page(b, "Ajeno", DocumentStatus.READY, "filtración secreta de la otra empresa");
        card(b, CardStatus.APPROVED, "Ajena", "filtración resuelta en la otra empresa");
        assertThat(find(CompanyScope.of(a), "filtración")).isEmpty();
        assertThat(find(CompanyScope.of(b), "filtración")).hasSize(2);
        assertThat(find(CompanyScope.all(), "filtración")).hasSize(2);
    }

    @Test
    void pendingCardNeverReturned() {
        CompanyId c = newCompany();
        card(c, CardStatus.PENDING, "Pendiente", "filtración pendiente de revisar");
        assertThat(find(CompanyScope.of(c), "filtración")).isEmpty();
    }

    @Test
    void rejectedCardNeverReturned() {
        CompanyId c = newCompany();
        card(c, CardStatus.REJECTED, "Rechazada", "filtración rechazada");
        card(c, CardStatus.APPROVED, "Aprobada", "filtración aprobada");
        assertThat(find(CompanyScope.of(c), "filtración")).extracting(SearchHit::title).containsExactly("Aprobada");
    }

    @Test
    void notReadyDocumentNotReturned() {
        CompanyId c = newCompany();
        page(c, "Procesando", DocumentStatus.PROCESSING, "filtración en proceso");
        page(c, "Fallido", DocumentStatus.FAILED, "filtración fallida");
        assertThat(find(CompanyScope.of(c), "filtración")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "¿?", "de la el"})
    void emptyOrStopwordQueryReturnsNoSource(String query) {
        CompanyId c = newCompany();
        page(c, "Doc", DocumentStatus.READY, "de la el filtración");
        assertThat(find(CompanyScope.of(c), query)).isEmpty();
        assertThat(find(CompanyScope.of(c))).isEmpty();
    }

    @Test
    void filtersByVisibleProjects() {
        CompanyId c = newCompany();
        ProjectId visible = newProject(c, "Visible");
        ProjectId hidden = newProject(c, "Oculto");
        page(c, "Libre", DocumentStatus.READY, "la franquicia del seguro");
        inProject(page(c, "Del visible", DocumentStatus.READY, "la franquicia del seguro"), visible);
        inProject(page(c, "Del oculto", DocumentStatus.READY, "la franquicia del seguro"), hidden);
        cardIn(c, hidden, "Ficha oculta", "la franquicia del seguro");
        cardIn(c, visible, "Ficha visible", "la franquicia del seguro");

        assertThat(titles(c, ProjectVisibility.of(Set.of(visible)), Optional.empty()))
                .containsExactlyInAnyOrder("Libre", "Del visible", "Ficha visible");
        assertThat(titles(c, ProjectVisibility.of(Set.of()), Optional.empty())).containsExactly("Libre");
        assertThat(titles(c, ProjectVisibility.all(), Optional.empty())).hasSize(5);
        assertThat(titles(c, ProjectVisibility.of(Set.of(visible)), Optional.of(hidden))).isEmpty();
        assertThat(titles(c, ProjectVisibility.of(Set.of(visible)), Optional.of(visible)))
                .containsExactlyInAnyOrder("Del visible", "Ficha visible");
    }

    private List<String> titles(CompanyId company, ProjectVisibility visibility, Optional<ProjectId> project) {
        return search.search(new SearchQuery(List.of("franquicia"), CompanyScope.of(company), visibility, project, 10))
                .hits().stream().map(SearchHit::title).toList();
    }

    private void inProject(Document d, ProjectId project) {
        docs.save(new Document(d.id(), d.companyId(), Optional.of(project), d.title(), d.filename(), d.format(),
                d.sha256(), d.status(), d.failureReason(), d.paginated(), d.uploadedBy(), d.uploadedAt(),
                d.processingStartedAt(), 0, Optional.empty(), Optional.empty()));
    }

    private void cardIn(CompanyId company, ProjectId project, String title, String body) {
        cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.of(project), title, body,
                Optional.empty(), newUser(company), NOW, CardStatus.APPROVED, Optional.empty()));
    }
}
