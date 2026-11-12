package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchiveLoaderTest {

    @Test
    void splitPagesReadsPageMarks() {
        Map<Integer, String> pages = ArchiveLoader.splitPages("=== Página 1 ===\n uno \n=== Página 2 ===\ndos\n");

        assertThat(pages).containsExactly(Map.entry(1, "uno"), Map.entry(2, "dos"));
    }

    @Test
    void splitPagesWithoutMarksIsOnePage() {
        Map<Integer, String> pages = ArchiveLoader.splitPages("todo el texto\nsin marcas\n");

        assertThat(pages).containsExactly(Map.entry(0, "todo el texto\nsin marcas"));
    }
}
