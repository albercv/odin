package com.e2d.odin.application.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.knowledge.QuestionKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DisclosureTest {

    private static final CardDecisions DECISIONS = new CardDecisions(2, 1, 1);
    private static final List<GapGroup> GROUPS = List.of(new GapGroup("goteras", QuestionKind.SEARCH, 3,
            LocalDate.of(2026, 11, 8)));

    @Test
    void gapsAreWithheldWithTwoPeopleAndShownWithThree() {
        assertThat(Disclosure.gaps(GROUPS, 2)).isEqualTo(new GapReport(List.of(), true));
        assertThat(Disclosure.gaps(GROUPS, 3)).isEqualTo(new GapReport(GROUPS, false));
        assertThat(Disclosure.gaps(GROUPS, 3).withheld()).isFalse();
    }

    @Test
    void nothingIsWithheldWhenNobodyAskedAnything() {
        assertThat(Disclosure.gaps(List.of(), 0)).isEqualTo(new GapReport(List.of(), false));
    }

    @Test
    void cardBreakdownIsWithheldWithTwoAuthorsAndShownWithThree() {
        assertThat(Disclosure.cards(4, 1, DECISIONS, 2)).isEqualTo(new CardCounts(4, 1, Optional.empty(), true));
        assertThat(Disclosure.cards(4, 1, DECISIONS, 3))
                .isEqualTo(new CardCounts(4, 1, Optional.of(DECISIONS), false));
    }

    @Test
    void departedRowIsDroppedWithOnePersonAndShownWithTwo() {
        assertThat(Disclosure.departedRow(1, 5, 2)).isEmpty();
        assertThat(Disclosure.departedRow(2, 5, 2)).contains(PersonUsage.departed(5, 2));
        assertThat(Disclosure.departedRow(0, 0, 0)).isEmpty();
    }

    @Test
    void thresholdsAreNamedConstants() {
        assertThat(Disclosure.MIN_PEOPLE_FOR_GAPS).isEqualTo(3);
        assertThat(Disclosure.MIN_AUTHORS_FOR_CARD_BREAKDOWN).isEqualTo(3);
        assertThat(Disclosure.MIN_DEPARTED_FOR_ROW).isEqualTo(2);
        assertThat(Disclosure.WITHHELD_REASON).isEqualTo("FEW_PEOPLE");
    }
}
