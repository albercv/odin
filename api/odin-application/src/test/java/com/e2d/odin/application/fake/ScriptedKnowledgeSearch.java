package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;

public class ScriptedKnowledgeSearch implements KnowledgeSearch {

    private SearchResult result = SearchResult.noSource();
    private SearchQuery lastQuery;

    public void returns(SearchResult newResult) {
        result = newResult;
    }

    public SearchQuery lastQuery() {
        return lastQuery;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        lastQuery = query;
        return result;
    }
}
