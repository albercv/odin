package com.e2d.odin.domain.search;

import java.util.List;

public record SearchResult(List<SearchHit> hits) {

    public SearchResult {
        hits = List.copyOf(hits);
    }

    public static SearchResult noSource() {
        return new SearchResult(List.of());
    }
}
