package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.intake.TextSimilarity.IdPair;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias de textlib.py (parte A del estudio), en JUnit. */
class TextSimilarityTest {

    private static String words(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> prefix + i).collect(Collectors.joining(" "));
    }

    @Test
    void shinglesAreKConsecutiveWords() {
        assertThat(TextSimilarity.shingles("a b c d", 2)).containsExactlyInAnyOrder("a b", "b c", "c d");
        assertThat(TextSimilarity.shingles("a b", 5)).containsExactly("a b");
    }

    @Test
    void jaccard() {
        assertThat(TextSimilarity.jaccard(Set.of(1, 2), Set.of(2, 3))).isEqualTo(1.0 / 3);
        assertThat(TextSimilarity.jaccard(Set.of(), Set.of())).isEqualTo(1.0);
    }

    @Test
    void minHashIsDeterministicAndHas64Functions() {
        Set<String> s = TextSimilarity.shingles(words("palabra", 100), 5);
        assertThat(TextSimilarity.minHash(s)).hasSize(64).containsExactly(TextSimilarity.minHash(s));
    }

    @Test
    void minHashEstimatesTheExactJaccard() {
        // 64 funciones: error típico de 0,06; la tolerancia de 0,15 son unas 2,5 desviaciones
        for (int shared : new int[] {0, 25, 50, 75, 90, 100}) {
            Set<String> a = TextSimilarity.shingles(words("w", 104), 5);
            Set<String> b = TextSimilarity.shingles(words("w", shared + 4).replace("w" + (shared + 3), "z"), 5);
            Set<String> c = new java.util.HashSet<>(IntStream.range(0, 200).mapToObj(i -> "x" + i).toList());
            Set<String> d = new java.util.HashSet<>(IntStream.range(200 - shared * 2, 400 - shared * 2)
                    .mapToObj(i -> "x" + i).toList());
            assertThat(estimate(c, d)).as("compartidas %d", shared).isCloseTo(TextSimilarity.jaccard(c, d),
                    org.assertj.core.data.Offset.offset(0.15));
            assertThat(estimate(a, b)).isCloseTo(TextSimilarity.jaccard(a, b), org.assertj.core.data.Offset.offset(0.15));
        }
    }

    private static double estimate(Set<String> a, Set<String> b) {
        long[] x = TextSimilarity.minHash(a);
        long[] y = TextSimilarity.minHash(b);
        return IntStream.range(0, x.length).filter(i -> x[i] == y[i]).count() / (double) x.length;
    }

    @Test
    void bandsFindTheAlmostEqualAndNotTheDifferent() {
        Set<String> a = TextSimilarity.shingles(words("palabra", 100), 5);
        Set<String> b = TextSimilarity.shingles(words("palabra", 98) + " otra cosa", 5);
        Set<String> c = TextSimilarity.shingles(words("distinta", 100), 5);
        Map<String, long[]> sigs = Map.of("a", TextSimilarity.minHash(a), "b", TextSimilarity.minHash(b),
                "c", TextSimilarity.minHash(c));
        Set<IdPair> pairs = TextSimilarity.lshCandidates(sigs);
        assertThat(pairs).contains(new IdPair("a", "b")).doesNotContain(new IdPair("a", "c"));
    }
}
