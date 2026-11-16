package com.e2d.odin.adapter.web.admin;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class Details {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Details() {
    }

    static Map<String, Object> parse(String json) {
        return JSON.readValue(json, new TypeReference<Map<String, Object>>() { });
    }

    static String compact(String json) {
        return JSON.writeValueAsString(parse(json));
    }
}
