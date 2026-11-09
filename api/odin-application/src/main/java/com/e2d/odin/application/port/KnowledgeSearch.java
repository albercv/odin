package com.e2d.odin.application.port;

import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;

public interface KnowledgeSearch {

    SearchResult search(SearchQuery query);
}
