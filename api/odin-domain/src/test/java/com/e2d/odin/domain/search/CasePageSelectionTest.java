package com.e2d.odin.domain.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CasePageSelectionTest {

    private static List<CasePage> pages(int count, int length) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> new CasePage(n, "x".repeat(length))).toList();
    }

    private static List<Integer> numbers(CasePageSelection.Selection selection) {
        return selection.pages().stream().map(CasePage::number).toList();
    }

    @Test
    void shortCaseIsReturnedWhole() {
        var selection = CasePageSelection.select(pages(2, 500), 2);

        assertThat(numbers(selection)).containsExactly(1, 2);
        assertThat(selection.truncated()).isFalse();
    }

    @Test
    void stopsAtThreePages() {
        var selection = CasePageSelection.select(pages(5, 100), 1);

        assertThat(numbers(selection)).containsExactly(1, 2, 3);
        assertThat(selection.truncated()).isTrue();
    }

    @Test
    void stopsAtTwelveThousandCharacters() {
        var selection = CasePageSelection.select(pages(3, 5_000), 1);

        assertThat(numbers(selection)).containsExactly(1, 2);
        assertThat(selection.truncated()).isTrue();
    }

    @Test
    void bestPageBeyondLimitIsAppended() {
        var selection = CasePageSelection.select(pages(6, 100), 5);

        assertThat(numbers(selection)).containsExactly(1, 2, 3, 5);
        assertThat(selection.truncated()).isTrue();
    }

    @Test
    void bestPageLongerThanLimitIsStillReturned() {
        List<CasePage> pages = List.of(new CasePage(1, "a".repeat(100)), new CasePage(2, "b".repeat(100)),
                new CasePage(3, "c".repeat(100)), new CasePage(4, "d".repeat(20_000)));

        var selection = CasePageSelection.select(pages, 4);

        assertThat(numbers(selection)).containsExactly(1, 2, 3, 4);
        assertThat(selection.pages().get(3).text()).hasSize(20_000);
        assertThat(selection.truncated()).isFalse();
    }
}
