package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.DocumentId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupersessionCheckTest {

    private static final Map<String, String> OLD_TO_CURRENT = Map.of("emp/p/viejo.txt", "emp/p/vigente.txt");

    @Test
    void acceptsCurrentFirstAndMarkedSuperseded() {
        List<SearchHit> hits = List.of(current("emp/p/vigente.txt"), superseded("emp/p/viejo.txt", "emp/p/vigente.txt"));

        assertThat(SupersessionCheck.problems("Q1", hits, OLD_TO_CURRENT)).isEmpty();
    }

    @Test
    void reportsSupersededBeforeCurrent() {
        List<SearchHit> hits = List.of(superseded("emp/p/viejo.txt", "emp/p/vigente.txt"), current("emp/p/vigente.txt"));

        assertThat(SupersessionCheck.problems("Q1", hits, OLD_TO_CURRENT))
                .singleElement().asString().contains("Q1", "emp/p/vigente.txt", "delante");
    }

    @Test
    void reportsMissingMarkOrWrongReplacement() {
        List<SearchHit> unmarked = List.of(current("emp/p/viejo.txt"));
        List<SearchHit> wrong = List.of(superseded("emp/p/viejo.txt", "emp/p/otro.txt"));
        List<SearchHit> noReplacement = List.of(hit("emp/p/viejo.txt", true, Optional.empty()));

        assertThat(SupersessionCheck.problems("Q1", unmarked, OLD_TO_CURRENT)).singleElement().asString()
                .contains("Q1", "emp/p/viejo.txt", "sin marca");
        assertThat(SupersessionCheck.problems("Q2", wrong, OLD_TO_CURRENT)).singleElement().asString()
                .contains("Q2", "emp/p/viejo.txt", "emp/p/otro.txt");
        assertThat(SupersessionCheck.problems("Q3", noReplacement, OLD_TO_CURRENT)).hasSize(1);
    }

    @Test
    void reportsUnexpectedMark() {
        List<SearchHit> hits = List.of(superseded("emp/p/libre.txt", "emp/p/vigente.txt"));

        assertThat(SupersessionCheck.problems("Q1", hits, OLD_TO_CURRENT)).singleElement().asString()
                .contains("Q1", "emp/p/libre.txt", "no está en las parejas");
    }

    private static SearchHit current(String title) {
        return hit(title, false, Optional.empty());
    }

    private static SearchHit superseded(String title, String replacementTitle) {
        return hit(title, true, Optional.of(new DocumentRef(new DocumentId(UUID.randomUUID()), replacementTitle)));
    }

    private static SearchHit hit(String title, boolean superseded, Optional<DocumentRef> replacement) {
        return new SearchHit(SourceType.ORIGINAL_DOCUMENT, UUID.randomUUID(), title, Optional.of(1), true, false, Optional.empty(),
                "autor", Instant.EPOCH, "", 1.0, superseded, replacement, Optional.empty());
    }
}
