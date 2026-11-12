package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentRef;
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
        assertThat(titles(c, ProjectVisibility.of(Set.of(visible)), Optional.of(hidden))).containsExactly("Libre");
    }

    @Test
    void projectFilterIncludesCompanyWideDocuments() {
        CompanyId c = newCompany();
        ProjectId project = newProject(c, "Comunidad Sol");
        page(c, "Norma interna", DocumentStatus.READY, "la franquicia del seguro");
        inProject(page(c, "Del proyecto", DocumentStatus.READY, "la franquicia del seguro"), project);
        assertThat(titles(c, ProjectVisibility.of(Set.of(project)), Optional.of(project)))
                .containsExactlyInAnyOrder("Norma interna", "Del proyecto");
    }

    @Test
    void projectFilterIncludesCompanyWideCards() {
        CompanyId c = newCompany();
        ProjectId project = newProject(c, "Comunidad Sol");
        card(c, CardStatus.APPROVED, "Ficha general", "la franquicia del seguro");
        cardIn(c, project, "Ficha del proyecto", "la franquicia del seguro");
        assertThat(titles(c, ProjectVisibility.of(Set.of(project)), Optional.of(project)))
                .containsExactlyInAnyOrder("Ficha general", "Ficha del proyecto");
    }

    @Test
    void projectFilterNeverReturnsAnotherProject() {
        CompanyId c = newCompany();
        ProjectId wanted = newProject(c, "Pedido");
        ProjectId other = newProject(c, "Otro");
        inProject(page(c, "Del pedido", DocumentStatus.READY, "la franquicia del seguro"), wanted);
        inProject(page(c, "Del otro", DocumentStatus.READY, "la franquicia del seguro"), other);
        cardIn(c, other, "Ficha del otro", "la franquicia del seguro");
        assertThat(titles(c, ProjectVisibility.all(), Optional.of(wanted))).containsExactly("Del pedido");
    }

    private List<SearchHit> findIn(CompanyId company, int limit, String... terms) {
        return search.search(new SearchQuery(List.of(terms), CompanyScope.of(company), ProjectVisibility.all(),
                Optional.empty(), limit)).hits();
    }

    private void supersede(CompanyId company, Document old, Document current) {
        docs.markSuperseded(company, old.id(), current.id(), NOW);
    }

    @Test
    void supersededHitGoesBehindCurrentOnesEvenWithBetterRank() {
        CompanyId c = newCompany();
        Document old = page(c, "Póliza 2022", DocumentStatus.READY, "póliza ATL-4417-2024 franquicia");
        Document current = page(c, "Póliza 2024", DocumentStatus.READY, "la póliza vigente");
        supersede(c, old, current);
        List<SearchHit> hits = findIn(c, 5, "ATL-4417-2024", "póliza");
        assertThat(hits).extracting(SearchHit::title).containsExactly("Póliza 2024", "Póliza 2022");
        assertThat(hits.get(1).rank()).isGreaterThanOrEqualTo(100);
        assertThat(hits.get(1).superseded()).isTrue();
        assertThat(hits.get(1).replacement()).contains(DocumentRef.of(current));
        assertThat(hits.get(0).superseded()).isFalse();
        assertThat(hits.get(0).replacement()).isEmpty();
    }

    @Test
    void topFiveAreTheSameWithAndWithoutMarks() {
        CompanyId c = newCompany();
        List<Document> candidates = java.util.stream.IntStream.range(0, 7).mapToObj(i -> page(c, "Doc " + i,
                DocumentStatus.READY, "franquicia ".repeat(7 - i) + "texto de relleno largo para variar")).toList();
        Document current = page(c, "Vigente", DocumentStatus.READY, "otra cosa");
        Set<String> before = findIn(c, 5, "franquicia").stream().map(h -> h.sourceId().toString())
                .collect(java.util.stream.Collectors.toSet());
        supersede(c, candidates.get(0), current);
        supersede(c, candidates.get(2), current);
        List<SearchHit> after = findIn(c, 5, "franquicia");
        assertThat(after).hasSize(5);
        assertThat(after.stream().map(h -> h.sourceId().toString()).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(before);
    }

    @Test
    void supersededAndCurrentKeepTheirRelativeOrder() {
        CompanyId c = newCompany();
        Document a = page(c, "A", DocumentStatus.READY, "la franquicia");
        page(c, "B", DocumentStatus.READY, "la franquicia");
        Document cc = page(c, "C", DocumentStatus.READY, "la franquicia");
        page(c, "D", DocumentStatus.READY, "la franquicia");
        Document current = page(c, "Vigente", DocumentStatus.READY, "otra cosa");
        supersede(c, a, current);
        supersede(c, cc, current);
        assertThat(findIn(c, 5, "franquicia")).extracting(SearchHit::title).containsExactly("B", "D", "A", "C");
    }

    @Test
    void replacementIsTheEndOfTheChain() {
        CompanyId c = newCompany();
        Document a = page(c, "A 2022", DocumentStatus.READY, "zafiro");
        Document b = page(c, "B 2023", DocumentStatus.READY, "otra cosa");
        Document last = page(c, "C 2024", DocumentStatus.READY, "algo distinto");
        supersede(c, a, b);
        supersede(c, b, last);
        List<SearchHit> hits = findIn(c, 5, "zafiro");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).superseded()).isTrue();
        assertThat(hits.get(0).replacement()).contains(DocumentRef.of(last));
    }

    @Test
    void replacementIsNullWhenTheWorkerCannotSeeIt() {
        CompanyId c = newCompany();
        ProjectId closed = newProject(c, "Cerrado");
        Document old = page(c, "Viejo", DocumentStatus.READY, "zafiro");
        Document current = page(c, "Nuevo", DocumentStatus.READY, "otra cosa");
        inProject(current, closed);
        supersede(c, old, current);
        SearchQuery blind = new SearchQuery(List.of("zafiro"), CompanyScope.of(c), ProjectVisibility.of(Set.of()),
                Optional.empty(), 5);
        List<SearchHit> hits = search.search(blind).hits();
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).superseded()).isTrue();
        assertThat(hits.get(0).replacement()).isEmpty();
        SearchQuery member = new SearchQuery(List.of("zafiro"), CompanyScope.of(c),
                ProjectVisibility.of(Set.of(closed)), Optional.empty(), 5);
        assertThat(search.search(member).hits().get(0).replacement()).contains(DocumentRef.of(current));
    }

    @Test
    void restrictedProjectDocumentsStayInvisibleMarkedOrNot() {
        CompanyId c = newCompany();
        ProjectId closed = newProject(c, "Cerrado");
        Document hiddenOld = page(c, "Oculto viejo", DocumentStatus.READY, "la franquicia");
        Document hiddenNew = page(c, "Oculto nuevo", DocumentStatus.READY, "la franquicia");
        inProject(hiddenOld, closed);
        inProject(hiddenNew, closed);
        page(c, "Libre", DocumentStatus.READY, "la franquicia");
        supersede(c, hiddenOld, hiddenNew);
        assertThat(titles(c, ProjectVisibility.of(Set.of()), Optional.empty())).containsExactly("Libre");
        assertThat(titles(c, ProjectVisibility.of(Set.of(closed)), Optional.empty())).hasSize(3);
    }

    @Test
    void cardsAreNeverSupersededAndStayAmongCurrent() {
        CompanyId c = newCompany();
        Document old = page(c, "Viejo", DocumentStatus.READY, "póliza ATL-4417-2024");
        Document current = page(c, "Nuevo", DocumentStatus.READY, "otra cosa");
        card(c, CardStatus.APPROVED, "Ficha", "la póliza en general");
        supersede(c, old, current);
        List<SearchHit> hits = findIn(c, 5, "ATL-4417-2024", "póliza");
        assertThat(hits).extracting(SearchHit::title).containsExactly("Ficha", "Viejo");
        assertThat(hits.get(0).sourceType()).isEqualTo(SourceType.KNOWLEDGE_CARD);
        assertThat(hits.get(0).superseded()).isFalse();
        assertThat(hits.get(0).replacement()).isEmpty();
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
