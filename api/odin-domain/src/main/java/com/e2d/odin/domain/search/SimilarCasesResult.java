package com.e2d.odin.domain.search;

import java.util.List;

public record SimilarCasesResult(List<SimilarCase> cases) {

    public SimilarCasesResult {
        cases = List.copyOf(cases);
    }

    public boolean noSource() {
        return cases.isEmpty();
    }
}
