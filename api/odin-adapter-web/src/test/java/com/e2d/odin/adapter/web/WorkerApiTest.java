package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class WorkerApiTest extends WebTestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    @Test
    void searchReturnsHitsAndNoSource() throws Exception {
        String key = workerKey(newCompany());
        w.search.returns(new SearchResult(List.of(new SearchHit(SourceType.ORIGINAL_DOCUMENT, UUID.randomUUID(),
                "Manual", Optional.of(3), true, Optional.empty(), "Ana", Fixtures.NOW, "texto", 0.5))));
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"vacaciones\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.noSource").value(false))
                .andExpect(jsonPath("$.hits[0].sourceType").value("ORIGINAL_DOCUMENT"))
                .andExpect(jsonPath("$.hits[0].pageNumber").value(3))
                .andExpect(jsonPath("$.hits[0].authorName").value("Ana"));
        w.search.returns(SearchResult.noSource());
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"nada\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.noSource").value(true)).andExpect(jsonPath("$.hits", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"queries\":[]}", "{\"queries\":[\"  \"]}", "{\"queries\":[null]}"})
    void invalidSearchInputIs400(String body) throws Exception {
        mvc.perform(post("/v1/search").header(AUTH, workerKey(newCompany()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void tooManyOrTooLongQueriesIs400() throws Exception {
        String key = workerKey(newCompany());
        String eleven = IntStream.range(0, 11).mapToObj(i -> "\"q" + i + "\"").collect(Collectors.joining(","));
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[" + eleven + "]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"" + "a".repeat(501) + "\"]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"" + "a".repeat(500) + "\"]}")).andExpect(status().isOk());
    }

    @Test
    void disabledCapabilityIs403WithMessage() throws Exception {
        CompanyId company = Fixtures.companyWith(w.orgs, EnumSet.of(Capability.SAVE_KNOWLEDGE_CARD));
        mvc.perform(post("/v1/search").header(AUTH, workerKey(company)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"x\"]}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu empresa no tiene activada esta función"));
    }

    @Test
    void problemJsonShape() throws Exception {
        mvc.perform(get("/v1/documents/" + UUID.randomUUID() + "/pages/1").header(AUTH, workerKey(newCompany())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("Documento no encontrado"));
        mvc.perform(get("/v1/documents/not-a-uuid/pages/1").header(AUTH, workerKey(newCompany())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void meListsCompanyAndProjects() throws Exception {
        CompanyId company = newCompany();
        Fixtures.project(w.orgs, company);
        mvc.perform(get("/v1/me").header(AUTH, workerKey(company))).andExpect(status().isOk())
                .andExpect(jsonPath("$.company.id").value(company.value().toString()))
                .andExpect(jsonPath("$.projects", hasSize(1)));
    }

    @Test
    void readsPagesAndVerifiesCitations() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        Document doc = Fixtures.readyDocument(w.docs, company, "Las vacaciones son 22 días");
        mvc.perform(get("/v1/documents/" + doc.id().value() + "/pages/1").header(AUTH, key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Las vacaciones son 22 días"))
                .andExpect(jsonPath("$.pageNumber").value(1));
        mvc.perform(get("/v1/documents").header(AUTH, key)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        String quote = "{\"documentId\":\"" + doc.id().value() + "\",\"pageNumber\":1,\"quote\":\"%s\"}";
        mvc.perform(post("/v1/citations/verify").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content(quote.formatted("vacaciones  SON 22"))).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(post("/v1/citations/verify").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content(quote.formatted("vacaciones son 23"))).andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void savingCardReturns201Pending() throws Exception {
        mvc.perform(post("/v1/cards").header(AUTH, workerKey(newCompany())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Truco\",\"body\":\"Se hace así\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Ficha guardada; queda pendiente de revisión."));
    }
}
