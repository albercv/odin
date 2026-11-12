package com.e2d.odin.application.port;

import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SimilarCasesQuery;
import com.e2d.odin.domain.search.SimilarCasesResult;

public interface KnowledgeSearch {

    SearchResult search(SearchQuery query);

    SimilarCasesResult findSimilarCases(SimilarCasesQuery query);
}
