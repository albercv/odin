package com.e2d.odin.adapter.readers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpreadsheetPagerTest {

    private final SpreadsheetPager pager = new SpreadsheetPager();

    @Test
    void threeRowsMakeOnePageWithTabs() {
        pager.startSheet("Cuotas");
        pager.row(1, List.of("Socio", "Cuota"));
        pager.row(2, List.of("Ana", "10"));
        pager.row(3, List.of("Luis", "20"));
        assertThat(pager.pages()).containsExactly("Hoja «Cuotas»\nSocio\tCuota\nAna\t10\nLuis\t20");
    }

    @Test
    void trailingEmptyCellsDroppedMiddleKeptEmptyRowsSkipped() {
        pager.startSheet("S");
        pager.row(1, List.of("a", "", "c", "", ""));
        pager.row(2, List.of("", " "));
        pager.row(3, List.of("x"));
        assertThat(pager.pages()).containsExactly("Hoja «S»\na\t\tc\nx");
    }

    @Test
    void longSheetSplitsEveryHundredRowsWithRepeatedHeader() {
        pager.startSheet("Cuotas");
        pager.row(1, List.of("Socio", "Cuota"));
        for (int i = 2; i <= 250; i++) {
            pager.row(i, List.of("S" + i, "1"));
        }
        List<String> pages = pager.pages();
        assertThat(pages).hasSize(3);
        assertThat(pages.get(0)).startsWith("Hoja «Cuotas» (filas 1–100)\n");
        assertThat(pages.get(1)).startsWith("Hoja «Cuotas» (filas 101–200)\nSocio\tCuota\nS101\t1\n");
        assertThat(pages.get(2)).startsWith("Hoja «Cuotas» (filas 201–250)\nSocio\tCuota\n");
    }

    @Test
    void pageNeverExceedsTenThousandChars() {
        pager.startSheet("Texto");
        pager.row(1, List.of("Nota"));
        for (int i = 2; i <= 40; i++) {
            pager.row(i, List.of("x".repeat(3000)));
        }
        pager.row(41, List.of("y".repeat(25_000)));
        List<String> pages = pager.pages();
        assertThat(pages).hasSizeGreaterThan(5);
        assertThat(pages).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(SpreadsheetPager.MAX_CHARS));
        assertThat(String.join("", pages)).contains("y".repeat(4000));
    }

    @Test
    void twoSheetsInOrderAndEmptySheetGivesNoPage() {
        pager.startSheet("Uno");
        pager.row(1, List.of("a"));
        pager.startSheet("Vacia");
        pager.row(1, List.of("", ""));
        pager.startSheet("Tres");
        pager.row(1, List.of("b"));
        assertThat(pager.pages()).containsExactly("Hoja «Uno»\na", "Hoja «Tres»\nb");
    }

    @Test
    void overHalfMillionRowsFails() {
        pager.startSheet("Grande");
        assertThatThrownBy(() -> {
            for (int i = 1; i <= SpreadsheetPager.MAX_TOTAL_ROWS + 1; i++) {
                pager.row(i, new ArrayList<>(List.of("a")));
            }
        }).isInstanceOf(UnsupportedFormatException.class).hasMessage("Hoja de cálculo demasiado grande");
    }
}
