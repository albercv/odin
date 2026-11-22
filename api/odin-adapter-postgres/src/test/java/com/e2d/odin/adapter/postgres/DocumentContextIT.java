package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.citation.LinkDocuments;
import com.e2d.odin.domain.citation.LinkRelation;
import com.e2d.odin.domain.context.CorrectionDirection;
import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.context.DocumentVersion;
import com.e2d.odin.domain.context.PageText;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentContextIT extends AbstractPostgresIT {

    private static final String REPLACES = "Esta póliza sustituye y anula a la póliza n.º ZZQ-%d-77001 desde el 1 de octubre de 20%d.";

    private final PostgresDocumentContextRepository context = new PostgresDocumentContextRepository(tx);
    private final LinkDocuments linker = new LinkDocuments(new PostgresDocumentLinkRepository(tx),
            Clock.fixed(NOW, ZoneOffset.UTC), 50);
    private CompanyId company;
    private UserId user;
    private CompanyScope scope;

    @BeforeEach
    void seed() {
        company = newCompany();
        user = newUser(company);
        scope = CompanyScope.of(company);
    }

    private Document doc(Optional<ProjectId> project, String title, String text) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, project, title, "f.txt",
                DocumentFormat.PLAIN_TEXT, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true,
                user, NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, text, "t", "1")));
        return d;
    }

    private Document doc(String title, String text) {
        return doc(Optional.empty(), title, text);
    }

    private void link() {
        while (linker.processBatch() > 0) {
            // hasta que no quede nada por analizar
        }
    }

    /** Cuatro pólizas de un seguro inventado, cada una con la frase que sustituye a la anterior, todas marcadas. */
    private List<Document> policies() {
        List<Document> chain = unmarkedPolicies();
        for (int i = 0; i < 3; i++) {
            forceMark(chain.get(i), chain.get(i + 1));
        }
        return chain;
    }

    private List<Document> unmarkedPolicies() {
        List<Document> chain = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int year = 16 + 3 * i;
            String own = "Póliza n.º ZZQ-" + year + "-77001\nEfecto: 1 de octubre de 20" + year + "\n";
            String replaces = i == 0 ? "" : REPLACES.formatted(year - 3, year);
            chain.add(doc("Póliza ZZQ-" + year, own + replaces + "\nFranquicia por daños por agua " + (150 + 150 * i)));
        }
        link();
        return chain;
    }

    /** La cadena de cuatro con la marca de la segunda quitada: 1 -> 2 marcada, 2 sin marca, 3 -> 4 marcada. */
    private List<Document> policiesWithGap() {
        List<Document> chain = unmarkedPolicies();
        forceMark(chain.get(0), chain.get(1));
        forceMark(chain.get(2), chain.get(3));
        return chain;
    }

    private DocumentContext of(Document d) {
        return context.contexts(scope, ProjectVisibility.all(), List.of(d.id())).get(d.id());
    }

    @Test
    void aMarkedChainOfFourComesOrderedFromTheOldestWithLinkAndHeading() {
        List<Document> chain = policies();

        DocumentContext current = of(chain.get(3));

        assertThat(current.versions()).extracting(DocumentVersion::title)
                .containsExactly("Póliza ZZQ-16", "Póliza ZZQ-19", "Póliza ZZQ-22", "Póliza ZZQ-25");
        assertThat(current.versions()).extracting(DocumentVersion::current).containsExactly(false, false, false, true);
        assertThat(current.versions().get(0).link()).isEmpty();
        assertThat(current.versions().get(3).link()).contains(new PageText(1,
                "Esta póliza sustituye y anula a la póliza n.º ZZQ-22-77001 desde el 1 de octubre de 2025."));
        assertThat(current.versions().get(3).heading().orElseThrow().text()).startsWith("Póliza n.º ZZQ-25-77001")
                .contains("Efecto: 1 de octubre de 2025");
        assertThat(current.versions().get(3).heading().orElseThrow().page()).isEqualTo(1);
        assertThat(current.incomplete()).isFalse();
    }

    @Test
    void anOldVersionGetsTheSameChain() {
        List<Document> chain = policies();

        assertThat(of(chain.get(0)).versions()).isEqualTo(of(chain.get(3)).versions());
        assertThat(of(chain.get(1)).versions()).hasSize(4);
    }

    @Test
    void aDocumentWithoutChainHasNoVersions() {
        Document alone = doc("Suelto", "Texto sin nada.");
        assertThat(of(alone)).isEqualTo(DocumentContext.NONE);
    }

    @Test
    void aDetectedRectificationIsReturnedUnconfirmedInBothDirections() {
        Document original = doc("Factura ZZQ-2025-0302", "Número de factura: ZZQ-2025-0302\nTotal 481,58");
        Document fix = doc("Rectificativa", "FACTURA RECTIFICATIVA\nNúmero de factura rectificativa: ZZQ-2025-0302-R\n"
                + "Rectifica la factura ZZQ-2025-0302: importe correcto 389,00 €.");
        link();

        DocumentCorrection by = of(original).corrections().get(0);
        DocumentCorrection corrects = of(fix).corrections().get(0);

        assertThat(by.direction()).isEqualTo(CorrectionDirection.CORRECTED_BY);
        assertThat(by.relation()).isEqualTo(LinkRelation.RECTIFIES);
        assertThat(by.document().orElseThrow().title()).isEqualTo("Rectificativa");
        assertThat(by.confirmed()).contains(false);
        assertThat(by.hidden()).isFalse();
        assertThat(by.evidence()).contains(new PageText(1, "Rectifica la factura ZZQ-2025-0302: importe correcto 389,00 €."));
        assertThat(corrects.direction()).isEqualTo(CorrectionDirection.CORRECTS);
        assertThat(corrects.document().orElseThrow().title()).isEqualTo("Factura ZZQ-2025-0302");
        assertThat(of(original).corrected()).isTrue();
        assertThat(of(fix).corrected()).isFalse();
        assertThat(context.corrections(scope, ProjectVisibility.all(), List.of(original.id(), fix.id())))
                .containsEntry(original.id(), of(original).corrections());
    }

    @Test
    void aDetectedReplacementNotYetMarkedAppearsAsCorrection() {
        doc("Vieja", "Póliza n.º ZZQ-16-77001");
        Document next = doc("Nueva", "Póliza n.º ZZQ-19-77001\n" + REPLACES.formatted(16, 19));
        link();

        DocumentCorrection correction = of(next).corrections().get(0);

        assertThat(correction.relation()).isEqualTo(LinkRelation.REPLACES);
        assertThat(correction.confirmed()).contains(false);
        assertThat(of(next).versions()).isEmpty();
    }

    @Test
    void aGapInTheChainShowsTheUnconfirmedReplacementAndIsIncomplete() {
        List<Document> chain = policiesWithGap();

        for (Document d : chain) {
            assertThat(of(d).incomplete()).as(d.title()).isTrue();
        }
        DocumentCorrection onTarget = of(chain.get(1)).corrections().get(0);
        assertThat(onTarget.direction()).isEqualTo(CorrectionDirection.CORRECTED_BY);
        assertThat(onTarget.relation()).isEqualTo(LinkRelation.REPLACES);
        assertThat(onTarget.document().orElseThrow().title()).isEqualTo("Póliza ZZQ-22");
        assertThat(onTarget.confirmed()).contains(false);
        DocumentCorrection onSource = of(chain.get(2)).corrections().get(0);
        assertThat(onSource.direction()).isEqualTo(CorrectionDirection.CORRECTS);
        assertThat(onSource.relation()).isEqualTo(LinkRelation.REPLACES);
        assertThat(onSource.confirmed()).contains(false);
    }

    @Test
    void anUnconfirmedRectificationAlsoMakesTheContextIncomplete() {
        Document original = doc("Factura ZZQ-2025-0302", "Número de factura: ZZQ-2025-0302\nTotal 481,58");
        doc("Rectificativa", "Número de factura rectificativa: ZZQ-2025-0302-R\n"
                + "Rectifica la factura ZZQ-2025-0302: importe correcto 389,00 €.");
        link();

        assertThat(of(original).incomplete()).isTrue();
    }

    @Test
    void aCorrectionInAProjectTheWorkerDoesNotSeeIsHiddenWithoutIdOrTitle() {
        ProjectId closed = newProject(company, "Cerrado");
        Document original = doc("Factura ZZQ-2025-0302", "Número de factura: ZZQ-2025-0302\nTotal 481,58");
        doc(Optional.of(closed), "Rectificativa secreta", "Número de factura rectificativa: ZZQ-2025-0302-R\n"
                + "Rectifica la factura ZZQ-2025-0302: importe correcto 389,00 €.");
        link();

        DocumentContext seen = context.contexts(scope, ProjectVisibility.of(Set.of()), List.of(original.id()))
                .get(original.id());

        assertThat(seen.corrections()).hasSize(1);
        DocumentCorrection hidden = seen.corrections().get(0);
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.document()).isEmpty();
        assertThat(hidden.evidence()).isEmpty();
        assertThat(hidden.confirmed()).isEmpty();
        assertThat(hidden.direction()).isEqualTo(CorrectionDirection.CORRECTED_BY);
        assertThat(seen.incomplete()).isTrue();
        assertThat(seen.toString()).doesNotContain("secreta");
        assertThat(of(original).corrections().get(0).hidden()).isFalse();
    }

    @Test
    void aWorkerCannotReadTheRawLinkWhoseSourceOrTargetIsInAClosedProject() {
        ProjectId closed = newProject(company, "Cerrado");
        Document original = doc("Factura ZZQ-2025-0302", "Número de factura: ZZQ-2025-0302");
        doc(Optional.of(closed), "Rectificativa secreta", "Número de factura rectificativa: ZZQ-2025-0302-R\n"
                + "Rectifica la factura ZZQ-2025-0302: importe secreta.");
        Document closedOriginal = doc(Optional.of(closed), "Factura cerrada", "Número de factura: ZZQ-2026-0900");
        doc("Rectificativa abierta", "Número de factura rectificativa: ZZQ-2026-0900-R\n"
                + "Rectifica la factura ZZQ-2026-0900: importe abierta.");
        link();

        Integer rows = tx.inScope(scope, ProjectVisibility.of(Set.of()), jdbc -> jdbc.sql("SELECT count(*) FROM document_link")
                .query(Integer.class).single());
        List<String> quotes = tx.inScope(scope, ProjectVisibility.of(Set.of()), jdbc -> jdbc
                .sql("SELECT evidence_quote FROM document_link").query(String.class).list());

        assertThat(rows).as("solo la cita pendiente del documento abierto").isEqualTo(1);
        assertThat(quotes).hasSize(1).allSatisfy(q -> assertThat(q).contains("abierta").doesNotContain("secreta"));
        assertThat(original).isNotNull();
        assertThat(closedOriginal).isNotNull();
    }

    private record Hidden(String kind, String relation, int count) {
    }

    private List<Hidden> hiddenAround(CompanyScope as, ProjectVisibility visibility, DocumentId seed, DocumentId... chain) {
        return tx.inScope(as, visibility, jdbc -> jdbc.sql("SELECT kind, relation, hidden_count FROM document_hidden_context("
                + ":seed, CAST(string_to_array(:chain, ',') AS uuid[]))").param("seed", seed.value())
                .param("chain", java.util.Arrays.stream(chain).map(c -> c.value().toString())
                        .collect(java.util.stream.Collectors.joining(",")))
                .query((rs, n) -> new Hidden(rs.getString("kind"), rs.getString("relation"), rs.getInt("hidden_count")))
                .list());
    }

    @Test
    void theHiddenFunctionReturnsOnlyCountsAndNothingForWhatTheCallerCannotSee() {
        ProjectId closed = newProject(company, "Cerrado");
        Document original = doc("Factura ZZQ-2025-0302", "Número de factura: ZZQ-2025-0302");
        doc(Optional.of(closed), "Rectificativa secreta", "Número de factura rectificativa: ZZQ-2025-0302-R\n"
                + "Rectifica la factura ZZQ-2025-0302: importe secreta.");
        Document inClosed = doc(Optional.of(closed), "Cerrada", "texto");
        link();

        assertThat(hiddenAround(scope, ProjectVisibility.of(Set.of()), original.id(), original.id()))
                .containsExactly(new Hidden("CORRECTED_BY", "RECTIFIES", 1));
        assertThat(hiddenAround(scope, ProjectVisibility.all(), original.id(), original.id())).isEmpty();
        assertThat(hiddenAround(scope, ProjectVisibility.of(Set.of()), inClosed.id(), inClosed.id())).isEmpty();
        assertThat(hiddenAround(CompanyScope.of(newCompany()), ProjectVisibility.all(), original.id(), original.id()))
                .isEmpty();
        List<String> columns = tx.inScope(scope, ProjectVisibility.of(Set.of()), jdbc -> jdbc
                .sql("SELECT * FROM document_hidden_context(:s, ARRAY[:s]::uuid[])").param("s", original.id().value())
                .query((rs, n) -> {
                    List<String> names = new java.util.ArrayList<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        names.add(rs.getMetaData().getColumnLabel(i));
                    }
                    return names;
                }).list().get(0));
        assertThat(columns).containsExactly("kind", "relation", "hidden_count");
    }

    @Test
    void aHiddenPredecessorMakesTheChainIncomplete() {
        ProjectId closed = newProject(company, "Cerrado");
        Document old = doc(Optional.of(closed), "Vieja cerrada", "Póliza n.º ZZQ-16-77001");
        Document next = doc("Nueva", "Póliza n.º ZZQ-19-77001");
        forceMark(old, next);

        DocumentContext seen = context.contexts(scope, ProjectVisibility.of(Set.of()), List.of(next.id()))
                .get(next.id());

        assertThat(seen.incomplete()).isTrue();
        assertThat(seen.versions()).isEmpty();
        assertThat(seen.toString()).doesNotContain("cerrada");
    }

    @Test
    void anotherCompanyNeverSeesTheContextByRlsNorByTheExplicitFilter() {
        List<Document> chain = policies();
        CompanyScope other = CompanyScope.of(newCompany());
        var ids = List.of(chain.get(3).id());
        var withoutRls = new PostgresDocumentContextRepository(new ScopedTransactions(Db.owner()));

        assertThat(context.contexts(other, ProjectVisibility.all(), ids)).isEmpty();
        assertThat(withoutRls.contexts(other, ProjectVisibility.all(), ids)).isEmpty();
        assertThat(withoutRls.corrections(other, ProjectVisibility.all(), ids).get(chain.get(3).id())).isEmpty();
        assertThat(withoutRls.bestMatches(other, ProjectVisibility.all(), List.of("franquicia"), ids)).isEmpty();
        assertThat(withoutRls.contexts(scope, ProjectVisibility.all(), ids).get(ids.get(0)).versions()).hasSize(4);
    }

    @Test
    void aPendingCitationMakesTheContextIncomplete() {
        Document next = doc("Nueva", "Póliza n.º ZZQ-19-77001\n" + REPLACES.formatted(16, 19));
        link();

        assertThat(of(next).incomplete()).isTrue();
    }

    @Test
    void aCutChainIsIncompleteAndHasNoCurrentVersion() {
        ProjectId closed = newProject(company, "Cerrado");
        Document old = doc("Vieja", "Póliza n.º ZZQ-16-77001");
        Document next = doc(Optional.of(closed), "Nueva cerrada", "Póliza n.º ZZQ-19-77001");
        forceMark(old, next);

        DocumentContext seen = context.contexts(scope, ProjectVisibility.of(Set.of()), List.of(old.id()))
                .get(old.id());

        assertThat(seen.incomplete()).isTrue();
        assertThat(seen.versions()).isEmpty();
        assertThat(seen.toString()).doesNotContain("cerrada");
    }

    @Test
    void aBranchedChainIsReturnedOnceAndMarkedIncomplete() {
        Document a = doc("A", "uno");
        Document b = doc("B", "dos");
        Document c = doc("C", "tres");
        forceMark(a, c);
        forceMark(b, c);

        DocumentContext seen = of(c);

        assertThat(seen.versions()).extracting(DocumentVersion::title).containsExactly("A", "B", "C");
        assertThat(seen.incomplete()).isTrue();
        assertThat(of(a).versions()).isEqualTo(seen.versions());
    }

    @Test
    void aLongChainIsCappedAtTenKeepingTheNewest() {
        List<Document> chain = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            chain.add(doc(String.format("V%02d", i), "texto " + i));
        }
        for (int i = 0; i < 11; i++) {
            forceMark(chain.get(i), chain.get(i + 1));
        }

        DocumentContext seen = of(chain.get(11));

        assertThat(seen.versions()).hasSize(10);
        assertThat(seen.versions().get(0).title()).isEqualTo("V02");
        assertThat(seen.versions().get(9).current()).isTrue();
        assertThat(seen.incomplete()).isTrue();
    }

    @Test
    void theBestMatchOfEachVersionUsesTheSearchText() {
        List<Document> chain = policies();
        List<DocumentId> ids = chain.stream().map(Document::id).toList();

        Map<DocumentId, PageText> matches = context.bestMatches(scope, ProjectVisibility.all(),
                List.of("franquicia daños agua"), ids);

        assertThat(matches).hasSize(4);
        assertThat(matches.get(chain.get(3).id()).page()).isEqualTo(1);
        assertThat(matches.get(chain.get(3).id()).text()).contains("Franquicia");
        assertThat(of(chain.get(3)).withMatches(matches).versions()).allSatisfy(v -> assertThat(v.match()).isPresent());
    }
}
