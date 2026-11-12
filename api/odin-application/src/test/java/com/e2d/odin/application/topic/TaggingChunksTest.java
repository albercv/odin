package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaggingChunksTest {

    private static final DocumentId DOC = new DocumentId(UUID.randomUUID());

    private static DocumentPage page(int number, String text) {
        return new DocumentPage(DOC, number, text, "lector", "1");
    }

    @Test
    void joinsPagesUpToLimit() {
        List<DocumentPage> pages = List.of(page(1, "aaaa"), page(2, "bbbb"), page(3, "cccc"));

        assertThat(TaggingChunks.of(pages, 9)).containsExactly("aaaa\nbbbb", "cccc");
        assertThat(TaggingChunks.of(pages, 100)).containsExactly("aaaa\nbbbb\ncccc");
        assertThat(TaggingChunks.of(List.of(page(1, "   ")), 100)).isEmpty();
    }

    @Test
    void splitsLongPageAtLastSpace() {
        List<DocumentPage> pages = List.of(page(1, "uno"), page(2, "aaaa bbbb cccc dddd"), page(3, "fin"));

        assertThat(TaggingChunks.of(pages, 10)).containsExactly("uno", "aaaa bbbb", "cccc dddd", "fin");
    }

    @Test
    void splitsLongPageWithoutSpacesAtTheLimit() {
        assertThat(TaggingChunks.of(List.of(page(1, "abcdefghij")), 4)).containsExactly("abcd", "efgh", "ij");
    }

    @Test
    void splitGivesTwoHalves() {
        String chunk = "a".repeat(999) + " " + "b".repeat(1500);

        List<String> halves = TaggingChunks.split(chunk);

        assertThat(TaggingChunks.canSplit(chunk)).isTrue();
        assertThat(halves).containsExactly("a".repeat(999), "b".repeat(1500));
    }

    @Test
    void splitNeverGoesBelowTheMinimum() {
        assertThat(TaggingChunks.canSplit("x".repeat(1999))).isFalse();
        assertThat(TaggingChunks.canSplit("x".repeat(2000))).isTrue();
        assertThat(TaggingChunks.split("x".repeat(2000))).containsExactly("x".repeat(1000), "x".repeat(1000));
    }
}
