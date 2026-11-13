package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.ReviewStatus;
import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.search.CasePage;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SimilarCase;
import com.e2d.odin.domain.search.SimilarCasesQuery;
import com.e2d.odin.domain.search.SimilarCasesResult;
import com.e2d.odin.domain.search.SourceType;
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
        return search.search(new SearchQuery(List.of(terms), scope, ProjectVisibility.all(), Optional.empty(), 5, Db.NOBODY)).hits();
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
                Optional.empty(), limit, Db.NOBODY)).hits();
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
        forceMark(old, current);
        SearchQuery blind = new SearchQuery(List.of("zafiro"), CompanyScope.of(c), ProjectVisibility.of(Set.of()),
                Optional.empty(), 5, Db.NOBODY);
        List<SearchHit> hits = search.search(blind).hits();
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).superseded()).isTrue();
        assertThat(hits.get(0).replacement()).isEmpty();
        SearchQuery member = new SearchQuery(List.of("zafiro"), CompanyScope.of(c),
                ProjectVisibility.of(Set.of(closed)), Optional.empty(), 5, Db.NOBODY);
        assertThat(search.search(member).hits().get(0).replacement()).contains(DocumentRef.of(current));
    }

    @Test
    void replacementOfAnotherCompanyIsNeverReturned() {
        CompanyId c = newCompany();
        Document old = page(c, "Viejo", DocumentStatus.READY, "zafiro");
        Document foreign = page(newCompany(), "Ajeno", DocumentStatus.READY, "otra cosa");
        forceMarkAcrossCompanies(old, foreign);
        for (CompanyScope scope : List.of(CompanyScope.of(c), CompanyScope.all())) {
            List<SearchHit> hits = find(scope, "zafiro");
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).replacement()).isEmpty();
        }
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

    private Document caseDocument(CompanyId company, String title, String... texts) {
        Document d = page(company, title, DocumentStatus.READY, texts);
        docs.markCaseDocuments(company, Set.of(d.id()), true);
        return d;
    }

    @Test
    void caseDocumentFlagsCaseCards() {
        CompanyId c = newCompany();
        caseDocument(c, "Ficha del caso", "la franquicia del seguro");
        page(c, "Documento normal", DocumentStatus.READY, "la franquicia del seguro");
        card(c, CardStatus.APPROVED, "Ficha aprobada", "la franquicia del seguro");

        List<SearchHit> hits = find(CompanyScope.of(c), "franquicia");

        assertThat(hits).extracting(SearchHit::title, SearchHit::caseDocument).containsExactlyInAnyOrder(
                tuple("Ficha del caso", true), tuple("Documento normal", false), tuple("Ficha aprobada", false));
    }

    private SimilarCasesResult cases(CompanyId company, ProjectVisibility visibility, String... terms) {
        return search.findSimilarCases(new SimilarCasesQuery(List.of(terms), CompanyScope.of(company), visibility,
                Optional.empty(), 5, Db.NOBODY));
    }

    private static List<String> caseTitles(SimilarCasesResult result) {
        return result.cases().stream().map(SimilarCase::title).toList();
    }

    @Test
    void onlyCaseDocumentsAndApprovedCardsAreCandidates() {
        CompanyId c = newCompany();
        caseDocument(c, "Caso marcado", "la franquicia del seguro");
        page(c, "Documento suelto", DocumentStatus.READY, "la franquicia del seguro");
        card(c, CardStatus.APPROVED, "Ficha aprobada", "la franquicia del seguro");
        caseDocument(c, "Caso sin leer", "la franquicia del seguro");
        docs.save(docs.findBySha256(c, docs.list(CompanyScope.of(c), ProjectVisibility.all(), Optional.empty(),
                Optional.empty(), 0, 10).stream().filter(e -> e.document().title().equals("Caso sin leer"))
                .findFirst().orElseThrow().document().sha256()).orElseThrow().markFailed("roto"));

        SimilarCasesResult result = cases(c, ProjectVisibility.all(), "franquicia");

        assertThat(caseTitles(result)).containsExactlyInAnyOrder("Caso marcado", "Ficha aprobada");
        SimilarCase card = result.cases().stream().filter(x -> x.sourceType() == SourceType.KNOWLEDGE_CARD)
                .findFirst().orElseThrow();
        assertThat(card.body()).contains("la franquicia del seguro");
        assertThat(card.pages()).isEmpty();
    }

    @Test
    void pendingAndRejectedCardsAreExcluded() {
        CompanyId c = newCompany();
        card(c, CardStatus.PENDING, "Pendiente", "la franquicia del seguro");
        card(c, CardStatus.REJECTED, "Rechazada", "la franquicia del seguro");
        card(c, CardStatus.APPROVED, "Aprobada", "la franquicia del seguro");

        assertThat(caseTitles(cases(c, ProjectVisibility.all(), "franquicia"))).containsExactly("Aprobada");
    }

    @Test
    void twoMatchingPagesGiveOneCase() {
        CompanyId c = newCompany();
        caseDocument(c, "Caso largo", "la franquicia del seguro en la primera página",
                "otra franquicia del seguro en la segunda");

        SimilarCasesResult result = cases(c, ProjectVisibility.all(), "franquicia seguro");

        assertThat(result.cases()).hasSize(1);
        SimilarCase found = result.cases().get(0);
        assertThat(found.pageCount()).isEqualTo(2);
        assertThat(found.pages()).extracting(CasePage::number).containsExactly(1, 2);
        assertThat(found.pages().get(0).text()).isEqualTo("la franquicia del seguro en la primera página");
        assertThat(found.truncated()).isFalse();
        assertThat(found.sourceType()).isEqualTo(SourceType.ORIGINAL_DOCUMENT);
        assertThat(found.excerpt()).isNotBlank();
        assertThat(found.addedAt()).isEqualTo(NOW);
    }

    @Test
    void bestPageBeyondThreeIsAppendedAndTruncated() {
        CompanyId c = newCompany();
        caseDocument(c, "Caso de cinco", "uno", "dos", "tres", "cuatro", "la filtración de la azotea");

        SimilarCase found = cases(c, ProjectVisibility.all(), "filtración").cases().get(0);

        assertThat(found.pageCount()).isEqualTo(5);
        assertThat(found.pages()).extracting(CasePage::number).containsExactly(1, 2, 3, 5);
        assertThat(found.truncated()).isTrue();
    }

    @Test
    void restrictedProjectOfOthersIsInvisible() {
        CompanyId c = newCompany();
        ProjectId closed = newProject(c, "Cerrado");
        ProjectId mine = newProject(c, "Mío");
        inProject(caseDocument(c, "Mejor caso del cerrado", "franquicia franquicia franquicia ATL-4417-2024"), closed);
        inProject(caseDocument(c, "Caso del mío", "la franquicia"), mine);
        cardIn(c, closed, "Ficha del cerrado", "la franquicia ATL-4417-2024");

        SimilarCasesResult result = cases(c, ProjectVisibility.of(Set.of(mine)), "franquicia", "ATL-4417-2024");

        assertThat(caseTitles(result)).containsExactly("Caso del mío");
        assertThat(caseTitles(cases(c, ProjectVisibility.of(Set.of()), "franquicia"))).isEmpty();
    }

    @Test
    void openProjectOfOthersContributesCases() {
        CompanyId c = newCompany();
        ProjectId open = new ProjectId(UUID.randomUUID());
        orgs.saveProject(new com.e2d.odin.domain.organization.Project(open, c, "Abierto", false));
        inProject(caseDocument(c, "Caso del abierto", "la franquicia"), open);

        assertThat(caseTitles(cases(c, ProjectVisibility.of(Set.of(open)), "franquicia")))
                .containsExactly("Caso del abierto");
    }

    @Test
    void projectlessCaseIsVisibleToEveryone() {
        CompanyId c = newCompany();
        caseDocument(c, "Caso general", "la franquicia");
        card(c, CardStatus.APPROVED, "Ficha general", "la franquicia");

        assertThat(caseTitles(cases(c, ProjectVisibility.of(Set.of()), "franquicia")))
                .containsExactlyInAnyOrder("Caso general", "Ficha general");
    }

    @Test
    void supersededCaseComesAfterCurrentOnesWithReplacement() {
        CompanyId c = newCompany();
        Document old = caseDocument(c, "Caso 2022", "póliza ATL-4417-2024 franquicia");
        Document current = caseDocument(c, "Caso 2024", "la póliza vigente");
        supersede(c, old, current);

        List<SimilarCase> found = cases(c, ProjectVisibility.all(), "ATL-4417-2024", "póliza").cases();

        assertThat(found).extracting(SimilarCase::title).containsExactly("Caso 2024", "Caso 2022");
        assertThat(found.get(1).superseded()).isTrue();
        assertThat(found.get(1).replacement()).contains(DocumentRef.of(current));
        assertThat(found.get(0).superseded()).isFalse();
        assertThat(found.get(0).replacement()).isEmpty();
    }

    @Test
    void otherCompanyCasesAreNeverReturned() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        caseDocument(b, "Caso de B", "la franquicia");
        card(b, CardStatus.APPROVED, "Ficha de B", "la franquicia");

        assertThat(cases(a, ProjectVisibility.all(), "franquicia").noSource()).isTrue();
        assertThat(caseTitles(cases(b, ProjectVisibility.all(), "franquicia")))
                .containsExactlyInAnyOrder("Caso de B", "Ficha de B");
    }

    @Test
    void noAuthorIsSelected() {
        assertThat(java.util.Arrays.stream(SimilarCase.class.getRecordComponents()).map(rc -> rc.getName().toLowerCase()))
                .noneMatch(name -> name.contains("author") || name.contains("uploa") || name.contains("user"));
    }

    private List<String> titles(CompanyId company, ProjectVisibility visibility, Optional<ProjectId> project) {
        return search.search(new SearchQuery(List.of("franquicia"), CompanyScope.of(company), visibility, project, 10, Db.NOBODY))
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

    private void ownCard(CompanyId company, UserId author, Optional<ProjectId> project, CardStatus status,
            String title, String body) {
        cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), company, project, title, body, Optional.empty(),
                author, NOW, status, Optional.empty()));
    }

    private List<SearchHit> findAs(UserId searcher, CompanyId company, ProjectVisibility visibility, String term) {
        return search.search(new SearchQuery(List.of(term), CompanyScope.of(company), visibility, Optional.empty(), 5,
                searcher)).hits();
    }

    private List<SimilarCase> casesAs(UserId searcher, CompanyId company, ProjectVisibility visibility, String term) {
        return search.findSimilarCases(new SimilarCasesQuery(List.of(term), CompanyScope.of(company), visibility,
                Optional.empty(), 5, searcher)).cases();
    }

    @Test
    void authorSeesOwnPendingCardMarked() {
        CompanyId c = newCompany();
        UserId author = newUser(c);
        ownCard(c, author, Optional.empty(), CardStatus.PENDING, "Mi pendiente", "filtración pendiente de revisar");
        card(c, CardStatus.APPROVED, "Aprobada", "filtración aprobada");
        page(c, "Documento", DocumentStatus.READY, "filtración en un documento");

        List<SearchHit> hits = findAs(author, c, ProjectVisibility.all(), "filtración");

        assertThat(hits).extracting(SearchHit::title).containsExactlyInAnyOrder("Mi pendiente", "Aprobada", "Documento");
        assertThat(hits).filteredOn(h -> h.title().equals("Mi pendiente")).singleElement()
                .extracting(SearchHit::reviewStatus).isEqualTo(Optional.of(ReviewStatus.PENDING));
        assertThat(hits).filteredOn(h -> h.title().equals("Aprobada")).singleElement()
                .extracting(SearchHit::reviewStatus).isEqualTo(Optional.of(ReviewStatus.APPROVED));
        assertThat(hits).filteredOn(h -> h.title().equals("Documento")).singleElement()
                .extracting(SearchHit::reviewStatus).isEqualTo(Optional.empty());
    }

    @Test
    void colleagueDoesNotSeePendingCard() {
        CompanyId c = newCompany();
        ownCard(c, newUser(c), Optional.empty(), CardStatus.PENDING, "Título exacto pendiente", "filtración pendiente");

        assertThat(findAs(newUser(c), c, ProjectVisibility.all(), "Título exacto pendiente")).isEmpty();
        assertThat(findAs(newUser(c), c, ProjectVisibility.all(), "filtración")).isEmpty();
    }

    @Test
    void rejectedCardIsNeverFound() {
        CompanyId c = newCompany();
        UserId author = newUser(c);
        ownCard(c, author, Optional.empty(), CardStatus.REJECTED, "Rechazada", "filtración rechazada");
        card(c, CardStatus.APPROVED, "Aprobada", "filtración aprobada");

        assertThat(findAs(author, c, ProjectVisibility.all(), "filtración")).extracting(SearchHit::title)
                .containsExactly("Aprobada");
        assertThat(casesAs(author, c, ProjectVisibility.all(), "filtración")).extracting(SimilarCase::title)
                .containsExactly("Aprobada");
    }

    @Test
    void ownPendingCardInRestrictedProjectNoLongerVisibleIsHidden() {
        CompanyId c = newCompany();
        UserId author = newUser(c);
        ProjectId closed = newProject(c, "Cerrado");
        ownCard(c, author, Optional.of(closed), CardStatus.PENDING, "Pendiente del cerrado", "filtración pendiente");

        assertThat(findAs(author, c, ProjectVisibility.of(Set.of()), "filtración")).isEmpty();
        assertThat(casesAs(author, c, ProjectVisibility.of(Set.of()), "filtración")).isEmpty();
        assertThat(findAs(author, c, ProjectVisibility.of(Set.of(closed)), "filtración")).hasSize(1);
    }

    @Test
    void samePersonWithKeyOfOtherCompanySeesNoPendingCardOfTheFirst() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId author = newUser(a);
        ownCard(a, author, Optional.empty(), CardStatus.PENDING, "Pendiente de A", "filtración pendiente");

        assertThat(findAs(author, b, ProjectVisibility.all(), "filtración")).isEmpty();
        assertThat(casesAs(author, b, ProjectVisibility.all(), "filtración")).isEmpty();
    }

    @Test
    void authorSeesOwnPendingCardInSimilarCases() {
        CompanyId c = newCompany();
        UserId author = newUser(c);
        ownCard(c, author, Optional.empty(), CardStatus.PENDING, "Mi pendiente", "la franquicia pendiente");
        card(c, CardStatus.APPROVED, "Aprobada", "la franquicia aprobada");
        caseDocument(c, "Caso", "la franquicia del caso");

        List<SimilarCase> found = casesAs(author, c, ProjectVisibility.all(), "franquicia");

        assertThat(found).extracting(SimilarCase::title).containsExactlyInAnyOrder("Mi pendiente", "Aprobada", "Caso");
        assertThat(found).filteredOn(x -> x.title().equals("Mi pendiente")).singleElement()
                .extracting(SimilarCase::reviewStatus).isEqualTo(Optional.of(ReviewStatus.PENDING));
        assertThat(found).filteredOn(x -> x.title().equals("Aprobada")).singleElement()
                .extracting(SimilarCase::reviewStatus).isEqualTo(Optional.of(ReviewStatus.APPROVED));
        assertThat(found).filteredOn(x -> x.title().equals("Caso")).singleElement()
                .extracting(SimilarCase::reviewStatus).isEqualTo(Optional.empty());
    }

    @Test
    void colleagueDoesNotSeePendingCardInSimilarCases() {
        CompanyId c = newCompany();
        ownCard(c, newUser(c), Optional.empty(), CardStatus.PENDING, "Título exacto pendiente", "la franquicia");

        assertThat(casesAs(newUser(c), c, ProjectVisibility.all(), "franquicia")).isEmpty();
        assertThat(casesAs(newUser(c), c, ProjectVisibility.all(), "Título exacto pendiente")).isEmpty();
    }
}
