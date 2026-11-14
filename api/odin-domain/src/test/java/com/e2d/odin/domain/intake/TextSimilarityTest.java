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
