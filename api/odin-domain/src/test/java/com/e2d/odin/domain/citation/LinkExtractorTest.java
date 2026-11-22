package com.e2d.odin.domain.citation;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkExtractorTest {

    private static List<DocumentPage> pages(String... texts) {
        DocumentId id = new DocumentId(UUID.randomUUID());
        return IntStream.range(0, texts.length).mapToObj(i -> new DocumentPage(id, i + 1, texts[i], "t", "1")).toList();
    }

    private static List<DocumentCitation> citations(String... texts) {
        List<DocumentPage> pages = pages(texts);
        return LinkExtractor.citations(pages, LinkExtractor.ownIdentifiers(pages));
    }

    @Test
    void policyChainReplacesTheCitedPolicy() {
        List<DocumentCitation> found = citations("CONDICIONES\nPóliza n.º XYZ-25-22222\n\nEsta póliza sustituye y anula a la póliza n.º XYZ-19-11111 desde el 1 de octubre de 2019. Otra frase.");

        assertThat(found).singleElement().satisfies(c -> {
            assertThat(c.relation()).isEqualTo(LinkRelation.REPLACES);
            assertThat(c.citedIdentifier()).isEqualTo("XYZ-19-11111");
            assertThat(c.page()).isEqualTo(1);
            assertThat(c.quote()).isEqualTo("Esta póliza sustituye y anula a la póliza n.º XYZ-19-11111 desde el 1 de octubre de 2019.");
        });
    }

    @Test
    void creditNoteRectifiesTheInvoiceAndIsNotItsOwnIdentifierToo() {
        List<DocumentPage> pages = pages("FACTURA RECTIFICATIVA\nNúmero de factura rectificativa: AB-2025-0302-R\n"
                + "Rectifica la factura n.º AB-2025-0302: importe correcto 389,00 €.");

        assertThat(LinkExtractor.ownIdentifiers(pages)).extracting(OwnIdentifier::identifier)
                .containsExactly("AB-2025-0302-R");
        assertThat(LinkExtractor.citations(pages, LinkExtractor.ownIdentifiers(pages)))
                .extracting(DocumentCitation::relation, DocumentCitation::citedIdentifier)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(LinkRelation.RECTIFIES, "AB-2025-0302"));
    }

    @Test
    void codeIsComparedWithWordBoundaries() {
        assertThat(citations("Rectifica la factura AB-2025-0302-R.")).extracting(DocumentCitation::citedIdentifier)
                .containsExactly("AB-2025-0302-R");
        assertThat(citations("Rectifica la factura AB-2025-0302-r.")).isEmpty();
        assertThat(citations("Rectifica la factura XAB-2025-0302X.")).extracting(DocumentCitation::citedIdentifier)
                .containsExactly("XAB-2025-0302X");
    }

    @Test
    void mentionWithoutChangeVerbDoesNotLink() {
        assertThat(citations("El informe del perito nombra la póliza n.º VLM-22-48120 sin más.")).isEmpty();
    }

    @Test
    void replacingWithoutCodeDoesNotLink() {
        assertThat(citations("Este importe sustituye al que venía abonándose. Se modifica la cláusula tercera.")).isEmpty();
        assertThat(citations("Los operarios sustituyeron cuatro metros de colector en la póliza VLM-22-48120.")).isEmpty();
    }

    @Test
    void aDocumentDoesNotCiteItself() {
        assertThat(citations("Póliza nº ATL-4471-2023\nEsta póliza anula a la póliza nº ATL-4471-2023.")).isEmpty();
    }

    @Test
    void verbsMapToRelationsOnAnyPage() {
        List<DocumentCitation> found = citations("Portada sin nada.",
                "Se modifica el contrato n.º TRL-2025-0388 en su cláusula tercera. Además, deja sin efecto la póliza VLM-22-48120.");

        assertThat(found).extracting(DocumentCitation::relation, DocumentCitation::citedIdentifier, DocumentCitation::page)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(LinkRelation.MODIFIES, "TRL-2025-0388", 2),
                        org.assertj.core.groups.Tuple.tuple(LinkRelation.MODIFIES, "VLM-22-48120", 2));
    }

    @Test
    void annulsMapsToReplaces() {
        assertThat(citations("Este escrito anula a la póliza VLM-22-48120.")).extracting(DocumentCitation::relation)
                .containsExactly(LinkRelation.REPLACES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Póliza n.º TRL-2025-0388", "Póliza nº TRL-2025-0388", "Póliza n° TRL-2025-0388",
            "Póliza núm. TRL-2025-0388", "N.º de póliza: TRL-2025-0388", "Nº de póliza TRL-2025-0388",
            "Número de contrato: TRL-2025-0388", "Contrato n.º TRL-2025-0388", "Factura n.º TRL-2025-0388",
            "Presupuesto nº: TRL-2025-0388", "Número de factura TRL-2025-0388", "NÚM. DE PÓLIZA TRL-2025-0388"})
    void ownIdentifierLabelVariants(String line) {
        assertThat(LinkExtractor.ownIdentifiers(pages("TÍTULO\n" + line))).extracting(OwnIdentifier::identifier)
                .containsExactly("TRL-2025-0388");
    }

    @Test
    void ownIdentifierOnlyInTheFirstLinesOfPageOne() {
        String late = "\nrelleno".repeat(20) + "\n" + "Póliza n.º TRL-2025-0388";
        assertThat(LinkExtractor.ownIdentifiers(pages("x" + late))).isEmpty();
        assertThat(LinkExtractor.ownIdentifiers(pages("Portada", "Póliza n.º TRL-2025-0388"))).isEmpty();
    }

    @Test
    void codesNeedADigitAndTwoBlocks() {
        assertThat(LinkExtractor.ownIdentifiers(pages("Póliza n.º ABC\nContrato n.º ABC-DEF\nFactura n.º 2025"))).isEmpty();
    }
}
