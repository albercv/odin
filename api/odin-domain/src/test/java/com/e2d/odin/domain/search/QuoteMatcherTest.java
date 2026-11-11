package com.e2d.odin.domain.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteMatcherTest {

    private static final String PAGE = "La clausula de   franquicia\nse aplica\tsiempre.";

    @Test
    void matchesAcrossLineBreaks() {
        assertThat(QuoteMatcher.containsLiteral(PAGE, "franquicia se aplica siempre")).isTrue();
    }

    @Test
    void matchesAcrossNonBreakingSpaces() {
        assertThat(QuoteMatcher.containsLiteral("La\u00a0franquicia\u2009se\u202faplica", "franquicia se aplica"))
                .isTrue();
    }

    @Test
    void matchesIgnoringCase() {
        assertThat(QuoteMatcher.containsLiteral(PAGE, "FRANQUICIA SE APLICA")).isTrue();
    }

    @Test
    void rejectsChangedAccent() {
        assertThat(QuoteMatcher.containsLiteral("La franquicia es fija", "fránquicia")).isFalse();
    }

    @Test
    void rejectsBlankQuote() {
        assertThat(QuoteMatcher.containsLiteral(PAGE, "  \n ")).isFalse();
        assertThat(QuoteMatcher.containsLiteral(PAGE, null)).isFalse();
    }
}
