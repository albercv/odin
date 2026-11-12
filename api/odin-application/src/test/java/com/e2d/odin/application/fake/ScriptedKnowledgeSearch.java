package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SimilarCasesQuery;
import com.e2d.odin.domain.search.SimilarCasesResult;
import java.util.List;

public class ScriptedKnowledgeSearch implements KnowledgeSearch {

    private SearchResult result = SearchResult.noSource();
    private SearchQuery lastQuery;
    private SimilarCasesResult similarCases = new SimilarCasesResult(List.of());
    private SimilarCasesQuery lastSimilarCasesQuery;

    public void returns(SearchResult newResult) {
        result = newResult;
    }

    public void returnsCases(SimilarCasesResult newResult) {
        similarCases = newResult;
    }

    public SearchQuery lastQuery() {
        return lastQuery;
    }

    public SimilarCasesQuery lastSimilarCasesQuery() {
        return lastSimilarCasesQuery;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        lastQuery = query;
        return result;
    }

    @Override
    public SimilarCasesResult findSimilarCases(SimilarCasesQuery query) {
        lastSimilarCasesQuery = query;
        return similarCases;
    }
}
