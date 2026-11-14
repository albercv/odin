package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.intake.DuplicateFinder.Candidate;
import com.e2d.odin.domain.intake.DuplicateFinder.Duplicate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Las reglas de clasificación de parejas del estudio (classify_pair y predict), con textos pequeños. */
class DuplicateFinderTest {

    private static final String BODY = IntStream.range(0, 120).mapToObj(i -> "clausula" + i)
            .collect(Collectors.joining(" "));

    private static String policy(String id, String date, String premium, String extra) {
        return "CONDICIONES DEL SEGURO\nPóliza nº " + id + "\nFecha: " + date + "\nPrima de " + premium + " euros\n" + BODY
                + extra;
    }

    private static Candidate doc(String id, String project, boolean scan, String text) {
        return new Candidate(id, project, scan, text);
    }

    @Test
    void sameTemplateInAnotherCommunityIsNotProposed() {
        String text = policy("ATL-1111-2023", "1 de marzo de 2023", "300", "");
        assertThat(DuplicateFinder.find(List.of(doc("a", "calle abeto 16", false, text),
                doc("b", "calle olmo 25", false, policy("ATL-2222-2024", "1 de marzo de 2024", "490", ""))))).isEmpty();
    }

    @Test
    void aScanWithTheSameDatesAndAmountsIsTheSameAsScan() {
        String text = policy("ATL-1111-2023", "1 de marzo de 2023", "300", "");
        List<Duplicate> found = DuplicateFinder.find(List.of(doc("orig", "p", false, text),
                doc("scan", "p", true, text.replace("clausula7 ", "clausu1a7 "))));
        assertThat(found).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(ProposalKind.SAME_AS_SCAN);
            assertThat(d.documentId()).isEqualTo("scan");
            assertThat(d.otherId()).isEqualTo("orig");
            assertThat(text).contains(d.reason());
        });
    }

    @Test
    void aRenewedPolicySupersedesTheOlderOneEvenIfTheListIsReversed() {
        Candidate old = doc("old", "p", false, policy("ATL-1111-2023", "1 de marzo de 2023", "300", ""));
        Candidate renewed = doc("new", "p", false, policy("ATL-2222-2025", "1 de marzo de 2025", "490", ""));
        for (List<Candidate> docs : List.of(List.of(old, renewed), List.of(renewed, old))) {
            assertThat(DuplicateFinder.find(docs)).singleElement().satisfies(d -> {
                assertThat(d.kind()).isEqualTo(ProposalKind.SUPERSEDES);
                assertThat(d.documentId()).isEqualTo("new");
                assertThat(d.otherId()).isEqualTo("old");
                assertThat(d.reason()).isEqualTo("1 de marzo de 2025");
            });
        }
    }

    @Test
    void theTextSayingItReplacesAPolicyIsEnoughEvenWhenTheTextsDiffer() {
        Candidate old = doc("old", "p", false, policy("ATL-1194-2023", "1 de marzo de 2023", "300", ""));
        Candidate renewed = doc("new", "p", false, "CONDICIONES DEL SEGURO\nPóliza nº ATL-3887-2025\n"
                + "Esta póliza sustituye a la póliza nº ATL-1194-2023.\nTexto completamente distinto sobre otra cosa.");
        assertThat(DuplicateFinder.find(List.of(old, renewed))).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(ProposalKind.SUPERSEDES);
            assertThat(d.documentId()).isEqualTo("new");
            assertThat(d.reason()).isEqualTo("sustituye a la póliza nº ATL-1194-2023");
        });
    }

    @Test
    void twoDigitalDocumentsWithTheSameFactsAreNotAVersion() {
        String text = policy("ATL-1111-2023", "1 de marzo de 2023", "300", "");
        assertThat(DuplicateFinder.find(List.of(doc("a", "p", false, text), doc("b", "p", false, text + " fin")))).isEmpty();
    }
}
